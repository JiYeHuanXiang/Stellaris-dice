package stellaris.dice.diceFunction




























enum class RuleMode(val word: String) {


    NONE("off"),


    COC("coc"),


    COC6("coc6"),


    DND5E("dnd5e"),


    DND3R("dnd3r"),


    DG("dg"),


    FATE("fate"),


    DX("dx"),


    WX("wx"),


    CPR("cpr");

    companion object {


        fun parse(text: String): RuleMode? =
            entries.firstOrNull { it.word.equals(text.trim(), ignoreCase = true) }


        val DEFAULT = NONE
    }
}
