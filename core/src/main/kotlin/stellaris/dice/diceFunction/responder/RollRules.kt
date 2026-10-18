package stellaris.dice.diceFunction.responder









enum class RollRules {


    R,


    RA,


    RC,


    RAB,


    RAP,


    RCB,


    RCP,


    RAH,


    RCH,


    RAV,


    RCV,


    WW,


    WW_SIMPLE,


    DX,


    DX_WRAPPED_LINE,


    FATE;


    fun contains(enumArray: Array<RollRules>): Boolean {
        for (rule in enumArray) {
            if (rule == this) {
                return true
            }
        }
        return false
    }







    val isCheck: Boolean
        get() = contains(CHECK_RULES)


    val isBonusDie: Boolean
        get() = this == RAB || this == RCB


    val isPenaltyDie: Boolean
        get() = this == RAP || this == RCP







    val isOpposed: Boolean
        get() = this == RAV || this == RCV







    val followsHouseRule: Boolean
        get() = contains(HOUSE_RULE_RULES)

    companion object {


        private val CHECK_RULES = arrayOf(RA, RC, RAB, RAP, RCB, RCP, RAH, RCH)


        private val HOUSE_RULE_RULES = arrayOf(RA, RAB, RAP, RAH, RAV)
    }
}
