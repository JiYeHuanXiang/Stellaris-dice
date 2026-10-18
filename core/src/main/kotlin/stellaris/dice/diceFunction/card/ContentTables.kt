package stellaris.dice.diceFunction.card

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File















class ContentTables(rootPath: String) {


    val skills: Map<String, Int>


    val aliases: Map<String, String>

    init {
        val dataDir = File(rootPath, DiceConstant.SAVE_PATH_DATA)
        skills = loadIntMap(File(dataDir, SKILLS_FILE))
        aliases = loadStringMap(File(dataDir, ALIASES_FILE))
    }


    fun canonical(name: String): String = aliases[name.lowercase()] ?: name


    fun defaultSkill(name: String): Int = skills[name] ?: DEFAULT_SKILL_VALUE

    private fun loadIntMap(file: File): Map<String, Int> {
        val json = readObject(file) ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        json.keys().forEach { key ->
            out[key] = runCatching { json.optInt(key, DEFAULT_SKILL_VALUE) }
                .getOrElse { DEFAULT_SKILL_VALUE }
        }
        return out
    }

    private fun loadStringMap(file: File): Map<String, String> {
        val json = readObject(file) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        json.keys().forEach { key ->
            val value = json.optString(key, "")
            if (value.isNotEmpty()) {
                out[key.lowercase()] = value
            }
        }
        return out
    }

    private fun readObject(file: File): JSONObject? {
        if (!file.isFile) {
            return null
        }
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (t: Throwable) {
            DiceLogger.error("内容表读取失败，按空表继续：${file.name}（$t）", LOG_TAG)
            null
        }
    }

    companion object {


        const val DEFAULT_SKILL_VALUE = 1

        const val SKILLS_FILE = "skills.json"
        const val ALIASES_FILE = "ability-aliases.json"

        private const val LOG_TAG = "ContentTables"
    }
}
