package stellaris.dice.diceFunction.responder




























enum class CheckLevel(val text: String) {


    CRITICAL_SUCCESS("大成功"),


    EXTREME_SUCCESS("极难成功"),


    HARD_SUCCESS("困难成功"),


    SUCCESS("成功"),


    FAILURE("失败"),


    CRITICAL_FAILURE("大失败");


    val isSuccess: Boolean
        get() = this == CRITICAL_SUCCESS || this == EXTREME_SUCCESS ||
            this == HARD_SUCCESS || this == SUCCESS
}


























enum class CocHouseRule(val code: Int, val description: String) {


    RULEBOOK(0, "出 1 大成功；不满 50 出 96~100 大失败、满 50 出 100 大失败"),


    ONE(1, "不满 50 出 1、满 50 出 1~5 大成功；大失败同规则书"),


    TWO(2, "出 1~5 且判定成功为大成功；出 100、或出 96~99 且判定失败为大失败"),


    THREE(3, "出 1~5 大成功、96~100 大失败（无视判定结果）"),


    FOUR(4, "出 1~5 且 ≤⌊目标/10⌋ 大成功；不满 50 出 ≥96+⌊目标/10⌋、满 50 出 100 大失败"),


    FIVE(5, "出 1~2 且 <⌊目标/5⌋ 大成功；不满 50 出 96~100、满 50 出 99~100 大失败");


    val label: String
        get() = if (this == RULEBOOK) "规则书" else "房规$code"

    companion object {


        val DEFAULT = RULEBOOK


        fun of(code: Int): CocHouseRule? = entries.firstOrNull { it.code == code }


        fun parse(text: String): CocHouseRule? = text.trim().toIntOrNull()?.let { of(it) }


        val codes: IntRange get() = 0..5


        fun listing(): String = entries.joinToString("\n") {
            "${it.code} ${it.label}：${it.description}"
        }
    }
}







object CheckRule {


    fun level(roll: Int, target: Int): CheckLevel = level(roll, target, CocHouseRule.DEFAULT)








    fun level(roll: Int, target: Int, houseRule: CocHouseRule): CheckLevel {
        if (isCriticalSuccess(roll, target, houseRule)) {
            return CheckLevel.CRITICAL_SUCCESS
        }
        if (isCriticalFailure(roll, target, houseRule)) {
            return CheckLevel.CRITICAL_FAILURE
        }

        val extreme = maxOf(1, target / 5)
        val hard = maxOf(1, target / 2)
        return when {
            roll <= extreme -> CheckLevel.EXTREME_SUCCESS
            roll <= hard -> CheckLevel.HARD_SUCCESS
            roll <= target -> CheckLevel.SUCCESS
            else -> CheckLevel.FAILURE
        }
    }


    private fun isCriticalSuccess(roll: Int, target: Int, houseRule: CocHouseRule): Boolean =
        when (houseRule) {
            CocHouseRule.RULEBOOK -> roll == 1
            CocHouseRule.ONE -> if (target < HIGH_TARGET) roll == 1 else roll in 1..5
            CocHouseRule.TWO -> roll in 1..5 && roll <= target
            CocHouseRule.THREE -> roll in 1..5
            CocHouseRule.FOUR -> roll in 1..5 && roll <= target / 10
            CocHouseRule.FIVE -> roll in 1..2 && roll < target / 5
        }


    private fun isCriticalFailure(roll: Int, target: Int, houseRule: CocHouseRule): Boolean {
        val lowTarget = target < HIGH_TARGET
        return when (houseRule) {
            CocHouseRule.RULEBOOK, CocHouseRule.ONE ->
                if (lowTarget) roll >= CRITICAL_FAILURE_LOW_ROLL else roll == MAX_ROLL

            CocHouseRule.TWO ->
                roll == MAX_ROLL || (roll in CRITICAL_FAILURE_LOW_ROLL until MAX_ROLL && roll > target)

            CocHouseRule.THREE -> roll >= CRITICAL_FAILURE_LOW_ROLL

            CocHouseRule.FOUR ->
                if (lowTarget) roll >= CRITICAL_FAILURE_LOW_ROLL + target / 10 else roll == MAX_ROLL

            CocHouseRule.FIVE ->
                if (lowTarget) roll >= CRITICAL_FAILURE_LOW_ROLL else roll >= HIGH_TARGET_MINUS_ONE
        }
    }


    private const val HIGH_TARGET = 50


    private const val HIGH_TARGET_MINUS_ONE = 99


    private const val CRITICAL_FAILURE_LOW_ROLL = 96


    private const val MAX_ROLL = 100
}
