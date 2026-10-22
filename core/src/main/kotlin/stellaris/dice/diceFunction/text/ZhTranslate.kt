package stellaris.dice.diceFunction.text

/**
 * 繁→简字符归一。
 *
 * 映射表随 jar 走 `stellaris/hk2zh.txt`（每行 `繁 简`，`#` 开头为注释）。
 * 用于让用繁体输入的用户命中简体指令、关键词、人物卡属性与插件命令。
 */
object ZhTranslate {

    private const val RESOURCE = "/stellaris/hk2zh.txt"
    private const val CACHE_LIMIT = 256

    private val table: Map<Int, String> by lazy { load() }

    private val cache = object : LinkedHashMap<String, String>(CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CACHE_LIMIT
    }

    @Synchronized
    fun hk2zh(input: String?): String {
        if (input.isNullOrEmpty()) {
            return ""
        }
        cache[input]?.let { return it }
        val builder = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val codePoint = input.codePointAt(index)
            val mapped = table[codePoint]
            if (mapped != null) {
                builder.append(mapped)
            } else {
                builder.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        val output = builder.toString()
        cache[input] = output
        return output
    }

    fun startsWith(text: String, prefix: String): Boolean =
        hk2zh(text).startsWith(hk2zh(prefix))

    fun equal(left: String, right: String): Boolean =
        hk2zh(left) == hk2zh(right)

    fun contained(text: String, keyword: String): Boolean =
        hk2zh(text).contains(hk2zh(keyword))

    private fun load(): Map<Int, String> {
        val map = HashMap<Int, String>(4096)
        val stream = ZhTranslate::class.java.getResourceAsStream(RESOURCE) ?: return map
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith('#')) {
                    return@forEach
                }
                val space = line.indexOf(' ')
                if (space <= 0 || space >= line.length - 1) {
                    return@forEach
                }
                val key = line.substring(0, space)
                val value = line.substring(space + 1)
                if (key.codePointCount(0, key.length) == 1) {
                    map[key.codePointAt(0)] = value
                }
            }
        }
        return map
    }
}
