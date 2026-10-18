package stellaris.dice.diceFunction.responder

import stellaris.dice.diceFunction.DiceConstant







































object RollArguments {


    private val REPEAT_PREFIX_PATTERN = Regex("(\\d+#)(.*)", RegexOption.DOT_MATCHES_ALL)


    private val REPEAT_SUFFIX_PATTERN = Regex("^(.+)#(\\d+)$")







    class RollArguments(val expression: String, val reason: String)


    class RepeatTimes(val remain: String, val times: Int)






    fun parse(contentMiddle: String): RollArguments {
        val rollExpressionParts = ArrayList<String>()
        val reasonParts = ArrayList<String>()

        if (DiceConstant.REGEX_PURE_EXPRESSION_CHARS.matches(contentMiddle)) {

            rollExpressionParts.add(contentMiddle)
        } else {
            val params = contentMiddle.split(' ')
            var index = 0
            var isFirstParam = true
            while (index < params.size) {
                val param = params[index]
                index++
                if (param.isEmpty()) {
                    continue
                }
                if (DiceConstant.REGEX_PURE_EXPRESSION_CHARS.matches(param)) {

                    when {
                        isFirstParam -> rollExpressionParts.add(param)
                        index >= params.size && !DiceConstant.REGEX_PURE_NUMBERS.matches(param) ->
                            reasonParts.add(param)

                        else -> rollExpressionParts.add(param)
                    }
                } else {
                    if (isFirstParam) {

                        val match = DiceConstant.REGEX_EXPRESSION_CHARS.find(param)
                        if (match == null) {
                            reasonParts.add(param)
                        } else {
                            match.groups[1]?.let { expression ->
                                rollExpressionParts.add(expression.value)
                                reasonParts.add(param.substring(expression.value.length))
                            }
                        }
                    } else {

                        reasonParts.add(param)
                        while (index < params.size) {
                            reasonParts.add(params[index])
                            index++
                        }
                    }
                    break
                }
                isFirstParam = false
            }
        }
        return RollArguments(rollExpressionParts.joinToString(""), reasonParts.joinToString(" "))
    }













    fun repeatTimes(input: String): RepeatTimes {
        val boundary = input.indexOfFirst { it.isWhitespace() }
        val head = if (boundary < 0) input else input.substring(0, boundary)
        val tail = if (boundary < 0) "" else input.substring(boundary)
        REPEAT_SUFFIX_PATTERN.matchEntire(head)?.let { match ->
            val times = (match.groupValues[2].toIntOrNull() ?: 1).coerceAtLeast(1)
            return RepeatTimes(match.groupValues[1] + tail, times)
        }
        val match = REPEAT_PREFIX_PATTERN.find(input) ?: return RepeatTimes(input, 1)
        val times = match.groupValues[1].dropLast(1).toIntOrNull() ?: 1
        return RepeatTimes(match.groupValues[2], times)
    }
}
