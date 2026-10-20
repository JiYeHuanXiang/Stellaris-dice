package stellaris.dice.diceFunction.draw

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import org.json.JSONObject
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer
import java.io.Serializable
import java.util.ArrayList





































class DrawFile(
    text: String?,
    val fileName: String,
    val uuid: Long,
    val hidden: Boolean
) : Serializable {

    companion object {

        private const val MAX_YAML_CODE_POINTS = 1_000_000
        private const val MAX_YAML_ALIASES = 50
        private const val MAX_YAML_NESTING = 20

        private val BOM = String(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), Charsets.UTF_8)


        private val suhuiRepeater = Regex("^::(.*)::(.*)", RegexOption.DOT_MATCHES_ALL)


        private val sitanyaRepeater = Regex("(.*)\\*\\*\\*(.*)\\*\\*\\*$", RegexOption.DOT_MATCHES_ALL)
    }

    private val data: ArrayList<DrawData> = ArrayList()


    var format: DrawFileFormat = DrawFileFormat.Unknown


    val size get() = data.size


    val text: String? = text?.removePrefix(BOM)


    val metaData: HashMap<String, String> = HashMap()

    init {
        reload()
    }


    fun findData(name: String): DrawData? {
        data.forEach {
            if (it.isTitle(name)) {
                return it
            }
        }
        return null
    }


    private fun reload() {
        val source = text ?: return
        data.clear()
        metaData.clear()

        var jsonFailure: Throwable? = null
        var yamlFailure: Throwable? = null

        try {
            try {
                loadJson(source)
            } catch (jsonError: Throwable) {
                loadJson5(source)
            }
        } catch (bothJsonError: Throwable) {
            jsonFailure = bothJsonError
            try {
                loadYaml(source)
            } catch (yamlError: Throwable) {
                yamlFailure = yamlError
            }
        }

        if (jsonFailure != null && yamlFailure != null) {
            throw IllegalStateException(
                "Draw File initial failure : \n" +
                    "JSON ERROR [${jsonFailure.message}] \nYAML ERROR [${yamlFailure.message}]"
            )
        }
    }


    private fun loadJson(source: String) {
        data.clear()
        val json = JSONObject(source)
        format = DrawFileFormat.Json
        for (headerKey in json.keys()) {
            val headerArray = json.getJSONArray(headerKey)
            val drawData = DrawData(this, arrayListOf(headerKey), headerKey.startsWith('_'))
            for (i in 0 until headerArray.length()) {
                appendDrawContent(drawData, headerArray.getString(i))
            }
            drawData.freshPriorities()
            data.add(drawData)
        }
    }


    private fun loadJson5(source: String) {
        data.clear()
        val json5 = Jankson.builder().build().load(source)
        format = DrawFileFormat.Json5
        for (headerKey in json5.keys) {
            val headerArray = json5[headerKey]
            if (headerArray is JsonArray) {
                val drawData = DrawData(this, arrayListOf(headerKey), headerKey.startsWith('_'))
                for (i in 0 until headerArray.size) {
                    appendDrawContent(drawData, headerArray.getString(i, ""))
                }
                drawData.freshPriorities()
                data.add(drawData)
            }

        }
    }





    private fun loadYaml(source: String) {
        data.clear()
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = MAX_YAML_ALIASES
            nestingDepthLimit = MAX_YAML_NESTING
            codePointLimit = MAX_YAML_CODE_POINTS
        }
        val constructor = SafeConstructor(options)
        val yaml = Yaml(
            constructor,
            Representer(DumperOptions()),
            DumperOptions(),
            options
        ).load<Map<String, Any>>(source)

        for ((key, value) in yaml) {
            if (value is String) {
                metaData[key] = value
            }
        }
        format = DrawFileFormat.Yaml

        val command = yaml["command"] as String?
            ?: throw NullPointerException("command is null")
        val includes = yaml["includes"] as? ArrayList<*> ?: arrayListOf("default")

        for ((key, value) in yaml) {
            if (value.javaClass != ArrayList::class.java) {
                continue
            }
            if (key == "includes") {
                continue
            }


            val titles = ArrayList<String>()
            if (key == "default") {
                titles.add(command)
            } else {
                if (includes.indexOf(key) != -1) {
                    titles.add(key)
                }
                titles.add("$command $key")
            }

            val drawData = DrawData(this, titles, key.startsWith('_'))
            (value as ArrayList<*>).forEach { item ->
                var content = item as String
                val match = sitanyaRepeater.find(content)
                val priority = if (match != null) {
                    content = match.groupValues[1]
                    match.groupValues[2]
                } else {
                    "1"
                }
                drawData.appendData(content, priority)
            }
            drawData.freshPriorities()
            data.add(drawData)
        }
    }


    private fun appendDrawContent(drawData: DrawData, content: String) {
        val match = suhuiRepeater.find(content)
        if (match == null) {
            drawData.appendData(content, "1")
            return
        }
        val priority = match.groupValues[1]
        val pureContent = match.groupValues[2]
        if (priority.startsWith('[') && priority.endsWith(']')) {
            drawData.appendData(pureContent, priority.substring(1, priority.length - 1))
        } else {
            drawData.appendData(pureContent, priority)
        }
    }


    fun getCommands(): String {
        val commands = StringBuilder()
        data.forEach {
            if (!it.hidden) {
                it.title.forEach { title ->
                    commands.append("draw $title\n")
                }
            }
        }
        return commands.toString()
    }


    fun search(key: String): ArrayList<String> {
        val result = ArrayList<String>()
        data.forEach {
            if (!it.hidden) {
                it.title.forEach { title ->
                    if (title.indexOf(key, ignoreCase = true) != -1) {
                        result.add(title)
                    }
                }
            }
        }
        return result
    }
}
