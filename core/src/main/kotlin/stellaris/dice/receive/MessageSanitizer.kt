package stellaris.dice.receive

import stellaris.dice.diceFunction.template.DiceMessageTemplate
import stellaris.dice.diceFunction.template.DiceMessageTemplateInvoke































object MessageSanitizer {


    const val CODE_PLACEHOLDER = "*CODE*"






    fun sanitize(text: String): String {
        if (text.isEmpty()) {
            return text
        }
        val sanitizer = DiceMessageTemplate().apply {
            diceMessageTemplateInvoke = object : DiceMessageTemplateInvoke {
                override fun invoke(template: DiceMessageTemplate, command: String, param: String): String =
                    CODE_PLACEHOLDER
            }
        }
        return sanitizer.buildAll(text)
    }
}
