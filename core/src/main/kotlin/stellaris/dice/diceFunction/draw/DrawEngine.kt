package stellaris.dice.diceFunction.draw

import java.util.ArrayList








class DrawEngine(private val environmentCommonData: ArrayList<DrawFile>) {


    fun getDrawData(name: String): DrawData? {
        environmentCommonData.forEach {
            it.findData(name)?.let { drawData ->
                return drawData
            }
        }
        return null
    }
}
