package stellaris.dice.diceFunction.commands










class DiceCommandDecision(
    val command: String,
    val param: String,
    val isProtected: Boolean,
    val rewritten: Boolean
)













object DiceCommandRouter {








    fun route(input: String, extras: List<String> = emptyList()): DiceCommandDecision? {
        var decision: DiceCommandDecision? = null
        val capture = object : DiceCommandCallback {
            override fun onException(exception: onedice.defines.DiceException) {

            }

            override fun onCommand(command: String, param: String, isProtected: Boolean): Boolean {
                decision = DiceCommandDecision(command, param, isProtected, rewritten = false)
                return true
            }

            override fun extraCommands(): ArrayList<String> = ArrayList(extras)
        }

        val rewritten = rewriteOnce(input, extras)
        DiceCommandDecoder.invoke(capture, rewritten ?: input)
        return decision?.let {
            DiceCommandDecision(it.command, it.param, it.isProtected, rewritten != null)
        }
    }


    fun isCommand(input: String, extras: List<String> = emptyList()): Boolean = route(input, extras) != null








    private fun rewriteOnce(input: String, extras: List<String>): String? {
        val commands = ArrayList<String>().apply {
            addAll(DiceCommandDecoder.registeredCommands())
            addAll(extras)
        }
        commands.sortWith(SortByLengthComparator())
        for (command in commands) {
            if (input.startsWith(command, ignoreCase = true)) {
                val rewritten = DiceCommandLegacy.EFFECT_COMMANDS[command]?.onFix(input)
                return if (rewritten != null && !input.equals(rewritten, ignoreCase = true)) rewritten else null
            }
        }
        return null
    }
}
