package stellaris.dice.diceFunction.draw

/**
 * 牌堆抽取进度的作用域：群聊按群、私聊按人。
 *
 * 此前直接用群号做键，私聊时群号为空串，导致所有私聊用户共享同一份进度。
 */
object DeckIdentity {

    fun key(isGroup: Boolean, group: String, person: String): String = when {
        isGroup && group.isNotEmpty() -> "GROUP:$group"
        person.isNotEmpty() -> "PERSON:$person"
        group.isNotEmpty() -> "GROUP:$group"
        else -> "UNKNOWN"
    }
}
