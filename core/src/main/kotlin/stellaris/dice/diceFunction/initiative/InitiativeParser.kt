package stellaris.dice.diceFunction.initiative

import stellaris.dice.diceFunction.DiceConstant





























object InitiativeParser {


    class NameSpec(
        val name: String,
        val count: Int,
        val shared: Boolean,
        val expression: String
    )


    class Request(val specs: List<NameSpec>)


    private val REPEAT = Regex("^(\\d+)#(.+)$")







    private val TRAILING_EXPRESSION = Regex(
        "^(.*?)((?:[+-]\\d+)|(?:\\d*d\\d+(?:[kq]\\d+)?(?:[+-]\\d+)?))$",
        RegexOption.IGNORE_CASE
    )








    fun parse(
        param: String,
        defaultName: String,
        defaultExpression: String = DEFAULT_EXPRESSION
    ): Request {
        val rest = param.trim()
        if (rest.isEmpty()) {
            return Request(listOf(NameSpec(defaultName, 1, false, defaultExpression)))
        }


        val tokens = rest.split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.size == 1 && isExpression(tokens[0])) {
            return Request(
                listOf(NameSpec(defaultName, 1, false, toExpression(tokens[0], defaultExpression)))
            )
        }


        var baseExpression = defaultExpression
        var namesPart = rest
        if (tokens.size >= 2 && isExpression(tokens.last())) {
            baseExpression = toExpression(tokens.last(), defaultExpression)
            namesPart = tokens.dropLast(1).joinToString(" ")
        }


        val specs = namesPart.split('/')
            .filter { it.isNotBlank() }
            .map { parseName(it, baseExpression, defaultName) }
        return Request(specs.ifEmpty { listOf(NameSpec(defaultName, 1, false, baseExpression)) })
    }

    private fun parseName(raw: String, baseExpression: String, defaultName: String): NameSpec {
        var token = raw.trim()
        var shared = false
        if (token.startsWith("=")) {
            shared = true
            token = token.substring(1)
        }

        var count = 1
        REPEAT.matchEntire(token)?.let { match ->
            count = (match.groupValues[1].toIntOrNull() ?: 1)
                .coerceIn(1, DiceConstant.MAX_FIRSTLY_ATTACKING_VALUE_COUNT)
            token = match.groupValues[2]
        }

        if (token.startsWith("=")) {
            shared = true
            token = token.substring(1)
        }

        var name = token
        var expression = baseExpression
        TRAILING_EXPRESSION.matchEntire(token)?.let { match ->
            name = match.groupValues[1].ifEmpty { defaultName }
            val suffix = match.groupValues[2]
            expression = if (suffix.startsWith("+") || suffix.startsWith("-")) {
                "$baseExpression$suffix"
            } else {
                suffix
            }
        }
        if (name.isEmpty()) {
            name = defaultName
        }
        return NameSpec(name, count, shared, expression)
    }


    private fun isExpression(text: String): Boolean =
        DiceConstant.REGEX_PURE_EXPRESSION_CHARS.matches(text)


    private fun toExpression(token: String, defaultExpression: String): String =
        if (token.startsWith("+") || token.startsWith("-")) {
            "$defaultExpression$token"
        } else {
            token
        }


    const val DEFAULT_EXPRESSION = "d20"

    private val WHITESPACE = Regex("\\s+")
}
