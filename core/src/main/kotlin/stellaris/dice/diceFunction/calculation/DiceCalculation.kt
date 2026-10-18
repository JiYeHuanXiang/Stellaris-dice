package stellaris.dice.diceFunction.calculation

import onedice.Roll
import onedice.data.DiceConfigs
import onedice.data.DiceProcessFormat
import onedice.function.BoundedRandom
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceRandomManager
import stellaris.dice.diceFunction.card.CardAbilities
import stellaris.dice.diceFunction.responder.RollOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern


























object DiceCalculation {








    class FinalResult(val result: Int, val expression: String, inputDetail: String) {







        val detail: String = EquationBuilder().apply {
            inputDetail.replace("\\{(\\d+)\\}\\((\\d+)\\)".toRegex()) { match ->
                val process = match.groups[1]?.value
                val outcome = match.groups[2]?.value
                if (process != null && process == outcome) process else match.groups[0]?.value ?: ""
            }.split('=').forEach { add(it) }
        }.toString()


        val intNumber: Int by lazy { result }


        val numberString: String by lazy {
            val text = result.toString()
            if (text.endsWith(".0")) text.substring(0, text.length - 2) else text
        }

        override fun toString(): String =
            EquationBuilder().add(expression).add(detail).toString() + "=" + numberString
    }








    fun rollCalculation(exp: String, rollOptions: RollOptions = RollOptions()): FinalResult {
        val diceConfigs = DiceConfigs(
            timeout = rollOptions.timeout,
            singleLine = !rollOptions.dx_ww_wrapped_line,
            random = DiceRandomManager.randomInstance
        )

        diceConfigs.setDefaultDiceNumber(1, rollOptions.dxd_defaultNumber)
        diceConfigs.setDefaultFormat(DiceProcessFormat.LEVEL_1, "%s*")

        val res = Roll(exp, diceConfigs).start()
        var number = when (rollOptions.dxd_mode) {
            RollOptions.DxDModes.MAXIMIZE -> res.resIntMax
            RollOptions.DxDModes.MINIMIZE -> res.resIntMin
            RollOptions.DxDModes.RANDOM -> res.resInt
        }
        if (!rollOptions.allowNegativeNumber && number < 0) {
            number = 0
        }
        return if (rollOptions.simple) {
            FinalResult(number, exp, "$exp = ${res.resNumberDetail}")
        } else {
            val detail = if (res.resDetail.length > DiceConstant.DICE_MAX_ROLL_STRING_LENGTH) {
                res.resDetail.substring(0, DiceConstant.DICE_MAX_ROLL_STRING_LENGTH) + "..."
            } else {
                res.resDetail
            }
            FinalResult(number, exp, "$detail = ${res.resNumberDetail}")
        }
    }




    fun notEmptyArray(items: List<String>): Array<String> {
        val kept = ArrayList<String>()
        for (item in items) {
            val trimmed = item.trim()
            if (trimmed.isNotEmpty()) {
                kept.add(trimmed)
            }
        }
        return kept.toTypedArray()
    }








    fun toDBC(input: String): String {
        val chars = input.toCharArray()
        for (i in chars.indices) {
            if (chars[i] == '\u3000') {
                chars[i] = ' '
            } else if (chars[i] in '！'..'～') {
                chars[i] = chars[i] - 65248
            }
        }
        return String(chars).replace("。", ".")
    }


    fun textIsEmpty(text: String?): Boolean = text == null || text.isEmpty()


    fun getRandomInt(min: Int, max: Int): Int =
        BoundedRandom.intInRange(DiceRandomManager.randomInstance, min, max)


    fun isNumber(o: Any?): Boolean =
        o != null && o.toString().isNotEmpty() && Pattern.compile("[0-9]*").matcher(o.toString()).matches()






    fun isStartWithNumber(str: String): Boolean {
        if (str.isEmpty()) {
            return false
        }
        return Pattern.compile("[0-9]*").matcher(str[0].toString()).matches()
    }







    fun stringToInt(num: Any?, def: Int): Int {
        if (num == null || num == "") {
            return def
        }
        if (num is Int) {
            return num
        }
        val digits = StringBuilder()
        if (num is String) {
            for (character in num) {
                when {
                    character == '+' || character == '-' || character.isDigit() -> digits.append(character)
                    character != ' ' && character != '\t' && character != '\n' && character != '\r' -> break
                }
            }
        }
        return digits.toString().toIntOrNull() ?: def
    }


    fun stringToLong(num: Any?): Long {
        if (num == null || num == "") {
            return 0
        }
        if (num is Long) {
            return num
        }
        if (num is Int) {
            return num.toLong()
        }
        return (num as? String)?.toLongOrNull() ?: 0
    }


    fun date(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis()))








    fun abilitiesGetMaxSan(cthulhuMythos: Int): Int {
        val mythos = if (cthulhuMythos > 99) 99 else cthulhuMythos
        return 99 - mythos
    }








    fun abilitiesGetMaxHp(bodyQuality: Int, size: Int): Int = (bodyQuality + size) / 10


    fun abilitiesGetMaxSan(card: CardAbilities): Int =
        abilitiesGetMaxSan(card.readProperty(CardAbilities.CTHULHU_MYTHOS))






    fun abilitiesGetMaxHp(card: CardAbilities): Int {
        if (card.hasProperty(CardAbilities.BODY_QUALITY) && card.hasProperty(CardAbilities.SIZE)) {
            return abilitiesGetMaxHp(
                card.readProperty(CardAbilities.BODY_QUALITY),
                card.readProperty(CardAbilities.SIZE)
            )
        }
        return -1
    }


    fun abilitiesGetDB(card: CardAbilities): String =
        abilitiesGetDB(card.readProperty(CardAbilities.POWER), card.readProperty(CardAbilities.SIZE))
















    fun abilitiesGetDB(power: Int, size: Int): String {
        val mixed = power + size
        return when {
            mixed <= 64 -> "-2"
            mixed <= 84 -> "-1"
            mixed <= 124 -> "0"
            mixed <= 164 -> "1d4"
            else -> "${(mixed - 204) / 80 + 1}d6"
        }
    }
}
