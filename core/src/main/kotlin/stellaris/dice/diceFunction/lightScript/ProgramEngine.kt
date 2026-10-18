package stellaris.dice.diceFunction.lightScript

import stellaris.dice.diceFunction.calculation.DiceCalculation
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/**
 * `[[auto]].program` 的 `【】` 轻脚本。
 *
 * 只实现规范里已声明可用的指令。`插入一行`/`取出一行` 保持空结果。
 */
class ProgramContext(
    val pluginId: String,
    val autoName: String,
    val senderId: String,
    val groupId: String,
    val isGroup: Boolean,
    val defines: Map<String, String>,
    val config: Map<String, String>,
    val regexGroups: List<String>,
    val variables: ProgramVariables,
    val scriptExecutor: ((String) -> String?)? = null
) {
    var scope: String = "global"
}

class ProgramException(message: String) : Exception(message)

class ProgramAbort(val value: String) : Exception()

object ProgramEngine {

    private val COMMANDS = listOf(
        "设置变量针对个人", "设置变量针对群", "设置变量有效期", "设置固态变量",
        "详细骰点计算", "分割随取", "距离上次激活时长", "最后激活时间",
        "骰点计算", "赋值变量", "执行变量", "正则匹配到的", "10位时间戳",
        "现行日期", "当前群号", "发送者QQ", "执行脚本", "插入一行", "取出一行",
        "随机数", "到整数", "计算", "比较", "判断", "判空", "隐藏", "分隔",
        "换行", "常量", "读项目", "变量", "随取", "返回", "块", "一天", "艾特"
    )

    fun execute(program: String, context: ProgramContext): String = try {
        render(program, context)
    } catch (abort: ProgramAbort) {
        abort.value
    } catch (t: Throwable) {
        "无法执行！你这行写错了：${t.message ?: ""}"
    }

    private fun render(text: String, context: ProgramContext): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("#zzk", index) -> {
                    out.append('【')
                    index += 4
                }

                text.startsWith("#yzk", index) -> {
                    out.append('】')
                    index += 4
                }

                text.startsWith("#ywdh", index) -> {
                    out.append(',')
                    index += 5
                }

                text[index] == '【' -> {
                    val end = matchingBracket(text, index)
                    if (end < 0) throw ProgramException("第 ${index + 1} 个字符的【没有闭合")
                    out.append(evaluate(text.substring(index + 1, end), context))
                    index = end + 1
                }

                else -> {
                    out.append(text[index])
                    index++
                }
            }
        }
        return out.toString()
    }

    private fun matchingBracket(text: String, start: Int): Int {
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '【' -> depth++
                '】' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun splitTopLevel(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in text) {
            when (ch) {
                '【' -> {
                    depth++
                    current.append(ch)
                }

                '】' -> {
                    depth--
                    current.append(ch)
                }

                ',' -> if (depth == 0) {
                    result.add(current.toString())
                    current.setLength(0)
                } else {
                    current.append(ch)
                }

                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun evaluate(inner: String, context: ProgramContext): String {
        val command = COMMANDS.firstOrNull { inner.startsWith(it) }
            ?: throw ProgramException("未知指令：$inner")
        val rawArgs = inner.substring(command.length).trimStart()
        val args = if (rawArgs.isEmpty()) {
            emptyList()
        } else {
            splitTopLevel(rawArgs).map { arithmetic(render(it, context)) }
        }
        return when (command) {
            "块" -> args.firstOrNull() ?: ""
            "随取" -> {
                if (args.isEmpty()) throw ProgramException("随取缺少参数")
                args[DiceCalculation.getRandomInt(0, args.size - 1)]
            }

            "分割随取" -> {
                if (args.size < 2) throw ProgramException("分割随取需要分隔符和文本")
                val pieces = args[1].split(args[0]).filter { it.isNotEmpty() }
                if (pieces.isEmpty()) "" else pieces[DiceCalculation.getRandomInt(0, pieces.size - 1)]
            }

            "随机数" -> randomNumber(args)
            "计算" -> strictArithmetic(args.firstOrNull() ?: "")
            "到整数" -> (args.firstOrNull()?.toBigDecimalOrNull()?.toInt() ?: 0).toString()
            "比较" -> {
                val a = args.getOrNull(0)?.toBigDecimalOrNull()
                val b = args.getOrNull(1)?.toBigDecimalOrNull()
                if (a != null && b != null && a >= b) args.getOrElse(2) { "" } else args.getOrElse(3) { "" }
            }

            "判断" -> if (args.getOrNull(0) != args.getOrNull(1)) {
                args.getOrElse(2) { "" }
            } else {
                args.getOrElse(3) { "" }
            }

            "判空" -> if (args.getOrNull(0).isNullOrEmpty()) args.getOrElse(1) { "" } else args[0]
            "返回" -> throw ProgramAbort(args.firstOrNull() ?: "")
            "隐藏" -> ""
            "分隔", "换行" -> "\n"
            "一天" -> "86400"
            "艾特" -> " @艾特你 "
            "变量" -> context.variables.get(key(context, args.firstOrNull() ?: "")) ?: ""
            "赋值变量" -> {
                context.variables.set(key(context, args.getOrNull(0) ?: ""), args.getOrElse(1) { "" })
                ""
            }

            "执行变量" -> {
                val value = context.variables.get(key(context, args.firstOrNull() ?: "")) ?: ""
                render(value, context)
            }

            "设置固态变量" -> {
                context.variables.markSolid(key(context, args.firstOrNull() ?: ""), context.pluginId)
                ""
            }

            "设置变量针对个人" -> {
                context.scope = "person:${context.senderId}"
                ""
            }

            "设置变量针对群" -> {
                context.scope = "group:${context.groupId}"
                ""
            }

            "设置变量有效期" -> {
                val seconds = args.getOrNull(1)?.toLongOrNull() ?: 0L
                context.variables.setExpiry(key(context, args.firstOrNull() ?: ""), seconds)
                ""
            }

            "常量" -> context.defines[args.firstOrNull() ?: ""] ?: ""
            "读项目" -> context.config[args.firstOrNull() ?: ""] ?: ""
            "正则匹配到的" -> {
                val index = args.firstOrNull()?.toIntOrNull() ?: 0
                context.regexGroups.getOrNull(index) ?: ""
            }

            "骰点计算" -> try {
                DiceCalculation.rollCalculation(args.firstOrNull() ?: "").intNumber.toString()
            } catch (t: Throwable) {
                ""
            }

            "详细骰点计算" -> try {
                DiceCalculation.rollCalculation(args.firstOrNull() ?: "").toString()
            } catch (t: Throwable) {
                ""
            }

            "发送者QQ" -> context.senderId
            "当前群号" -> if (context.isGroup) context.groupId else ""
            "现行日期" -> LocalDate.now().toString().replace('-', '_')
            "10位时间戳" -> (System.currentTimeMillis() / 1000).toString()
            "最后激活时间" -> "0"
            "距离上次激活时长" -> "0"
            "执行脚本" -> context.scriptExecutor?.invoke(args.firstOrNull() ?: "") ?: ""
            "插入一行", "取出一行" -> ""
            else -> ""
        }
    }

    private fun randomNumber(args: List<String>): String {
        if (args.isEmpty()) return "0"
        val single = args.firstOrNull()?.trim().orEmpty()
        if (args.size == 1 && single.contains('-')) {
            val parts = single.split('-', limit = 2)
            val min = parts[0].trim().toIntOrNull()
            val max = parts[1].trim().toIntOrNull()
            if (min != null && max != null) {
                return DiceCalculation.getRandomInt(min, max).toString()
            }
        }
        val min = args.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val max = args.getOrNull(1)?.trim()?.toIntOrNull() ?: min
        return DiceCalculation.getRandomInt(min, max).toString()
    }

    private fun key(context: ProgramContext, name: String): String =
        "${context.pluginId}\u0000${context.autoName}\u0000${context.scope}\u0000$name"

    /**
     * 参数先做整数/浮点四则运算；不是纯算式时原样返回。
     */
    private fun arithmetic(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text
        if (!trimmed.any { it.isDigit() }) return text
        if (trimmed.any { it !in "0123456789.+-*/() " }) return text
        return try {
            strictArithmetic(trimmed)
        } catch (t: Throwable) {
            text
        }
    }

    private fun strictArithmetic(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw ProgramException("计算缺少表达式")
        val value = ExpressionParser(trimmed).parse()
        return if (value.stripTrailingZeros().scale() <= 0) {
            value.toBigInteger().toString()
        } else {
            value.stripTrailingZeros().toPlainString()
        }
    }

    private class ExpressionParser(private val source: String) {
        private var pos = 0

        fun parse(): BigDecimal {
            val value = expression()
            skipSpaces()
            if (pos < source.length) throw IllegalArgumentException("无法计算：$source")
            return value
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '+' -> {
                        pos++
                        value = value.add(term())
                    }

                    '-' -> {
                        pos++
                        value = value.subtract(term())
                    }

                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '*' -> {
                        pos++
                        value = value.multiply(factor())
                    }

                    '/' -> {
                        pos++
                        value = value.divide(factor(), 10, java.math.RoundingMode.HALF_UP)
                    }

                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            skipSpaces()
            return when (peek()) {
                '(' -> {
                    pos++
                    val value = expression()
                    skipSpaces()
                    if (peek() != ')') throw IllegalArgumentException("缺少右括号")
                    pos++
                    value
                }

                '-' -> {
                    pos++
                    factor().negate()
                }

                else -> number()
            }
        }

        private fun number(): BigDecimal {
            skipSpaces()
            val start = pos
            while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("缺少数字")
            return BigDecimal(source.substring(start, pos))
        }

        private fun peek(): Char? = source.getOrNull(pos)

        private fun skipSpaces() {
            while (pos < source.length && source[pos].isWhitespace()) pos++
        }
    }
}

/**
 * `program` 变量。固态变量立即落 `custom/data/<plugin>.json`。
 */
class ProgramVariables(private val dataDir: java.io.File) {

    private data class Value(var value: String, var solid: Boolean, var expireAt: Long)

    private val values = HashMap<String, Value>()
    private val loaded = HashSet<String>()
    private val storageNames = HashMap<String, String>()

    /** 插件身份与数据文件名分开：数据文件跟插件文件名走。 */
    @Synchronized
    fun register(pluginId: String, storageId: String) {
        storageNames[pluginId] = storageId
    }

    private fun storageName(pluginId: String): String = storageNames[pluginId] ?: pluginId

    @Synchronized
    fun get(key: String): String? {
        val value = values[key] ?: return null
        if (value.expireAt > 0 && System.currentTimeMillis() > value.expireAt) {
            values.remove(key)
            return null
        }
        return value.value
    }

    @Synchronized
    fun set(key: String, value: String) {
        values.getOrPut(key) { Value("", false, 0) }.value = value
    }

    @Synchronized
    fun setExpiry(key: String, seconds: Long) {
        values.getOrPut(key) { Value("", false, 0) }.expireAt =
            if (seconds <= 0) 0 else System.currentTimeMillis() + seconds * 1000
    }

    @Synchronized
    fun markSolid(key: String, pluginId: String) {
        values.getOrPut(key) { Value("", false, 0) }.solid = true
        persist(pluginId)
    }

    @Synchronized
    private fun persist(pluginId: String) {
        try {
            dataDir.mkdirs()
            val file = java.io.File(dataDir, "${storageName(pluginId)}.json")
            val prefix = "$pluginId\u0000"
            val now = System.currentTimeMillis()
            val persistable = LinkedHashMap<String, String>()
            values.forEach { (key, value) ->
                if (!value.solid || !key.startsWith(prefix)) return@forEach
                if (value.value.isEmpty()) return@forEach
                if (value.expireAt > 0 && now > value.expireAt) return@forEach
                persistable[key] = value.value
            }
            PluginStorageFile.writeVars(file, prefix, persistable)
        } catch (t: Throwable) {
            stellaris.dice.diceFunction.DiceLogger.error("插件变量写入失败：$t")
        }
    }

    @Synchronized
    fun load(pluginId: String) {
        if (!loaded.add(pluginId)) return
        val file = java.io.File(dataDir, "${storageName(pluginId)}.json")
        if (!file.isFile) return
        try {
            PluginStorageFile.readVars(file).forEach { (key, value) ->
                values[key] = Value(value, true, 0)
            }
        } catch (t: Throwable) {
            stellaris.dice.diceFunction.DiceLogger.error("插件变量载入失败：$t")
        }
    }
}
