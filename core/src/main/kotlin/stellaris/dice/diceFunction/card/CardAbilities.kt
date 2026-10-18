package stellaris.dice.diceFunction.card
















interface CardAbilities {


    fun readProperty(name: String): Int


    fun hasProperty(name: String): Boolean

    companion object {









        val EMPTY: CardAbilities = object : CardAbilities {
            override fun readProperty(name: String): Int = 0

            override fun hasProperty(name: String): Boolean = false
        }





        const val POWER = "力量"


        const val SIZE = "体型"


        const val BODY_QUALITY = "体质"


        const val CTHULHU_MYTHOS = "克苏鲁神话"
    }
}
