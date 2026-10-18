package stellaris.dice.diceFunction

import stellaris.dice.diceFunction.database.entity.data.KeywordsReplyData






interface DiceKeywordsReplyManager {


    fun list(): ArrayList<KeywordsReplyData>


    fun find(mode: KeywordsReplyData.ReplyType, keyword: String): KeywordsReplyData?


    fun find(uuid: Long): KeywordsReplyData?


    fun update(obj: KeywordsReplyData): Boolean






    fun add(
        mode: KeywordsReplyData.ReplyType = KeywordsReplyData.ReplyType.MATCH_FULL,
        weight: Int = 0,
        keyword: String = "",
        content: String = "",
        legacy: Boolean = false
    ): KeywordsReplyData?


    fun remove(uuid: Long): Boolean
}
