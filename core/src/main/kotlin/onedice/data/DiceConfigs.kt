package onedice.data

import java.util.HashMap
import java.util.Random










class DiceConfigs(
    val timeout: Long = 1000,
    val singleLine: Boolean = true,
    val random: Random = DiceRandomManagerRandom.defaultSource()
) {


    private val customFormat = HashMap<DiceProcessFormat, String>()


    val customDefault = HashMap<Char, CalOperationDefault>()








    val tempValue = HashMap<Int, ResRecursive>()







    fun setDefaultDiceNumber(minimum: Int, maximum: Int) {
        customDefault['d'] = CalOperationDefault(leftD = minimum, rightD = maximum)
    }


    fun setDefaultFormat(type: DiceProcessFormat, format: String) {
        customFormat[type] = format
    }


    fun readDefaultFormat(type: DiceProcessFormat): String = customFormat[type] ?: when (type) {
        DiceProcessFormat.LEVEL_1 -> "[%s]"
        DiceProcessFormat.LEVEL_2 -> "<%s>"
        DiceProcessFormat.LEVEL_3 -> "{%s}"
        DiceProcessFormat.LEVEL_4 -> "(%s)"
    }







    fun defaultsFor(operator: Char): OperatorDefaults {
        customDefault[operator]?.let {
            return OperatorDefaults(it.leftD, it.rightD)
        }
        return when (operator) {
            'd' -> OperatorDefaults(1, 100)
            'f' -> OperatorDefaults(4, 3)
            'b', 'p' -> OperatorDefaults(1, 1)
            '$' -> OperatorDefaults(0, 0)
            else -> OperatorDefaults(null, null)
        }
    }


    class OperatorDefaults(val left: Int?, val right: Int?)
}







internal object DiceRandomManagerRandom {
    fun defaultSource(): Random = try {
        stellaris.dice.diceFunction.DiceRandomManager.randomInstance
    } catch (t: Throwable) {

        Random()
    }
}
