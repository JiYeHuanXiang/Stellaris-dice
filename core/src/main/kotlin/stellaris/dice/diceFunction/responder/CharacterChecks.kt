package stellaris.dice.diceFunction.responder

import stellaris.core.defines.DiceException
import stellaris.dice.diceFunction.calculation.DiceCalculation






























object CharacterChecks {


    const val SAN_ATTRIBUTE = "理智"


    const val SKILL_CAP = 99


    class GrowthResult(

        val roll: Int,

        val base: Int,

        val grown: Boolean,

        val growth: Int,

        val newValue: Int
    ) {


        val rollSegment: String
            get() = if (grown) {
                "1d100=$roll/$base 成长+$growth，$base→$newValue"
            } else {
                "1d100=$roll/$base 未成长"
            }
    }


    class SanRequest(
        val successExpression: String,
        val failureExpression: String,

        val sanOverride: Int?
    )


    class SanResult(

        val roll: Int,

        val target: Int,

        val level: CheckLevel,

        val loss: Int,

        val newValue: Int
    ) {


        val success: Boolean get() = level.isSuccess


        val rollSegment: String
            get() = "1d100=$roll/$target ${level.text}，理智减少${loss}点（$target→$newValue）"
    }


    fun growth(base: Int): GrowthResult {
        val roll = DiceCalculation.rollCalculation("1d100").intNumber
        val grown = roll > base
        val growth = if (grown) DiceCalculation.rollCalculation("1d10").intNumber else 0
        val newValue = if (grown) minOf(SKILL_CAP, base + growth) else base
        return GrowthResult(roll, base, grown, growth, newValue)
    }








    fun parseSan(param: String): SanRequest? {
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return null
        }
        val slash = tokens[0].indexOf('/')
        if (slash < 0) {
            return null
        }
        val successExpression = tokens[0].substring(0, slash).trim()
        val failureExpression = tokens[0].substring(slash + 1).trim()
        if (successExpression.isEmpty() || failureExpression.isEmpty()) {
            return null
        }
        val override = when {
            tokens.size == 1 -> null
            tokens.size == 2 -> tokens[1].toIntOrNull() ?: return null
            else -> return null
        }
        return SanRequest(successExpression, failureExpression, override)
    }








    fun sanity(
        request: SanRequest,
        target: Int,
        houseRule: CocHouseRule = CocHouseRule.DEFAULT
    ): SanResult? = sanity(request, target, stellaris.dice.diceFunction.judge.BuiltinJudgeRule(houseRule))

    fun sanity(
        request: SanRequest,
        target: Int,
        judge: stellaris.dice.diceFunction.judge.JudgeRule
    ): SanResult? {
        val roll = DiceCalculation.rollCalculation("1d100").intNumber
        val level = judge.judge(roll, target)
        val loss = try {
            when (level) {

                CheckLevel.CRITICAL_SUCCESS ->
                    extremum(request.successExpression, RollOptions.DxDModes.MINIMIZE)

                CheckLevel.CRITICAL_FAILURE ->
                    extremum(request.failureExpression, RollOptions.DxDModes.MAXIMIZE)

                else -> DiceCalculation.rollCalculation(
                    if (level.isSuccess) request.successExpression else request.failureExpression
                ).intNumber
            }
        } catch (e: DiceException) {
            return null
        }
        val appliedLoss = maxOf(0, loss)
        return SanResult(
            roll = roll,
            target = target,
            level = level,
            loss = appliedLoss,
            newValue = maxOf(0, target - appliedLoss)
        )
    }


    private fun extremum(expression: String, mode: RollOptions.DxDModes): Int =
        DiceCalculation.rollCalculation(expression, RollOptions(dxd_mode = mode)).intNumber
}
