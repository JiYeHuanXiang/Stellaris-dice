package stellaris.dice.diceFunction.responder

import stellaris.dice.diceFunction.card.CardAbilities
import stellaris.dice.diceFunction.card.PlayerCardData






























class CheckArguments(

    val skillName: String,

    val explicitTarget: Int?,

    val targetDelta: Int?,

    val difficulty: Difficulty,

    val cardName: String,

    val reason: String,

    val bonusPenaltyCount: Int,

    val repeatTimes: Int
) {


    enum class Difficulty(val label: String) {
        NORMAL(""),
        HARD("困难"),
        EXTREME("极难");


        fun applyTo(target: Int): Int = when (this) {
            NORMAL -> target
            HARD -> target / 2
            EXTREME -> target / 5
        }
    }






    fun displayName(defaultValueLabel: String): String {
        val base = if (skillName.isEmpty()) defaultValueLabel else "${difficulty.label}$skillName"
        val delta = targetDelta ?: return base
        return "$base(${if (delta >= 0) "+$delta" else delta.toString()})"
    }

    companion object {


        /**
         * b/p 个数的两种写法：
         * - `2 侦查`：单个 1~9 数字 + 空格 + 目标/技能；
         * - `12斗殴`：数字直接粘着技能名，允许两位以上，交给引擎判 1~9 上限。
         */
        private val LEADING_COUNT_SPACED = Regex("^([1-9])(?=\\s+\\S)")
        private val LEADING_COUNT_CONCAT = Regex("^([1-9]\\d*)(?=[^\\s\\d])")


        private val REPEAT_PREFIX = Regex("^#\\d+")


        private val REPEAT_SUFFIX = Regex("^(.+)#(\\d+)$")


        private val PURE_NUMBER = Regex("^\\d+$")


        private val SIGNED_NUMBER = Regex("^[+-]\\d+$")


        private val NAME_THEN_SIGNED = Regex("^(.*?)([+-]\\d+)$")


        private val NAME_THEN_NUMBER = Regex("^(.*?)(\\d+)$")


        private val NUMBER_THEN_NAME = Regex("^(\\d+)(.*)$")







        fun parse(param: String, rule: RollRules): CheckArguments {
            var rest = param.trim()


            var repeatTimes = 1
            val boundary = rest.indexOfFirst { it.isWhitespace() }
            val firstToken = if (boundary < 0) rest else rest.substring(0, boundary)
            val tail = if (boundary < 0) "" else rest.substring(boundary)
            REPEAT_SUFFIX.matchEntire(firstToken)?.let { match ->
                repeatTimes = (match.groupValues[2].toIntOrNull() ?: 1).coerceAtLeast(1)
                rest = (match.groupValues[1] + tail).trim()
            }


            var count = 1
            if (rule.isBonusDie || rule.isPenaltyDie) {
                val match = LEADING_COUNT_SPACED.find(rest) ?: LEADING_COUNT_CONCAT.find(rest)
                if (match != null) {
                    val afterCount = rest.substring(match.value.length).trimStart()
                    if (afterCount.isNotEmpty()) {
                        count = match.value.toInt()
                        rest = afterCount
                    }
                }
            }

            rest = REPEAT_PREFIX.replaceFirst(rest, "")


            var cardName = ""
            val colon = rest.indexOfFirst { it == ':' || it == '：' }
            if (colon >= 0) {
                cardName = rest.substring(0, colon).trim()
                rest = rest.substring(colon + 1).trim()
            }


            val space = rest.indexOf(' ')
            var head = if (space < 0) rest else rest.substring(0, space)
            val reason = if (space < 0) "" else rest.substring(space + 1).trim()


            var difficulty = Difficulty.NORMAL
            when {
                head.startsWith(Difficulty.EXTREME.label) -> {
                    difficulty = Difficulty.EXTREME
                    head = head.removePrefix(Difficulty.EXTREME.label)
                }

                head.startsWith(Difficulty.HARD.label) -> {
                    difficulty = Difficulty.HARD
                    head = head.removePrefix(Difficulty.HARD.label)
                }
            }

            var skillName = ""
            var explicitTarget: Int? = null
            var targetDelta: Int? = null
            when {
                head.isEmpty() -> Unit
                PURE_NUMBER.matches(head) -> explicitTarget = head.toIntOrNull()
                SIGNED_NUMBER.matches(head) -> targetDelta = head.toIntOrNull()
                else -> {
                    val signed = NAME_THEN_SIGNED.matchEntire(head)
                    val nameFirst = NAME_THEN_NUMBER.matchEntire(head)
                    val numberFirst = NUMBER_THEN_NAME.matchEntire(head)
                    when {
                        signed != null && signed.groupValues[1].isNotEmpty() -> {
                            skillName = signed.groupValues[1]
                            targetDelta = signed.groupValues[2].toIntOrNull()
                        }

                        nameFirst != null && nameFirst.groupValues[1].isNotEmpty() -> {
                            skillName = nameFirst.groupValues[1]
                            explicitTarget = nameFirst.groupValues[2].toIntOrNull()
                        }

                        numberFirst != null && numberFirst.groupValues[2].isNotEmpty() -> {
                            explicitTarget = numberFirst.groupValues[1].toIntOrNull()
                            skillName = numberFirst.groupValues[2]
                        }

                        else -> skillName = head
                    }
                }
            }
            return CheckArguments(
                skillName = skillName,
                explicitTarget = explicitTarget,
                targetDelta = targetDelta,
                difficulty = difficulty,
                cardName = cardName,
                reason = reason,
                bonusPenaltyCount = count,
                repeatTimes = repeatTimes
            )
        }











        fun resolveTarget(args: CheckArguments, card: CardAbilities): Int? {
            val base = args.explicitTarget ?: run {
                if (args.skillName.isEmpty()) {
                    return null
                }
                if (card.hasProperty(args.skillName)) {
                    card.readProperty(args.skillName)
                } else {
                    PlayerCardData.DEFAULT_PROPERTY_VALUE
                }
            }
            val adjusted = base + (args.targetDelta ?: 0)

            return args.difficulty.applyTo(adjusted).coerceAtLeast(0)
        }
    }
}
