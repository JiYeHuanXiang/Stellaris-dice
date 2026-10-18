package stellaris.dice.diceFunction.responder

import stellaris.dice.diceFunction.RuleMode
import stellaris.dice.diceFunction.calculation.DiceCalculation

/**
 * 一次体系检定的骰点结果。CPR 的暴击链使用这个结构。
 */
class EngineRoll(val value: Int, val expression: String, val detail: String) {

    companion object {
        fun of(expression: String, allowNegative: Boolean = false): EngineRoll {
            val result = DiceCalculation.rollCalculation(
                expression,
                RollOptions(allowNegativeNumber = allowNegative)
            )
            return EngineRoll(result.intNumber, expression, result.toString())
        }
    }
}

/**
 * 七体系检定的统一接口。COC 系（NONE/COC/COC6）走房规链，
 * 这里承载 DND/DG/FATE/DX/WX/CPR 的差异。
 */
interface CheckEngine {

    val mode: RuleMode

    /** FATE 的 4dF 允许负数。 */
    val allowsNegative: Boolean get() = false

    /** 该体系是否有 d100 意义上的奖励/惩罚骰。 */
    val supportsBonusPenalty: Boolean get() = true

    /** 结果里是否显示“/目标值”；骰池体系只显示池结果。 */
    val showsTarget: Boolean get() = true

    /** 把技能值/显式目标与 b/p 折算成该体系真正使用的目标值（或骰池大小）。 */
    fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int

    /** 该体系真正掷的表达式。 */
    fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String

    /** 成功级别。 */
    fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel

    /** 目标值的显示形式，如 DG 的 `50+(40)`、FATE 的 `2+(-4)`。 */
    fun displayTarget(args: CheckArguments, target: Int, rule: RollRules, rawBase: Int): String =
        target.toString()

    /** COC 之外的体系没有 d100 奖惩骰；返回一行等价写法提示，COC 返回 null。 */
    fun plainBonusHint(bonus: Boolean): String? = null

    fun roll(expression: String): EngineRoll = EngineRoll.of(expression, allowsNegative)

    /** 结果在回执里的写法；CPR 的暴击链需要显示 `10 + 7 (17)`。 */
    fun displayRoll(result: EngineRoll): String = result.value.toString()

    fun bpCount(args: CheckArguments, rule: RollRules): Int =
        if (rule.isBonusDie) args.bonusPenaltyCount
        else if (rule.isPenaltyDie) -args.bonusPenaltyCount
        else 0
}

private object CocEngine : CheckEngine {

    override val mode = RuleMode.COC

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int =
        args.difficulty.applyTo(base + (args.targetDelta ?: 0)).coerceAtLeast(0)

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String = when {
        rule.isBonusDie -> "1b${args.bonusPenaltyCount}"
        rule.isPenaltyDie -> "1p${args.bonusPenaltyCount}"
        else -> "1d100"
    }

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel =
        CheckRule.level(roll, target, houseRule)
}

private object DgEngine : CheckEngine {

    override val mode = RuleMode.DG

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int {
        val adjusted = base + (args.targetDelta ?: 0)
        val n = bpCount(args, rule)
        if (n == 0) return adjusted
        val delta = if (kotlin.math.abs(n) >= DG_LARGE_COUNT) DG_LARGE_PERCENT else DG_SMALL_PERCENT
        return (adjusted + if (n > 0) delta else -delta).coerceAtLeast(0)
    }

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String = "1d100"

    override fun displayTarget(args: CheckArguments, target: Int, rule: RollRules, rawBase: Int): String {
        val n = bpCount(args, rule)
        if (n == 0) return target.toString()
        val delta = target - rawBase
        return "$rawBase+($delta)"
    }

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel {
        if (roll == 1) return CheckLevel.CRITICAL_SUCCESS
        if (roll == 100) return CheckLevel.CRITICAL_FAILURE
        val success = roll <= target
        if (roll in 11..99 && roll % 11 == 0) {
            return if (success) CheckLevel.CRITICAL_SUCCESS else CheckLevel.CRITICAL_FAILURE
        }
        return if (success) CheckLevel.SUCCESS else CheckLevel.FAILURE
    }

    override fun plainBonusHint(bonus: Boolean): String = if (bonus) {
        "DG 模式没有奖惩骰（官方为目标值 +20%，两个及以上 +40%）：奖励检定请用 .rab 2 技能名，单纯骰点请用 .r"
    } else {
        "DG 模式没有奖惩骰（官方为目标值 -20%，两个及以上 -40%）：惩罚检定请用 .rap 2 技能名，单纯骰点请用 .r"
    }

    private const val DG_SMALL_PERCENT = 20
    private const val DG_LARGE_PERCENT = 40
    private const val DG_LARGE_COUNT = 2
}

private object Dnd5eEngine : CheckEngine {

    override val mode = RuleMode.DND5E

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int =
        (base + (args.targetDelta ?: 0)).coerceAtLeast(0)

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String = when {
        rule.isBonusDie -> "2d20k1"
        rule.isPenaltyDie -> "2d20q1"
        else -> "1d20"
    }

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel = when {
        roll == 20 -> CheckLevel.CRITICAL_SUCCESS
        roll == 1 -> CheckLevel.CRITICAL_FAILURE
        roll >= target -> CheckLevel.SUCCESS
        else -> CheckLevel.FAILURE
    }

    override fun plainBonusHint(bonus: Boolean): String = if (bonus) {
        "DND(5E) 没有 d100 奖惩骰：优势请用 .rab 2 目标值（2d20 取高），单纯骰点请用 .r 1d20"
    } else {
        "DND(5E) 没有 d100 奖惩骰：劣势请用 .rap 2 目标值（2d20 取低），单纯骰点请用 .r 1d20"
    }
}

private object Dnd3rEngine : CheckEngine {

    override val mode = RuleMode.DND3R

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int =
        (base + (args.targetDelta ?: 0)).coerceAtLeast(0)

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String = "1d20"

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel = when {
        roll == 20 -> CheckLevel.CRITICAL_SUCCESS
        roll == 1 -> CheckLevel.CRITICAL_FAILURE
        roll >= target -> CheckLevel.SUCCESS
        else -> CheckLevel.FAILURE
    }

    override fun plainBonusHint(bonus: Boolean): String =
        "DND(3R) 没有优势/劣势：请用 .rab 目标值（普通 d20 检定），单纯骰点请用 .r 1d20"
}

private object FateEngine : CheckEngine {

    override val mode = RuleMode.FATE

    override val allowsNegative = true

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int =
        base + (args.targetDelta ?: 0) - bpCount(args, rule) * 2

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String = "f"

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel = when {
        roll >= 4 -> CheckLevel.CRITICAL_SUCCESS
        roll <= -4 -> CheckLevel.CRITICAL_FAILURE
        roll >= target -> CheckLevel.SUCCESS
        else -> CheckLevel.FAILURE
    }

    override fun displayTarget(args: CheckArguments, target: Int, rule: RollRules, rawBase: Int): String {
        val n = bpCount(args, rule)
        if (n == 0) return target.toString()
        val adjust = -n * 2
        return "${rawBase + (args.targetDelta ?: 0)}+($adjust)"
    }

    override fun plainBonusHint(bonus: Boolean): String = if (bonus) {
        "FATE 没有奖惩骰：奖励=援引要素 +2，请用 .rab 2 难度；单纯骰点请用 .r 4dF"
    } else {
        "FATE 没有奖惩骰：惩罚=-2，请用 .rap 2 难度；单纯骰点请用 .r 4dF"
    }
}

private object DxEngine : CheckEngine {

    override val mode = RuleMode.DX

    override val showsTarget = false

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int =
        (base + (args.targetDelta ?: 0)).coerceAtLeast(0)

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String {
        val pool = target + bpCount(args, rule)
        return if (pool < 1) "0" else "${pool}c10"
    }

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel = when {
        roll == 1 -> CheckLevel.CRITICAL_FAILURE
        roll > 1 -> CheckLevel.SUCCESS
        else -> CheckLevel.FAILURE
    }

    override fun plainBonusHint(bonus: Boolean): String = if (bonus) {
        "双重十字没有奖惩骰：奖励=加骰，请用 .rab 2 能力值（池 +2 颗）；单纯骰点请用 .r Nc10"
    } else {
        "双重十字没有奖惩骰：惩罚=减骰，请用 .rap 2 能力值（池 -2 颗）；单纯骰点请用 .r Nc10"
    }
}

private object WxEngine : CheckEngine {

    override val mode = RuleMode.WX

    override val showsTarget = false

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int =
        (base + (args.targetDelta ?: 0)).coerceAtLeast(0)

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String {
        val pool = target + bpCount(args, rule)
        return if (pool < 1) "0" else "${pool}a10"
    }

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel =
        if (roll > 0) CheckLevel.SUCCESS else CheckLevel.FAILURE

    override fun plainBonusHint(bonus: Boolean): String = if (bonus) {
        "无限流没有奖惩骰：奖励=加骰，请用 .rab 2 属性值（池 +2 颗）；单纯骰点请用 .r Na10"
    } else {
        "无限流没有奖惩骰：惩罚=减骰，请用 .rap 2 属性值（池 -2 颗）；单纯骰点请用 .r Na10"
    }
}

private object CprEngine : CheckEngine {

    override val mode = RuleMode.CPR

    override fun resolveTarget(args: CheckArguments, base: Int, rule: RollRules): Int =
        (base + (args.targetDelta ?: 0) - bpCount(args, rule) * 2).coerceAtLeast(0)

    override fun rollExpression(args: CheckArguments, rule: RollRules, target: Int): String = "1d10"

    override fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel =
        if (roll >= target) CheckLevel.SUCCESS else CheckLevel.FAILURE

    override fun displayTarget(args: CheckArguments, target: Int, rule: RollRules, rawBase: Int): String {
        val n = bpCount(args, rule)
        if (n == 0) return target.toString()
        val adjust = -n * 2
        return "${rawBase + (args.targetDelta ?: 0)}+($adjust)"
    }

    override fun roll(expression: String): EngineRoll {
        var current = DiceCalculation.getRandomInt(1, 10)
        val chain = StringBuilder(current.toString())
        var chained = false
        var direction = when (current) {
            10 -> 1
            1 -> -1
            else -> 0
        }
        var sum = current
        while (direction != 0) {
            current = DiceCalculation.getRandomInt(1, 10)
            sum += direction * current
            chain.append(if (direction > 0) " + " else " - ").append(current)
            chained = true
            direction = when {
                direction > 0 && current == 10 -> 1
                direction < 0 && current == 1 -> -1
                else -> 0
            }
        }
        val detail = if (chained) "$chain ($sum)" else sum.toString()
        return EngineRoll(sum, "1d10", detail)
    }

    override fun displayRoll(result: EngineRoll): String = result.detail

    override fun plainBonusHint(bonus: Boolean): String = if (bonus) {
        "赛博朋克红没有奖惩骰：奖励=+2 修正，请用 .rab 2 门限；单纯骰点请用 .r 1d10"
    } else {
        "赛博朋克红没有奖惩骰：惩罚=-2 修正，请用 .rap 2 门限；单纯骰点请用 .r 1d10"
    }
}

/**
 * 体系模式与检定引擎的绑定，同时负责 `.r` 的默认骰面与裸 `d` 的改写。
 */
data class RuleProfile(val mode: RuleMode, val engine: CheckEngine) {

    /** 裸 `.r` 的默认表达式；NONE 时沿用 `.set` 的骰面。 */
    fun defaultExpression(setNumber: Int): String = when (mode) {
        RuleMode.NONE -> "1d$setNumber"
        RuleMode.COC, RuleMode.COC6, RuleMode.DG -> "1d100"
        RuleMode.DND5E, RuleMode.DND3R -> "1d20"
        RuleMode.FATE -> "f"
        RuleMode.DX -> "1c10"
        RuleMode.WX -> "1a10"
        RuleMode.CPR -> "1d10"
    }

    /**
     * 把裸 `d`（如 `.r 3d`）按体系换成对应骰子项：
     * DX → `3c10`、WX → `3a10`、FATE → `3f`、DND → `3d20`、CPR → `3d10`。
     */
    fun rewriteBareDice(expression: String, setNumber: Int): String {
        val pattern = Regex("(\\d*)d(?![0-9A-Za-z(])")
        return pattern.replace(expression) { match ->
            val count = match.groupValues[1]
            when (mode) {
                RuleMode.FATE -> "${count}f"
                RuleMode.DX -> "${count}c10"
                RuleMode.WX -> "${count}a10"
                RuleMode.DND5E, RuleMode.DND3R -> "${count}d20"
                RuleMode.CPR -> "${count}d10"
                RuleMode.COC, RuleMode.COC6, RuleMode.DG -> "${count}d100"
                RuleMode.NONE -> "${count}d$setNumber"
            }
        }
    }

    fun bonusPenaltyHint(bonus: Boolean): String? = engine.plainBonusHint(bonus)

    companion object {

        private val COC = RuleProfile(RuleMode.NONE, CocEngine)

        fun of(mode: RuleMode): RuleProfile = when (mode) {
            RuleMode.NONE -> COC
            RuleMode.COC -> RuleProfile(mode, CocEngine)
            RuleMode.COC6 -> RuleProfile(mode, CocEngine)
            RuleMode.DND5E -> RuleProfile(mode, Dnd5eEngine)
            RuleMode.DND3R -> RuleProfile(mode, Dnd3rEngine)
            RuleMode.DG -> RuleProfile(mode, DgEngine)
            RuleMode.FATE -> RuleProfile(mode, FateEngine)
            RuleMode.DX -> RuleProfile(mode, DxEngine)
            RuleMode.WX -> RuleProfile(mode, WxEngine)
            RuleMode.CPR -> RuleProfile(mode, CprEngine)
        }
    }
}
