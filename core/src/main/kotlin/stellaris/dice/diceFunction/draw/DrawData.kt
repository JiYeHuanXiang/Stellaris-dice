package stellaris.dice.diceFunction.draw

import stellaris.core.function.BoundedRandom
import stellaris.dice.diceFunction.DiceRandomManager
import java.io.Serializable
import java.util.Random
import kotlin.math.roundToLong

class DrawData(
    val parent: DrawFile = DrawFile(null, "undefined", random.nextLong(), false),
    val title: ArrayList<String> = ArrayList(),
    val hidden: Boolean = false
) : Serializable {

    private companion object {
        const val DEFAULT_WEIGHT = 1.0
        const val MAX_DECIMAL_SCALE = 6
        const val MAX_SINGLE_WEIGHT = 1_000_000_000L

        val random: Random
            get() = DiceRandomManager.randomInstance
    }

    val format get() = parent.format

    val metadata get() = parent.metaData

    private val data: ArrayList<String> = ArrayList()

    private val prioritiesDynamic: ArrayList<String> = ArrayList()

    private val priorities: ArrayList<Long> = ArrayList()

    private var prioritiesSum: Long = 0

    fun remain(): Int = prioritiesSum.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun detail(): String = "还剩下${prioritiesSum}个可抽取项目"

    fun freshPriorities() {
        val factor = decimalFactor(decimalScale())
        priorities.clear()
        prioritiesDynamic.forEach { raw ->
            val weight = resolveWeight(raw)
            priorities.add((weight * factor).roundToLong().coerceIn(1L, MAX_SINGLE_WEIGHT))
        }
        prioritiesSum = priorities.fold(0L) { total, weight ->
            if (total > Long.MAX_VALUE - weight) Long.MAX_VALUE else total + weight
        }
    }

    fun appendData(str: String, priority: String = "1") {
        data.add(str)
        prioritiesDynamic.add(priority)
    }

    fun clone(): DrawData {
        val copy = DrawData(parent, title, hidden)
        copy.data.addAll(data)
        copy.prioritiesDynamic.addAll(prioritiesDynamic)
        copy.freshPriorities()
        return copy
    }

    fun draw(putBack: Boolean): String {
        if (isEmpty()) {
            throw IllegalStateException("Draw data is empty!")
        }
        val position = BoundedRandom.boundedLong(random, prioritiesSum)

        var cursor = 0L
        priorities.forEachIndexed { index, value ->
            if (position >= cursor && position < cursor + value) {
                val hit = data[index]
                if (!putBack) {
                    if (value == 1L) {
                        data.removeAt(index)
                        priorities.removeAt(index)
                        prioritiesDynamic.removeAt(index)
                    } else {
                        priorities[index] = value - 1
                    }
                    prioritiesSum--
                }
                return hit
            }
            cursor += value
        }

        throw IllegalStateException("Draw data error!")
    }

    fun isTitle(name: String): Boolean =
        title.any { it.equals(name, ignoreCase = true) }

    fun isEmpty(): Boolean = prioritiesSum == 0L

    private fun resolveWeight(raw: String): Double {
        val expression = raw.trim()
        val numeric = expression.toDoubleOrNull()
        if (numeric != null) {
            return numeric.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_WEIGHT
        }
        return DrawWeights.evaluate(expression)
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: DEFAULT_WEIGHT
    }

    private fun decimalScale(): Int {
        return prioritiesDynamic
            .asSequence()
            .mapNotNull { it.trim().toBigDecimalOrNull() }
            .filter { it.signum() > 0 }
            .map { it.stripTrailingZeros().scale().coerceAtLeast(0) }
            .maxOrNull()
            ?.coerceAtMost(MAX_DECIMAL_SCALE)
            ?: 0
    }

    private fun decimalFactor(scale: Int): Double {
        var factor = 1.0
        repeat(scale) { factor *= 10.0 }
        return factor
    }
}
