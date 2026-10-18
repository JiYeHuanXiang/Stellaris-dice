package stellaris.dice.receive

import stellaris.dice.DiceMessageSource
import stellaris.dice.diceFunction.DiceFunctions

































class MessageReceived(
    val diceFunctions: DiceFunctions,
    val contentReal: String = "",
    val group: String = "",
    val triggerId: String = "",
    var nick: String = "",
    var groupNick: String = "",
    val isGroupAdmin: Boolean = false,
    val isGroupOwner: Boolean = false,
    val isGroupMsg: Boolean = false,
    val isAtMe: Boolean = false,
    val isPureMode: Boolean = false,
    val time: Int = 0,
    val context: Any? = null,
    val atTargets: List<String> = emptyList()
) {


    val isMaster: Boolean
        get() = diceFunctions.diceConfig.isMaster(triggerId)


    val rootPath: String
        get() = diceFunctions.root()


    val identity: DiceMessageSource
        get() {
            if (isGroupMsg) {
                return DiceMessageSource(DiceMessageSource.Type.GROUP, group)
            }
            if (triggerId.isNotEmpty()) {
                return DiceMessageSource(DiceMessageSource.Type.PERSON, triggerId)
            }
            return DiceMessageSource(DiceMessageSource.Type.UNKNOWN, "UNKNOWN")
        }

    companion object {









        fun buildMessageEvent(
            instance: DiceFunctions,
            content: String? = "",
            group: String? = null,
            triggerId: String? = null,
            nick: String? = null,
            groupNick: String? = null,
            time: Int = 0,
            isGroupAdmin: Boolean = false,
            isGroupOwner: Boolean = false,
            isGroupMsg: Boolean = false,
            isAtMe: Boolean = false,
            isPureMode: Boolean = false,
            context: Any? = null,

            atTargets: List<String> = emptyList()
        ): MessageReceived {
            return MessageReceived(
                diceFunctions = instance,
                contentReal = content ?: "",
                group = group ?: "",
                triggerId = triggerId ?: "0",
                nick = nick ?: "undefined",
                groupNick = groupNick ?: "undefined",
                isGroupAdmin = isGroupAdmin,
                isGroupOwner = isGroupOwner,
                isGroupMsg = isGroupMsg,
                isAtMe = isAtMe,
                isPureMode = isPureMode,
                time = time,
                context = context,
                atTargets = atTargets
            )
        }
    }
}
