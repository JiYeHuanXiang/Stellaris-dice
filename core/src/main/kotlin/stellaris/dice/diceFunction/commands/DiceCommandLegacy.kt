package stellaris.dice.diceFunction.commands

import onedice.function.DiceCore
import java.util.regex.Pattern






















object DiceCommandLegacy {







    fun trimCommand(s: String): String = s.replace("\\s".toRegex(), "").lowercase()




    private val FIXER_HELP = object : DiceCommandFixer {
        private val helpDraw = Pattern.compile("^helpdraw(.*)?$")
        override fun onFix(s: String): String? {
            val mh = helpDraw.matcher(trimCommand(s))
            if (mh.find()) {
                return "draw help ${mh.group(1)}"
            }
            return null
        }
    }


    private val FIXER_HL = object : DiceCommandFixer {
        private val hlNumber = Pattern.compile("^hl(\\d+)$")
        override fun onFix(s: String): String? {
            val mh = hlNumber.matcher(trimCommand(s))
            if (mh.find()) {
                return "rah ${mh.group(1)}"
            }
            return null
        }
    }







    private val FIXER_R = object : DiceCommandFixer {


        private val diceTypeBeforeMark = Pattern.compile("^r([bp])([0-9]+)#([0-9]+)?(.*)?$")


        private val countAfterDiceType = Pattern.compile("^r[a]?([bp])(\\d+)(.*)$")


        private val diceTypeBeforeSkill = Pattern.compile("^r([bp])(.*)$")









        private val skillAfterDiceType = Pattern.compile("^(r[ac])(\\d+)([^\\d]+)$")


        private val skillWithNumber = Pattern.compile("[0-9]?[^0-9+\\-*/]+[0-9]+|[0-9]?[^0-9+\\-*/]+")

        override fun onFix(s: String): String? {
            val normalized = trimCommand(s)

            diceTypeBeforeMark.matcher(normalized).let { mh ->
                if (mh.find()) {
                    val diceType = mh.group(1)
                    val count = mh.group(2)
                    val mark = mh.group(3) ?: ""
                    val rest = mh.group(4) ?: ""
                    return "ra$diceType$count#$mark$rest"
                }
            }

            countAfterDiceType.matcher(normalized).let { mh ->
                if (mh.find()) {
                    val diceType = mh.group(1)
                    val count = mh.group(2)
                    val rest = mh.group(3)
                    val number = toIntOr(count, 1)

                    if (number > DiceCore.MAX_PUNISH_BONUS_NUMBER && !Pattern.matches(".*\\d+$", rest)) {
                        return "ra$diceType$rest$count"
                    }
                }
            }

            diceTypeBeforeSkill.matcher(normalized).let { mh ->
                if (mh.find()) {
                    val diceType = mh.group(1)
                    val rest = mh.group(2)
                    if (skillWithNumber.matcher(rest).find()) {
                        return "ra$diceType$rest"
                    }
                }
            }

            skillAfterDiceType.matcher(normalized).let { mh ->
                if (mh.find()) {
                    val command = mh.group(1)
                    val skill = mh.group(3)

                    if (skill != null && !skill.startsWith("#")) {
                        return "$command ${skill}${mh.group(2)}"
                    }
                }
            }

            return null
        }
    }


    private val FIXER_NN = object : DiceCommandFixer {
        private val nnShow = Pattern.compile("^nnshow(.*)?$")
        override fun onFix(s: String): String? {
            val mh = nnShow.matcher(trimCommand(s))
            if (mh.find()) {
                return "pc list ${mh.group(1)}"
            }
            return null
        }
    }


    private val FIXER_ST = object : DiceCommandFixer {
        private val stList = Pattern.compile("^stlist(.*)?$")
        private val stRename = Pattern.compile("^strename(.*)?$")
        private val stRm = Pattern.compile("^strm(.*)?$")
        override fun onFix(s: String): String? {
            val normalized = trimCommand(s)
            stList.matcher(normalized).let { if (it.find()) return "pc list ${it.group(1)}" }
            stRename.matcher(normalized).let { if (it.find()) return "nn ${it.group(1)}" }
            stRm.matcher(normalized).let { if (it.find()) return "pc del ${it.group(1)}" }
            return null
        }
    }


    private val FIXER_DEL = object : DiceCommandFixer {
        private val del = Pattern.compile("^del(.*)?$")
        override fun onFix(s: String): String? {
            val mh = del.matcher(trimCommand(s))
            if (mh.find()) {
                return "pc del ${mh.group(1)}"
            }
            return null
        }
    }


    private val FIXER_DRAW = object : DiceCommandFixer {
        private val drawReload = Pattern.compile("^drawreload$")
        override fun onFix(s: String): String? {
            val mh = drawReload.matcher(trimCommand(s))
            if (mh.find()) {
                return "system load"
            }
            return null
        }
    }


    private val FIXER_GAS = object : DiceCommandFixer {
        private val gas = Pattern.compile("^gas$")
        override fun onFix(s: String): String? {
            val mh = gas.matcher(trimCommand(s))
            if (mh.find()) {
                return "draw 煤气灯"
            }
            return null
        }
    }


    private val FIXER_SYSTEM = object : DiceCommandFixer {
        private val systemState = Pattern.compile("^systemstate$")
        override fun onFix(s: String): String? {
            val mh = systemState.matcher(trimCommand(s))
            if (mh.find()) {
                return "runtime"
            }
            return null
        }
    }







    private val FIXER_GROUP = object : DiceCommandFixer {
        private val group = Pattern.compile("^group([+\\-])(停用指令|禁用回复|禁用ob)$")
        override fun onFix(s: String): String? {
            val mh = group.matcher(trimCommand(s))
            if (mh.find()) {
                val state = if (mh.group(1) == "+") "off" else "on"
                return when (mh.group(2)) {
                    "停用指令" -> "bot $state"
                    "禁用回复" -> "reply $state this"
                    "禁用ob" -> "ob $state"
                    else -> null
                }
            }
            return null
        }
    }








    private val FIXER_HP_SAN_MP = object : DiceCommandFixer {
        private val hpSanMp = Pattern.compile("^(hp|san|mp|sp)(.*)?$")
        override fun onFix(s: String): String? {
            val mh = hpSanMp.matcher(trimCommand(s))
            if (mh.find()) {
                val field = mh.group(1)
                val rest = mh.group(2)
                return if (rest.isNullOrEmpty()) "st show $field" else "st $field$rest"
            }
            return null
        }
    }






    val EFFECT_COMMANDS = HashMap<String, DiceCommandFixer>()

    init {
        EFFECT_COMMANDS["help"] = FIXER_HELP
        EFFECT_COMMANDS["hl"] = FIXER_HL
        EFFECT_COMMANDS["r"] = FIXER_R
        EFFECT_COMMANDS["nn"] = FIXER_NN
        EFFECT_COMMANDS["st"] = FIXER_ST
        EFFECT_COMMANDS["del"] = FIXER_DEL
        EFFECT_COMMANDS["draw"] = FIXER_DRAW
        EFFECT_COMMANDS["system"] = FIXER_SYSTEM
        EFFECT_COMMANDS["group"] = FIXER_GROUP

        EFFECT_COMMANDS["san"] = FIXER_HP_SAN_MP
        EFFECT_COMMANDS["mp"] = FIXER_HP_SAN_MP
        EFFECT_COMMANDS["sp"] = FIXER_HP_SAN_MP
        EFFECT_COMMANDS["gas"] = FIXER_GAS
    }








    private fun toIntOr(value: Any?, fallback: Int): Int {
        if (value == null || value == "") {
            return fallback
        }
        if (value is Int) {
            return value
        }
        val digits = StringBuilder()
        if (value is String) {
            for (character in value) {
                when {
                    character == '+' || character == '-' || character.isDigit() -> digits.append(character)
                    character != ' ' && character != '\t' && character != '\n' && character != '\r' -> break
                }
            }
        }
        return digits.toString().toIntOrNull() ?: fallback
    }
}
