package stellaris.dice.diceFunction.calculation











class EquationBuilder {

    private val expressions = ArrayList<String>()

    private var result: Int = 0






    fun add(expression: String, position: Int = -1): EquationBuilder {
        val input = expression.trim()
        for (existing in expressions) {
            if (existing == input) {
                return this
            }
        }
        if (DiceCalculation.isNumber(input)) {

            result = DiceCalculation.stringToInt(input, result)
            return this
        }
        if (position >= 0) {
            expressions.add(position, input)
        } else {
            expressions.add(input)
        }
        return this
    }

    override fun toString(): String {
        if (expressions.isEmpty()) {
            return result.toString()
        }
        val builder = StringBuilder()
        for (expression in expressions) {
            builder.append(expression).append("=")
        }
        val joined = builder.toString()
        return joined.substring(0, joined.length - 1)
    }
}
