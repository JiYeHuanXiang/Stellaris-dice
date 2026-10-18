package stellaris.dice.diceFunction.responder

import stellaris.dice.diceFunction.DiceConstant

















class RollOptions(
    val dxd_mode: DxDModes = DxDModes.RANDOM,
    val dxd_defaultNumber: Int = 100,
    val simple: Boolean = false,
    val allowNegativeNumber: Boolean = false,
    val dx_ww_wrapped_line: Boolean = true,
    val timeout: Long = DiceConstant.DICE_SINGLE_ROLL_TIME_OUT
) {


    enum class DxDModes {

        MAXIMIZE,


        MINIMIZE,


        RANDOM
    }
}
