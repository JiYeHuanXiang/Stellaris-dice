package stellaris.dice.diceFunction.responder








interface RollSentences {


    val sentenceRoll: String


    val sentenceRollWithReason: String


    val sentenceIllegal: String


    val sentenceIllegalTooMuch: String







    val sentenceHiddenRollHint: String







    val sentenceHiddenRoll: String








    val sentenceCheck: String


    val sentenceCheckWithReason: String








    val sentenceCheckMultiple: String


    val sentenceCheckMultipleWithReason: String







    val sentenceCardUpdated: String


    val sentenceCardShow: String


    val sentenceCardNoSuchAbility: String


    val sentenceGrowth: String


    val sentenceSanCheck: String









    val sentenceOpposedStart: String


    val sentenceOpposedFirstWin: String


    val sentenceOpposedLastWin: String


    val sentenceOpposedEqual: String


    val sentenceOpposedAllFailure: String






    val sentenceFirstAttacking: String







    val sentenceDraw: String







    val sentenceMadness: String
}











object DefaultSentences : RollSentences {

    override val sentenceRoll: String = "{player}进行的骰点结果:{roll}\n"

    override val sentenceRollWithReason: String = "由于{reason}，{player}进行的骰点结果:{roll}\n"

    override val sentenceIllegal: String = "看不懂的指令呢"

    override val sentenceIllegalTooMuch: String = "呃，太多啦，丢不出来QAQ"

    override val sentenceHiddenRollHint: String = "暗骰请用 rh"

    override val sentenceHiddenRoll: String = "有人进行了一次暗骰，会是啥捏？"

    override val sentenceCheck: String = "{player}进行的{skill}检定结果为:{roll} {success}\n"

    override val sentenceCheckWithReason: String =
        "由于{reason}，{player}进行的{skill}检定结果为:{roll} {success}\n"

    override val sentenceCheckMultiple: String =
        "{player}进行的 {skill} 多重鉴定结果:\n{roll}\n"

    override val sentenceCheckMultipleWithReason: String =
        "由于{reason}，{player}进行的 {skill} 多重鉴定结果:\n{roll}\n"

    override val sentenceCardUpdated: String = "{player}的人物卡属性已更新:{attrs}{rejected}\n"

    override val sentenceCardShow: String = "{player}的人物卡属性:{attrs}\n"

    override val sentenceCardNoSuchAbility: String = "{player}的人物卡上没有「{skill}」这一项\n"

    override val sentenceGrowth: String = "{player}的{skill}成长检定:{roll}\n"

    override val sentenceSanCheck: String = "{player}的理智检定:{roll}\n"

    override val sentenceOpposedStart: String = "先手{competitor_first}发起了{skill}对抗\n"

    override val sentenceOpposedFirstWin: String = "{result}\n先手胜利\n"

    override val sentenceOpposedLastWin: String = "{result}\n后手胜利\n"

    override val sentenceOpposedEqual: String = "{result}\n平局\n"

    override val sentenceOpposedAllFailure: String = "{result}\n全部失败\n"

    override val sentenceFirstAttacking: String = "{riname}的先攻点为: {roll}"

    override val sentenceDraw: String = "{player}的抽牌结果:{draw}\n"

    override val sentenceMadness: String =
        "{player}的{kind}发作（{duration}）:\n{roll} {label}——{summary}\n"
}
