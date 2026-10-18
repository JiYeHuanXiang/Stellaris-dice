package stellaris.dice.diceFunction.template








interface DiceMessageTemplateInvoke {







    fun invoke(template: DiceMessageTemplate, command: String, param: String): String?
}
