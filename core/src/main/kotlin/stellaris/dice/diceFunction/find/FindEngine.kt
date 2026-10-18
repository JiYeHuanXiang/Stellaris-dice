package stellaris.dice.diceFunction.find

import org.json.JSONArray
import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import stellaris.dice.diceFunction.calculation.DiceCalculation
import java.io.File
import java.util.Locale
import kotlin.random.Random











class FindEngine(private val rootPath: String) {

    data class Entry(
        val group: String,
        val module: String,
        val title: String,
        val display: String,
        val content: String,
        val keywords: List<String>
    )

    sealed class Result {
        data class One(val entry: Entry) : Result()
        data class ListPage(
            val entries: List<Entry>,
            val total: Int,
            val page: Int,
            val pages: Int,
            val pageSize: Int
        ) : Result()
        data class Error(val message: String) : Result()
        object Help : Result()
    }

    class Args {
        var keywords: String = ""
        var group: String? = null
        var number: Int? = null
        var random: Boolean = false
        var help: Boolean = false
        var groupCommand: Boolean = false
        var groupArgument: String = ""
        var page: Int = 1
        var pageSize: Int = DEFAULT_PAGE_SIZE
        var error: String? = null
    }

    private var entries: List<Entry> = emptyList()
    private var synonyms: Map<String, Set<String>> = emptyMap()
    private val lastResults = LinkedHashMap<String, List<Entry>>()

    private val directory: File
        get() = File(rootPath, DiceConstant.SAVE_PATH_FIND)

    init {
        reload()
    }


    fun reload(): Int {
        val docs = ArrayList<Doc>()
        val rawSynonyms = LinkedHashMap<String, MutableList<String>>()
        val files = directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?: emptyList()

        files.forEach { file ->
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val stem = file.nameWithoutExtension
                val group = stem.substringBefore('-').ifBlank { stem }
                val mod = json.optString("mod", group)
                val module = mod.substringBefore('（').substringBefore('(').trim()
                    .ifBlank { group }
                val help = json.optJSONObject("helpdoc")
                if (help != null && help.length() > 0) {
                    val keywordsJson = json.optJSONObject("keywords")
                    val entries = LinkedHashMap<String, String>()
                    val keywords = HashMap<String, List<String>>()
                    for (title in help.keys()) {
                        val content = help.optString(title, "").trim()
                        if (title.isBlank() || content.isEmpty()) continue
                        entries[title] = content
                        keywords[title] = stringList(keywordsJson?.opt(title))
                    }
                    docs.add(Doc(group, module, entries, keywords))
                }
                json.optJSONObject("synonyms")?.let { table ->
                    for (alias in table.keys()) {
                        val values = stringList(table.opt(alias))
                        if (values.isNotEmpty()) {
                            rawSynonyms.getOrPut(alias) { ArrayList() }.addAll(values)
                        }
                    }
                }
            } catch (t: Throwable) {
                DiceLogger.error("词条库加载失败，已跳过：${file.name}（$t）", LOG_TAG)
            }
        }

        val perGroup = docs.groupingBy { it.group }.eachCount()
        val rebuilt = ArrayList<Entry>()
        docs.forEach { doc ->
            val multi = (perGroup[doc.group] ?: 1) > 1
            val alreadyPrefixed = doc.entries.keys.any { it.startsWith("${doc.module}/") }
            val prefix = if (multi && !alreadyPrefixed) "${doc.module}/" else ""
            doc.entries.forEach { (title, content) ->
                rebuilt.add(
                    Entry(
                        group = doc.group,
                        module = doc.module,
                        title = title,
                        display = prefix + title,
                        content = content,
                        keywords = doc.keywords[title] ?: emptyList()
                    )
                )
            }
        }
        entries = rebuilt
        synonyms = expandSynonyms(rawSynonyms)
        return rebuilt.size
    }


    val entryCount: Int get() = entries.size


    fun groups(): List<String> = entries.map { it.group }.distinct().sorted()


    fun resolveGroup(input: String?): String? {
        if (input == null) return null
        val normalized = normalize(input)
        if (normalized.isEmpty()) return null
        return groups().firstOrNull { normalize(it) == normalized }
            ?: entries.firstOrNull { normalize(it.module) == normalized }?.group
    }


    fun parse(raw: String): Args {
        val args = Args()
        val tokens = raw.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            args.help = true
            return args
        }
        if (tokens[0].equals("group", true) || tokens[0] == "分组") {
            args.groupCommand = true
            args.groupArgument = tokens.drop(1).joinToString(" ").trim()
            return args
        }
        val words = ArrayList<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            when {
                token.equals("--rand", true) || token.equals("--random", true) ||
                    token == "--随机" -> args.random = true
                token == "-h" || token == "--help" -> args.help = true
                token == "-n" || token == "--num" -> {
                    val value = tokens.getOrNull(index + 1)?.toIntOrNull()
                    if (value == null) {
                        args.error = "「$token」后面要跟一个数字，例如 .find 火球 -n 10"
                        return args
                    }
                    args.pageSize = value.coerceIn(1, MAX_PAGE_SIZE)
                    index++
                }
                token == "-p" || token == "--page" -> {
                    val value = tokens.getOrNull(index + 1)?.toIntOrNull()
                    if (value == null) {
                        args.error = "「$token」后面要跟一个页码，例如 .find 火球 -p 2"
                        return args
                    }
                    args.page = value.coerceAtLeast(1)
                    index++
                }
                token.startsWith("#") && token.length > 1 -> args.group = token.substring(1)
                else -> words.add(token)
            }
            index++
        }
        args.keywords = words.joinToString(" ").trim()
        if (words.size == 1) {
            words[0].toIntOrNull()?.let { args.number = it }
        }
        return args
    }







    fun search(args: Args, defaultGroup: String?, scope: String): Result {
        args.error?.let { return Result.Error(it) }
        if (args.help) return Result.Help

        val wantedGroup = args.group ?: defaultGroup
        val resolvedGroup = resolveGroup(wantedGroup)
        if (wantedGroup != null && resolvedGroup == null) {
            return Result.Error("没有这个分组：$wantedGroup（可用：${groups().joinToString("、")}）")
        }
        val pool = entries.filter { resolvedGroup == null || it.group == resolvedGroup }

        if (args.number != null) {
            val list = lastResults[scope]
                ?: return Result.Error("还没有可用的列表；先 .find 关键词 查一次")
            val entry = list.getOrNull(args.number!! - 1)
                ?: return Result.Error("编号超出范围：${args.number}（上次列表共 ${list.size} 条）")
            return Result.One(entry)
        }

        if (args.random) {
            if (pool.isEmpty()) return Result.Error("没有可抽取的词条")
            return Result.One(pool[Random.nextInt(pool.size)])
        }

        if (args.keywords.isBlank()) return Result.Help

        val scored = pool.map { it to score(it, args.keywords) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Entry, Int>> { it.second }
                    .thenBy { it.first.display.length }
                    .thenBy { it.first.display }
            )
        if (scored.isEmpty()) {
            return Result.Error("没有找到「${args.keywords}」；可用分组：${groups().joinToString("、")}")
        }
        if (scored.size == 1 || scored.first().second >= STRONG_MATCH) {
            return Result.One(scored.first().first)
        }

        val ordered = scored.map { it.first }
        lastResults[scope] = ordered
        val pageSize = args.pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val pages = (ordered.size + pageSize - 1) / pageSize
        val page = args.page.coerceIn(1, pages)
        return Result.ListPage(
            entries = ordered.drop((page - 1) * pageSize).take(pageSize),
            total = ordered.size,
            page = page,
            pages = pages,
            pageSize = pageSize
        )
    }


    fun groupStatus(defaultGroup: String?): String {
        val current = defaultGroup?.takeIf { it.isNotBlank() } ?: "未设置"
        return "当前默认分组：$current\n可用分组：${groups().joinToString("、")}"
    }

    fun helpText(): String = HELP_TEXT

    fun render(result: Result): String = when (result) {
        is Result.Help -> HELP_TEXT
        is Result.Error -> result.message
        is Result.One -> result.entry.display + "\n" + truncate(result.entry.content)
        is Result.ListPage -> buildString {
            append("共 ${result.total} 条，第 ${result.page}/${result.pages} 页")
            append("（-n 每页条数，-p 页码；.find 编号 看正文）\n")
            result.entries.forEachIndexed { index, entry ->
                val number = (result.page - 1) * result.pageSize + index + 1
                append("[$number] ${entry.display}\n")
            }
            append("—— 分组：").append(result.entries.firstOrNull()?.group ?: "")
        }.trimEnd()
    }

    private fun score(entry: Entry, query: String): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val title = normalize(entry.title)
        val display = normalize(entry.display)
        val keys = entry.keywords.map { normalize(it) }
        val standards = synonyms[q] ?: emptySet()

        var score = 0
        if (title == q) score = maxOf(score, 100)
        if (display == q) score = maxOf(score, 95)
        if (keys.any { it == q }) score = maxOf(score, 90)
        if (standards.any { it == title || it == display }) score = maxOf(score, 85)
        if (title.startsWith(q)) score = maxOf(score, 80)
        if (display.startsWith(q)) score = maxOf(score, 75)
        if (title.contains(q)) score = maxOf(score, 60)
        if (display.contains(q)) score = maxOf(score, 55)
        if (keys.any { it.contains(q) }) score = maxOf(score, 50)
        if (standards.any { title.contains(it) || display.contains(it) }) {
            score = maxOf(score, 45)
        }
        if (q.length >= 2 && entry.content.contains(query, ignoreCase = true)) {
            score = maxOf(score, 15)
        }
        return score
    }

    private fun expandSynonyms(raw: Map<String, List<String>>): Map<String, Set<String>> {
        val byKey = raw.mapKeys { normalize(it.key) }
        val out = HashMap<String, Set<String>>()
        byKey.forEach { (alias, standards) ->
            val seen = LinkedHashSet<String>()
            var frontier = standards.map { normalize(it) }.filter { it.isNotEmpty() }
            var depth = 0
            while (frontier.isNotEmpty() && depth < MAX_SYNONYM_HOPS) {
                val next = ArrayList<String>()
                frontier.forEach { word ->
                    if (seen.add(word)) byKey[word]?.forEach { next.add(normalize(it)) }
                }
                frontier = next
                depth++
            }
            if (seen.isNotEmpty()) out[alias] = seen
        }
        return out
    }

    private fun stringList(value: Any?): List<String> = when (value) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> (0 until value.length()).mapNotNull {
            value.optString(it, "").trim().takeIf { text -> text.isNotEmpty() }
        }
        is String -> listOf(value.trim()).filter { it.isNotEmpty() }
        else -> listOf(value.toString().trim()).filter { it.isNotEmpty() }
    }

    private fun normalize(text: String): String =
        DiceCalculation.toDBC(text).lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ").trim()

    private fun truncate(text: String): String =
        if (text.length <= MAX_CONTENT_CHARS) text
        else text.take(MAX_CONTENT_CHARS) + "\n…（内容过长，已截断）"

    private class Doc(
        val group: String,
        val module: String,
        val entries: LinkedHashMap<String, String>,
        val keywords: HashMap<String, List<String>>
    )

    private companion object {
        const val LOG_TAG = "FindEngine"
        const val DEFAULT_PAGE_SIZE = 5
        const val MAX_PAGE_SIZE = 10
        const val STRONG_MATCH = 75
        const val MAX_SYNONYM_HOPS = 8
        const val MAX_CONTENT_CHARS = 1200
        val WHITESPACE = Regex("\\s+")

        val HELP_TEXT = """
            .find / .查询 / .查找
            .find 火球          关键词检索（标题/别名/同义词）
            .find #5E 龙        限定分组
            .find -n 10 -p 2    每页条数 / 页码
            .find 12            看上次列表的第 12 条
            .find --rand        随机一条
            .find group         查看默认分组
            .find group 5E      设置本群默认分组
            .find group off     取消默认分组
            .rules 火球         兼容入口（旧规则书速查）
            .rules 5E:火球      分组 + 词条
            .rules set 5E       设置默认分组
        """.trimIndent()
    }
}
