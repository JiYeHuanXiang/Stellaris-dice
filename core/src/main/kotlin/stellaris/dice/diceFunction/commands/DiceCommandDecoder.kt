package stellaris.dice.diceFunction.commands

import onedice.defines.DiceException
import stellaris.dice.diceFunction.DiceLogger
import java.util.Collections




























object DiceCommandDecoder {

    private val registerCommands = ArrayList<String>()


    private val protectedCommands = ArrayList(listOf("dismiss"))


    private fun register(vararg commands: String) {
        next@ for (command in commands) {
            for (registered in registerCommands) {
                if (registered.equals(command, ignoreCase = true)) {
                    continue@next
                }
            }
            registerCommands.add(command)
        }
    }

    init {
        register(
            "recovery", "bot", "robot",
            "reply", "guard", "black", "system",
            "backup", "group",
            "admin", "draw", "deck", "pc",
            "sc", "who", "send",
            "helpdoc", "help",
            "welcome", "runtime", "master",
            "about", "setcoc",
            "dismiss", "init", "name", "jrrp",
            "log", "set", "coc", "dnd", "coc6",
            "dg", "setdg", "fate", "setfate", "wx", "setwx", "cpr", "setcpr", "setdx", "setdnd",
            "rb", "rp", "rd", "blackcloud",
            "find", "查询", "查找",
            "sn", "setasn",
            "nn", "en", "st", "ob",
            "li", "ti", "hiy", "rules", "rule", "psy", "nolawnosky",

            "debug",

            "r", "rah", "rch", "rab", "rap", "rcb", "rcp", "ra", "rc", "ri", "rav", "rcv",
            "ww", "www", "w", "dxx", "dx", "rh", "hl", "h",
            "range",

            "rx", "rhx", "wwx",
            "rax", "rcx", "rabx", "rapx", "rcbx", "rcpx", "rahx", "rchx",
            "scx", "enx",

            "hp", "san", "mp", "sp",

            "del",

            "gas",

            "stc"
        )












    }


    fun registeredCommands(): List<String> = Collections.unmodifiableList(registerCommands)


    fun isProtected(command: String): Boolean = protectedCommands.contains(command)


    fun invoke(diceCommandCallback: DiceCommandCallback, input: String): Boolean =
        invoke(diceCommandCallback, input, 0)









    fun invoke(diceCommandCallback: DiceCommandCallback, input: String, deep: Int = 0): Boolean {
        if (deep >= 5) {
            throw IllegalStateException("指令改写递归超过 5 层，改写规则可能互相打架：$input")
        }

        val commands = ArrayList<String>().apply {
            addAll(registerCommands)
            addAll(diceCommandCallback.extraCommands())
        }
        commands.sortWith(SortByLengthComparator())

        for (command in commands) {
            if (!input.startsWith(command, ignoreCase = true)) {
                continue
            }

            val rewritten = DiceCommandLegacy.EFFECT_COMMANDS[command]?.onFix(input)
            if (rewritten == null || input.equals(rewritten, ignoreCase = true)) {
                try {
                    return diceCommandCallback.onCommand(
                        command,
                        input.substring(command.length).trim(),
                        protectedCommands.contains(command)
                    )
                } catch (e: DiceException) {

                    diceCommandCallback.onException(e)
                } catch (e: Throwable) {

                    DiceLogger.error("指令 $command 处理失败：$e", LOG_TAG)
                }
            } else {
                DiceLogger.log("指令改写：$input -> $rewritten", LOG_TAG)
                invoke(diceCommandCallback, rewritten, deep + 1)
            }

            return false
        }
        return false
    }

    private const val LOG_TAG = "DiceCommand"
}
