package stellaris.dice.diceFunction.card

/**
 * `.sn` 插件的名片模板渲染。字段形如 `{pc_name}`、`{pc_hp}`、`{pc_敏捷}`。
 * 卡上没有的属性返回空串。
 */
object CardAutoName {

    private val FIELD = Regex("\\{pc_([^}]+)}")

    private val ALIASES = mapOf(
        "name" to "playerName",
        "hp" to "体力",
        "san" to "理智",
        "dex" to "敏捷",
        "str" to "力量",
        "con" to "体质",
        "int" to "智力",
        "pow" to "意志",
        "luck" to "幸运"
    )

    fun render(card: PlayerCardData, format: String): String {
        if (format.isEmpty()) return ""
        return FIELD.replace(format) { match ->
            val key = match.groupValues[1]
            when (key) {
                "name", "playerName" -> card.playerName
                else -> {
                    val property = ALIASES[key] ?: key
                    if (card.hasProperty(property)) card.readProperty(property).toString() else ""
                }
            }
        }.take(MAX_LENGTH)
    }

    private const val MAX_LENGTH = 64
}
