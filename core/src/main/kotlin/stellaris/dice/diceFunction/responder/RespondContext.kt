package stellaris.dice.diceFunction.responder

import stellaris.dice.diceFunction.card.CardAbilities
import stellaris.dice.diceFunction.template.DiceMessageTemplate




















class RespondContext(
    val param: String,
    val isGroupMsg: Boolean,
    val groupId: String,
    val senderId: String,
    val senderNick: String,
    val template: DiceMessageTemplate,
    val sentences: RollSentences,
    val defaultDiceNumber: Int = DEFAULT_DICE_NUMBER,
    val simplify: Boolean = false,








    val damageBonus: String = DEFAULT_DAMAGE_BONUS,







    val card: CardAbilities = CardAbilities.EMPTY,







    val cardProvider: (String) -> CardAbilities = { CardAbilities.EMPTY },






    val rollRecorder: ((Int) -> Unit)? = null,






    val houseRule: CocHouseRule = CocHouseRule.DEFAULT,

    /** 当前房规（内置或自定义）；为空时按 [houseRule] 走内置档位。 */
    val judgeRule: stellaris.dice.diceFunction.judge.JudgeRule? = null,

    val ruleMode: stellaris.dice.diceFunction.RuleMode = stellaris.dice.diceFunction.RuleMode.NONE
) {


    /** 这条检定该用的判档规则：不跟随房规的指令恒用规则书。 */
    fun judgeFor(rule: RollRules): stellaris.dice.diceFunction.judge.JudgeRule =
        if (!rule.followsHouseRule) {
            stellaris.dice.diceFunction.judge.BuiltinJudgeRule(CocHouseRule.RULEBOOK)
        } else {
            judgeForCheck()
        }


    /** 不区分指令的判档规则（`.sc` 等固定走房规的路径）。 */
    fun judgeForCheck(): stellaris.dice.diceFunction.judge.JudgeRule =
        judgeRule ?: stellaris.dice.diceFunction.judge.BuiltinJudgeRule(houseRule)








    fun withParam(newParam: String): RespondContext = RespondContext(
        param = newParam,
        isGroupMsg = isGroupMsg,
        groupId = groupId,
        senderId = senderId,
        senderNick = senderNick,
        template = template,
        sentences = sentences,
        defaultDiceNumber = defaultDiceNumber,
        simplify = simplify,
        damageBonus = damageBonus,
        card = card,
        cardProvider = cardProvider,
        rollRecorder = rollRecorder,
        houseRule = houseRule,
        judgeRule = judgeRule,
        ruleMode = ruleMode
    )







    fun withSimplify(): RespondContext = if (simplify) {
        this
    } else {
        RespondContext(
            param = param,
            isGroupMsg = isGroupMsg,
            groupId = groupId,
            senderId = senderId,
            senderNick = senderNick,
            template = template,
            sentences = sentences,
            defaultDiceNumber = defaultDiceNumber,
            simplify = true,
            damageBonus = damageBonus,
            card = card,
            cardProvider = cardProvider,
            rollRecorder = rollRecorder,
            houseRule = houseRule,
        judgeRule = judgeRule,
            ruleMode = ruleMode
        )
    }


    fun withRollRecorder(recorder: (Int) -> Unit): RespondContext = RespondContext(
        param = param,
        isGroupMsg = isGroupMsg,
        groupId = groupId,
        senderId = senderId,
        senderNick = senderNick,
        template = template,
        sentences = sentences,
        defaultDiceNumber = defaultDiceNumber,
        simplify = simplify,
        damageBonus = damageBonus,
        card = card,
        cardProvider = cardProvider,
        rollRecorder = recorder,
        houseRule = houseRule,
        judgeRule = judgeRule,
        ruleMode = ruleMode
    )


    fun cardNamed(name: String): CardAbilities = if (name.isEmpty()) card else cardProvider(name)

    companion object {


        const val DEFAULT_DICE_NUMBER = 100


        const val DEFAULT_DAMAGE_BONUS = "0"








        fun seedTemplate(
            template: DiceMessageTemplate,
            senderNick: String,
            senderId: String,
            groupId: String,
            botName: String = ""
        ) {
            template.add("player", senderNick)
            template.add("user", senderNick)
            template.add("id", senderId)
            template.add("group", groupId)
            template.add("nick", botName)

            template.add("FormFeed", stellaris.dice.diceFunction.DiceConstant.USER_SPLIT_STRING)
        }
    }
}
