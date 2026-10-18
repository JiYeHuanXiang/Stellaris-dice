package stellaris.dice.diceFunction.commands










class SortByLengthComparator : Comparator<String> {
    override fun compare(var1: String, var2: String): Int = when {
        var1.length > var2.length -> -1
        var1.length == var2.length -> 0
        else -> 1
    }
}
