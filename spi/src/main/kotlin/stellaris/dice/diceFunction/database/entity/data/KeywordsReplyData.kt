package stellaris.dice.diceFunction.database.entity.data











interface KeywordsReplyData {


    var uuid: Long


    var mode: ReplyType


    var weight: Int


    var keyword: String


    var content: String


    var legacy: Boolean


    var modifyTime: Long

    enum class ReplyType {
        MATCH_FULL,
        MATCH_BLUR,
        MATCH_REGEX,


        MATCH_PREFIX,


        MATCH_SUFFIX
    }
}
