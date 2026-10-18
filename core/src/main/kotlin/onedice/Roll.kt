package onedice

import onedice.data.DiceConfigs
import onedice.data.Format
import onedice.data.ResRecursive
import onedice.defines.DiceException
import onedice.defines.ResErrorException
import onedice.defines.ResErrorType
import onedice.function.DiceCore
import onedice.function.Mode
import java.util.concurrent.atomic.AtomicReference




















































































class Roll(
    private val initData: String,
    private val diceConfigs: DiceConfigs = DiceConfigs()
) {


    val originData: String = normalize(initData)

    private val diceCore by lazy { DiceCore(diceConfigs) }







    fun start(): ResRecursive {
        val result = AtomicReference<ResRecursive?>(null)
        val failure = AtomicReference<Throwable?>(null)

        val worker = Thread {
            try {
                result.set(rollMultiLines())
            } catch (e: Throwable) {

                failure.set(
                    if (e is DiceException) e
                    else ResErrorException(ResErrorType.UNKNOWN_COMPLETE_FATAL, cause = e)
                )
            }
        }.apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
        worker.start()
        worker.join(diceConfigs.timeout)

        result.get()?.let { return it }
        failure.get()?.let { throw it }
        worker.interrupt()
        throw ResErrorException(
            ResErrorType.NODE_INTERRUPTED,
            "单次计算量过大导致超时，已终止计算,>${diceConfigs.timeout}ms"
        )
    }


    private fun rollMultiLines(): ResRecursive {
        var last: ResRecursive? = null
        originData.split(";").forEach { line ->
            last = rollSingleLine(line)
        }
        return last ?: throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "表达式为空")
    }


    private fun rollSingleLine(expression: String): ResRecursive {
        if (expression.isEmpty()) {
            throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "空的子表达式")
        }
        val tree = try {
            Parser(expression, diceConfigs).parse()
        } catch (e: DiceException) {
            throw e
        } catch (t: Throwable) {
            throw ResErrorException(ResErrorType.UNKNOWN_GENERATE_FATAL, cause = t)
        }
        val evaluated = evaluate(tree)
        return ResRecursive(
            resInt = evaluated.resInt,
            resDetail = evaluated.resDetail,
            resNumberDetail = evaluated.resNumberDetail,
            resIntMin = evaluated.resIntMin,
            resIntMax = evaluated.resIntMax,
            reRoll = { rollSingleLine(expression) }
        )
    }


    private fun normalize(input: String): String {
        val compact = input.lowercase().filterNot { it.isWhitespace() }
        return if (compact.startsWith("+")) compact.substring(1) else compact
    }



    private fun evaluate(expr: Expr): ResRecursive = when (expr) {
        is Literal -> literal(expr.value)

        is Not -> diceCore.calculationNot(evaluate(expr.operand).resInt)

        is Factorial -> factorial(expr.operand, evaluate(expr.operand))

        is Ternary -> {
            val condition = evaluate(expr.condition)
            val thenBranch = evaluate(expr.thenBranch)
            val elseBranch = evaluate(expr.elseBranch)
            diceCore.calculationTernaryOperator(condition.resInt, thenBranch.resInt, elseBranch.resInt)
        }

        is Binary -> evaluateBinary(expr)

        is DiceOperator -> evaluateDiceOperator(expr)
    }


    private fun literal(value: Int): ResRecursive =
        ResRecursive(value, value.toString(), value.toString(), resIntMin = value, resIntMax = value)

    private fun evaluateBinary(expr: Binary): ResRecursive {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)
        val priority = OperatorTable.priorityOf(expr.operator)
        val leftDetail = DetailParenthesis.wrap(expr.left, left.resDetail, priority, isRightSide = false)
        val rightDetail = DetailParenthesis.wrap(expr.right, right.resDetail, priority, isRightSide = true)

        return when (expr.operator) {
            '+' -> {
                if ((left.resInt == Int.MIN_VALUE && right.resInt == Int.MAX_VALUE) ||
                    (left.resInt == Int.MAX_VALUE && right.resInt == Int.MIN_VALUE)
                ) {

                    throw ResErrorException(ResErrorType.NODE_EXTREME_VAL_INVALID)
                }
                ResRecursive(
                    left.resInt + right.resInt,
                    Format.build("{replace}+{replace}", leftDetail, rightDetail),
                    Format.build("{replace}+{replace}", left.resInt, right.resInt),
                    resIntMin = left.resIntMin + right.resIntMin,
                    resIntMax = left.resIntMax + right.resIntMax
                )
            }

            '-' -> {
                if ((left.resInt == Int.MIN_VALUE && right.resInt == Int.MIN_VALUE) ||
                    (left.resInt == Int.MAX_VALUE && right.resInt == Int.MAX_VALUE)
                ) {
                    throw ResErrorException(ResErrorType.NODE_EXTREME_VAL_INVALID)
                }
                var min = 0
                var max = 0
                ResRecursive.getExtremum(left, right, { a, b -> a - b }) { cornerMax, cornerMin ->
                    max = cornerMax
                    min = cornerMin
                }
                ResRecursive(
                    left.resInt - right.resInt,
                    Format.build("{replace}-{replace}", leftDetail, rightDetail),
                    Format.build("{replace}-{replace}", left.resInt, right.resInt),
                    resIntMin = min,
                    resIntMax = max
                )
            }

            '*' -> {
                var min = 0
                var max = 0
                ResRecursive.getExtremum(left, right, { a, b -> a * b }) { cornerMax, cornerMin ->
                    max = cornerMax
                    min = cornerMin
                }
                ResRecursive(
                    left.resInt * right.resInt,
                    Format.build("{replace}*{replace}", leftDetail, rightDetail),
                    Format.build("{replace}*{replace}", left.resInt, right.resInt),
                    resIntMin = min,
                    resIntMax = max
                )
            }

            '/' -> {
                var min = 0
                var max = 0
                ResRecursive.getExtremum(left, right, { a, b -> a / b }) { cornerMax, cornerMin ->
                    max = cornerMax
                    min = cornerMin
                }
                ResRecursive(
                    left.resInt / right.resInt,
                    Format.build("{replace}/{replace}", leftDetail, rightDetail),
                    Format.build("{replace}/{replace}", left.resInt, right.resInt),
                    resIntMin = min,
                    resIntMax = max
                )
            }

            '^' -> {
                if (left.resInt == 0 && right.resInt == 0) {
                    throw ResErrorException(ResErrorType.NODE_EXTREME_VAL_INVALID)
                }
                if (left.resInt >= 100) {
                    throw ResErrorException(ResErrorType.NODE_LEFT_VAL_INVALID)
                }
                if (right.resInt >= 100) {
                    throw ResErrorException(ResErrorType.NODE_RIGHT_VAL_INVALID)
                }
                var min = 0
                var max = 0
                ResRecursive.getExtremum(left, right, { a, b -> floatPower(a, b) }) { cornerMax, cornerMin ->
                    max = cornerMax
                    min = cornerMin
                }
                ResRecursive(
                    integerPower(left.resInt, right.resInt),
                    Format.build("{replace}^{replace}", leftDetail, rightDetail),
                    Format.build("{replace}^{replace}", left.resInt, right.resInt),
                    resIntMin = min,
                    resIntMax = max
                )
            }

            '=' -> diceCore.calculationEqual(left.resInt, right.resInt)
            '>' -> diceCore.calculationBiggerThan(left.resInt, right.resInt)
            '<' -> diceCore.calculationSmallerThan(left.resInt, right.resInt)
            '&' -> diceCore.calculationAnd(left.resInt, right.resInt)
            '|' -> diceCore.calculationOr(left.resInt, right.resInt)

            else -> throw ResErrorException(
                ResErrorType.NODE_OPERATION_INVALID,
                "未实现的算子 '${expr.operator}'"
            )
        }
    }

    private fun evaluateDiceOperator(expr: DiceOperator): ResRecursive {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)
        val subs = HashMap<Char, ResRecursive>()
        expr.subs.forEach { (key, value) -> subs[key] = evaluate(value) }

        return when (expr.operator) {
            'd' -> {
                val present = listOf('k', 'q', 'a').filter { subs.containsKey(it) }
                if (present.size > 1) {
                    throw ResErrorException(ResErrorType.NODE_NONSENSE, "d的子参 k q a 只能存在一个")
                }
                if (right.resInt <= 0 || right.resInt >= 10000) {
                    throw ResErrorException(ResErrorType.NODE_RIGHT_VAL_INVALID, "d表达式右值非法,要求1~9999")
                }
                if (left.resInt <= 0 || left.resInt >= 10000) {
                    throw ResErrorException(ResErrorType.NODE_LEFT_VAL_INVALID, "d表达式左值非法,要求1~9999")
                }
                val mode = when (present.firstOrNull()) {
                    'k' -> Mode.FRONT_SUM_MAXIMUM
                    'q' -> Mode.FRONT_SUM_MINIMUM
                    'a' -> Mode.EXCESS
                    else -> Mode.NORMAL_DICE
                }
                val addition = present.firstOrNull()?.let { subs[it]?.resInt } ?: 1
                diceCore.calculationDice(mode, left.resInt, right.resInt.toLong(), addition)
            }

            'a' -> {

                val maxValue = subs['m']?.resInt ?: 10
                val successLine = subs['k']?.resInt ?: 8
                val addLine = right.resInt
                val num = left.resInt
                if (addLine <= 1 || addLine >= 1000) {
                    throw ResErrorException(ResErrorType.NODE_RIGHT_VAL_INVALID, "a表达式右值非法,要求2~999")
                } else if (num <= 0 || num >= 1000) {
                    throw ResErrorException(ResErrorType.NODE_LEFT_VAL_INVALID, "a表达式左值非法,要求1~999")
                } else if (maxValue <= 0 || maxValue >= 1000) {
                    throw ResErrorException(ResErrorType.NODE_SUB_VAL_INVALID, "a的子表达式m值非法,要求1~999")
                } else if (successLine <= 0 || successLine >= 1000) {
                    throw ResErrorException(ResErrorType.NODE_SUB_VAL_INVALID, "a的子表达式k值非法,要求1~999")
                }
                diceCore.calculationWWandDX(Mode.WW, num, addLine, maxValue, successLine, diceConfigs.singleLine)
            }

            'c' -> {

                val maxValue = subs['m']?.resInt ?: 10
                val addLine = right.resInt
                val num = left.resInt
                if (addLine <= 1 || addLine >= 1000) {
                    throw ResErrorException(ResErrorType.NODE_RIGHT_VAL_INVALID, "c表达式右值非法,要求2~999")
                }
                if (num <= 0 || num >= 1000) {
                    throw ResErrorException(ResErrorType.NODE_LEFT_VAL_INVALID, "c表达式左值非法,要求1~999")
                }
                if (maxValue <= 0 || maxValue >= 1000) {
                    throw ResErrorException(ResErrorType.NODE_SUB_VAL_INVALID, "c的子表达式m值非法,要求1~999")
                }
                diceCore.calculationWWandDX(Mode.DX, num, addLine, maxValue, addLine, diceConfigs.singleLine)
            }

            'f' -> {
                val range = right.resInt
                val times = left.resInt
                if (range <= 1 || range >= 500) {
                    throw ResErrorException(ResErrorType.NODE_RIGHT_VAL_INVALID, "f的右参数必须小于500")
                }
                if (times <= 0 || times >= 100) {
                    throw ResErrorException(ResErrorType.NODE_LEFT_VAL_INVALID, "f的左参数必须小于100")
                }
                if (range % 2 == 0) {
                    throw ResErrorException(ResErrorType.NODE_RIGHT_VAL_INVALID, "f的右参数必须是奇数")
                }
                diceCore.calculationFate(times, (range - 1) / 2)
            }

            'b' -> diceCore.calculationPunishBonus(Mode.BONUS, right.resInt)

            'p' -> diceCore.calculationPunishBonus(Mode.PUNISH, right.resInt)

            '$' -> {
                val index = right.resInt
                subs['e']?.let { diceConfigs.tempValue[index] = it }
                val stored = diceConfigs.tempValue[index] ?: throw ResErrorException(
                    ResErrorType.NODE_SUB_VAL_INVALID,
                    "临时变量 \$$index 未赋值"
                )
                ResRecursive(
                    stored.resInt,
                    stored.resDetail,
                    stored.resNumberDetail,
                    resIntMin = stored.resIntMin,
                    resIntMax = stored.resIntMax
                )
            }

            else -> throw ResErrorException(
                ResErrorType.NODE_OPERATION_INVALID,
                "未实现的骰算子 '${expr.operator}'"
            )
        }
    }





    private fun factorial(child: Expr, operand: ResRecursive): ResRecursive {
        if (operand.resInt < 0) {
            throw ResErrorException(ResErrorType.NODE_LEFT_VAL_INVALID, "阶乘不能作用于负数")
        }
        val operandDetail = DetailParenthesis.wrap(
            child,
            operand.resDetail,
            OperatorTable.priorityOf('!'),
            isRightSide = false
        )
        return ResRecursive(
            factorialValue(operand.resInt),
            Format.build("{replace}!", operandDetail),
            Format.build("{replace}!", operand.resInt),
            resIntMin = factorialValue(operand.resIntMin),
            resIntMax = factorialValue(operand.resIntMax)
        )
    }

    private fun factorialValue(value: Int): Int {
        if (value <= 1) return 1
        var product = 1L
        for (i in 1..value) {
            product *= i
            if (product > Int.MAX_VALUE) return Int.MAX_VALUE
        }
        return product.toInt()
    }


    private fun integerPower(base: Int, exponent: Int): Int {
        val result = Math.pow(base.toDouble(), exponent.toDouble())
        return when {
            result > Int.MAX_VALUE -> Int.MAX_VALUE
            result < Int.MIN_VALUE -> Int.MIN_VALUE
            else -> result.toInt()
        }
    }


    private fun floatPower(base: Float, exponent: Float): Float =
        Math.pow(base.toDouble(), exponent.toDouble()).toFloat()
}











private sealed interface Expr


private class Literal(val value: Int) : Expr


private class Binary(val operator: Char, val left: Expr, val right: Expr) : Expr


private class DiceOperator(
    val operator: Char,
    val left: Expr,
    val right: Expr,
    val subs: Map<Char, Expr>
) : Expr


private class Factorial(val operand: Expr) : Expr


private class Not(val operand: Expr) : Expr


private class Ternary(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr











private object OperatorTable {


    const val DICE_OPERATORS = "dacfbp$"


    const val NOT_PRIORITY = 8


    const val UNARY_MINUS_PRIORITY = 6


    const val UNARY_MINUS_OPERAND_PRIORITY = 7


    const val TERNARY_PRIORITY = 1


    const val TERNARY_BRANCH_PRIORITY = 2

    private const val OPERATORS = "?|&=><+-*/^!dacfbp$"

    fun isOperator(char: Char): Boolean = char in OPERATORS

    fun priorityOf(operator: Char): Int = when (operator) {
        '?' -> 1
        '|' -> 2
        '&' -> 3
        '=' -> 4
        '>', '<' -> 5
        '+', '-' -> 6
        '*', '/' -> 7
        '^', '!' -> 8
        'd', 'a', 'c', 'f', 'b', 'p', '$' -> 9
        else -> throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "非法字符 '$operator'")
    }
}








private object DetailParenthesis {


    fun priorityOf(expr: Expr): Int? = when (expr) {
        is Literal -> null
        is Binary -> OperatorTable.priorityOf(expr.operator)
        is DiceOperator -> OperatorTable.priorityOf(expr.operator)
        is Factorial -> OperatorTable.priorityOf('!')
        is Not -> OperatorTable.NOT_PRIORITY
        is Ternary -> OperatorTable.priorityOf('?')
    }


    fun wrap(child: Expr, detail: String, parentPriority: Int, isRightSide: Boolean): String {
        val childPriority = priorityOf(child) ?: return detail
        val needWrap = if (isRightSide) childPriority <= parentPriority else childPriority < parentPriority
        return if (needWrap) "($detail)" else detail
    }
}















private class Parser(private val source: String, private val configs: DiceConfigs) {

    private var position = 0

    fun parse(): Expr {
        val tree = parseExpression(1)
        if (position < source.length) {
            throw ResErrorException(
                ResErrorType.INPUT_RAW_INVALID,
                "表达式尾部有未消费的内容：'${source.substring(position)}'"
            )
        }
        return tree
    }

    private fun peek(): Char? = if (position < source.length) source[position] else null

    private fun expect(char: Char) {
        if (peek() != char) {
            throw ResErrorException(ResErrorType.INPUT_CHILD_PARA_INVALID, "缺少 '$char'")
        }
        position++
    }


    private fun parseExpression(minPriority: Int): Expr {
        var left = parseOperand()
        while (true) {
            val char = peek() ?: break

            if (char == ')' || char == ':') break
            if (!OperatorTable.isOperator(char)) {
                throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "非法字符 '$char'")
            }
            if (OperatorTable.priorityOf(char) < minPriority) break

            when {
                char == '?' -> {
                    position++
                    val thenBranch = parseExpression(OperatorTable.TERNARY_BRANCH_PRIORITY)
                    expect(':')
                    val elseBranch = parseExpression(OperatorTable.TERNARY_BRANCH_PRIORITY)
                    left = Ternary(left, thenBranch, elseBranch)
                }

                char == '!' -> {
                    position++
                    left = Factorial(left)
                }

                char in OperatorTable.DICE_OPERATORS -> {

                    position++
                    left = parseDiceOperator(char, left)
                }

                else -> {
                    position++
                    val right = parseExpression(OperatorTable.priorityOf(char) + 1)
                    left = Binary(char, left, right)
                }
            }
        }
        return left
    }






    private fun parseDiceOperator(operator: Char, left: Expr): DiceOperator {
        val subs = HashMap<Char, Expr>()
        captureSubParameters(operator, subs)

        var right: Expr? = if (canStartOperand(peek())) parseOperand() else null
        captureSubParameters(operator, subs)

        if (right == null) {
            val fallback = configs.defaultsFor(operator).right ?: throw ResErrorException(
                ResErrorType.INPUT_RAW_INVALID,
                "算子 '$operator' 缺少操作数，且没有默认值"
            )
            right = Literal(fallback)
        }
        return DiceOperator(operator, left, right, subs)
    }


    private fun parseOperand(): Expr {
        val char = peek() ?: throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "表达式不完整")
        return when {
            char == '(' -> {
                position++
                val inner = parseExpression(1)
                expect(')')
                inner
            }

            char.isDigit() -> Literal(readInteger())

            char == '-' -> {


                position++
                Binary('-', Literal(0), parseExpression(OperatorTable.UNARY_MINUS_OPERAND_PRIORITY))
            }

            char == '!' -> {
                position++
                Not(parseOperand())
            }

            char in OperatorTable.DICE_OPERATORS -> {

                val fallback = configs.defaultsFor(char).left ?: throw ResErrorException(
                    ResErrorType.INPUT_RAW_INVALID,
                    "算子 '$char' 缺少左操作数，且没有默认值"
                )
                Literal(fallback)
            }

            else -> throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "非法字符 '$char'")
        }
    }


    private fun parseSubValue(): Expr {
        val char = peek() ?: throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "子参数缺少取值")
        return when {
            char == '(' -> {
                position++
                val inner = parseExpression(1)
                expect(')')
                inner
            }

            char == '-' -> {
                position++
                Binary('-', Literal(0), parseSubValue())
            }

            char.isDigit() -> Literal(readInteger())

            else -> throw ResErrorException(ResErrorType.INPUT_RAW_INVALID, "子参数取值非法：'$char'")
        }
    }







    private fun captureSubParameters(operator: Char, into: MutableMap<Char, Expr>) {
        val allowed = subParametersOf(operator) ?: return
        while (true) {
            val char = peek() ?: break
            if (char !in allowed || into.containsKey(char)) break
            position++
            into[char] = parseSubValue()
        }
    }








    private fun subParametersOf(operator: Char): Set<Char>? = when (operator) {
        'd' -> setOf('k', 'q', 'p', 'b', 'a')
        'a' -> setOf('k', 'm')
        'c' -> setOf('m')
        '$' -> setOf('e')
        else -> null
    }

    private fun canStartOperand(char: Char?): Boolean =
        char != null && (char.isDigit() || char == '(' || char == '-' || char == '!')







    private fun readInteger(): Int {
        val start = position
        while (position < source.length && source[position].isDigit()) {
            position++
        }
        return source.substring(start, position).toIntOrNull() ?: 0
    }
}
