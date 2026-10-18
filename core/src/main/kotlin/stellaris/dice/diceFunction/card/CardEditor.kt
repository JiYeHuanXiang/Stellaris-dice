package stellaris.dice.diceFunction.card

import stellaris.dice.diceFunction.calculation.DiceCalculation
import stellaris.dice.diceFunction.responder.RollOptions


































object CardEditor {


    sealed class Request {


        object ShowAll : Request()


        data class Show(val name: String) : Request()


        data class Delete(val name: String) : Request()


        object Clear : Request()


        class Set(val edits: List<Edit>) : Request()


        object Usage : Request()
    }


    sealed class Outcome {







        class Updated(val appliedNames: List<String>, val rejectedNames: List<String>) : Outcome()


        data class Shown(val name: String) : Outcome()


        object ShowAll : Outcome()


        data class Deleted(val name: String) : Outcome()


        object Cleared : Outcome()


        data class Missing(val name: String) : Outcome()


        object Usage : Outcome()
    }







    data class Edit(val name: String, val text: String) {


        val value: Int? get() = text.toIntOrNull()


        val isDelta: Boolean get() = text.startsWith("+") || text.startsWith("-")
    }







    private val NAME = Regex(
        "^([^\\d+\\-]+?)(?=[+\\-]?(?:\\d|d\\d))",
        RegexOption.IGNORE_CASE
    )







    private val VALUE = Regex(
        "^[+\\-]?(?:\\d*d\\d+(?:[kq]\\d+)?(?:[+\\-*/]\\d+(?:d\\d+)?)*" +
            "|\\d+(?:[+\\-*/]\\d+(?:d\\d+)?)*)",
        RegexOption.IGNORE_CASE
    )


    private val BARE_NAME = Regex("^[^\\d+\\-]+$")

    private const val SHOW = "show"
    private const val DEL = "del"
    private const val CLR = "clr"







    internal val DEFAULT_ALIASES = mapOf("san" to "理智")


    private fun canonical(name: String, aliases: Map<String, String>): String =
        aliases[name.lowercase()] ?: name


    fun parse(param: String, aliases: Map<String, String> = DEFAULT_ALIASES): Request {
        val raw = param.trim()
        if (raw.isEmpty()) {
            return Request.ShowAll
        }

        if (raw.equals(CLR, ignoreCase = true)) {
            return Request.Clear
        }
        if (raw.equals(DEL, ignoreCase = true)) {
            return Request.Usage
        }
        if (raw.length > DEL.length &&
            raw.regionMatches(0, DEL, 0, DEL.length, ignoreCase = true) &&
            raw[DEL.length].isWhitespace()
        ) {
            val name = raw.substring(DEL.length).trim()
            return if (name.isEmpty()) Request.Usage else Request.Delete(canonical(name, aliases))
        }

        val normalized = raw.replace("\\s".toRegex(), "")
        if (normalized.regionMatches(0, SHOW, 0, SHOW.length, ignoreCase = true)) {
            val name = normalized.substring(SHOW.length)
            return if (name.isEmpty()) Request.ShowAll else Request.Show(canonical(name, aliases))
        }

        val edits = ArrayList<Edit>()
        var rest = normalized
        while (rest.isNotEmpty()) {
            val nameMatch = NAME.find(rest) ?: return fallback(rest, edits, aliases)
            val name = canonical(nameMatch.groupValues[1], aliases)
            val afterName = rest.substring(nameMatch.value.length)
            val valueMatch = VALUE.find(afterName) ?: return fallback(rest, edits, aliases)
            edits.add(Edit(name, valueMatch.value))
            rest = afterName.substring(valueMatch.value.length)
        }
        return if (edits.isEmpty()) Request.Usage else Request.Set(edits)
    }







    private fun fallback(rest: String, edits: List<Edit>, aliases: Map<String, String>): Request {
        if (edits.isNotEmpty()) {
            return Request.Set(edits)
        }
        return if (BARE_NAME.matches(rest)) Request.Show(canonical(rest, aliases)) else Request.Usage
    }







    fun apply(card: PlayerCardData, param: String): Outcome =
        when (val request = parse(param, DEFAULT_ALIASES + card.aliases)) {
        is Request.ShowAll -> Outcome.ShowAll
        is Request.Show -> Outcome.Shown(request.name)
        is Request.Delete -> if (card.hasProperty(request.name)) {
            card.removeProperty(request.name)
            Outcome.Deleted(request.name)
        } else {
            Outcome.Missing(request.name)
        }

        is Request.Clear -> {
            card.clearProperties()
            Outcome.Cleared
        }

        is Request.Set -> {

            val resolved = ArrayList<Pair<Edit, Int>>()
            request.edits.forEach { edit ->
                val value = evaluate(edit.text) ?: return Outcome.Usage
                resolved.add(edit to value)
            }
            val applied = ArrayList<String>()
            val rejected = ArrayList<String>()
            resolved.forEach { (edit, value) ->
                val newValue = if (edit.isDelta) {
                    card.readProperty(edit.name) + value
                } else {
                    value
                }
                if (card.setProperty(edit.name, newValue)) {
                    applied.add(edit.name)
                } else {

                    rejected.add(edit.name)
                }
            }
            Outcome.Updated(applied, rejected)
        }

        Request.Usage -> Outcome.Usage
    }


    private fun evaluate(text: String): Int? {
        text.toIntOrNull()?.let { return it }
        return try {
            DiceCalculation.rollCalculation(
                text,
                RollOptions(allowNegativeNumber = true)
            ).intNumber
        } catch (t: Throwable) {
            null
        }
    }


    fun render(card: PlayerCardData, names: List<String>): String =
        names.joinToString(" ") { "$it${card.readProperty(it)}" }
}
