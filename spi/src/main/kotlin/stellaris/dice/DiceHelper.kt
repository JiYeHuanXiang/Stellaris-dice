package stellaris.dice

import stellaris.dice.diceFunction.DiceFunctions
import stellaris.dice.receive.MessageReceived










interface DiceHelper {


    fun readDiceConfig(key: String): String


    fun writeDiceConfig(key: String, value: String)


    fun getDiceFunction(): DiceFunctions


    fun getDiceConfigsIndicator(): String


    fun eventMessageReceive(message: MessageReceived)


    fun eventNewMemberJoin(message: MessageReceived)


    fun eventNudge(target: String, message: MessageReceived)


    fun eventJoinNewGroup(groupId: Long, operator: Long)


    fun eventKick(operator: Long, groupId: Long)


    fun eventMute(operator: Long, groupId: Long)


    fun eventAddFriend(userId: Long, callback: DiceRequestCallback)


    fun eventInviteInGroup(userId: Long, groupId: Long, callback: DiceRequestCallback)
}
