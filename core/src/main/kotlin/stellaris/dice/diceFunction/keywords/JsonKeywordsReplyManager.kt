package stellaris.dice.diceFunction.keywords

import org.json.JSONArray
import org.json.JSONObject
import stellaris.dice.diceFunction.DiceKeywordsReplyManager
import stellaris.dice.diceFunction.DiceLogger
import stellaris.dice.diceFunction.database.entity.data.KeywordsReplyData
import java.io.File
import java.util.regex.PatternSyntaxException

/**
 * 关键词自动回复的 JSON 存储：`<实例根>/data/keywords.json`。
 */
class JsonKeywordsReplyManager(rootPath: String) : DiceKeywordsReplyManager {

    private val file = File(File(rootPath, "data"), "keywords.json")
    private val entries = ArrayList<KeywordEntry>()

    init {
        load()
    }

    @Synchronized
    override fun list(): ArrayList<KeywordsReplyData> =
        ArrayList(entries.sortedWith(ENTRY_ORDER))

    @Synchronized
    override fun find(mode: KeywordsReplyData.ReplyType, keyword: String): KeywordsReplyData? =
        entries.firstOrNull { it.mode == mode && it.keyword == keyword }

    @Synchronized
    override fun find(uuid: Long): KeywordsReplyData? = entries.firstOrNull { it.uuid == uuid }

    @Synchronized
    override fun update(obj: KeywordsReplyData): Boolean {
        val index = entries.indexOfFirst { it.uuid == obj.uuid }
        if (index < 0) return false
        entries[index] = KeywordEntry(
            uuid = obj.uuid,
            mode = obj.mode,
            weight = obj.weight,
            keyword = obj.keyword,
            content = obj.content,
            legacy = obj.legacy,
            modifyTime = System.currentTimeMillis()
        )
        flush()
        return true
    }

    @Synchronized
    override fun add(
        mode: KeywordsReplyData.ReplyType,
        weight: Int,
        keyword: String,
        content: String,
        legacy: Boolean
    ): KeywordsReplyData? {
        if (keyword.isBlank() || content.isEmpty()) return null
        val entry = KeywordEntry(
            uuid = nextUuid(),
            mode = mode,
            weight = weight.coerceIn(0, MAX_WEIGHT),
            keyword = keyword,
            content = content,
            legacy = legacy,
            modifyTime = System.currentTimeMillis()
        )
        entries.add(entry)
        flush()
        return entry
    }

    @Synchronized
    override fun remove(uuid: Long): Boolean {
        val removed = entries.removeIf { it.uuid == uuid }
        if (removed) flush()
        return removed
    }

    /**
     * 非指令消息的关键词匹配：按权重从高到低，先到先得。
     * 匹配只做原文比较，不做全角/半角归一。
     */
    @Synchronized
    fun findMatch(text: String): KeywordEntry? {
        if (text.isEmpty()) return null
        for (entry in entries.sortedWith(ENTRY_ORDER)) {
            if (entry.keyword.isEmpty() || entry.content.isEmpty()) continue
            if (matches(entry, text)) return entry
        }
        return null
    }

    private fun matches(entry: KeywordEntry, text: String): Boolean = when (entry.mode) {
        KeywordsReplyData.ReplyType.MATCH_FULL -> text == entry.keyword
        KeywordsReplyData.ReplyType.MATCH_BLUR -> text.contains(entry.keyword)
        KeywordsReplyData.ReplyType.MATCH_PREFIX -> text.startsWith(entry.keyword)
        KeywordsReplyData.ReplyType.MATCH_SUFFIX -> text.endsWith(entry.keyword)
        KeywordsReplyData.ReplyType.MATCH_REGEX -> try {
            Regex(entry.keyword, RegexOption.DOT_MATCHES_ALL).containsMatchIn(text)
        } catch (e: PatternSyntaxException) {
            false
        }
    }

    private fun nextUuid(): Long =
        (entries.maxOfOrNull { it.uuid } ?: 0L) + 1L

    private fun load() {
        if (!file.isFile) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val array = root.optJSONArray(FIELD_ENTRIES) ?: JSONArray()
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val mode = runCatching {
                    KeywordsReplyData.ReplyType.valueOf(
                        json.optString("mode", KeywordsReplyData.ReplyType.MATCH_FULL.name)
                    )
                }.getOrDefault(KeywordsReplyData.ReplyType.MATCH_FULL)
                entries.add(
                    KeywordEntry(
                        uuid = json.optLong("uuid"),
                        mode = mode,
                        weight = json.optInt("weight", 0),
                        keyword = json.optString("keyword", ""),
                        content = json.optString("content", ""),
                        legacy = json.optBoolean("legacy", false),
                        modifyTime = json.optLong("modifyTime")
                    )
                )
            }
        } catch (t: Throwable) {
            DiceLogger.error("关键词库载入失败：${file.path}：$t")
        }
    }

    private fun flush() {
        try {
            val array = JSONArray()
            for (entry in entries) {
                array.put(
                    JSONObject()
                        .put("uuid", entry.uuid)
                        .put("mode", entry.mode.name)
                        .put("weight", entry.weight)
                        .put("keyword", entry.keyword)
                        .put("content", entry.content)
                        .put("legacy", entry.legacy)
                        .put("modifyTime", entry.modifyTime)
                )
            }
            val root = JSONObject().put(FIELD_VERSION, FORMAT_VERSION).put(FIELD_ENTRIES, array)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            DiceLogger.error("关键词库写入失败：${file.path}：$t")
        }
    }

    class KeywordEntry(
        override var uuid: Long = 0,
        override var mode: KeywordsReplyData.ReplyType = KeywordsReplyData.ReplyType.MATCH_FULL,
        override var weight: Int = 0,
        override var keyword: String = "",
        override var content: String = "",
        override var legacy: Boolean = false,
        override var modifyTime: Long = 0
    ) : KeywordsReplyData

    private companion object {
        const val FORMAT_VERSION = 1
        const val FIELD_VERSION = "version"
        const val FIELD_ENTRIES = "entries"
        const val MAX_WEIGHT = 9999

        val ENTRY_ORDER: Comparator<KeywordEntry> =
            compareByDescending<KeywordEntry> { it.weight }.thenBy { it.uuid }
    }
}
