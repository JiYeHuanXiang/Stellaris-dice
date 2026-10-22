package stellaris.dice

import stellaris.dice.receive.MessageReceived




















@Keep
interface DiceIntentEvent {








    fun sendMessage(sendContent: String, context: MessageReceived, target: DiceMessageSource) {}












    fun sendGroupFile(filePath: String, fileName: String, groupId: String): Boolean {
        return false
    }









    fun setGroupNumberName(s: String, id: String, groupId: String): Boolean {
        return false
    }







    fun setDismiss(groupId: String, callback: ((result: Boolean) -> Unit)? = null) {}


    fun isSelfGroupOperator(groupId: String): Boolean {
        return false
    }


    fun log(str: String) {}


    fun botFrame(): String {
        return "UNKNOWN"
    }


    fun getGroupInfo(): ArrayList<GroupInfo>? {
        return null
    }


    fun getFriendsInfo(): ArrayList<FriendsInfo>? {
        return null
    }


    fun hasInitialed(): Boolean {
        return false
    }











    fun setGroupTitle(s: String, id: String, groupId: String): Boolean {
        return false
    }










    fun setGroupWholeBan(ban: Boolean, groupId: String): Boolean {
        return false
    }

    /**
     * 宿主可选的“重启骰核”能力：`.system reload` 会调用它。
     * 默认不支持；支持时返回 true（真正的重启动作由宿主完成）。
     */
    fun requestRestart(): Boolean {
        return false
    }

    class GroupInfo(var id: Long = 0, var groupName: String, var selfLastTalkTime: Int = 0)




    class FriendsInfo @JvmOverloads constructor(
        var id: String,
        var nick: String,
        var remark: String = ""
    )











































































}
