package stellaris.dice.diceFunction.responder

import stellaris.core.defines.DiceException
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.RuleMode
import stellaris.dice.diceFunction.calculation.DiceCalculation
import stellaris.dice.diceFunction.card.PlayerCardData



































object DiceResponder {


    private const val MAX_REPEAT_TIMES = 10


    private const val MULTI_ROLL_DETAIL_THRESHOLD = 50








    fun r(context: RespondContext, rule: RollRules = RollRules.R): String {
        val repeat = RollArguments.repeatTimes(context.param)
        if (repeat.times > MAX_REPEAT_TIMES) {
            return context.sentences.sentenceIllegalTooMuch
        }
        if (!rule.contains(SUPPORTED_RULES)) {


            return context.sentences.sentenceIllegal
        }

        val arguments = RollArguments.parse(repeat.remain)
        var action = arguments.reason.trim()
        var expression = arguments.expression.trim()
        expression = trimDanglingOperator(expression)

        expression.split(' ', limit = 2).let { parts ->
            if (parts.size > 1) {
                action = parts[1] + action
                expression = parts[0]
            }
        }

        if (expression.contains("db")) {
            val bonus = context.damageBonus
            expression = expression.replace("db", if (bonus.startsWith("-")) "($bonus)" else bonus)
        }

        val profile = RuleProfile.of(context.ruleMode)
        if (rule == RollRules.R) {
            if (expression.startsWith("b") || expression.startsWith("p")) {
                profile.bonusPenaltyHint(expression.startsWith("b"))?.let { return it }
            }
            expression = profile.rewriteBareDice(expression, context.defaultDiceNumber)
        }

        val defaultExpression = when (rule) {
            RollRules.R -> profile.defaultExpression(context.defaultDiceNumber)
            RollRules.WW, RollRules.WW_SIMPLE -> "10a10"
            RollRules.DX, RollRules.DX_WRAPPED_LINE -> "1c10"
            RollRules.FATE -> "f"
            else -> "d"
        }
        expression = if (expression.isEmpty()) {
            defaultExpression
        } else {
            rewriteLeadingNumber(expression, rule, defaultExpression)
        }

        val prefix = StringBuilder()
        if (!context.simplify) {
            if (action.isEmpty()) {
                prefix.append(context.sentences.sentenceRoll)
            } else {
                prefix.append(context.sentences.sentenceRollWithReason)
                context.template.add("reason", action).unescape = true
            }
        }

        val rollOptions = RollOptions(
            dxd_defaultNumber = context.defaultDiceNumber,
            simple = rule == RollRules.WW_SIMPLE || context.simplify,
            dx_ww_wrapped_line = rule == RollRules.DX_WRAPPED_LINE
        )

        val rollResult = StringBuilder()
        try {
            if (repeat.times == 1) {
                val result = DiceCalculation.rollCalculation(expression, rollOptions)
                context.rollRecorder?.invoke(result.intNumber)
                context.template.add("num", result.intNumber.toString())
                context.template.add("exp", result.expression)
                rollResult.append(result.toString())
            } else {
                rollResult.append(multiRoll(expression, rollOptions, repeat.times, context.rollRecorder))
            }
        } catch (e: DiceException) {


            return context.sentences.sentenceIllegal
        }

        context.template.add("roll", rollResult.toString()).addTailIfNotExist = true
        return prefix.toString()
    }














    fun range(context: RespondContext): String {
        val expression = context.param.trim()
        return try {
            val roll = stellaris.core.Roll(expression)
            val result = roll.start()
            val detail = if (result.resDetail.length > RANGE_DETAIL_LIMIT) {
                result.resDetail.take(RANGE_DETAIL_LIMIT) + "..."
            } else {
                result.resDetail
            }
            "取值范围：${result.resIntMin} ~ ${result.resIntMax}\n" +
                "运算细节：${roll.originData} = $detail = ${result.resInt}"
        } catch (e: DiceException) {
            context.sentences.sentenceIllegal
        }
    }


    private const val RANGE_DETAIL_LIMIT = 30






















    fun check(context: RespondContext, rule: RollRules): String {
        val arguments = CheckArguments.parse(context.param, rule)
        if (arguments.repeatTimes > MAX_REPEAT_TIMES) {
            return context.sentences.sentenceIllegalTooMuch
        }
        return if (context.ruleMode == RuleMode.NONE ||
            context.ruleMode == RuleMode.COC ||
            context.ruleMode == RuleMode.COC6
        ) {
            checkCoc(context, rule, arguments)
        } else {
            checkByRuleMode(context, rule, arguments)
        }
    }

    /** COC/COC6/未设置模式时的原房规检定链。 */
    private fun checkCoc(
        context: RespondContext,
        rule: RollRules,
        arguments: CheckArguments
    ): String {
        val target = CheckArguments.resolveTarget(arguments, context.cardNamed(arguments.cardName))
            ?: return context.sentences.sentenceIllegal

        val expression = checkExpression(rule, arguments.bonusPenaltyCount)
        val houseRule = houseRuleOf(rule, context.houseRule)
        val rolls = try {
            List(arguments.repeatTimes) {
                DiceCalculation.rollCalculation(expression, RollOptions()).intNumber
            }
        } catch (e: DiceException) {
            return context.sentences.sentenceIllegal
        }
        rolls.forEach { context.rollRecorder?.invoke(it) }

        val levels = rolls.map { CheckRule.level(it, target, houseRule) }
        val rollSegment = buildRollSegment(
            expression,
            rolls.map { it.toString() },
            levels,
            target.toString(),
            showTarget = true
        )
        return assembleCheckReply(context, arguments, rollSegment, if (rolls.size == 1) levels[0] else null)
    }

    /** DND/DG/FATE/DX/WX/CPR 的检定链。 */
    private fun checkByRuleMode(
        context: RespondContext,
        rule: RollRules,
        arguments: CheckArguments
    ): String {
        val card = context.cardNamed(arguments.cardName)
        val rawBase = arguments.explicitTarget ?: run {
            if (arguments.skillName.isEmpty()) {
                return context.sentences.sentenceIllegal
            }
            if (card.hasProperty(arguments.skillName)) {
                card.readProperty(arguments.skillName)
            } else {
                PlayerCardData.DEFAULT_PROPERTY_VALUE
            }
        }
        val profile = RuleProfile.of(context.ruleMode)
        val engine = profile.engine
        val target = engine.resolveTarget(arguments, rawBase, rule)
        val expression = engine.rollExpression(arguments, rule, target)
        val houseRule = houseRuleOf(rule, context.houseRule)
        val results = try {
            List(arguments.repeatTimes) { engine.roll(expression) }
        } catch (e: DiceException) {
            return context.sentences.sentenceIllegal
        }
        results.forEach { context.rollRecorder?.invoke(it.value) }

        val levels = results.map { engine.level(it.value, target, houseRule) }
        val targetText = engine.displayTarget(arguments, target, rule, rawBase)
        val rollSegment = buildRollSegment(
            expression,
            results.map { engine.displayRoll(it) },
            levels,
            targetText,
            engine.showsTarget
        )
        return assembleCheckReply(context, arguments, rollSegment, if (results.size == 1) levels[0] else null)
    }

    private fun buildRollSegment(
        expression: String,
        rolls: List<String>,
        levels: List<CheckLevel>,
        targetText: String,
        showTarget: Boolean
    ): String {
        val suffix = if (showTarget) "/$targetText" else ""
        return if (rolls.size == 1) {
            "$expression=${rolls[0]}$suffix"
        } else {
            rolls.indices.joinToString("\n") { index ->
                "$expression=${rolls[index]}$suffix ${levels[index].text}"
            }
        }
    }

    private fun assembleCheckReply(
        context: RespondContext,
        arguments: CheckArguments,
        rollSegment: String,
        singleLevel: CheckLevel?
    ): String {
        if (context.simplify) {
            return if (singleLevel != null) "$rollSegment ${singleLevel.text}" else rollSegment
        }

        val prefix = if (arguments.repeatTimes == 1) {
            if (arguments.reason.isEmpty()) {
                context.sentences.sentenceCheck
            } else {
                context.template.add("reason", arguments.reason).unescape = true
                context.sentences.sentenceCheckWithReason
            }
        } else {
            if (arguments.reason.isEmpty()) {
                context.sentences.sentenceCheckMultiple
            } else {
                context.template.add("reason", arguments.reason).unescape = true
                context.sentences.sentenceCheckMultipleWithReason
            }
        }

        context.template.add(
            "skill",
            arguments.displayName(DiceConstant.ENV_DEFAULT_SHOW_SKILL_NAME)
        )
        context.template.add("roll", rollSegment).addTailIfNotExist = true
        if (singleLevel != null) {
            context.template.add("success", singleLevel.text)
        }
        return prefix
    }







    private fun checkExpression(rule: RollRules, count: Int): String = when {
        rule.isBonusDie -> "1b$count"
        rule.isPenaltyDie -> "1p$count"
        else -> "1d$CHECK_DICE_FACES"
    }


    private const val CHECK_DICE_FACES = 100




















    fun opposed(
        context: RespondContext,
        rule: RollRules,
        first: OpposedParticipant,
        last: OpposedParticipant
    ): String {
        val houseRule = houseRuleOf(rule, context.houseRule)
        val mode = context.ruleMode
        val profile = RuleProfile.of(mode)
        val engine = profile.engine
        val expression = when (mode) {
            RuleMode.DND5E, RuleMode.DND3R -> "1d20"
            RuleMode.DG -> "1d100"
            RuleMode.CPR -> "1d10"
            else -> "1d100"
        }
        val (firstRoll, lastRoll) = try {
            if (mode == RuleMode.CPR) {
                engine.roll(expression).value to engine.roll(expression).value
            } else {
                DiceCalculation.rollCalculation(expression).intNumber to
                    DiceCalculation.rollCalculation(expression).intNumber
            }
        } catch (e: DiceException) {
            return context.sentences.sentenceIllegal
        }
        context.rollRecorder?.invoke(firstRoll)
        context.rollRecorder?.invoke(lastRoll)
        val firstLevel = if (mode in setOf(RuleMode.DND5E, RuleMode.DND3R, RuleMode.DG, RuleMode.CPR)) {
            engine.level(firstRoll, first.target, houseRule)
        } else {
            CheckRule.level(firstRoll, first.target, houseRule)
        }
        val lastLevel = if (mode in setOf(RuleMode.DND5E, RuleMode.DND3R, RuleMode.DG, RuleMode.CPR)) {
            engine.level(lastRoll, last.target, houseRule)
        } else {
            CheckRule.level(lastRoll, last.target, houseRule)
        }

        val result = "先手(${first.name})：${first.skill} $expression=$firstRoll/${first.target} " +
            "${firstLevel.text}\n后手(${last.name})：${last.skill} $expression=$lastRoll/${last.target} " +
            lastLevel.text
        if (context.simplify) {
            return result
        }

        context.template.add("competitor_first", first.name)
        context.template.add("competitor_last", last.name)

        context.template.add("result", result).addHeadIfNotExist = true


        if (!firstLevel.isSuccess && !lastLevel.isSuccess) {
            return context.sentences.sentenceOpposedAllFailure
        }
        return when (
            OpposedCheck.compare(
                OpposedCheck.Side(firstLevel, first.target, firstRoll),
                OpposedCheck.Side(lastLevel, last.target, lastRoll)
            )
        ) {
            OpposedCheck.Verdict.WIN -> {
                context.template.add("winner", first.name)
                context.sentences.sentenceOpposedFirstWin
            }

            OpposedCheck.Verdict.LOSE -> {
                context.template.add("winner", last.name)
                context.sentences.sentenceOpposedLastWin
            }

            OpposedCheck.Verdict.DRAW -> context.sentences.sentenceOpposedEqual
        }
    }








    class OpposedParticipant(val name: String, val skill: String, val target: Int)







    private fun houseRuleOf(rule: RollRules, configured: CocHouseRule): CocHouseRule =
        if (rule.followsHouseRule) configured else CocHouseRule.RULEBOOK


    private val SUPPORTED_RULES = arrayOf(
        RollRules.R,
        RollRules.WW,
        RollRules.WW_SIMPLE,
        RollRules.DX,
        RollRules.DX_WRAPPED_LINE,
        RollRules.FATE
    )


    private fun trimDanglingOperator(expression: String): String {
        var result = expression
        while (result.endsWith("(") || result.endsWith("+") || result.endsWith("-") ||
            result.endsWith("*") || result.endsWith("/")
        ) {
            result = result.dropLast(1)
        }
        return result
    }







    private fun substituteBareDiceFaces(expression: String, defaultFaces: Int): String {
        val builder = StringBuilder()
        for (index in expression.indices) {
            val char = expression[index]
            val next = expression.getOrNull(index + 1)
            if (char == 'd' && (next == null || (next !in '0'..'9' && next != '('))) {
                builder.append("d").append(defaultFaces)
            } else {
                builder.append(char)
            }
        }
        return builder.toString()
    }








    private fun rewriteLeadingNumber(expression: String, rule: RollRules, defaultExpression: String): String {
        val match = Regex("^(\\d+)$").find(expression) ?: Regex("^(\\d+)?[+\\-]").find(expression)
        ?: return expression
        val number = match.groupValues[1]
        if (number.isEmpty()) {
            return defaultExpression + expression
        }
        val isLegalMath = try {

            DiceCalculation.rollCalculation(expression, RollOptions(dxd_mode = RollOptions.DxDModes.MAXIMIZE))
            true
        } catch (notMath: Throwable) {
            false
        }
        if (!isLegalMath) {
            return expression
        }
        val replacement = when (rule) {
            RollRules.R -> "1d$number"
            RollRules.WW, RollRules.WW_SIMPLE -> "${number}a10"
            RollRules.DX, RollRules.DX_WRAPPED_LINE -> "${number}c10"
            RollRules.FATE -> "${number}f"
            else -> "d"
        }
        return expression.replaceFirst(number, replacement)
    }








    private fun multiRoll(
        expression: String,
        rollOptions: RollOptions,
        times: Int,
        recorder: ((Int) -> Unit)? = null
    ): String {
        val hasOperator = expression.any { it == '+' || it == '-' || it == '*' || it == '/' }
        val rolls = ArrayList<DiceCalculation.FinalResult>()
        var brief = false
        val startedAt = System.currentTimeMillis()
        val builder = StringBuilder()
        for (index in 0 until times) {
            val roll = DiceCalculation.rollCalculation(expression, rollOptions)
            recorder?.invoke(roll.intNumber)
            rolls.add(roll)
            if (!hasOperator || roll.detail.length > MULTI_ROLL_DETAIL_THRESHOLD) {
                brief = true
            }
            if (System.currentTimeMillis() - startedAt > DiceConstant.DICE_MUTILATION_ROLL_TIME_OUT) {
                builder.append("(!超时,>${DiceConstant.DICE_MUTILATION_ROLL_TIME_OUT}ms)")
                break
            }
        }
        builder.append("$expression={ ")
        var sum = 0
        rolls.forEachIndexed { index, roll ->
            if (brief) {
                if (index != 0) {
                    builder.append('、')
                }
                builder.append(roll.numberString)
            } else {
                builder.append('\n').append(roll.detail).append('=').append(roll.numberString)
            }
            sum += roll.intNumber
        }
        builder.append(" }($sum)")
        return builder.toString()
    }
}
