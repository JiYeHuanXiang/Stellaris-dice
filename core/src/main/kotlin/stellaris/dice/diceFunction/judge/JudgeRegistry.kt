package stellaris.dice.diceFunction.judge

import stellaris.dice.diceFunction.DiceLogger
import stellaris.dice.diceFunction.lightScript.DiceJsSandbox
import stellaris.dice.diceFunction.responder.CheckLevel
import stellaris.dice.diceFunction.responder.CocHouseRule
import java.io.File
import javax.script.Invocable

/**
 * 一个 `rules/ 下的 .js` 自定义房规。
 *
 * 脚本约定与上游一致：暴露 `name`、`describe`，以及
 * `onJudgeExtremeLevel(roll, ability)` / `onJudgeNormalLevel(roll, ability)`；
 * 返回 `BIG_SUCCESS|BIG_FAILURE|VERY_HARD_SUCCESS|HARD_SUCCESS|SUCCESS|FAILURE` 或 `null`
 * （`null` 表示回落到规则书）。
 */
class ScriptJudgeRule(
    override val name: String,
    override val describe: String,
    private val engine: Invocable
) : JudgeRule {

    override val system: Boolean = false

    override fun onJudgeExtremeLevel(roll: Int, target: Int): CheckLevel? =
        invoke(FUNCTION_EXTREME, roll, target)

    override fun onJudgeNormalLevel(roll: Int, target: Int): CheckLevel? =
        invoke(FUNCTION_NORMAL, roll, target)

    private fun invoke(function: String, roll: Int, target: Int): CheckLevel? = try {
        when (val raw = engine.invokeFunction(function, roll, target)) {
            is String -> LEVELS[raw.trim().uppercase()]
            else -> null
        }
    } catch (t: Throwable) {
        null
    }

    private companion object {
        const val FUNCTION_EXTREME = "onJudgeExtremeLevel"
        const val FUNCTION_NORMAL = "onJudgeNormalLevel"

        val LEVELS = mapOf(
            "BIG_SUCCESS" to CheckLevel.CRITICAL_SUCCESS,
            "BIG_FAILURE" to CheckLevel.CRITICAL_FAILURE,
            "VERY_HARD_SUCCESS" to CheckLevel.EXTREME_SUCCESS,
            "HARD_SUCCESS" to CheckLevel.HARD_SUCCESS,
            "SUCCESS" to CheckLevel.SUCCESS,
            "FAILURE" to CheckLevel.FAILURE
        )
    }
}

/**
 * 房规注册表：内置 0~5 档 + `rules/ 下的 .js`。
 *
 * `.setcoc` 接收数字（0~5）或规则名；配置里存名字，旧数字值仍可读。
 */
class JudgeRegistry(private val rootPath: String) {

    private val builtins: List<BuiltinJudgeRule> =
        CocHouseRule.entries.map { BuiltinJudgeRule(it) }

    private val rules = LinkedHashMap<String, JudgeRule>()

    init {
        reload()
    }

    @Synchronized
    fun reload(): Int {
        rules.clear()
        builtins.forEach { rule ->
            rules[rule.name.lowercase()] = rule
            rules[rule.rule.code.toString()] = rule
        }
        val directory = File(rootPath, RULES_DIR)
        if (!directory.isDirectory) {
            return 0
        }
        var loaded = 0
        directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".js", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val rule = loadCustom(file)
                if (rule == null) {
                    DiceLogger.error("自定义房规加载失败，已跳过：${file.name}", LOG_TAG)
                } else {
                    rules[rule.name.lowercase()] = rule
                    loaded++
                }
            }
        return loaded
    }

    @Synchronized
    fun find(nameOrCode: String?): JudgeRule? {
        val key = nameOrCode?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) {
            return null
        }
        return rules[key]
    }

    @Synchronized
    fun default(): JudgeRule = builtins.first { it.rule == CocHouseRule.DEFAULT }

    @Synchronized
    fun listing(): String {
        val builtinText = builtins.joinToString("\n") {
            "${it.rule.code} ${it.name}：${it.describe}"
        }
        val custom = rules.values
            .filter { !it.system }
            .distinctBy { it.name }
        if (custom.isEmpty()) {
            return builtinText
        }
        return builtinText + custom.joinToString("\n") { "\n${it.name}：${it.describe.ifEmpty { "（自定义房规）" }}" }
    }

    @Synchronized
    fun customCount(): Int = rules.values.count { !it.system }

    private fun loadCustom(file: File): JudgeRule? {
        if (file.length() > MAX_SCRIPT_BYTES) {
            DiceLogger.error("自定义房规超过 ${MAX_SCRIPT_BYTES} 字节：${file.name}", LOG_TAG)
            return null
        }
        if (file.length() == 0L) {
            return null
        }
        return try {
            val engine = DiceJsSandbox.create()
            engine.eval(file.readText(Charsets.UTF_8))
            val name = engine.get("name")?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                return null
            }
            val describe = engine.get("describe")?.toString()?.trim().orEmpty()
            ScriptJudgeRule(name, describe, engine as Invocable)
        } catch (t: Throwable) {
            DiceLogger.error("自定义房规执行失败：${file.name}（$t）", LOG_TAG)
            null
        }
    }

    private companion object {
        const val RULES_DIR = "rules"
        const val MAX_SCRIPT_BYTES = 64L * 1024L
        const val LOG_TAG = "JudgeRegistry"
    }
}
