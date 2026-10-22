package stellaris.dice.diceFunction.judge

import stellaris.dice.diceFunction.responder.CheckLevel
import stellaris.dice.diceFunction.responder.CocHouseRule
import stellaris.dice.diceFunction.responder.CheckRule

/**
 * 一次 d100 检定的判档规则。内置 0~5 档与 `rules/ 下的 .js` 自定义规则都实现这个接口。
 */
interface JudgeRule {

    val name: String

    val describe: String

    val system: Boolean

    /** 先问极端档（大成功/大失败），再问普通档，最后回落到规则书。 */
    fun judge(roll: Int, target: Int): CheckLevel {
        onJudgeExtremeLevel(roll, target)?.let { return it }
        onJudgeNormalLevel(roll, target)?.let { return it }
        return CheckRule.level(roll, target, CocHouseRule.RULEBOOK)
    }

    fun onJudgeExtremeLevel(roll: Int, target: Int): CheckLevel?

    fun onJudgeNormalLevel(roll: Int, target: Int): CheckLevel?
}

/** 内置房规（规则书 / 房规 1~5）。 */
class BuiltinJudgeRule(val rule: CocHouseRule) : JudgeRule {

    override val name: String get() = rule.label

    override val describe: String get() = rule.description

    override val system: Boolean get() = true

    override fun onJudgeExtremeLevel(roll: Int, target: Int): CheckLevel? = null

    override fun onJudgeNormalLevel(roll: Int, target: Int): CheckLevel? = null

    override fun judge(roll: Int, target: Int): CheckLevel =
        CheckRule.level(roll, target, rule)
}
