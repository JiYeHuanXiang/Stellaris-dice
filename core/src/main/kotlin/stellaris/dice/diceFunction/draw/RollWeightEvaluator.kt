package stellaris.dice.diceFunction.draw

import stellaris.core.Roll
import stellaris.core.data.DiceConfigs
import stellaris.dice.diceFunction.DiceLogger





















class RollWeightEvaluator(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : DrawWeightEvaluator {

    companion object {


        private const val LOG_TAG = "DrawWeight"








        const val DEFAULT_TIMEOUT_MILLIS: Long = 200








        @JvmStatic
        fun install() {
            DrawWeights.install(RollWeightEvaluator())
        }


        @JvmStatic
        fun uninstall() {
            DrawWeights.install(null)
        }
    }

    override fun evaluate(expression: String): Double? {
        val value = try {

            Roll(expression, DiceConfigs(timeout = timeoutMillis)).start().resInt
        } catch (t: Throwable) {
            DiceLogger.log("牌堆权重表达式求值失败，按兜底值 1.0 处理：[$expression] $t", LOG_TAG)
            return null
        }
        return if (value > 0) value.toDouble() else null
    }
}
