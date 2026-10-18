package stellaris.dice.diceFunction.draw

import stellaris.dice.diceFunction.DiceRandomManager
import java.io.Serializable
import java.util.Random













class DrawData(
    val parent: DrawFile = DrawFile(null, "undefined", random.nextLong(), false),
    val title: ArrayList<String> = ArrayList(),
    val hidden: Boolean = false
) : Serializable {

    companion object {




        private val random: Random = try {
            DiceRandomManager.randomInstance
        } catch (t: Throwable) {
            Random()
        }
    }


    val format get() = parent.format


    val metadata get() = parent.metaData


    private val data: ArrayList<String> = ArrayList()


    private val prioritiesDynamic: ArrayList<String> = ArrayList()


    private val priorities: ArrayList<Int> = ArrayList()

    private var prioritiesSum: Int = 0


    fun remain(): Int = prioritiesSum


    fun detail(): String = "还剩下${prioritiesSum}个可抽取项目"











    fun freshPriorities() {
        priorities.clear()

        var maxDecimalDigit = 0
        prioritiesDynamic.forEach {
            try {
                val parts = it.split('.', limit = 2)
                if (parts.size == 2) {
                    if (parts[1].toInt() > 0 && parts[0].toInt() >= 0) {
                        val len = parts[1].length
                        if (len > maxDecimalDigit) {
                            maxDecimalDigit = len
                        }
                    }
                }
            } catch (t: Throwable) {

            }
        }

        prioritiesDynamic.forEach {
            var realNumber = try {
                it.toDouble()
            } catch (t: Throwable) {
                DrawWeights.evaluate(it) ?: 1.0
            }
            repeat(maxDecimalDigit) {
                realNumber *= 10
            }
            priorities.add(realNumber.toInt())
        }

        prioritiesSum = priorities.sumOf { it }
    }


    fun appendData(str: String, priority: String = "1") {
        data.add(str)
        prioritiesDynamic.add(priority)
    }





    fun clone(): DrawData {
        val copy = DrawData(parent, title, hidden)
        data.forEach { copy.data.add(it) }
        prioritiesDynamic.forEach { copy.prioritiesDynamic.add(it) }
        copy.freshPriorities()
        return copy
    }









    fun draw(putBack: Boolean): String {
        if (isEmpty()) {
            throw IllegalStateException("Draw data is empty!")
        }
        val position = random.nextInt(prioritiesSum)


        var cursor = 0
        priorities.forEachIndexed { index, value ->
            if (position >= cursor && position < cursor + value) {
                val hit = data[index]
                if (!putBack) {
                    if (priorities[index] == 1) {

                        data.removeAt(index)
                        priorities.removeAt(index)
                        prioritiesDynamic.removeAt(index)
                    } else {
                        priorities[index]--
                    }
                    prioritiesSum--
                }
                return hit
            }
            cursor += value
        }


        throw IllegalStateException("Draw data error!")
    }


    fun isTitle(name: String): Boolean {
        title.forEach {
            if (it.equals(name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }


    fun isEmpty(): Boolean = prioritiesSum == 0
}
