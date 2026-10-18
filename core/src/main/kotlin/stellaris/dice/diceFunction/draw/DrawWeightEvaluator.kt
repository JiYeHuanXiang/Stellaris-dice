package stellaris.dice.diceFunction.draw
















fun interface DrawWeightEvaluator {






    fun evaluate(expression: String): Double?
}


object DrawWeights {

    @Volatile
    private var evaluator: DrawWeightEvaluator? = null


    @JvmStatic
    fun install(evaluator: DrawWeightEvaluator?) {
        this.evaluator = evaluator
    }

    internal fun evaluate(expression: String): Double? {
        val current = evaluator ?: return null
        return try {
            current.evaluate(expression)
        } catch (t: Throwable) {


            null
        }
    }
}
