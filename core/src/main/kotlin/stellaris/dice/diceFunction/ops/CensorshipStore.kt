package stellaris.dice.diceFunction.ops

import stellaris.dice.diceFunction.DiceConfigStore

/**
 * 出口敏感词处理。
 *
 * 词库来自全局配置：
 * - `FORBIDDEN_WORDS`：每行一个词，命中后替换为等长 `*`；
 * - `REPLACE_FORBIDDEN_WORDS`：每行 `词=替换`，替换写 `-` 表示整条拦截；
 * - `SYSTEM_CENSORSHIP=1` 时启用。
 *
 * 不内置默认词库：词表由实例维护，避免随包内容带来许可与误伤问题。
 */
class CensorshipStore(private val config: DiceConfigStore) {

    class Result(val text: String, val blocked: Boolean, val matched: List<String>)

    fun enabled(): Boolean = config.readGlobal(DiceConfigStore.SYSTEM_CENSORSHIP) == "1"

    fun check(text: String): Result {
        if (!enabled() || text.isEmpty()) {
            return Result(text, false, emptyList())
        }
        var output = text
        val matched = LinkedHashSet<String>()

        parseRules().forEach { (word, replacement) ->
            if (word.isEmpty() || !output.contains(word)) {
                return@forEach
            }
            matched.add(word)
            output = if (replacement == BLOCK_MARK) {
                ""
            } else {
                output.replace(word, replacement)
            }
        }
        if (output.isEmpty() && matched.isNotEmpty()) {
            return Result("", true, matched.toList())
        }

        parseWords().forEach { word ->
            if (word.isEmpty() || !output.contains(word)) {
                return@forEach
            }
            matched.add(word)
            output = output.replace(word, "*".repeat(word.length))
        }
        return Result(output, false, matched.toList())
    }

    fun status(): String {
        val words = parseWords().size
        val rules = parseRules().size
        return "敏感词审查：${if (enabled()) "开启" else "关闭"}；" +
            "词表 $words 条；替换/拦截规则 $rules 条"
    }

    private fun parseWords(): List<String> =
        config.readGlobal(DiceConfigStore.FORBIDDEN_WORDS)
            .split('\n', ',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun parseRules(): List<Pair<String, String>> =
        config.readGlobal(DiceConfigStore.REPLACE_FORBIDDEN_WORDS)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('=') }
            .map { line ->
                val index = line.indexOf('=')
                line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

    private companion object {
        const val BLOCK_MARK = "-"
    }
}
