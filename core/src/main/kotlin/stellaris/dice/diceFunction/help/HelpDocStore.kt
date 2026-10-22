package stellaris.dice.diceFunction.help

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File

/**
 * 自定义帮助词条：master 用 `.helpdoc` 写入，`.help` 查询时优先命中的就是这里。
 *
 * 落盘 `data/helpdoc.json`：
 * ```
 * {
 *   "mod": "...", "author": "...", "brief": "...", "comment": "...",
 *   "entries":   { "词条": "内容" },
 *   "redirects": { "词条": "目标词条" }
 * }
 * ```
 */
class HelpDocStore(private val rootPath: String) {

    class Match(val keyword: String, val content: String)

    private val entries = LinkedHashMap<String, String>()
    private val redirects = HashMap<String, String>()

    private val storeFile: File
        get() = File(File(rootPath, DiceConstant.SAVE_PATH_DATA), FILE_NAME)

    init {
        reload()
    }

    @Synchronized
    fun reload() {
        entries.clear()
        redirects.clear()
        if (!storeFile.isFile) {
            return
        }
        try {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            root.optJSONObject(FIELD_ENTRIES)?.let { json ->
                json.keys().forEach { key -> entries[key] = json.optString(key) }
            }
            root.optJSONObject(FIELD_REDIRECTS)?.let { json ->
                json.keys().forEach { key -> redirects[key] = json.optString(key) }
            }
        } catch (t: Throwable) {
            DiceLogger.error("自定义帮助词条读取失败，按空库继续：$t", LOG_TAG)
        }
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun keywords(): List<String> = entries.keys.toList()

    /** 词条名上限；超长返回 null。 */
    fun validateKeyword(keyword: String): String? =
        keyword.trim().takeIf { it.isNotEmpty() && it.length <= DiceConstant.MAX_HELPDOC_KEYWORD_LENGTH }

    @Synchronized
    fun setEntry(keyword: String, content: String): Boolean {
        val key = validateKeyword(keyword) ?: return false
        entries[key] = content.trim()
        persist()
        return true
    }

    @Synchronized
    fun deleteEntry(keyword: String): Boolean {
        val removed = entries.remove(keyword.trim()) != null
        if (removed) {
            persist()
        }
        return removed
    }

    @Synchronized
    fun addRedirect(from: String, to: String): Boolean {
        val key = validateKeyword(from) ?: return false
        val target = validateKeyword(to) ?: return false
        redirects[key] = target
        persist()
        return true
    }

    @Synchronized
    fun deleteRedirect(keyword: String): Boolean {
        val removed = redirects.remove(keyword.trim()) != null
        if (removed) {
            persist()
        }
        return removed
    }

    /** 顺着重定向找到最终词条；没有重定向时原样返回。 */
    @Synchronized
    fun resolveRedirect(keyword: String): String {
        var current = keyword.trim()
        repeat(MAX_REDIRECT_DEPTH) {
            current = redirects[current] ?: return current
        }
        return current
    }

    /** 精确命中优先，其次包含匹配。 */
    @Synchronized
    fun find(keyword: String): List<Match> {
        val key = keyword.trim()
        if (key.isEmpty()) {
            return emptyList()
        }
        entries[key]?.let { return listOf(Match(key, it)) }
        return entries.entries
            .filter { it.key.contains(key, ignoreCase = true) }
            .map { Match(it.key, it.value) }
    }

    /** 展开词条内容里的 `{其他词条}` 引用，最多 [MAX_EXPAND_DEPTH] 层。 */
    @Synchronized
    fun expand(content: String): String {
        var text = content
        repeat(MAX_EXPAND_DEPTH) {
            var changed = false
            REFERENCE.findAll(text).forEach { match ->
                val name = match.groupValues[1].trim()
                val replacement = entries[name]
                if (replacement != null) {
                    text = text.replace(match.value, replacement)
                    changed = true
                }
            }
            if (!changed) {
                return text
            }
        }
        return text
    }

    @Synchronized
    private fun persist() {
        try {
            val root = JSONObject()
            root.put("mod", MOD_NAME)
            root.put("author", AUTHOR)
            root.put("brief", BRIEF)
            root.put("comment", COMMENT)
            val entryJson = JSONObject()
            entries.forEach { (key, value) -> entryJson.put(key, value) }
            root.put(FIELD_ENTRIES, entryJson)
            val redirectJson = JSONObject()
            redirects.forEach { (key, value) -> redirectJson.put(key, value) }
            root.put(FIELD_REDIRECTS, redirectJson)
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("自定义帮助词条落盘失败：$t", LOG_TAG)
        }
    }

    private companion object {
        const val FILE_NAME = "helpdoc.json"
        const val FIELD_ENTRIES = "entries"
        const val FIELD_REDIRECTS = "redirects"
        const val MOD_NAME = "自定义 help 文件"
        const val AUTHOR = "dice 用户"
        const val BRIEF = "用户自定义的 help 文件"
        const val COMMENT = "由 .helpdoc 维护"
        const val MAX_REDIRECT_DEPTH = 5
        const val MAX_EXPAND_DEPTH = 5
        const val LOG_TAG = "HelpDoc"
        val REFERENCE = Regex("\\{([^{}]+)}")
    }
}
