package stellaris.dice.diceFunction.template

import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.calculation.DiceCalculation
import java.util.regex.Pattern
































class DiceMessageTemplate(
    private val referer: Pattern = Pattern.compile("\\{([a-z_]+)\\}"),
    private val invoke: Pattern = Pattern.compile("#[{](.*?)[}]")
) {


    lateinit var diceMessageTemplateInvoke: DiceMessageTemplateInvoke

    private val templates = HashMap<SingleTemplateKey, String>()


    private class BuildResult {
        val replacedKeys = HashMap<SingleTemplateKey, String>()
        lateinit var text: String
    }


    fun buildAll(text: String): String {
        val built = build(text)
        var outText = built.text


        synchronized(templates) {
            for ((key, value) in templates) {
                if (built.replacedKeys.containsKey(key)) {
                    continue
                }
                val appended = build(key.decode(value)).text
                when {
                    key.addHeadIfNotExist -> outText = "$appended$outText "
                    key.addTailIfNotExist -> outText = "$outText$appended "
                }
            }
        }
        return outText
    }


    fun add(key: String, value: String?, def: String? = value): SingleTemplateKey {
        val singleTemplate = find(key) ?: SingleTemplateKey(key)
        val effective = if (value.isNullOrEmpty()) def else value
        synchronized(templates) {
            if (effective != null) {
                templates[singleTemplate] = effective
            }
        }
        return singleTemplate
    }


    fun get(key: String): String = find(key)?.let { templates[it] } ?: ""


    fun find(key: String): SingleTemplateKey? {
        synchronized(templates) {
            for (templateKey in templates.keys) {
                if (templateKey.keyword.equals(key, ignoreCase = true) ||
                    templateKey.escapeKey.equals(key, ignoreCase = true)
                ) {
                    return templateKey
                }
            }
        }
        return null
    }


    fun remove(key: String): Boolean {
        val found = find(key) ?: return false
        synchronized(templates) {
            return templates.remove(found) != null
        }
    }


    fun removeAll() {
        synchronized(templates) {
            templates.clear()
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        synchronized(templates) {
            for ((key, value) in templates) {
                builder.append("{").append(key.keyword).append("}=").append(value).append(",")
            }
        }
        return builder.toString()
    }



    private fun cloneTemplates(): HashMap<SingleTemplateKey, String> = cloneTemplates(templates)

    private fun cloneTemplates(source: HashMap<SingleTemplateKey, String>): HashMap<SingleTemplateKey, String> =
        HashMap(source)








    private fun build(
        text: String,
        deep: Int,
        availableTemplates: HashMap<SingleTemplateKey, String>,
        replacedCumulativeKeys: HashMap<SingleTemplateKey, String>
    ): String {
        if (deep >= MAX_DEEP) {
            return text
        }
        var outText = text


        if (this::diceMessageTemplateInvoke.isInitialized) {
            val matcher = invoke.matcher(text)
            while (matcher.find()) {
                val whole = matcher.group(0)
                val content = matcher.group(1)
                val parts = content.split("-", limit = 2)
                if (parts.size == 2) {
                    val command = parts[0]
                    val param = build(parts[1], deep + 1, cloneTemplates(availableTemplates), replacedCumulativeKeys)
                    val replaced = diceMessageTemplateInvoke.invoke(this, command, param)
                    if (replaced != null) {
                        outText = outText.replaceFirstLiteral(
                            whole,
                            build(replaced, deep + 1, cloneTemplates(availableTemplates), replacedCumulativeKeys)
                        )
                    }
                }
            }
        }


        val matcher = referer.matcher(outText)
        while (matcher.find()) {
            val matchedKeyword = matcher.group(1)
            val key = find(matchedKeyword) ?: continue
            val value = availableTemplates[key] ?: continue

            val rendered = build(
                value,
                deep + 1,
                cloneTemplates(availableTemplates).apply { remove(key) },
                replacedCumulativeKeys
            )
            val replacement = key.decode(rendered).let {
                if (key.escapeMatch(matchedKeyword)) MessageCodec.htmlEscape(it) else it
            }
            outText = outText.replace(matcher.group(0), replacement, ignoreCase = true)
            replacedCumulativeKeys[key] = value
        }
        return outText
    }

    private fun build(source: String): BuildResult = BuildResult().apply {
        text = build(source, 0, cloneTemplates(), replacedKeys)
    }

    private companion object {

        const val MAX_DEEP = 10
    }
}


private fun String.replaceFirstLiteral(target: String, replacement: String): String {
    val index = indexOf(target)
    return if (index < 0) this else substring(0, index) + replacement + substring(index + target.length)
}
