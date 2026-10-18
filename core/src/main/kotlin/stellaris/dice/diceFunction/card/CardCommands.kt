package stellaris.dice.diceFunction.card

import stellaris.dice.diceFunction.DiceConstant







































class CardCommands(
    private val manager: PlayerCardManager,
    private val owner: String,
    private val group: String
) {


    sealed class Outcome {






        class Listed(val cards: List<PlayerCardData>, val currentName: String?) : Outcome()


        class Created(val card: PlayerCardData) : Outcome()


        class Deleted(val name: String) : Outcome()


        class Shown(val card: PlayerCardData) : Outcome()


        class Bound(val card: PlayerCardData) : Outcome()


        class Copied(val from: String, val to: String) : Outcome()


        class RuleChanged(val card: PlayerCardData) : Outcome()


        class BindingsListed(val bindings: Map<String, String>) : Outcome()


        object BindingsCleared : Outcome()


        object AllCleared : Outcome()


        object Unbound : Outcome()


        class Renamed(val from: String, val to: String) : Outcome()


        class ShownAbove(val card: PlayerCardData, val threshold: Int) : Outcome()


        class Built(
            val card: PlayerCardData,

            val force: Boolean,
            val summary: String
        ) : Outcome()


        class Failed(val reason: Reason, val detail: String = "") : Outcome()
    }


    enum class Reason {


        USAGE,


        NO_SUCH_CARD,


        EXISTS,


        NAME_TOO_LONG,


        TOO_MANY_CARDS,


        UNSUPPORTED_RULE
    }


    fun run(param: String): Outcome {
        val normalized = param.trim()
        if (normalized.isEmpty()) {
            return Outcome.Failed(Reason.USAGE)
        }
        val space = normalized.indexOf(' ')
        val sub = (if (space < 0) normalized else normalized.substring(0, space)).lowercase()
        val rest = if (space < 0) "" else normalized.substring(space + 1).trim()
        return when (sub) {
            "list" -> list()
            "new" -> newCard(rest)
            "del" -> delete(rest)
            "show" -> show(rest)
            "grp" -> group(rest)
            "tag" -> tag(rest)
            "cpy" -> copy(rest)
            "rule" -> changeRule(rest)
            "nn" -> rename(rest)
            "clr" -> clearAll()
            "build" -> buildAttributes(rest, force = false)
            "redo" -> buildAttributes(rest, force = true)
            else -> Outcome.Failed(Reason.USAGE)
        }
    }

    private fun list(): Outcome =
        Outcome.Listed(manager.getPlayers(owner), manager.currentPlayerName(owner, group))

    private fun newCard(rest: String): Outcome {
        val parts = split(rest)
        if (parts.isEmpty()) {
            return Outcome.Failed(Reason.USAGE)
        }
        val name = parts[0]
        val rule = if (parts.size > 1) {
            PlayerCardData.RuleType.parse(parts[1]) ?: return Outcome.Failed(Reason.USAGE)
        } else {
            DEFAULT_RULE
        }
        if (name.length > DiceConstant.MAX_PLAYER_LENGTH) {
            return Outcome.Failed(Reason.NAME_TOO_LONG, name)
        }
        if (manager.countPlayers(owner) >= DiceConstant.MAX_CARD_COUNT) {
            return Outcome.Failed(Reason.TOO_MANY_CARDS)
        }
        if (manager.verifyPlayerExistence(owner, name)) {
            return Outcome.Failed(Reason.EXISTS, name)
        }
        val card = manager.createPlayer(owner, name) ?: return Outcome.Failed(Reason.EXISTS, name)
        card.rule = rule
        manager.savePlayer(card)

        manager.setCurrentPlayer(owner, group, card)
        return Outcome.Created(card)
    }

    private fun delete(rest: String): Outcome {


        val explicit = rest.trim()
        val name = if (explicit.isNotEmpty()) {
            explicit
        } else {
            manager.currentPlayerName(owner, group) ?: return Outcome.Failed(Reason.USAGE)
        }
        val card = find(name) ?: return Outcome.Failed(Reason.NO_SUCH_CARD, name)
        manager.deleteCertainPlayer(card)
        return Outcome.Deleted(name)
    }

    private fun show(rest: String): Outcome {
        if (rest.isEmpty()) {
            return Outcome.Shown(manager.getCurrentPlayer(owner, group))
        }

        rest.toIntOrNull()?.let { threshold ->
            return Outcome.ShownAbove(manager.getCurrentPlayer(owner, group), threshold)
        }
        return Outcome.Shown(find(rest) ?: return Outcome.Failed(Reason.NO_SUCH_CARD, rest))
    }


    private fun group(rest: String): Outcome {
        if (rest.equals("clr", ignoreCase = true)) {
            manager.clearBindings(owner)
            return Outcome.BindingsCleared
        }
        if (rest.isEmpty()) {
            return Outcome.BindingsListed(manager.getBindings(owner))
        }
        return bind(rest)
    }


    private fun tag(rest: String): Outcome {
        if (rest.isEmpty()) {
            manager.setCurrentPlayer(owner, group, null)
            return Outcome.Unbound
        }
        return bind(rest)
    }

    private fun bind(rest: String): Outcome {
        if (rest.isEmpty()) {
            return Outcome.Failed(Reason.USAGE)
        }
        val card = find(rest) ?: return Outcome.Failed(Reason.NO_SUCH_CARD, rest)
        manager.setCurrentPlayer(owner, group, card)
        return Outcome.Bound(card)
    }


    private fun rename(rest: String): Outcome {
        if (rest.isEmpty()) {
            return Outcome.Failed(Reason.USAGE)
        }
        if (rest.length > DiceConstant.MAX_PLAYER_LENGTH) {
            return Outcome.Failed(Reason.NAME_TOO_LONG, rest)
        }
        if (manager.verifyPlayerExistence(owner, rest)) {
            return Outcome.Failed(Reason.EXISTS, rest)
        }
        val card = manager.getCurrentPlayer(owner, group)
        val from = card.readFriendlyName(defaultName = DiceConstant.ENV_DEFAULT_SHOW_PLAYER_NAME)
        card.playerName = rest
        manager.savePlayer(card)
        return Outcome.Renamed(from, rest)
    }


    private fun clearAll(): Outcome {
        manager.removeAll(owner)
        return Outcome.AllCleared
    }







    private fun buildAttributes(rest: String, force: Boolean): Outcome {
        val card = if (rest.isEmpty()) {
            manager.getCurrentPlayer(owner, group)
        } else {
            find(rest) ?: return Outcome.Failed(Reason.NO_SUCH_CARD, rest)
        }
        val result = CardBuilder.build(card, force)
            ?: return Outcome.Failed(Reason.UNSUPPORTED_RULE)
        manager.savePlayer(card)
        return Outcome.Built(card, force, result.summary)
    }

    private fun copy(rest: String): Outcome {
        val parts = split(rest)
        if (parts.isEmpty()) {
            return Outcome.Failed(Reason.USAGE)
        }
        val from: String
        val to: String
        if (parts.size == 1) {
            from = manager.getCurrentPlayer(owner, group).playerName
            to = parts[0]
        } else {
            from = parts[0]
            to = parts[1]
        }
        if (to.length > DiceConstant.MAX_PLAYER_LENGTH) {
            return Outcome.Failed(Reason.NAME_TOO_LONG, to)
        }
        if (!manager.verifyPlayerExistence(owner, from)) {
            return Outcome.Failed(Reason.NO_SUCH_CARD, from)
        }
        if (manager.verifyPlayerExistence(owner, to)) {
            return Outcome.Failed(Reason.EXISTS, to)
        }
        if (manager.countPlayers(owner) >= DiceConstant.MAX_CARD_COUNT) {
            return Outcome.Failed(Reason.TOO_MANY_CARDS)
        }
        manager.copyPlayer(owner, from, to) ?: return Outcome.Failed(Reason.EXISTS, to)
        return Outcome.Copied(from, to)
    }

    private fun changeRule(rest: String): Outcome {
        if (rest.isEmpty()) {
            return Outcome.Failed(Reason.USAGE)
        }
        val rule = PlayerCardData.RuleType.parse(rest) ?: return Outcome.Failed(Reason.USAGE)
        val card = manager.getCurrentPlayer(owner, group)
        card.rule = rule
        manager.savePlayer(card)
        return Outcome.RuleChanged(card)
    }

    private fun find(name: String): PlayerCardData? =
        manager.getPlayers(owner).firstOrNull { it.playerName == name }

    private fun split(rest: String): List<String> =
        rest.split("\\s+".toRegex()).filter { it.isNotEmpty() }

    private companion object {


        val DEFAULT_RULE = PlayerCardData.RuleType.COC7
    }
}
