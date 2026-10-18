package stellaris.dice.diceFunction.template

import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.calculation.DiceCalculation
import java.util.regex.Pattern











object MessageCodec {


    private val ESCAPED_CHARS = charArrayOf(
        '<', '>', '"', '\'', '&', '[', ']', ':', ',', '\\', '\n', '#', '*'
    )















    fun htmlEscape(text: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < text.length) {
            val placeholder = when {
                text.startsWith(DiceConstant.SYSTEM_SPLIT_STRING, index) -> DiceConstant.SYSTEM_SPLIT_STRING
                text.startsWith(DiceConstant.USER_SPLIT_STRING, index) -> DiceConstant.USER_SPLIT_STRING
                else -> null
            }
            if (placeholder != null) {
                builder.append("&#10;")
                index += placeholder.length
                continue
            }
            val char = text[index]
            if (char in ESCAPED_CHARS) {
                builder.append("&#").append(char.code).append(';')
            } else {
                builder.append(char)
            }
            index++
        }
        return builder.toString()
    }








    fun decodeToContent(text: String): String {
        if (text.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        var offset = 0
        for (i in text.indices) {
            val index = i + offset
            if (index >= text.length) {
                break
            }
            val char = text[index]
            if (char != '\\') {
                builder.append(char)
                continue
            }
            if (index + 1 >= text.length) {

                break
            }
            when (val escaped = text[index + 1]) {
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                else -> builder.append(escaped)
            }
            offset++
        }
        return builder.toString()
    }
}


fun String.decodeToContent(): String = MessageCodec.decodeToContent(this)


private val INLINE_DICE_PATTERN = Pattern.compile("\\[(.*?)[]]")


private val INLINE_DICE_EXPRESSION_PATTERN =
    Pattern.compile("^([${DiceConstant.EXPRESSION_CHARS}]+)$", Pattern.CASE_INSENSITIVE)













fun String.decodeXdX(): String {
    var outText = this
    val matcher = INLINE_DICE_PATTERN.matcher(this)
    while (matcher.find()) {
        val whole = matcher.group(0)
        val expression = matcher.group(1) ?: continue
        if (!INLINE_DICE_EXPRESSION_PATTERN.matcher(expression).find()) {
            continue
        }
        val number = try {
            DiceCalculation.rollCalculation(expression).numberString
        } catch (ignored: Throwable) {
            continue
        }
        val index = outText.indexOf(whole)
        if (index >= 0) {
            outText = outText.substring(0, index) + number + outText.substring(index + whole.length)
        }
    }
    return outText
}
