package stellaris.dice.diceFunction.card

import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger


















class PlayerCardData {


    var owner: String = ""


    var playerName: String = DiceConstant.ENV_DEFAULT_PLAYER_NAME


    var rule: RuleType = RuleType.COC7


    var brief: String = ""

    /** `.sn` 插件使用的自动名片格式与开关。 */
    var autoNameFormat: String = ""
    var autoNameEnabled: Boolean = false

    /**
     * 开启自动名片的群。`.sn off` 只把当前群除名，不影响其他群。
     */
    val autoNameGroups: MutableSet<String> = LinkedHashSet()





    var aliases: Map<String, String> = emptyMap()





    var defaultSkillValues: Map<String, Int> = emptyMap()





    private val propertyValues = LinkedHashMap<String, Int>()


    val propertyNames: List<String> get() = propertyValues.keys.toList()


    val propertyCount: Int get() = propertyValues.size




    fun hasProperty(name: String): Boolean = propertyValues.containsKey(name)






    fun setProperty(name: String, value: Int): Boolean {
        if (name.length > DiceConstant.MAX_SKILL_LENGTH) {
            DiceLogger.error(
                "技能名过长，未记录：" +
                    "【${name.take(DiceConstant.MAX_SKILL_LENGTH)}…】" +
                    "（上限 ${DiceConstant.MAX_SKILL_LENGTH} 字）",
                LOG_TAG
            )
            return false
        }
        propertyValues[name] = value
        return true
    }


    fun readProperty(name: String?): Int = readProperty(name, defaultValue(name))


    fun readProperty(name: String?, default: Int): Int {
        if (name == null) {
            return default
        }
        return propertyValues[name] ?: default
    }


    fun defaultValue(name: String?): Int {
        if (name == null) {
            return DEFAULT_PROPERTY_VALUE
        }
        return defaultSkillValues[name] ?: DEFAULT_PROPERTY_VALUE
    }


    fun canonicalName(name: String): String = aliases[name.lowercase()] ?: name


    fun removeProperty(name: String) {
        propertyValues.remove(name)
    }


    fun clearProperties() {
        propertyValues.clear()
    }


    fun merge(other: PlayerCardData) {
        other.propertyValues.forEach { (name, value) -> setProperty(name, value) }
    }






    fun readFriendlyName(defaultName: String = DiceConstant.ENV_DEFAULT_SHOW_PLAYER_NAME): String {
        if (playerName == DiceConstant.ENV_DEFAULT_PLAYER_NAME) {
            return defaultName
        }
        return playerName
    }

    override fun toString(): String = readFriendlyName()








    enum class RuleType(val word: String) {
        COC6("coc6"),
        COC7("coc7"),
        DND3R("dnd3r"),
        DND5E("dnd5e"),
        DX("dx"),
        FATE("fate"),
        DG("dg"),
        WX("wx"),
        CPR("cpr");

        companion object {


            fun parse(text: String): RuleType? =
                entries.firstOrNull { it.word.equals(text.trim(), ignoreCase = true) }
        }
    }


    fun asAbilities(): CardAbilities = object : CardAbilities {
        override fun readProperty(name: String): Int = this@PlayerCardData.readProperty(name)

        override fun hasProperty(name: String): Boolean = this@PlayerCardData.hasProperty(name)
    }

    companion object {


        const val DEFAULT_PROPERTY_VALUE = 1

        private const val LOG_TAG = "PlayerCard"


        fun create(owner: String, playerName: String = DiceConstant.ENV_DEFAULT_PLAYER_NAME): PlayerCardData =
            PlayerCardData().apply {
                this.owner = owner
                this.playerName = playerName
            }
    }
}
