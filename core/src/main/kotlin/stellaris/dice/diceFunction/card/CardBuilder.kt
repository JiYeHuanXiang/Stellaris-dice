package stellaris.dice.diceFunction.card

import stellaris.dice.diceFunction.calculation.DiceCalculation




























object CardBuilder {


    class BuildResult(
        val card: PlayerCardData,

        val force: Boolean,

        val summary: String
    )


    private val COC_ATTRIBUTES = listOf(
        "力量", "体质", "体型", "敏捷", "外貌", "智力", "意志", "教育", "幸运"
    )


    private val DND_ATTRIBUTES = listOf("力量", "敏捷", "体质", "智力", "感知", "魅力")







    fun build(card: PlayerCardData, force: Boolean): BuildResult? {
        return when (card.rule) {
            PlayerCardData.RuleType.COC6,
            PlayerCardData.RuleType.COC7 -> buildCoc(card, force)

            PlayerCardData.RuleType.DND3R,
            PlayerCardData.RuleType.DND5E,
            PlayerCardData.RuleType.DND5R -> buildDnd(card, force)

            else -> null
        }
    }


    private fun buildCoc(card: PlayerCardData, force: Boolean): BuildResult {
        val generated = LinkedHashMap<String, Int>()
        COC_ATTRIBUTES.forEach { name ->
            generated[name] = when (name) {
                "体型", "智力", "教育" -> (roll("2d6") + 6) * 5
                else -> roll("3d6") * 5
            }
        }
        generated.forEach { (name, value) -> setIf(card, name, value, force) }
        val summary = COC_ATTRIBUTES.joinToString(" ") { "$it${card.readProperty(it)}" }
        return BuildResult(card, force, summary)
    }


    private fun buildDnd(card: PlayerCardData, force: Boolean): BuildResult {
        val generated = LinkedHashMap<String, Int>()
        DND_ATTRIBUTES.forEach { name ->
            generated[name] = roll("4d6k3")
        }
        generated.forEach { (name, value) -> setIf(card, name, value, force) }

        val con = card.readProperty("体质")
        val dex = card.readProperty("敏捷")
        val wis = card.readProperty("感知")
        val maxHp = 10 + modifier(con)
        val ac = 10 + modifier(dex)
        val pp = 10 + modifier(wis)

        setIf(card, "dndmaxhp", maxHp, force)
        setIf(card, "ac", ac, force)
        setIf(card, "体力", maxHp, force)
        if (card.rule == PlayerCardData.RuleType.DND5E ||
            card.rule == PlayerCardData.RuleType.DND5R
        ) {
            setIf(card, "pp", pp, force)
        }

        val abilities = DND_ATTRIBUTES.joinToString(" ") { name ->
            val value = card.readProperty(name)
            "$name$value(${modifierText(modifier(value))})"
        }
        val derived = if (card.rule == PlayerCardData.RuleType.DND5E ||
            card.rule == PlayerCardData.RuleType.DND5R
        ) {
            "血量上限$maxHp 无甲AC$ac 被动察觉$pp"
        } else {
            "血量上限$maxHp 无甲AC$ac"
        }
        return BuildResult(card, force, "$abilities\n$derived")
    }


    private fun setIf(card: PlayerCardData, name: String, value: Int, force: Boolean) {
        if (force || !card.hasProperty(name)) {
            card.setProperty(name, value)
        }
    }


    private fun roll(expression: String): Int =
        DiceCalculation.rollCalculation(expression).intNumber


    fun modifier(value: Int): Int = Math.floorDiv(value - 10, 2)


    fun modifierText(modifier: Int): String =
        if (modifier >= 0) "+$modifier" else modifier.toString()
}
