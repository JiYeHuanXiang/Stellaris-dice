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


    /** `.sc` 的外层模板；为空时回落到 [sentenceSanCheck]。可用 `{result}`。 */
    val sentenceScPre: String

    val sentenceScSuccess: String

    val sentenceScFailure: String

    val sentenceScBigSuccess: String

    val sentenceScBigFailure: String

    val sentenceScInnocent: String

    val sentenceScTemporary: String

    val sentenceScRandom: String

    val sentenceScPermanent: String


    /** 六档成功级别的可配置写法。 */
    val sentenceBigSuccess: String
    val sentenceVeryHardSuccess: String
    val sentenceHardSuccess: String
    val sentenceSuccess: String
    val sentenceFailure: String
    val sentenceBigFailure: String









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

    override val sentenceScPre: String = ""

    override val sentenceScSuccess: String = "成功"

    override val sentenceScFailure: String = "失败"

    override val sentenceScBigSuccess: String = "大成功"

    override val sentenceScBigFailure: String = "大失败"

    override val sentenceScInnocent: String = "（sc）掉san超过5，灵感失败：傻人有傻福"

    override val sentenceScTemporary: String = "（sc）掉san超过5，临时疯狂"

    override val sentenceScRandom: String = "（sc）掉san达到1/5，不定时疯狂"

    override val sentenceScPermanent: String = "（sc）掉san为0，永久疯狂"

    override val sentenceBigSuccess: String = "大成功"

    override val sentenceVeryHardSuccess: String = "极难成功"

    override val sentenceHardSuccess: String = "困难成功"

    override val sentenceSuccess: String = "成功"

    override val sentenceFailure: String = "失败"

    override val sentenceBigFailure: String = "大失败"

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
