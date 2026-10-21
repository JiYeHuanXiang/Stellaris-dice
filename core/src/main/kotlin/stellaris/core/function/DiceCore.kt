package stellaris.core.function

import stellaris.core.data.DiceConfigs
import stellaris.core.data.DiceProcessFormat
import stellaris.core.data.Format
import stellaris.core.data.ResRecursive
import stellaris.core.defines.ResErrorException
import stellaris.core.defines.ResErrorType
import java.util.Random






















class DiceCore(private val diceConfigs: DiceConfigs) {

    companion object {






        const val MAX_PUNISH_BONUS_NUMBER = 9


        private const val MAX_FATE_TIMES = 50
    }

    private val random: Random get() = diceConfigs.random




    private fun randomTo(nMax: Long): Long = BoundedRandom.longInRange(random, 1L, nMax)


    private fun random(nMin: Int, nMax: Int): Int = BoundedRandom.intInRange(random, nMin, nMax)




    fun calculationTernaryOperator(condition: Int, t: Int, f: Int): ResRecursive {
        val select = if (condition != 0) t else f
        return ResRecursive(
            select,
            format(
                DiceProcessFormat.LEVEL_3,
                Format.build("if {replace} then {replace} else {replace}", condition, t, f),
                select
            ),
            resIntMin = minOf(t, select),
            resIntMax = maxOf(t, select)
        )
    }

    fun calculationTernaryOperator(condition: Int, selected: ResRecursive, whenTrue: Boolean): ResRecursive {
        val branch = if (whenTrue) "then" else "else"
        return ResRecursive(
            selected.resInt,
            format(
                DiceProcessFormat.LEVEL_3,
                Format.build("if {replace} $branch {replace}", condition, selected.resDetail),
                selected.resNumberDetail
            ),
            selected.resNumberDetail,
            resIntMin = selected.resIntMin,
            resIntMax = selected.resIntMax
        )
    }

    fun calculationShortCircuit(operator: Char, left: ResRecursive): ResRecursive {
        val result = when (operator) {
            '&' -> 0
            '|' -> 1
            else -> throw IllegalArgumentException("unsupported short-circuit operator: $operator")
        }
        val word = if (operator == '&') "and" else "or"
        return ResRecursive(
            result,
            format(
                DiceProcessFormat.LEVEL_3,
                Format.build("{replace} $word <短路>", left.resDetail),
                result
            ),
            resIntMin = 0,
            resIntMax = 1
        )
    }


    fun calculationEqual(a: Int, b: Int): ResRecursive =
        boolean(a == b) { Format.build("{replace} equ {replace}", a, b) }


    fun calculationSmallerThan(a: Int, b: Int): ResRecursive =
        boolean(a < b) { Format.build("{replace} < {replace}", a, b) }


    fun calculationBiggerThan(a: Int, b: Int): ResRecursive =
        boolean(a > b) { Format.build("{replace} > {replace}", a, b) }


    fun calculationAnd(a: Int, b: Int): ResRecursive =
        boolean(a != 0 && b != 0) { Format.build("{replace} and {replace}", a, b) }


    fun calculationOr(a: Int, b: Int): ResRecursive =
        boolean(a != 0 || b != 0) { Format.build("{replace} or {replace}", a, b) }


    fun calculationNot(a: Int): ResRecursive =
        boolean(a == 0) { Format.build("not {replace}", a) }


    private inline fun boolean(result: Boolean, message: () -> String): ResRecursive {
        val value = if (result) 1 else 0
        return ResRecursive(
            value,
            format(DiceProcessFormat.LEVEL_3, message(), value),
            resIntMin = 0,
            resIntMax = 1
        )
    }
















    fun calculationPunishBonus(mode: Mode, num: Int): ResRecursive {
        if (mode != Mode.PUNISH && mode != Mode.BONUS) {
            throw ResErrorException(ResErrorType.NODE_SUB_VAL_INVALID)
        }
        if (num < 1 || num > MAX_PUNISH_BONUS_NUMBER) {
            throw ResErrorException(
                ResErrorType.NODE_RIGHT_VAL_INVALID,
                "无法计算,要求 惩罚骰个数 必须不大于${MAX_PUNISH_BONUS_NUMBER}且 大于0"
            )
        }

        var current = random(1, 100)
        var currentTens = current / 10
        val detail = StringBuilder().apply {
            append(if (mode == Mode.PUNISH) "惩罚" else "奖励")
            append("d100=").append(current).append("|")
        }

        val drawn = StringBuilder()
        for (i in 0 until num) {
            checkInterrupted()
            var tens = random(0, 9)
            if (current % 10 == 0) {

                tens++
            }
            val better = mode == Mode.BONUS && tens < currentTens
            val worse = mode == Mode.PUNISH && tens > currentTens
            if (better || worse) {
                current = tens * 10 + current % 10
                currentTens = tens
            }
            drawn.append(tens).append(" ")
        }
        val drawnText = drawn.toString()
        detail.append(drawnText, 0, drawnText.length - 1)

        return ResRecursive(
            current,
            format(DiceProcessFormat.LEVEL_3, detail.toString(), current),
            resIntMin = 1,
            resIntMax = 100
        )
    }










    fun calculationDice(mode: Mode, times: Int, maxValue: Long, addition: Int = 1): ResRecursive {
        if (mode != Mode.EXCESS &&
            mode != Mode.FRONT_SUM_MAXIMUM &&
            mode != Mode.FRONT_SUM_MINIMUM &&
            mode != Mode.NORMAL_DICE
        ) {
            throw ResErrorException(ResErrorType.NODE_SUB_VAL_INVALID)
        }

        var total = 0L
        val process = StringBuilder()
        val numbers = Array(times) { randomTo(maxValue) }

        when (mode) {
            Mode.EXCESS -> {
                numbers.sortDescending()
                var atSplitPoint = false
                var splitPointTaken = false
                process.append("COUNT ")
                for (i in numbers.indices) {
                    checkInterrupted()
                    val number = numbers[i]
                    if (number >= addition) {
                        total++
                    } else if (!splitPointTaken) {
                        atSplitPoint = true
                    }
                    if (i > 0) {
                        if (atSplitPoint) {

                            atSplitPoint = false
                            splitPointTaken = true
                            process.append(" | ")
                        } else {
                            process.append(",")
                        }
                    }
                    process.append(number)
                }
            }

            Mode.FRONT_SUM_MAXIMUM, Mode.FRONT_SUM_MINIMUM -> {
                if (mode == Mode.FRONT_SUM_MAXIMUM) numbers.sortDescending() else numbers.sort()
                if (addition > numbers.size) {
                    throw ResErrorException(
                        ResErrorType.NODE_SUB_VAL_INVALID,
                        "参数在 $mode 模式下不应超过 ${numbers.size}"
                    )
                }
                for (i in 0 until addition) {
                    total += numbers[i]
                }
                process.append(if (mode == Mode.FRONT_SUM_MAXIMUM) "MAX" else "MIN").append(" ")
                for (i in numbers.indices) {
                    checkInterrupted()
                    if (i == addition) {

                        process.append(" | ")
                    } else if (i > 0) {
                        process.append(",")
                    }
                    process.append(numbers[i])
                }
                process.append(" ")
            }

            else -> {

                if (times >= 20) numbers.sort()
                for (i in numbers.indices) {
                    checkInterrupted()
                    total += numbers[i]
                    if (i > 0) process.append("+")
                    process.append(numbers[i])
                }
            }
        }

        val processText = format(DiceProcessFormat.LEVEL_3, process.toString(), total)
        return when (mode) {
            Mode.NORMAL_DICE ->
                ResRecursive(total.toInt(), processText, resIntMin = times, resIntMax = times * maxValue.toInt())

            Mode.EXCESS ->
                ResRecursive(total.toInt(), processText, resIntMin = 0, resIntMax = times)

            Mode.FRONT_SUM_MAXIMUM, Mode.FRONT_SUM_MINIMUM ->
                ResRecursive(total.toInt(), processText, resIntMin = addition, resIntMax = addition * maxValue.toInt())

            else -> ResRecursive(total.toInt(), processText)
        }
    }









    fun calculationFate(times: Int, range: Int): ResRecursive {
        if (times > MAX_FATE_TIMES) {
            throw ResErrorException(ResErrorType.NODE_LEFT_VAL_INVALID, "f的左值最大值为$MAX_FATE_TIMES")
        }
        val process = StringBuilder()
        var sum = 0
        for (i in 0 until times) {
            checkInterrupted()
            val value = random(-range, range)
            when (value) {
                0 -> process.append('0')
                1 -> {
                    sum++
                    process.append('+')
                }
                -1 -> {
                    sum--
                    process.append('-')
                }
                else -> {
                    sum += value
                    process.append(value)
                }
            }
            if (i != times - 1) {
                process.append(' ')
            }
        }
        return ResRecursive(
            sum,
            format(DiceProcessFormat.LEVEL_3, process.toString(), sum),
            resIntMax = times,
            resIntMin = -times
        )
    }






















    fun calculationWWandDX(
        mode: Mode,
        num: Int,
        addLine: Int,
        maxValue: Int,
        successLine: Int,
        singleLine: Boolean
    ): ResRecursive {
        if (mode != Mode.DX && mode != Mode.WW) {
            throw ResErrorException(ResErrorType.NODE_SUB_VAL_INVALID)
        }
        if (addLine < 2 || addLine > 99) {
            throw ResErrorException(ResErrorType.NODE_RIGHT_VAL_INVALID, "计算失败 加骰线要大于2 且 小于99")
        }
        if (maxValue < successLine && mode == Mode.WW) {
            throw ResErrorException(ResErrorType.NODE_NONSENSE)
        }

        var pending = num
        var successCount = 0
        var addTimes = 0
        var lastMax = 0
        val process = StringBuilder()

        do {
            var nextPending = 0
            var nextSuccess = 0
            lastMax = 0
            val round = StringBuilder()
            for (i in 0 until pending) {
                checkInterrupted()
                val value = random(1, maxValue)
                if (value > lastMax) {
                    lastMax = value
                }
                val reachesAddLine = value >= addLine
                val reachesSuccessLine = value >= successLine

                if (mode == Mode.DX) {
                    if (reachesAddLine) {
                        round.append(format(DiceProcessFormat.LEVEL_2, value))
                        nextPending++
                    } else {
                        round.append(value)
                    }
                } else {
                    when {
                        reachesAddLine && reachesSuccessLine -> {
                            round.append(format(DiceProcessFormat.LEVEL_2, format(DiceProcessFormat.LEVEL_1, value)))
                            nextPending++
                            nextSuccess++
                        }

                        reachesAddLine -> {
                            round.append(format(DiceProcessFormat.LEVEL_2, value))
                            nextPending++
                        }

                        reachesSuccessLine -> {
                            round.append(format(DiceProcessFormat.LEVEL_1, value))
                            nextSuccess++
                        }

                        else -> round.append(value)
                    }
                }
                if (i != pending - 1) {
                    round.append(",")
                }
            }

            pending = nextPending
            successCount += nextSuccess
            process.append(format(DiceProcessFormat.LEVEL_3, round.toString()))
            if (pending > 0) {
                process.append(",")
            }
            if (!singleLine) {
                process.append('\n')
            }
            addTimes++
        } while (pending > 0)
        addTimes--

        val resIntMax = if (maxValue >= addLine) Int.MAX_VALUE else 1
        return if (mode == Mode.DX) {
            val final = addTimes * 10 + lastMax
            ResRecursive(
                final,
                format(
                    DiceProcessFormat.LEVEL_3,
                    process.toString(),
                    Format.build("{replace}*10+{replace}", addTimes, lastMax)
                ) + format(DiceProcessFormat.LEVEL_4, final),
                resIntMin = 1,
                resIntMax = resIntMax
            )
        } else {
            ResRecursive(
                successCount,
                format(DiceProcessFormat.LEVEL_3, process.toString(), successCount),
                resIntMin = 0,
                resIntMax = resIntMax
            )
        }
    }




    private fun checkInterrupted() {
        if (Thread.interrupted()) {
            throw ResErrorException(ResErrorType.NODE_INTERRUPTED)
        }
    }


    private fun format(type: DiceProcessFormat, input: Any, result: Any? = null): String {
        val sb = StringBuilder()
        sb.append(String.format(diceConfigs.readDefaultFormat(type), input))
        if (result != null) {
            sb.append(String.format(diceConfigs.readDefaultFormat(DiceProcessFormat.LEVEL_4), result))
        }
        return sb.toString()
    }
}
