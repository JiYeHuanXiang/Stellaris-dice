package stellaris.dice

import onedice.defines.DiceException
import stellaris.dice.diceFunction.DiceFunctionImpl
import stellaris.dice.diceFunction.DiceConfigStore
import stellaris.dice.diceFunction.RuleMode
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.card.CardCommands
import stellaris.dice.diceFunction.card.CardEditor
import stellaris.dice.diceFunction.calculation.DiceCalculation
import stellaris.dice.diceFunction.commands.DiceCommandCallback
import stellaris.dice.diceFunction.commands.DiceCommandDecoder
import stellaris.dice.diceFunction.find.FindEngine
import stellaris.dice.diceFunction.initiative.InitiativeEntry
import stellaris.dice.diceFunction.initiative.InitiativeList
import stellaris.dice.diceFunction.initiative.InitiativeParser
import stellaris.dice.diceFunction.log.LogManager
import stellaris.dice.diceFunction.observer.ObserverStore
import stellaris.dice.diceFunction.opposed.OpposedContestStore
import stellaris.dice.diceFunction.responder.CharacterChecks
import stellaris.dice.diceFunction.responder.CheckArguments
import stellaris.dice.diceFunction.responder.CheckRule
import stellaris.dice.diceFunction.responder.CocHouseRule
import stellaris.dice.diceFunction.responder.Madness
import stellaris.dice.diceFunction.responder.DiceResponder
import stellaris.dice.diceFunction.responder.RespondContext
import stellaris.dice.diceFunction.responder.RollOptions
import stellaris.dice.diceFunction.responder.RollRules
import stellaris.dice.diceFunction.template.DiceMessageTemplate
import stellaris.dice.diceFunction.template.decodeXdX
import stellaris.dice.receive.MessageReceived
import stellaris.dice.receive.MessageSanitizer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit










class DiceHelperImpl(
    val botId: String,
    private val rootPath: String,
    @Volatile var intentEvent: DiceIntentEvent? = null
) : DiceHelper {


    val functions = DiceFunctionImpl(rootPath)

    private val pluginHost = object : stellaris.dice.diceFunction.lightScript.PluginHost {
        override fun setGroupCard(user: String, text: String, group: String): Boolean {
            // 空名片权限探针：只查询骰娘是否管理员。
            if (text.isEmpty()) {
                return intentEvent?.isSelfGroupOperator(group) ?: false
            }
            return intentEvent?.setGroupNumberName(text, user, group) ?: false
        }

        override fun configureAutoName(
            user: String,
            group: String,
            format: String,
            enabled: Boolean
        ): stellaris.dice.diceFunction.lightScript.AutoNameReceipt? {
            val card = functions.cardManager.configureAutoName(user, group, format, enabled)
                ?: return null
            return stellaris.dice.diceFunction.lightScript.AutoNameReceipt(
                card.playerName,
                card.autoNameEnabled,
                card.autoNameFormat
            )
        }

        override fun syncAutoName(user: String, group: String): Boolean {
            val text = functions.cardManager.renderAutoName(user, group) ?: return false
            if (text.isEmpty()) return false
            return intentEvent?.setGroupNumberName(text, user, group) ?: false
        }

        override fun sendGroupMessage(text: String, group: String) {
            val context = MessageReceived.buildMessageEvent(
                instance = functions,
                content = "",
                group = group,
                triggerId = botId,
                nick = "",
                isGroupMsg = true
            )
            send(
                stellaris.dice.diceFunction.lightScript.PluginLimits.limitResult(text) ?: "",
                context,
                DiceMessageSource(DiceMessageSource.Type.GROUP, group)
            )
        }

        override fun sendPrivateMessage(text: String, userId: String) {
            val context = MessageReceived.buildMessageEvent(
                instance = functions,
                content = "",
                triggerId = userId,
                nick = "",
                isGroupMsg = false
            )
            send(
                stellaris.dice.diceFunction.lightScript.PluginLimits.limitResult(text) ?: "",
                context,
                DiceMessageSource(DiceMessageSource.Type.PERSON, userId)
            )
        }

        override fun roll(expression: String): Int =
            DiceCalculation.rollCalculation(expression).intNumber

        override fun draw(group: String, name: String): String? =
            try {
                functions.drawManager.draw(group, name, 1)?.firstOrNull()
            } catch (t: Throwable) {
                null
            }

        override fun setGroupTitle(title: String, userId: String, group: String): Boolean =
            intentEvent?.setGroupTitle(title, userId, group) ?: false

        override fun setGroupWholeBan(ban: Boolean, group: String): Boolean =
            intentEvent?.setGroupWholeBan(ban, group) ?: false

        override fun reloadPlugins() {
            functions.drawManager.reload()
            functions.findEngine.reload()
            functions.pluginLoader.reload()
        }

        override fun readPlayerName(user: String, group: String): String? =
            functions.cardManager.findCurrentPlayer(user, group)?.playerName

        override fun readPlayerCard(user: String, name: String): Map<String, Int>? {
            if (!functions.cardManager.verifyPlayerExistence(user, name)) return null
            val card = functions.cardManager.getCertainPlayer(user, name, autoCreate = false)
            return card.propertyNames.associateWith { card.readProperty(it) }
        }

        override fun writePlayerCard(
            user: String,
            name: String,
            properties: Map<String, Int>
        ): Boolean {
            if (!functions.cardManager.verifyPlayerExistence(user, name)) return false
            // 先整体校验再写入。
            if (properties.keys.any { it.length > DiceConstant.MAX_SKILL_LENGTH }) return false
            val card = functions.cardManager.getCertainPlayer(user, name, autoCreate = false)
            properties.forEach { (key, value) -> card.setProperty(key, value) }
            return functions.cardManager.savePlayer(card)
        }

        override fun readLastAppearedGroup(user: String): String? =
            functions.lastAppearedStore.read(user)

        override fun readSystemConfig(key: String): String =
            functions.configStore.readGlobal(key)

        override fun openDeck(group: String, name: String): stellaris.dice.diceFunction.lightScript.PluginDeck? =
            try {
                functions.drawManager.find(name)?.clone()?.let {
                    stellaris.dice.diceFunction.lightScript.DrawDeckHandle(it)
                }
            } catch (t: Throwable) {
                null
            }

        override fun allowLocalNetwork(): Boolean =
            functions.configStore.readGlobal(DiceConfigStore.ALLOW_LOCAL_NETWORK) == "1"

        override fun httpGet(url: String): String =
            stellaris.dice.diceFunction.lightScript.NetworkAccess.httpGet(url, allowLocalNetwork())

        override fun httpPost(
            url: String,
            data: String,
            headers: Map<String, String>
        ): String? = stellaris.dice.diceFunction.lightScript.NetworkAccess
            .httpPost(url, data, headers, allowLocalNetwork())

        override fun requestRestart() {
            stellaris.dice.diceFunction.DiceLogger.error(
                "插件请求重启进程；当前宿主没有自动拉起机制，请手动重启"
            )
        }
    }

    private val backupScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "stellaris-backup-$botId").apply { isDaemon = true }
    }

    init {
        functions.pluginLoader.host = pluginHost
        functions.pluginLoader.reload()
        functions.pluginLoader.startTimers()
        backupScheduler.scheduleWithFixedDelay({
            try {
                if (functions.configStore.readGlobal(BACKUP_AUTO) == "1") {
                    functions.recoveryManager.makeBackup("auto")
                }
            } catch (t: Throwable) {
                stellaris.dice.diceFunction.DiceLogger.error("自动备份失败：$t")
            }
        }, 10, 10, TimeUnit.MINUTES)
    }

    override fun getDiceFunction() = functions

    override fun readDiceConfig(key: String): String = functions.configStore.readGlobal(key)

    override fun writeDiceConfig(key: String, value: String) {
        functions.configStore.writeGlobal(key, value)
    }

    override fun getDiceConfigsIndicator(): String =
        stellaris.dice.diceFunction.ConfigIndicators.json()












    override fun eventMessageReceive(message: MessageReceived) {
        val content = if (message.isMaster) {
            message.contentReal
        } else {

            message.nick = MessageSanitizer.sanitize(message.nick)
            message.groupNick = MessageSanitizer.sanitize(message.groupNick)
            MessageSanitizer.sanitize(message.contentReal)
        }

        val hadTrigger = content.startsWith(".") || content.startsWith("。") ||
            content.startsWith("!") || content.startsWith("！")
        val raw = stripLeadingTrigger(content).trim()
        if (raw.isEmpty()) {
            return
        }

        if (message.isGroupMsg && functions.blacklistStore.isGroupBlocked(message.group)) {
            return
        }
        if (!message.isMaster && functions.blacklistStore.isUserBlocked(message.triggerId)) {
            return
        }
        if (message.isGroupMsg && !message.isMaster) {
            val guard = functions.guardStore.onMessage(message.group, message.triggerId)
            if (guard.blocked) {
                guard.warning?.let { send(it, message, message.identity) }
                return
            }
        }
        if (message.isGroupMsg) {
            functions.lastAppearedStore.record(message.triggerId, message.group)
        }

        if (message.isGroupMsg && functions.configStore.readGroup(message.group, DiceConfigStore.BOT_OFF) == "1") {
            val lower = raw.lowercase()
            val isBotCommand = lower == "bot" || lower.startsWith("bot ") ||
                lower == "robot" || lower.startsWith("robot ")
            if (!isBotCommand) return
        }

        val template = DiceMessageTemplate()
        val displayName = message.nick.ifEmpty { message.groupNick }.ifEmpty { message.triggerId }

        if (message.isGroupMsg) {
            functions.logManager.appendUser(
                message.group, displayName, message.triggerId, message.contentReal
            )
        }
        RespondContext.seedTemplate(template, displayName, message.triggerId, message.group, botId)



        val card = functions.cardManager
            .getCurrentPlayer(message.triggerId, message.group)
            .asAbilities()
        val context = RespondContext(
            param = raw,
            isGroupMsg = message.isGroupMsg,
            groupId = message.group,
            senderId = message.triggerId,
            senderNick = displayName,
            template = template,
            sentences = functions.configStore,
            defaultDiceNumber = functions.configStore.defaultDiceNumber(
                message.isGroupMsg, message.group, message.triggerId
            ),
            simplify = message.isPureMode,

            damageBonus = stellaris.dice.diceFunction.calculation.DiceCalculation.abilitiesGetDB(card),
            card = card,

            cardProvider = { name ->
                functions.cardManager
                    .getCertainPlayer(message.triggerId, name, autoCreate = false)
                    .asAbilities()
            },

            houseRule = functions.configStore.cocHouseRule(
                message.isGroupMsg, message.group, message.triggerId
            ),

            ruleMode = functions.configStore.ruleMode(
                message.isGroupMsg, message.group, message.triggerId
            )
        )

        val callback = object : DiceCommandCallback {

            override fun onException(exception: DiceException) {

                send(context.sentences.sentenceIllegal, message, message.identity)
            }

            override fun onCommand(command: String, param: String, isProtected: Boolean): Boolean =
                respond(command, param, context, message, isProtected)

            override fun extraCommands(): ArrayList<String> =
                ArrayList(
                    (EXTRA_COMMANDS + functions.pluginLoader.customCommands)
                        .filterNot { isCoreCommand(it) }
                )
        }
        val commandText = if (hadTrigger) {
            functions.pluginLoader.replace(
                raw,
                message.triggerId,
                message.group,
                message.isGroupMsg,
                message.nick,
                message.groupNick
            ) ?: raw
        } else {
            raw
        }
        try {
            val handled = DiceCommandDecoder.invoke(callback, commandText)
            if (!handled && !hadTrigger) {
                keywordReply(raw, template, message)
            }
        } finally {
            functions.pluginLoader.afterMessage(
                message.triggerId,
                message.group,
                message.isGroupMsg,
                commandText,
                commandText,
                message.nick,
                message.groupNick
            )
        }
    }

    /** 非指令消息的关键词自动回复：先看总开关/群开关，再按权重先到先得。 */
    private fun keywordReply(raw: String, template: DiceMessageTemplate, message: MessageReceived) {
        if (!replyEnabled(message)) return
        val entry = functions.keywordManager.findMatch(raw)
        if (entry != null && !(message.isGroupMsg && entry.keyword in suppressedKeywords(message.group))) {
            template.add("keyword", entry.keyword)
            send(template.buildAll(entry.content), message, message.identity)
            return
        }
        val lua = functions.pluginLoader.matchKeyword(
            raw,
            message.triggerId,
            message.group,
            message.isGroupMsg,
            message.nick,
            message.groupNick
        )
        if (lua != null) {
            send(template.buildAll(lua), message, message.identity)
            return
        }
        val auto = functions.pluginLoader.matchAuto(
            raw,
            message.triggerId,
            message.group,
            message.isGroupMsg
        ) ?: return
        send(template.buildAll(auto.content), message, message.identity)
    }

    private fun replyEnabled(message: MessageReceived): Boolean {
        val global = functions.configStore.readGlobal(DiceConfigStore.REPLY_ON).ifEmpty { "1" }
        if (global == "0") return false
        if (message.isGroupMsg) {
            val group = functions.configStore.readGroup(message.group, DiceConfigStore.REPLY_ON)
            if (group == "0") return false
        }
        return true
    }


    private fun respond(
        command: String,
        param: String,
        context: RespondContext,
        message: MessageReceived,
        isProtected: Boolean = false
    ): Boolean {
        val normalized = command.lowercase()



        val base = X_VARIANTS[normalized] ?: normalized
        val effectiveContext = if (base != normalized) context.withSimplify() else context

        if (!isProtected && !isCoreCommand(base)) {
            val pluginReply = functions.pluginLoader.invokeCommand(
                base,
                param,
                message.triggerId,
                message.group,
                message.isGroupMsg,
                context.param,
                message.nick,
                message.groupNick
            )
            if (pluginReply != null) {
                return send(effectiveContext.template.buildAll(pluginReply), message, message.identity)
            }
        }

        if (base == "hl" || base == "h") {
            return send(context.sentences.sentenceHiddenRollHint, message, message.identity)
        }

        if (base == "setcoc") {
            return send(setCoc(param, message), message, message.identity)
        }

        if (base == "stc") {
            return send("已过时，请用 st", message, message.identity)
        }


        if (base == "hp" && message.isGroupMsg) {
            val initiative = functions.initiativeStore.peek(message.group)
            if (initiative != null && !initiative.isEmpty()) {
                return send(initiativeHp(param, context, message), message, message.identity)
            }
        }

        if (base in CARD_EDIT_COMMANDS) {
            val reply = editCard(base, param, context, message)
            return send(context.template.buildAll(reply), message, message.identity)
        }

        if (base == "pc") {
            val reply = if (param.trim().equals("stat", ignoreCase = true)) {

                hiyStats(context, message)
            } else {
                cardCommand(param, message)
            }
            return send(reply, message, message.identity)
        }

        if (base == "nn") {
            return send(renameCard(param, message), message, message.identity)
        }

        if (base == "en") {
            val checkContext = effectiveContext.withRollRecorder { value ->
                functions.hiyStore.record(message.triggerId, base, value)
            }
            val reply = growthCheck(param, checkContext, message)
            return send(effectiveContext.template.buildAll(reply), message, message.identity)
        }
        if (base == "sc") {
            val checkContext = effectiveContext.withRollRecorder { value ->
                functions.hiyStore.record(message.triggerId, base, value)
            }
            val reply = sanityCheck(param, checkContext, message)
            return send(effectiveContext.template.buildAll(reply), message, message.identity)
        }

        OPPOSED_RULES[base]?.let { opposedRule ->
            val opposedContext = effectiveContext.withRollRecorder { value ->
                functions.hiyStore.record(message.triggerId, base, value)
            }
            val reply = opposedCheck(param, opposedRule, opposedContext, message)
            return send(effectiveContext.template.buildAll(reply), message, message.identity)
        }

        if (base == "draw") {
            if (groupFeatureDisabled(DiceConfigStore.DISABLE_DRAW, message)) {
                return send("本群已禁用牌堆抽取。", message, message.identity)
            }
            val reply = drawDeck(param, context, message)
            return send(context.template.buildAll(reply), message, message.identity)
        }
        if (base == "deck") {
            if (groupFeatureDisabled(DiceConfigStore.DISABLE_DRAW, message)) {
                return send("本群已禁用牌堆抽取。", message, message.identity)
            }

            return send(
                context.template.buildAll(deckCommand(param, context, message)),
                message,
                message.identity
            )
        }

        if (base == "set") {
            return send(setDefaultDice(param, message), message, message.identity)
        }
        if (base == "name") {
            return send(randomName(param, message), message, message.identity)
        }
        if (base == "system") {
            return send(systemCommand(param), message, message.identity)
        }


        MADNESS_RULES[base]?.let { kind ->
            val reply = madnessRoll(kind, context, message)
            return send(context.template.buildAll(reply), message, message.identity)
        }

        if (base == "rb" || base == "rp") {
            return send(bonusPenaltyRoll(base, param, effectiveContext, message), message, message.identity)
        }

        if (base == "blackcloud") {
            return send(
                "云端黑名单在群星基准模版中未启用（无网络路径）；本地黑名单请用 .black。",
                message,
                message.identity
            )
        }

        if (base == "sn" || base == "setasn") {
            return send(
                "`.sn` 由默认插件 sn.toml 提供；当前实例未加载该插件。\n" +
                    "请把 sn.toml 放入实例的 custom/ 目录后执行 .system load。",
                message,
                message.identity
            )
        }

        if (base in MODE_FAMILIES) {
            if (base == "dnd" || base == "coc" || base == "coc6" ||
                base == "dg" || base == "wx" || base == "cpr" ||
                (base == "fate" && param.trim().startsWith("mode", ignoreCase = true)) ||
                ((base == "dx" || base == "ww" || base == "w") &&
                    param.trim().startsWith("mode", ignoreCase = true))
            ) {
                return send(ruleModeCommand(base, param, message), message, message.identity)
            }
        }

        if (base in MODE_SET_ALIASES) {
            return send(
                ruleModeCommand(MODE_SET_ALIASES.getValue(base), param, message),
                message,
                message.identity
            )
        }

        if (base == "ri") {
            val reply = initiativeRoll(param, context, message)
            return send(context.template.buildAll(reply), message, message.identity)
        }
        if (base == "init") {
            return send(initiativeCommand(param, message), message, message.identity)
        }

        if (base == "who") {
            return send(whoCommand(param), message, message.identity)
        }
        if (base == "jrrp") {
            if (groupFeatureDisabled(DiceConfigStore.DISABLE_JRRP, message)) {
                return send("本群已禁用今日人品。", message, message.identity)
            }
            return send(jrrpCommand(context, message), message, message.identity)
        }

        if (base == "hiy") {
            return send(hiyStats(context, message), message, message.identity)
        }

        if (base == "ob") {
            return send(observerCommand(param, context, message), message, message.identity)
        }
        if (base == "psy") {
            return psyCommand(param, context, message)
        }

        if (base == "log") {
            return send(logCommand(param, message), message, message.identity)
        }

        if (base == "reply") {
            return send(replyCommand(param, message), message, message.identity)
        }
        if (base == "black") {
            return send(blackCommand(param, message), message, message.identity)
        }
        if (base == "guard") {
            return send(guardCommand(param, message), message, message.identity)
        }
        if (base == "group") {
            return send(groupCommand(param, message), message, message.identity)
        }
        if (base == "backup") {
            return send(backupCommand(param, message), message, message.identity)
        }
        if (base == "recovery") {
            return send(recoveryCommand(param, message), message, message.identity)
        }

        if (base == "find" || base == "查询" || base == "查找") {
            return send(findCommand(param, message), message, message.identity)
        }
        if (base == "rules" || base == "rule") {
            return send(rulesCommand(param, message), message, message.identity)
        }
        if (base == "help" || base == "helpdoc") {
            if (groupFeatureDisabled(DiceConfigStore.DISABLE_HELP, message)) {
                return send("本群已禁用帮助。", message, message.identity)
            }
            return send(helpCommand(param), message, message.identity)
        }

        if (base == "about") {
            return send(ABOUT_TEXT, message, message.identity)
        }
        if (base == "runtime") {
            return send(runtimeStatus(message), message, message.identity)
        }
        if (base == "debug") {
            return send(debugCommand(param, message), message, message.identity)
        }
        if (base == "bot" || base == "robot") {
            return send(botCommand(param, message), message, message.identity)
        }
        if (base == "admin") {
            return send(adminState(message), message, message.identity)
        }
        if (base == "master") {
            return send(masterCommand(param, message), message, message.identity)
        }
        if (base == "send") {
            return send(sendToMaster(param, message), message, message.identity)
        }
        if (base == "dismiss") {
            return send(dismissCommand(message), message, message.identity)
        }
        if (base == "welcome") {
            return send(welcomeCommand(param, message), message, message.identity)
        }
        if (base == "nolawnosky") {
            return send(noLawnoskyCommand(param, message), message, message.identity)
        }
        val checkRule = CHECK_RULES[base]
        val rule = checkRule ?: ROLL_RULES[base]
        if (rule == null && base != "range") {

            send(context.sentences.sentenceIllegal, message, message.identity)
            return true
        }
        val rollContext = effectiveContext.withParam(param).withRollRecorder { value ->
            functions.hiyStore.record(message.triggerId, base, value)
        }
        val replyTemplate = when {
            checkRule != null -> DiceResponder.check(rollContext, checkRule)
            rule != null -> DiceResponder.r(rollContext, rule)
            else -> DiceResponder.range(rollContext)
        }
        val resultText = context.template.buildAll(replyTemplate)


        if (base in HIDDEN_COMMANDS) {
            if (!message.isGroupMsg) {
                return send(HIDDEN_GROUP_ONLY, message, message.identity)
            }
            return deliverHidden(resultText, context, message)
        }
        return send(resultText, message, message.identity)
    }










    private fun setCoc(param: String, message: MessageReceived): String {
        val argument = param.trim()
        if (argument.isEmpty()) {
            return "COC检定房规（.setcoc <0~5>）：\n${CocHouseRule.listing()}"
        }
        if (!canChangeGroupSettings(message)) {
            return SETTINGS_DENIED
        }
        val rule = CocHouseRule.parse(argument)
            ?: return "用法：.setcoc 0~5（不带参数=列出全部房规）"
        functions.configStore.writeCocHouseRule(
            message.isGroupMsg, message.group, message.triggerId, rule
        )
        return "COC检定房规已设为：${rule.label}（${rule.code}）"
    }














    private fun editCard(
        command: String,
        param: String,
        context: RespondContext,
        message: MessageReceived
    ): String {
        val card = functions.cardManager.getCurrentPlayer(message.triggerId, message.group)

        val argument = if (command == "hp") "$command$param" else param
        val template = context.template
        return when (val outcome = CardEditor.apply(card, argument)) {
            is CardEditor.Outcome.Updated -> {
                if (outcome.appliedNames.isNotEmpty()) {
                    functions.cardManager.savePlayer(card)
                }
                template.add("attrs", CardEditor.render(card, outcome.appliedNames))
                template.add(
                    "rejected",
                    if (outcome.rejectedNames.isEmpty()) {
                        ""
                    } else {
                        "（未记录名字过长的属性：${outcome.rejectedNames.joinToString("、")}）"
                    }
                )
                context.sentences.sentenceCardUpdated
            }

            is CardEditor.Outcome.ShowAll ->
                context.sentences.sentenceCardShow.also {
                    template.add("attrs", CardEditor.render(card, card.propertyNames))
                }

            is CardEditor.Outcome.Shown ->
                if (card.hasProperty(outcome.name)) {
                    context.sentences.sentenceCardShow.also {
                        template.add("attrs", CardEditor.render(card, listOf(outcome.name)))
                    }
                } else {
                    context.sentences.sentenceCardNoSuchAbility.also {
                        template.add("skill", outcome.name)
                    }
                }

            is CardEditor.Outcome.Deleted -> {
                functions.cardManager.savePlayer(card)
                "已删除属性：${outcome.name}"
            }

            is CardEditor.Outcome.Cleared -> {
                functions.cardManager.savePlayer(card)
                "已清空人物卡的所有属性"
            }

            is CardEditor.Outcome.Missing ->
                context.sentences.sentenceCardNoSuchAbility.also {
                    template.add("skill", outcome.name)
                }

            CardEditor.Outcome.Usage -> context.sentences.sentenceIllegal
        }
    }










    private fun cardCommand(param: String, message: MessageReceived): String {
        val commands = CardCommands(functions.cardManager, message.triggerId, message.group)
        return when (val outcome = commands.run(param)) {
            is CardCommands.Outcome.Listed -> if (outcome.cards.isEmpty()) {
                "你还没有人物卡"
            } else {
                "人物卡（当前：${outcome.currentName ?: "默认"}）：" +
                    outcome.cards.joinToString(" ") { "${it.playerName}(${it.rule.name})" }
            }

            is CardCommands.Outcome.Created ->
                "已新建人物卡：${outcome.card.playerName}（${outcome.card.rule.name}）"

            is CardCommands.Outcome.Deleted -> "已删除人物卡：${outcome.name}"

            is CardCommands.Outcome.Shown ->
                "人物卡「${outcome.card.playerName}」（${outcome.card.rule.name}）：" +
                    CardEditor.render(outcome.card, outcome.card.propertyNames)

            is CardCommands.Outcome.Bound -> "本群已切换到人物卡：${outcome.card.playerName}"

            is CardCommands.Outcome.Copied -> "已复制人物卡：${outcome.from} → ${outcome.to}"

            is CardCommands.Outcome.RuleChanged ->
                "人物卡「${outcome.card.playerName}」的规则已设为：${outcome.card.rule.name}"

            is CardCommands.Outcome.BindingsListed -> if (outcome.bindings.isEmpty()) {
                "还没有任何绑定关系（默认使用跨群卡）"
            } else {
                "绑定关系：\n" + outcome.bindings.entries.joinToString("\n") { (place, card) ->
                    val where = if (place.startsWith("@")) "私聊" else "群 $place"
                    "$where → $card"
                }
            }

            CardCommands.Outcome.BindingsCleared -> "已清空所有群的绑定关系"

            CardCommands.Outcome.AllCleared -> "已清空你的全部人物卡与绑定关系"

            CardCommands.Outcome.Unbound -> "已解除本场会话的人物卡绑定（回到默认卡）"

            is CardCommands.Outcome.Renamed ->
                "已将人物卡「${outcome.from}」改名为「${outcome.to}」"

            is CardCommands.Outcome.ShownAbove -> {
                val names = outcome.card.propertyNames.filter {
                    outcome.card.readProperty(it) > outcome.threshold
                }
                if (names.isEmpty()) {
                    "人物卡「${outcome.card.playerName}」（${outcome.card.rule.name}）" +
                        "没有大于 ${outcome.threshold} 的属性"
                } else {
                    "人物卡「${outcome.card.playerName}」（${outcome.card.rule.name}）" +
                        "大于 ${outcome.threshold} 的属性：" + CardEditor.render(outcome.card, names)
                }
            }

            is CardCommands.Outcome.Built ->
                "人物卡「${outcome.card.playerName}」（${outcome.card.rule.name}）属性生成：\n" +
                    outcome.summary + "\n" +
                    if (outcome.force) "redo：强制重算" else "build：只补缺失值"

            is CardCommands.Outcome.Failed -> when (outcome.reason) {
                CardCommands.Reason.USAGE -> PC_USAGE
                CardCommands.Reason.NO_SUCH_CARD -> "没有这张人物卡：${outcome.detail}"
                CardCommands.Reason.EXISTS -> "同名人物卡已存在：${outcome.detail}"
                CardCommands.Reason.NAME_TOO_LONG -> DiceConstant.ERROR_MSG_DUE_TO_INPUT_LONG_NAME
                CardCommands.Reason.TOO_MANY_CARDS ->
                    "人物卡太多啦（上限 ${DiceConstant.MAX_CARD_COUNT} 张）"

                CardCommands.Reason.UNSUPPORTED_RULE ->
                    "该卡型不支持自动生成属性（支持 COC6/COC7/DND3R/DND5E）"
            }
        }
    }








    private fun renameCard(param: String, message: MessageReceived): String {
        val newName = param.trim()
        val manager = functions.cardManager
        if (newName.isEmpty()) {
            val current = manager.currentPlayerName(message.triggerId, message.group)
                ?: return "你还没有绑定人物卡（用 .pc new 建卡；用 .nn 新名字 可给默认卡命名）"
            return "当前人物卡：$current（用 .nn 新名字 改名）"
        }
        if (newName.length > DiceConstant.MAX_PLAYER_LENGTH) {
            return DiceConstant.ERROR_MSG_DUE_TO_INPUT_LONG_NAME
        }
        val existing = manager.getPlayers(message.triggerId)
            .firstOrNull { it.playerName == newName }
        if (existing != null) {
            manager.setCurrentPlayer(message.triggerId, message.group, existing)
            return "已切换到人物卡：$newName"
        }
        val card = manager.getCurrentPlayer(message.triggerId, message.group)
        val oldName = card.readFriendlyName(defaultName = DiceConstant.ENV_DEFAULT_SHOW_PLAYER_NAME)
        card.playerName = newName
        manager.savePlayer(card)
        return "已将人物卡「$oldName」改名为「$newName」"
    }







    private fun growthCheck(param: String, context: RespondContext, message: MessageReceived): String {
        val card = functions.cardManager.getCurrentPlayer(message.triggerId, message.group)
        val skill = param.trim()
        if (skill.isEmpty()) {
            return context.sentences.sentenceIllegal
        }
        if (!card.hasProperty(skill)) {
            context.template.add("skill", skill)
            return context.sentences.sentenceCardNoSuchAbility
        }
        val result = CharacterChecks.growth(card.readProperty(skill))
        card.setProperty(skill, result.newValue)
        functions.cardManager.savePlayer(card)
        if (context.simplify) {
            return result.rollSegment
        }
        context.template.add("skill", skill)
        context.template.add("roll", result.rollSegment)
        return context.sentences.sentenceGrowth
    }









    private fun sanityCheck(param: String, context: RespondContext, message: MessageReceived): String {
        val request = CharacterChecks.parseSan(param)
            ?: return context.sentences.sentenceIllegal
        val card = functions.cardManager.getCurrentPlayer(message.triggerId, message.group)
        val target = request.sanOverride ?: if (card.hasProperty(CharacterChecks.SAN_ATTRIBUTE)) {
            card.readProperty(CharacterChecks.SAN_ATTRIBUTE)
        } else {
            context.template.add("skill", CharacterChecks.SAN_ATTRIBUTE)
            return context.sentences.sentenceCardNoSuchAbility
        }
        val result = CharacterChecks.sanity(request, target, context.houseRule)
            ?: return context.sentences.sentenceIllegal
        card.setProperty(CharacterChecks.SAN_ATTRIBUTE, result.newValue)
        functions.cardManager.savePlayer(card)
        if (context.simplify) {
            return result.rollSegment
        }
        context.template.add("roll", result.rollSegment)
        return context.sentences.sentenceSanCheck
    }














    private fun opposedCheck(
        param: String,
        rule: RollRules,
        context: RespondContext,
        message: MessageReceived
    ): String {
        if (!message.isGroupMsg) {
            return OPPOSED_GROUP_ONLY
        }
        if (param.trim().isEmpty()) {
            return OPPOSED_USAGE
        }
        val arguments = CheckArguments.parse(param, rule)

        val card = if (arguments.cardName.isEmpty()) {
            functions.cardManager.getCurrentPlayer(message.triggerId, message.group)
        } else {
            functions.cardManager.getCertainPlayer(
                message.triggerId, arguments.cardName, autoCreate = false
            )
        }
        val target = CheckArguments.resolveTarget(arguments, card.asAbilities())
            ?: return context.sentences.sentenceIllegal
        val skill = arguments.displayName(DiceConstant.ENV_DEFAULT_SHOW_SKILL_NAME)

        val name = card.readFriendlyName(defaultName = context.senderNick)

        val pending = functions.opposedStore.take(message.group)
        if (pending == null) {
            functions.opposedStore.join(
                message.group,
                OpposedContestStore.Pending(
                    senderId = message.triggerId,
                    senderName = name,
                    skill = skill,
                    target = target
                )
            )
            context.template.add("competitor_first", name)
            context.template.add("skill", skill)
            return context.sentences.sentenceOpposedStart
        }
        return DiceResponder.opposed(
            context,
            rule,
            DiceResponder.OpposedParticipant(pending.senderName, pending.skill, pending.target),
            DiceResponder.OpposedParticipant(name, skill, target)
        )
    }











    private fun initiativeRoll(
        param: String,
        context: RespondContext,
        message: MessageReceived
    ): String {
        if (!message.isGroupMsg) {
            return INITIATIVE_GROUP_ONLY
        }
        val list = functions.initiativeStore.get(message.group)
        val defaultName = functions.cardManager
            .getCurrentPlayer(message.triggerId, message.group)
            .readFriendlyName(defaultName = context.senderNick)
        val request = InitiativeParser.parse(param, defaultName)

        class Planned(val name: String, val expression: String, val value: Int)

        val planned = ArrayList<Planned>()
        val lines = ArrayList<String>()
        for (spec in request.specs) {
            val names = if (spec.count == 1) {
                listOf(spec.name)
            } else {
                List(spec.count) { spec.name + ('A' + it) }
            }
            if (spec.shared) {
                val result = rollInitiative(spec.expression)
                    ?: return context.sentences.sentenceIllegal
                functions.hiyStore.record(message.triggerId, "ri", result.intNumber)
                names.forEach { planned.add(Planned(it, spec.expression, result.intNumber)) }
                lines.add("${names.joinToString(" ")}: $result")
            } else {
                for (name in names) {
                    val result = rollInitiative(spec.expression)
                        ?: return context.sentences.sentenceIllegal
                    functions.hiyStore.record(message.triggerId, "ri", result.intNumber)
                    planned.add(Planned(name, spec.expression, result.intNumber))
                    lines.add("$name: $result")
                }
            }
        }


        val newNames = planned.asSequence().map { it.name }.distinct()
            .count { list.findExact(it) == null }
        if (list.size() + newNames > DiceConstant.MAX_FIRSTLY_ATTACKING_VALUE_COUNT) {
            return INITIATIVE_TOO_MANY
        }
        planned.forEach { list.put(it.name, it.expression, it.value, message.triggerId) }
        functions.initiativeStore.save(message.group)

        context.template.add("riname", planned.joinToString("、") { it.name })
        context.template.add("roll", "\n" + lines.joinToString("\n")).addTailIfNotExist = true
        return context.sentences.sentenceFirstAttacking
    }


    private fun rollInitiative(
        expression: String,
        allowNegative: Boolean = false
    ): DiceCalculation.FinalResult? = try {
        DiceCalculation.rollCalculation(
            expression,
            RollOptions(
                dxd_defaultNumber = INITIATIVE_DICE_FACES,
                allowNegativeNumber = allowNegative
            )
        )
    } catch (t: Throwable) {
        null
    }








    private fun initiativeCommand(param: String, message: MessageReceived): String {
        if (!message.isGroupMsg) {
            return INITIATIVE_GROUP_ONLY_INIT
        }
        val list = functions.initiativeStore.get(message.group)
        val trimmed = param.trim()
        val sub = trimmed.substringBefore(' ').lowercase()
        val rest = if (trimmed.contains(' ')) trimmed.substringAfter(' ').trim() else ""
        return when (sub) {
            "clr" -> {
                list.clear()
                functions.initiativeStore.save(message.group)
                "已清空先攻列表。"
            }

            "end" -> initiativeEnd(list, message.group)
            "set" -> initiativeSet(list, rest, message)
            "buff" -> initiativeBuff(list, rest, message.group)
            "del" -> initiativeDelete(list, rest, message.group)
            else -> if (list.isEmpty()) INITIATIVE_HELP_EMPTY else initiativeListText(list)
        }
    }


    private fun initiativeEnd(list: InitiativeList, group: String): String {
        if (list.isEmpty()) {
            return INITIATIVE_EMPTY_END
        }
        val current = list.current() ?: return INITIATIVE_EMPTY_END
        val next = list.next() ?: return INITIATIVE_EMPTY_END
        val upcoming = list.upcoming() ?: next
        functions.initiativeStore.save(group)
        return "——ROUND ${list.rounds}——\n" +
            "${current.name}的回合结束\n" +
            "${next.name}的回合开始\n" +
            "${upcoming.name}请做好准备"
    }


    private fun initiativeSet(
        list: InitiativeList,
        rest: String,
        message: MessageReceived
    ): String {
        val match = INITIATIVE_SET_PATTERN.matchEntire(rest) ?: return INITIATIVE_SET_USAGE
        val name = match.groupValues[1].trim()
        val value = match.groupValues[2].toIntOrNull() ?: return INITIATIVE_SET_USAGE
        if (name.isEmpty()) {
            return INITIATIVE_SET_USAGE
        }
        if (list.findExact(name) == null &&
            list.size() >= DiceConstant.MAX_FIRSTLY_ATTACKING_VALUE_COUNT
        ) {
            return INITIATIVE_TOO_MANY
        }
        list.put(name, "", value, message.triggerId)
        functions.initiativeStore.save(message.group)
        return "已设置 $name 的先攻点为 $value"
    }







    private fun initiativeBuff(list: InitiativeList, rest: String, group: String): String {
        val boundary = rest.indexOfFirst { it.isWhitespace() }
        if (boundary < 0) {
            return INITIATIVE_BUFF_USAGE
        }
        val namesPart = rest.substring(0, boundary).trim()
        val statusRaw = rest.substring(boundary + 1).trim()
        if (namesPart.isEmpty()) {
            return INITIATIVE_BUFF_USAGE
        }
        val mode = when (statusRaw.firstOrNull()) {
            '+' -> InitiativeEntry.BuffMode.ADD
            '-' -> InitiativeEntry.BuffMode.REMOVE
            else -> InitiativeEntry.BuffMode.OVERWRITE
        }
        val status = if (mode == InitiativeEntry.BuffMode.OVERWRITE) statusRaw else statusRaw.drop(1)

        val touched = LinkedHashSet<InitiativeEntry>()
        namesPart.split('/').filter { it.isNotBlank() }.forEach { name ->
            touched.addAll(list.search(name))
        }
        if (touched.isEmpty()) {
            return "没有找到可修改的项目"
        }
        touched.forEach { it.applyBuff(mode, status) }
        functions.initiativeStore.save(group)
        return buildString {
            append("已修改状态：\n")
            touched.forEach { append("『$it』 状态已更新\n") }
        }.trimEnd()
    }




    private fun initiativeDelete(list: InitiativeList, rest: String, group: String): String {
        val keyword = rest.trim()
        if (keyword.isEmpty()) {
            return "没有找到可删除的项目"
        }
        val exact = list.entries.filter { it.name.equals(keyword, ignoreCase = true) }
        val targets = if (exact.size == 1) exact else list.search(keyword)
        if (targets.isEmpty()) {
            return "没有找到可删除的项目"
        }
        targets.forEach { list.remove(it) }
        functions.initiativeStore.save(group)
        return buildString {
            append("以下条目被删除:\n")
            targets.forEach { append(it.name).append('\n') }
        }.trimEnd()
    }


    private fun initiativeListText(list: InitiativeList): String = buildString {
        append("本群先攻列表（ROUND ${list.rounds}）：\n")
        list.entries.forEachIndexed { index, entry ->
            if (index == list.currentIndex) {
                append("->")
            }
            append(index + 1).append('.').append(entry).append('\n')
        }
        append("\n现在是 ").append(list.current()?.name ?: "").append(" 的回合")
    }











    private fun initiativeHp(
        param: String,
        context: RespondContext,
        message: MessageReceived
    ): String {
        val list = functions.initiativeStore.get(message.group)
        val cardName = functions.cardManager
            .getCurrentPlayer(message.triggerId, message.group)
            .readFriendlyName(defaultName = context.senderNick)
        val tokens = param.trim().split(INITIATIVE_WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            val entry = list.findExact(cardName) ?: list.findExact(context.senderNick)
            return entry?.toString() ?: INITIATIVE_HP_NO_SELF
        }

        val spec = tokens.last()
        val names = if (tokens.size == 1) {
            listOf(cardName)
        } else {
            tokens.dropLast(1).joinToString(" ").split('/').filter { it.isNotBlank() }
        }
        if (names.isEmpty()) {
            return INITIATIVE_HP_USAGE
        }

        var setCurrent: Int? = null
        var setMax: Int? = null
        var delta: Int? = null
        INITIATIVE_HP_SET_PATTERN.matchEntire(spec)?.let { match ->
            setCurrent = match.groupValues[1].toIntOrNull()
            setMax = match.groupValues[2].toIntOrNull()
            if (setCurrent == null || setMax == null) {
                return INITIATIVE_HP_USAGE
            }
        } ?: run {
            if (PURE_SIGNED_INT.matches(spec)) {
                val value = spec.toIntOrNull() ?: return INITIATIVE_HP_USAGE
                if (spec.startsWith("+") || spec.startsWith("-")) {
                    delta = value
                } else {
                    setCurrent = value
                }
            } else {
                delta = rollInitiative(spec, allowNegative = true)?.intNumber
                    ?: return INITIATIVE_HP_USAGE
            }
        }

        val touched = LinkedHashSet<InitiativeEntry>()
        names.forEach { name -> touched.addAll(list.search(name)) }
        if (touched.isEmpty()) {
            return "先攻列表里没有找到「${names.joinToString("/")}」"
        }

        val lines = ArrayList<String>()
        val dead = ArrayList<InitiativeEntry>()
        touched.forEach { entry ->
            val before = entry.hp
            val detail: String
            when {
                setCurrent != null -> {
                    entry.setHp(setCurrent!!, setMax)
                    detail = if (entry.maxHp != null) {
                        "设置为 ${entry.hp}/${entry.maxHp}"
                    } else {
                        "设置为 ${entry.hp}"
                    }
                    if ((entry.hp ?: 1) <= 0) {
                        dead.add(entry)
                    }
                }

                delta != null -> {
                    val amount = delta!!
                    val deadNow = entry.addHp(amount)
                    detail = when {
                        amount == 0 -> "不变"
                        before != null -> {
                            val after = entry.hp ?: 0
                            when {
                                after < before -> "减少了${before - after}"
                                after > before -> "增加了${after - before}"
                                else -> "不变"
                            }
                        }

                        amount < 0 -> "减少了${-amount}"
                        else -> "增加了$amount"
                    }
                    if (deadNow) {
                        dead.add(entry)
                    }
                }

                else -> detail = "不变"
            }
            lines.add("『$entry』的生命值$detail")
        }
        dead.forEach { list.remove(it) }
        functions.initiativeStore.save(message.group)
        return buildString {
            lines.forEach { append(it).append('\n') }
            if (dead.isNotEmpty()) {
                append("已自动清除 ${dead.size} 条死亡对象\n")
            }
            if (list.isEmpty()) {
                append("注：先攻列表已为空！\n")
            }
        }.trimEnd()
    }






    private fun whoCommand(param: String): String {
        val names = param.trim().split(INITIATIVE_WHITESPACE).filter { it.isNotEmpty() }
        if (names.size < WHO_MIN_NAMES) {
            return WHO_USAGE
        }
        val shuffled = names.toMutableList()
        java.util.Collections.shuffle(
            shuffled,
            stellaris.dice.diceFunction.random.RandomService.businessRandom
        )
        return buildString {
            append("随机分配结果：\n")
            shuffled.forEachIndexed { index, name ->
                append(index + 1).append('.').append(name).append('\n')
            }
        }.trimEnd()
    }







    private fun jrrpCommand(context: RespondContext, message: MessageReceived): String {
        val day = java.time.LocalDate.now().toString()
        val seed = "$day:${message.triggerId}".hashCode().toLong()
        val value = ((seed and 0xFFFFFFFFL) % 100).toInt() + 1
        return "${context.senderNick}今日人品：$value（1~100）"
    }







    private fun observerCommand(
        param: String,
        context: RespondContext,
        message: MessageReceived
    ): String {
        val sub = param.trim().substringBefore(' ').lowercase()
        if (!message.isGroupMsg) {
            return when (sub) {
                "exit", "clr" -> {
                    val count = functions.observerStore.removeEverywhere(message.triggerId)
                    if (count > 0) "共清理了${count}个群的ob位" else "你没有在任何群里加入过ob位"
                }

                else -> "请在群内使用ob命令"
            }
        }

        val isAdmin = canAdministerGroup(message)
        if (!functions.configStore.observerEnabled(message.group) && !isAdmin) {
            return "ob功能已被管理员禁用"
        }
        return when (sub) {
            "help" -> OB_USAGE

            "exit" -> if (functions.observerStore.leave(message.group, message.triggerId)) {
                "你不再处于OB位"
            } else {
                "你已经不在OB位了"
            }

            "list" -> {
                val observers = functions.observerStore.list(message.group)
                if (observers.isEmpty()) {
                    "当前没有OB者"
                } else {
                    "当前的OB者有：\n" + observers.joinToString("\n") { "${it.name}(${it.id})" }
                }
            }

            "clr" -> if (!isAdmin) {
                OB_DENIED
            } else {
                functions.observerStore.clear(message.group)
                "已清空OB位"
            }

            "off" -> if (!isAdmin) {
                OB_DENIED
            } else {
                functions.configStore.writeObserverEnabled(message.group, false)
                "已关闭OB功能"
            }

            "on" -> if (!isAdmin) {
                OB_DENIED
            } else {
                functions.configStore.writeObserverEnabled(message.group, true)
                "已打开OB功能"
            }


            else -> when (
                functions.observerStore.join(
                    message.group, message.triggerId, context.senderNick
                )
            ) {
                ObserverStore.JoinResult.JOINED -> "已将${context.senderNick}加入OB位"
                ObserverStore.JoinResult.ALREADY -> "你已经处于OB位了，无法再次加入"
                ObserverStore.JoinResult.FULL ->
                    "OB位已满（最多 ${ObserverStore.MAX_OBSERVERS} 人）"
            }
        }
    }







    private fun psyCommand(
        param: String,
        context: RespondContext,
        message: MessageReceived
    ): Boolean {
        if (!message.isGroupMsg) {
            return send(PSY_GROUP_ONLY, message, message.identity)
        }
        val targetId = message.atTargets.singleOrNull()
            ?: return send(PSY_USAGE, message, message.identity)
        val skill = param.trim().split(INITIATIVE_WHITESPACE)
            .filter { it.isNotEmpty() && it !in message.atTargets }
            .joinToString(" ")
            .ifEmpty { DEFAULT_PSY_SKILL }
        val card = functions.cardManager.findCurrentPlayer(targetId, message.group)
            ?: return send("该玩家还没有人物卡：$targetId", message, message.identity)
        if (!card.hasProperty(skill)) {
            return send("该玩家技能 $skill 未录入属性", message, message.identity)
        }
        val targetValue = card.readProperty(skill)
        val roll = try {
            DiceCalculation.rollCalculation("1d100").intNumber
        } catch (t: Throwable) {
            return send(context.sentences.sentenceIllegal, message, message.identity)
        }
        functions.hiyStore.record(message.triggerId, "psy", roll)
        val level = CheckRule.level(roll, targetValue, context.houseRule)
        val targetName = card.readFriendlyName(defaultName = targetId)
        val result = "玩家 $targetName 技能 $skill：1d100=$roll/$targetValue ${level.text}"
        return deliverHidden(result, context, message)
    }







    private fun deliverHidden(
        result: String,
        context: RespondContext,
        message: MessageReceived
    ): Boolean {
        if (functions.configStore.readGlobal("MASTER_RH") == "0") {
            return send("暗骰已被 master 关闭，请改用普通检定。", message, message.identity)
        }
        val bridge = intentEvent ?: return false
        val placeholder = context.sentences.sentenceHiddenRoll
        if (placeholder.isNotEmpty()) {
            bridge.sendMessage(placeholder, message, message.identity)
            functions.logManager.appendBot(message.group, botId, placeholder)
        }
        val wrapped = "${context.senderNick}在群${message.group}的暗骰结果\n$result"
        bridge.sendMessage(
            wrapped,
            message,
            DiceMessageSource(DiceMessageSource.Type.PERSON, message.triggerId)
        )
        functions.observerStore.list(message.group).forEach { observer ->
            if (observer.id != message.triggerId) {
                bridge.sendMessage(
                    wrapped,
                    message,
                    DiceMessageSource(DiceMessageSource.Type.PERSON, observer.id)
                )
            }
        }
        return true
    }







    private fun hiyStats(context: RespondContext, message: MessageReceived): String {
        val stats = functions.hiyStore.get(message.triggerId)
            ?: return "你还没有骰点记录（掷过骰后可用 .hiy 查看）"
        val average = String.format(
            java.util.Locale.US,
            "%.1f",
            stats.sum.toDouble() / stats.rolls
        )
        val byCommand = stats.commands.entries
            .sortedByDescending { it.value }
            .joinToString("、") { "${it.key} ${it.value} 次" }
        return buildString {
            append(context.senderNick).append(" 的骰点统计（按 QQ 汇总）：\n")
            append("共 ").append(stats.rolls).append(" 次，平均 ").append(average)
            append("，最小 ").append(stats.min).append("，最大 ").append(stats.max)
            if (byCommand.isNotEmpty()) {
                append("\n按指令：").append(byCommand)
            }
        }
    }








    private fun logCommand(param: String, message: MessageReceived): String {
        if (!message.isGroupMsg) {
            return LOG_GROUP_ONLY
        }
        val trimmed = param.trim()
        val sub = trimmed.substringBefore(' ').lowercase()
        val rest = if (trimmed.contains(' ')) trimmed.substringAfter(' ').trim() else ""
        val manager = functions.logManager
        val isAdmin = canAdministerGroup(message)
        return when (sub) {
            "", "help" -> LOG_USAGE

            "list" -> logList(message.group)

            "on", "new" -> if (!isAdmin) SETTINGS_DENIED else startLog(rest, message)

            "off", "stop" -> if (!isAdmin) {
                SETTINGS_DENIED
            } else {
                val name = manager.stop(message.group)
                if (name == null) {
                    "本群没有正在记录的日志"
                } else {
                    "已暂停记录日志：$name（用 log on $name 继续，log end 上传）"
                }
            }

            "end", "get", "exit" -> if (!isAdmin) SETTINGS_DENIED else endLog(rest, message)

            "rm", "del" -> if (!isAdmin) SETTINGS_DENIED else removeLog(rest, message)

            "clr" -> if (!isAdmin) {
                SETTINGS_DENIED
            } else {
                val count = manager.clear(message.group)
                if (count == 0) "本群还没有日志" else "已清空 $count 份日志"
            }


            else -> if (!isAdmin) SETTINGS_DENIED else startLog(trimmed, message)
        }
    }


    private fun startLog(name: String, message: MessageReceived): String {
        val manager = functions.logManager
        return when (manager.start(message.group, name)) {
            LogManager.StartResult.STARTED ->
                "已开始记录日志：${manager.activeName(message.group)}（log end 结束并上传）"

            LogManager.StartResult.RESUMED ->
                "已继续记录日志：${manager.activeName(message.group)}"

            LogManager.StartResult.ALREADY ->
                "本群已经有一份日志在记录中（先 log end/log off）"

            LogManager.StartResult.TOO_MANY ->
                "本群日志已达上限 ${DiceConstant.MAX_LOG_COUNT} 份，请先 log rm/log clr"

            LogManager.StartResult.INVALID_NAME ->
                "日志名不合法（不能含路径分隔符，最长 30 字）"
        }
    }






    private fun endLog(name: String, message: MessageReceived): String {
        val manager = functions.logManager
        val targetName = name.ifEmpty { manager.activeName(message.group) ?: "" }
        if (targetName.isEmpty()) {
            return "本群没有正在记录的日志"
        }
        manager.stopIfActive(message.group, targetName)
        val file = manager.find(message.group, targetName)
            ?: return "没有找到日志：$targetName"
        val uploadName = "${targetName}.txt"
        val bridge = intentEvent
            ?: return "日志已停止记录，但宿主未就绪，无法上传：$uploadName（文件：${file.absolutePath}）"
        val uploaded = try {
            bridge.sendGroupFile(file.absolutePath, uploadName, message.group)
        } catch (t: Throwable) {
            false
        }
        return if (uploaded) {
            "日志已结束并上传：$uploadName"
        } else {
            "日志已保存，但宿主未提供上传能力：$uploadName（文件：${file.absolutePath}）"
        }
    }


    private fun removeLog(name: String, message: MessageReceived): String {
        val manager = functions.logManager
        val targetName = name.ifEmpty { manager.activeName(message.group) ?: "" }
        if (targetName.isEmpty()) {
            return "用法：log rm <文件名>"
        }
        return if (manager.remove(message.group, targetName)) {
            "已删除日志：$targetName"
        } else {
            "没有找到日志：$targetName"
        }
    }


    private fun logList(group: String): String {
        val logs = functions.logManager.list(group)
        if (logs.isEmpty()) {
            return "本群还没有日志"
        }
        return buildString {
            append("--日志列表--\n")
            logs.forEach { log ->
                append(if (log.active) "->" else "  ").append(log.name).append('\n')
            }
            append("（log end 文件名 上传；单群上限 ${DiceConstant.MAX_LOG_COUNT} 份）")
        }
    }












    private fun drawDeck(param: String, context: RespondContext, message: MessageReceived): String {
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        when (tokens.firstOrNull()?.lowercase()) {
            "help" -> return DRAW_HELP
            "list" -> return deckList(message)
            "search" -> {
                val keyword = tokens.drop(1).joinToString(" ").trim()
                if (keyword.isEmpty()) {
                    return "用法：.draw search <关键词>"
                }
                val hits = functions.drawManager.search(keyword)
                return if (hits.isEmpty()) {
                    "没有匹配「$keyword」的牌堆"
                } else {
                    "匹配「$keyword」的牌堆：${hits.joinToString("、")}"
                }
            }
        }

        val name = tokens.firstOrNull()?.ifEmpty { null }

            ?: functions.drawManager.runtimeDefaultName(message.group)
            ?: functions.configStore.drawDefaultDeck(
                message.isGroupMsg, message.group, message.triggerId
            )
        if (name.isEmpty()) {
            return DRAW_USAGE
        }

        val count = tokens.getOrNull(1)?.toIntOrNull()?.coerceIn(1, MAX_DRAW_COUNT) ?: 1
        val drawn = try {
            functions.drawManager.draw(message.group, name, count)
        } catch (t: Throwable) {
            return "这个牌堆抽不出东西：$name"
        } ?: return "没有这个牌堆：$name"
        if (drawn.isEmpty()) {
            return "这个牌堆是空的：$name"
        }

        val remain = functions.drawManager.remain(message.group, name) ?: 0
        val drawnText = drawn.joinToString("\n")
        if (context.simplify) {
            return drawnText
        }
        context.template.add("draw", drawnText)
        context.template.add("remain", if (remain > 0) "（还剩${remain}）" else "")
        return context.sentences.sentenceDraw
    }








    private fun deckCommand(param: String, context: RespondContext, message: MessageReceived): String {
        val normalized = param.trim()
        val sub = normalized.substringBefore(' ').lowercase()
        val rest = if (normalized.contains(' ')) normalized.substringAfter(' ').trim() else ""
        return when (sub) {
            "" -> drawDeck("", context, message)
            "show" -> deckList(message)
            "set" -> {
                if (rest.isEmpty()) {
                    DECK_USAGE
                } else {
                    val numeric = rest.toIntOrNull()
                    if (numeric != null) {
                        if (numeric !in 1..MAX_INLINE_NUMERIC_DECK) {
                            "错误，deck set 数值 的定义域在1~100"
                        } else if (!canChangeGroupSettings(message)) {
                            SETTINGS_DENIED
                        } else {
                            functions.drawManager.setInlineDeck(
                                message.group,
                                (1..numeric).map { it.toString() },
                                minimumEntries = 1
                            )
                            "默认牌堆已设为：1~$numeric（共 $numeric 张，临时）"
                        }
                    } else if (functions.drawManager.find(rest) == null) {
                        "没有这个牌堆：$rest"
                    } else if (!canChangeGroupSettings(message)) {
                        SETTINGS_DENIED
                    } else {
                        functions.configStore.writeDrawDefaultDeck(
                            message.isGroupMsg, message.group, message.triggerId, rest
                        )

                        functions.drawManager.clearRuntimeDefault(message.group)
                        "默认牌堆已设为：$rest"
                    }
                }
            }

            "new" -> {
                val entries = rest.split(Regex("[|\\s]+")).filter { it.isNotEmpty() }
                if (entries.size < 2) {
                    DECK_NEW_USAGE
                } else if (!canChangeGroupSettings(message)) {
                    SETTINGS_DENIED
                } else {
                    functions.drawManager.setInlineDeck(message.group, entries)
                    "已创建临时牌堆并设为默认（${entries.size} 条，抽取不放回）"
                }
            }

            "reset" -> {
                if (!canChangeGroupSettings(message)) {
                    SETTINGS_DENIED
                } else {
                    val target = rest.ifEmpty { null }
                    val known = target == null ||
                        functions.drawManager.find(target) != null ||
                        target == functions.drawManager.runtimeDefaultName(message.group)
                    if (!known) {
                        "没有这个牌堆：$target"
                    } else {
                        val count = functions.drawManager.reset(message.group, target)
                        if (target == null) "已重置本群全部牌堆的抽取进度（$count 个）"
                        else "已重置牌堆「$target」的抽取进度"
                    }
                }
            }

            else -> DECK_USAGE
        }
    }


    private fun deckList(message: MessageReceived): String {
        val titles = functions.drawManager.titles()
        val inline = functions.drawManager.inlineRemain(message.group)
        if (titles.isEmpty() && inline == 0) {
            return "还没有可用的牌堆（把牌堆文件放进实例的 draw/ 目录）"
        }
        val current = functions.configStore.drawDefaultDeck(
            message.isGroupMsg, message.group, message.triggerId
        )
        return buildString {
            if (titles.isNotEmpty()) {
                append("可用牌堆：").append(titles.joinToString("、"))
            }
            if (inline > 0) {
                if (titles.isNotEmpty()) append('\n')
                append("临时牌堆：$inline 条（.deck new 建的，不落盘）")
            }
            if (current.isNotEmpty()) {
                append("\n默认牌堆：").append(current)
            }
        }
    }










    private fun setDefaultDice(param: String, message: MessageReceived): String {
        val argument = param.trim()
        if (argument.isEmpty()) {
            val current = functions.configStore.defaultDiceNumber(
                message.isGroupMsg, message.group, message.triggerId
            )
            return "当前默认骰面数：$current"
        }
        val faces = argument.toIntOrNull() ?: return SET_USAGE
        if (faces !in MIN_DICE_FACES..MAX_DICE_FACES) {
            return SET_USAGE
        }
        if (message.isGroupMsg) {
            functions.configStore.writeGroup(
                message.group, DiceConfigStore.DEFAULT_DICE_NUMBER, faces.toString()
            )
        } else {
            functions.configStore.writePerson(
                message.triggerId, DiceConfigStore.DEFAULT_DICE_NUMBER, faces.toString()
            )
        }
        val mode = functions.configStore.ruleMode(
            message.isGroupMsg, message.group, message.triggerId
        )
        return if (mode == RuleMode.NONE) {
            "默认骰面数已设为：$faces"
        } else {
            "默认骰面数已设为：$faces（注意：当前 ${ruleModeLabel(mode)} 模式开着，" +
                "裸 .r 仍按体系默认骰；要按 d$faces 请先 .${ruleModeFamily(mode)} mode off）"
        }
    }

    private fun ruleModeFamily(mode: RuleMode): String = when (mode) {
        RuleMode.NONE -> "coc"
        RuleMode.COC -> "coc"
        RuleMode.COC6 -> "coc6"
        RuleMode.DND5E, RuleMode.DND3R -> "dnd"
        RuleMode.DG -> "dg"
        RuleMode.FATE -> "fate"
        RuleMode.DX -> "dx"
        RuleMode.WX -> "wx"
        RuleMode.CPR -> "cpr"
    }








    private fun randomName(param: String, message: MessageReceived): String {
        var language = "cn"
        var count = DEFAULT_NAME_COUNT
        param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.forEach { token ->
            when (token.lowercase()) {
                "cn", "jp", "en" -> language = token.lowercase()
                else -> token.toIntOrNull()?.let { count = it.coerceIn(1, MAX_NAME_COUNT) }
            }
        }
        val deck = when (language) {
            "jp" -> "随机姓名_jp"
            "en" -> "随机姓名_en"
            else -> "随机姓名_cn"
        }
        val names = try {
            functions.drawManager.draw(message.group, deck, count)
        } catch (t: Throwable) {
            null
        } ?: return NAME_CONTENT_MISSING
        if (names.isEmpty()) {
            return NAME_CONTENT_MISSING
        }
        val displayName = message.nick.ifEmpty { message.groupNick }.ifEmpty { message.triggerId }
        return "为${displayName}生成了以下名字：\n" + names.joinToString("\n")
    }








    private fun findCommand(param: String, message: MessageReceived): String {
        val engine = functions.findEngine
        val args = engine.parse(param)
        if (args.groupCommand) {
            return findGroupCommand(args.groupArgument, message)
        }
        val scope = if (message.isGroupMsg) message.group else message.triggerId
        return engine.render(engine.search(args, findDefaultGroup(message), scope))
    }

    private fun findGroupCommand(argument: String, message: MessageReceived): String {
        val engine = functions.findEngine
        val arg = argument.trim()
        if (arg.isEmpty()) {
            return engine.groupStatus(findDefaultGroup(message))
        }
        if (arg.equals("off", true) || arg == "取消") {
            writeFindGroup(message, "")
            return "已取消本群默认分组"
        }
        val group = engine.resolveGroup(arg)
            ?: return "没有这个分组：$arg（可用：${engine.groups().joinToString("、")}）"
        writeFindGroup(message, group)
        return "本群默认分组已设为：$group"
    }

    private fun findDefaultGroup(message: MessageReceived): String? {
        val value = if (message.isGroupMsg) {
            functions.configStore.readGroup(message.group, DiceConfigStore.FIND_GROUP)
        } else {
            functions.configStore.readPerson(message.triggerId, DiceConfigStore.FIND_GROUP)
        }
        return value.takeIf { it.isNotBlank() }
    }

    private fun writeFindGroup(message: MessageReceived, value: String) {
        if (message.isGroupMsg) {
            functions.configStore.writeGroup(message.group, DiceConfigStore.FIND_GROUP, value)
        } else {
            functions.configStore.writePerson(message.triggerId, DiceConfigStore.FIND_GROUP, value)
        }
    }







    private fun rulesCommand(param: String, message: MessageReceived): String {
        val raw = param.trim()
        if (raw.isEmpty() || raw.equals("help", true)) {
            return functions.findEngine.helpText()
        }
        val tokens = raw.split("\\s+".toRegex())
        if (tokens[0].equals("set", true) || tokens[0] == "设置") {
            val rest = tokens.drop(1).joinToString(" ").trim()
            if (rest.isEmpty()) return findGroupCommand("off", message)
            val group = functions.findEngine.resolveGroup(rest) ?: legacyRuleAlias(rest) ?: rest
            return findGroupCommand(group, message)
        }
        val split = raw.indexOfFirst { it == ':' || it == '：' }
        if (split > 0) {
            val rule = raw.substring(0, split).trim()
            val keyword = raw.substring(split + 1).trim()
            val group = functions.findEngine.resolveGroup(rule) ?: legacyRuleAlias(rule) ?: rule
            return findCommand(if (keyword.isEmpty()) "#$group" else "#$group $keyword", message)
        }
        return findCommand(raw, message)
    }

    private fun legacyRuleAlias(rule: String): String? =
        when (DiceCalculation.toDBC(rule).trim().lowercase()) {
            "coc", "coc7" -> "COC7"
            "dnd", "dnd5e", "5e" -> "5E"
            "dg", "delta", "deltagreen", "三角洲" -> "DG"
            "fate", "命运", "圣杯" -> "FATE"
            "dx", "双重十字" -> "DX"
            "wx", "wuxian", "无限", "无限流" -> "WX"
            "cpr", "cyberpunk", "red", "赛博朋克", "赛博朋克红" -> "CPR"
            else -> null
        }


    private fun helpCommand(param: String): String {
        val topic = param.trim().lowercase()
        if (topic.isEmpty() || topic == "help" || topic == "helpdoc") {
            return HELP_OVERVIEW
        }
        if (topic.contains("find") || topic.contains("查询") || topic.contains("查找")) {
            return functions.findEngine.helpText()
        }
        return HELP_TOPICS[topic] ?: "没有找到「${param.trim()}」的帮助条目。\n$HELP_OVERVIEW"
    }


    private fun runtimeStatus(message: MessageReceived): String {
        val uptimeSeconds = (System.currentTimeMillis() - STARTED_AT) / 1000
        val mode = functions.configStore.ruleMode(
            message.isGroupMsg, message.group, message.triggerId
        )
        val botOff = message.isGroupMsg &&
            functions.configStore.readGroup(message.group, DiceConfigStore.BOT_OFF) == "1"
        val defaultDice = functions.configStore.defaultDiceNumber(
            message.isGroupMsg, message.group, message.triggerId
        )
        return buildString {
            append("运行 ").append(uptimeSeconds).append(" 秒 · 实例 ").append(botId).append('\n')
            append("位置：").append(if (message.isGroupMsg) "群 ${message.group}" else "私聊")
            append(" · 规则：").append(ruleModeLabel(mode)).append('\n')
            append("牌堆：").append(functions.drawManager.fileCount).append(" 文件 / ")
            append(functions.drawManager.deckCount).append(" 堆；词条：")
            append(functions.findEngine.entryCount).append(" 条\n")
            append("本群开关：").append(if (botOff) "关闭" else "开启")
            append(" · 默认骰：d").append(defaultDice)
        }
    }


    private fun debugCommand(param: String, message: MessageReceived): String {
        val action = param.trim().lowercase()
        if (action.isEmpty()) {
            val on = functions.configStore.readGlobal(DiceConfigStore.DEBUG_ON) == "1"
            return "调试开关：${if (on) "开" else "关"}（.debug on/off）"
        }
        if (action != "on" && action != "off") return "用法：.debug on/off"
        if (!message.isMaster) return SETTINGS_DENIED
        functions.configStore.writeGlobal(DiceConfigStore.DEBUG_ON, if (action == "on") "1" else "0")
        return "调试开关已${if (action == "on") "开启" else "关闭"}"
    }


    private fun botCommand(param: String, message: MessageReceived): String {
        val action = param.trim().lowercase()
        val off = message.isGroupMsg &&
            functions.configStore.readGroup(message.group, DiceConfigStore.BOT_OFF) == "1"
        if (action.isEmpty()) {
            return "群星 Stellaris Dice · ${BuildConfig.BUILD_ID}\n" +
                "本群开关：${if (off) "关闭" else "开启"}\n" +
                "用法：.bot on / .bot off（群主/群管理员/master）"
        }
        if (!message.isGroupMsg) return "只能在群内使用 .bot on/off"
        if (action != "on" && action != "off") return "用法：.bot on / .bot off"
        if (!canAdministerGroup(message)) return SETTINGS_DENIED
        functions.configStore.writeGroup(message.group, DiceConfigStore.BOT_OFF, if (action == "off") "1" else "0")
        return if (action == "off") "本群骰子已关闭（.bot on 可重新打开）" else "本群骰子已开启"
    }


    private fun adminState(message: MessageReceived): String {
        val mode = functions.configStore.ruleMode(
            message.isGroupMsg, message.group, message.triggerId
        )
        val off = message.isGroupMsg &&
            functions.configStore.readGroup(message.group, DiceConfigStore.BOT_OFF) == "1"
        val cards = functions.cardManager.getPlayers(message.triggerId).size
        return buildString {
            append("群/私聊：").append(if (message.isGroupMsg) message.group else "私聊").append('\n')
            append("发送者：").append(message.triggerId)
            append(if (message.isMaster) "（master）" else if (message.isGroupOwner) "（群主）"
            else if (message.isGroupAdmin) "（群管理员）" else "").append('\n')
            append("骰子开关：").append(if (off) "关闭" else "开启")
            append(" · 规则：").append(ruleModeLabel(mode)).append('\n')
            append("词条：").append(functions.findEngine.entryCount).append(" 条 · 牌堆：")
            append(functions.drawManager.deckCount).append(" 堆 · 我的卡：").append(cards).append(" 张")
        }
    }


    private fun masterCommand(param: String, message: MessageReceived): String {
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            val masters = masterQqList()
            return "本骰 master：${if (masters.isEmpty()) "未配置" else masters.joinToString("、")}\n" +
                "你是 master：${if (message.isMaster) "是" else "否"}\n" +
                "可用：.master rh on/off"
        }
        if (tokens[0].equals("rh", true)) {
            if (!message.isMaster) return SETTINGS_DENIED
            val action = tokens.getOrNull(1)?.lowercase()
            if (action != "on" && action != "off") return "用法：.master rh on/off"
            functions.configStore.writeGlobal("MASTER_RH", if (action == "on") "1" else "0")
            return "暗骰总开关已${if (action == "on") "开启" else "关闭"}"
        }
        return "用法：.master / .master rh on/off"
    }


    private fun sendToMaster(param: String, message: MessageReceived): String {
        val content = param.trim()
        if (content.isEmpty()) return "用法：.send 内容"
        val master = masterQqList().firstOrNull() ?: return "未配置 master（MASTER_QQ）"
        val displayName = message.nick.ifEmpty { message.groupNick }.ifEmpty { message.triggerId }
        val from = if (message.isGroupMsg) "群${message.group}" else "私聊"
        val text = "来自$from 的$displayName：$content"
        val target = DiceMessageSource(DiceMessageSource.Type.PERSON, master)


        return if (send(text, message, target)) "" else "发送失败（宿主未接出站桥）"
    }


    private fun dismissCommand(message: MessageReceived): String {
        if (!message.isGroupMsg) return "只能在群内使用 .dismiss"
        if (!canAdministerGroup(message)) return SETTINGS_DENIED
        val bridge = intentEvent ?: return "宿主未接出站桥，无法退群"
        bridge.setDismiss(message.group)
        return "已请求退群"
    }


    private fun welcomeCommand(param: String, message: MessageReceived): String {
        if (!message.isGroupMsg) return "只能在群内使用 .welcome"
        val raw = param.trim()
        if (raw.isEmpty()) {
            val on = functions.configStore.readGroup(message.group, DiceConfigStore.WELCOME_ON) == "1"
            val text = welcomeText(message.group)
            return "入群欢迎：${if (on) "开" else "关"}\n文本：$text\n" +
                "用法：.welcome on / .welcome off / .welcome 欢迎{user}"
        }
        val action = raw.lowercase()
        if (action == "on" || action == "off") {
            if (!canAdministerGroup(message)) return SETTINGS_DENIED
            functions.configStore.writeGroup(
                message.group, DiceConfigStore.WELCOME_ON, if (action == "on") "1" else "0"
            )
            return "入群欢迎已${if (action == "on") "开启" else "关闭"}"
        }
        if (!canAdministerGroup(message)) return SETTINGS_DENIED
        functions.configStore.writeGroup(message.group, DiceConfigStore.WELCOME_TEXT, raw)
        functions.configStore.writeGroup(message.group, DiceConfigStore.WELCOME_ON, "1")
        return "入群欢迎已开启，文本已保存"
    }

    private fun welcomeText(group: String): String =
        functions.configStore.readGroup(group, DiceConfigStore.WELCOME_TEXT)
            .ifBlank { DEFAULT_WELCOME_TEXT }


    private fun noLawnoskyCommand(param: String, message: MessageReceived): String {
        if (!message.isGroupMsg) return "只能在群内使用 .nolawnosky"
        val action = param.trim().lowercase()
        if (action.isEmpty()) {
            val on = functions.configStore.readGroup(message.group, DiceConfigStore.NO_LAWNOSKY) == "1"
            return "无法无天模式：${if (on) "开" else "关"}（.nolawnosky on/off）"
        }
        if (action != "on" && action != "off") return "用法：.nolawnosky on/off"
        if (!message.isMaster) return SETTINGS_DENIED
        functions.configStore.writeGroup(
            message.group, DiceConfigStore.NO_LAWNOSKY, if (action == "on") "1" else "0"
        )
        return "无法无天模式已${if (action == "on") "开启" else "关闭"}（黑名单落地后生效）"
    }

    private fun masterQqList(): List<String> =
        functions.configStore.readGlobal(DiceConfigStore.MASTER_QQ)
            .split("\r?\n".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }








    private fun systemCommand(param: String): String {
        val sub = param.trim().substringBefore(' ').lowercase()
        return when (sub) {
            "load" -> {
                val files = functions.drawManager.reload()
                val entries = functions.findEngine.reload()
                functions.pluginLoader.reload()
                "已重新加载：牌堆 $files 个文件（${functions.drawManager.deckCount} 个牌堆）；" +
                    "词条库 $entries 条；插件 ${functions.pluginLoader.pluginCount} 个"
            }

            else -> SYSTEM_USAGE
        }
    }












    private fun madnessRoll(
        kind: Madness.Kind,
        context: RespondContext,
        message: MessageReceived
    ): String {

        if (functions.drawManager.find(kind.deckName) != null) {
            val drawn = try {
                functions.drawManager.draw(message.group, kind.deckName, 1)
            } catch (t: Throwable) {
                null
            }
            val entry = drawn?.firstOrNull()
            if (!entry.isNullOrEmpty()) {
                return entry.decodeXdX()
            }
        }
        val result = Madness.roll(kind)
        if (context.simplify) {
            return "${result.tableSegment} ${result.entry.label}（${result.durationText}）"
        }
        context.template.add("kind", kind.title)
        context.template.add("duration", result.durationText)
        context.template.add("roll", result.tableSegment)
        context.template.add("label", result.entry.label)
        context.template.add("summary", result.entry.summary)
        return context.sentences.sentenceMadness
    }








    private fun ruleModeCommand(command: String, param: String, message: MessageReceived): String {
        val family = when (command) {
            "ww", "w" -> "wx"
            else -> command
        }
        val usage = when (family) {
            "dnd" -> DND_USAGE
            "coc6" -> COC6_USAGE
            "coc" -> COC_USAGE
            "dg" -> DG_USAGE
            "fate" -> FATE_USAGE
            "dx" -> DX_USAGE
            "wx" -> WX_USAGE
            "cpr" -> CPR_USAGE
            else -> COC_USAGE
        }
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val current = functions.configStore.ruleMode(
            message.isGroupMsg, message.group, message.triggerId
        )
        if (tokens.isEmpty()) {
            return "当前规则模式：${ruleModeLabel(current)}"
        }

        var action = tokens[0].lowercase()
        var value = tokens.getOrNull(1)?.lowercase()
        if (action == "mode") {
            action = value ?: return usage
            value = tokens.getOrNull(2)?.lowercase()
        }

        val target = when {
            action in MODE_OFF_WORDS -> RuleMode.NONE
            family == "dnd" && action == "5e" -> RuleMode.DND5E
            family == "dnd" && action == "3r" -> RuleMode.DND3R
            family == "dnd" && action in MODE_ON_WORDS -> when (value) {
                "3r" -> RuleMode.DND3R
                "5e", null -> RuleMode.DND5E
                else -> return usage
            }

            action in MODE_ON_WORDS -> when (family) {
                "coc" -> RuleMode.COC
                "coc6" -> RuleMode.COC6
                "dg" -> RuleMode.DG
                "fate" -> RuleMode.FATE
                "dx" -> RuleMode.DX
                "wx" -> RuleMode.WX
                "cpr" -> RuleMode.CPR
                else -> return usage
            }

            else -> return usage
        }
        if (!canChangeGroupSettings(message)) {
            return SETTINGS_DENIED
        }
        functions.configStore.writeRuleMode(
            message.isGroupMsg, message.group, message.triggerId, target
        )
        return if (target == RuleMode.NONE) {
            "规则模式已关闭（按 CoC 处理）"
        } else {
            "规则模式已设为：${ruleModeLabel(target)}"
        }
    }

    private fun replyCommand(param: String, message: MessageReceived): String {
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return REPLY_USAGE
        return when (tokens[0].lowercase()) {
            "on", "off", "开启", "关闭" -> {
                val enable = tokens[0].lowercase() in setOf("on", "开启")
                val thisGroup = tokens.getOrNull(1)?.equals("this", ignoreCase = true) == true
                if (thisGroup) {
                    if (!message.isGroupMsg) {
                        return "私聊没有本群开关；请用 .reply on / .reply off"
                    }
                    if (!canAdministerGroup(message)) return SETTINGS_DENIED
                    functions.configStore.writeGroup(
                        message.group,
                        DiceConfigStore.REPLY_ON,
                        if (enable) "1" else "0"
                    )
                    return "本群关键词自动回复已${if (enable) "开启" else "关闭"}"
                }
                if (!message.isMaster) return SETTINGS_DENIED
                functions.configStore.writeGlobal(
                    DiceConfigStore.REPLY_ON,
                    if (enable) "1" else "0"
                )
                return "全局关键词自动回复已${if (enable) "开启" else "关闭"}"
            }

            "list", "列表" -> {
                val entries = functions.keywordManager.list()
                if (entries.isEmpty()) return "关键词库为空"
                val shown = entries.take(MAX_REPLY_LIST).joinToString("\n") {
                    "${it.uuid} [${it.mode}] ${it.keyword} → ${it.content.replace("\n", " ").take(40)}"
                }
                return if (entries.size > MAX_REPLY_LIST) {
                    "$shown\n…共 ${entries.size} 条"
                } else {
                    shown
                }
            }

            else -> REPLY_USAGE
        }
    }

    private fun backupCommand(param: String, message: MessageReceived): String {
        if (!message.isMaster) return MASTER_ONLY
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            val last = functions.recoveryManager.listBackups().firstOrNull()
            return "备份：自动备份=${if (functions.configStore.readGlobal(BACKUP_AUTO) == "1") "开" else "关"}；" +
                "最近备份=${last?.name ?: "无"}\n$BACKUP_USAGE"
        }
        return when (tokens[0].lowercase()) {
            "make", "备份" -> {
                val file = functions.recoveryManager.makeBackup("backup")
                if (file == null) "备份失败" else "已备份到：${file.name}"
            }

            "list", "列表" -> {
                val files = functions.recoveryManager.listBackups()
                if (files.isEmpty()) "没有备份文件" else files.take(MAX_BACKUP_LIST).joinToString("\n") {
                    "${it.name}（${it.length() / 1024} KB）"
                }
            }

            "del", "删除" -> {
                val name = tokens.getOrNull(1) ?: return BACKUP_USAGE
                if (functions.recoveryManager.deleteBackup(name)) "已删除备份 $name" else "找不到备份 $name"
            }

            "recovery", "恢复" -> {
                val name = tokens.getOrNull(1)
                val source = if (name == null) {
                    functions.recoveryManager.listBackups().firstOrNull()
                } else {
                    functions.recoveryManager.findBackup(name)
                } ?: return "没有可用的备份文件"
                val result = functions.recoveryManager.import(source)
                if (result.success) "${result.message}\n请重启骰娘使数据完全生效。" else result.message
            }

            "auto", "自动" -> {
                val action = tokens.getOrNull(1)?.lowercase() ?: return BACKUP_USAGE
                when (action) {
                    "on", "开", "开启", "1" -> {
                        functions.configStore.writeGlobal(BACKUP_AUTO, "1")
                        "自动备份已开启（每 10 分钟一次）"
                    }

                    "off", "关", "关闭", "0" -> {
                        functions.configStore.writeGlobal(BACKUP_AUTO, "0")
                        "自动备份已关闭"
                    }

                    else -> BACKUP_USAGE
                }
            }

            "30day", "30天" -> {
                val deleted = functions.recoveryManager.deleteOlderThan(30)
                "已清理 $deleted 个超过 30 天的备份"
            }

            else -> BACKUP_USAGE
        }
    }

    private fun recoveryCommand(param: String, message: MessageReceived): String {
        if (!message.isMaster) return MASTER_ONLY
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return RECOVERY_USAGE
        return when (tokens[0].lowercase()) {
            "list", "列表" -> {
                val files = functions.recoveryManager.listBackups()
                if (files.isEmpty()) "没有迁移包" else files.take(MAX_BACKUP_LIST).joinToString("\n") {
                    "${it.name}（${it.length() / 1024} KB）"
                }
            }

            "export", "导出" -> {
                val file = functions.recoveryManager.export()
                if (file == null) "导出失败" else "已导出到：backup/${file.name}"
            }

            "import", "导入" -> {
                val path = tokens.getOrNull(1) ?: return RECOVERY_USAGE
                val direct = File(path)
                val source = when {
                    direct.isFile -> direct
                    else -> functions.recoveryManager.findBackup(path)
                } ?: return "找不到迁移包：$path"
                val result = functions.recoveryManager.import(source)
                if (result.success) "${result.message}\n请重启骰娘使数据完全生效。" else result.message
            }

            "overwrite", "覆盖" -> {
                val botId = tokens.getOrNull(1) ?: return RECOVERY_USAGE
                val result = functions.recoveryManager.overwriteFrom(botId)
                if (result.success) "${result.message}\n请重启骰娘使数据完全生效。" else result.message
            }

            else -> RECOVERY_USAGE
        }
    }

    private fun blackCommand(param: String, message: MessageReceived): String {
        if (!message.isMaster) return MASTER_ONLY
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return BLACK_USAGE
        if (tokens[0].equals("cloud", ignoreCase = true) || tokens[0] == "云" || tokens[0] == "云端") {
            return "云端黑名单在群星基准模版中未启用（无网络路径）；本地黑名单请用 .black。"
        }
        return when (tokens[0].lowercase()) {
            "add", "添加" -> {
                if (tokens.size < 3) return BLACK_USAGE
                val type = when (tokens[1].lowercase()) {
                    "user", "用户", "人" -> stellaris.dice.diceFunction.ops.BlacklistStore.Type.USER
                    "group", "群", "群聊" -> stellaris.dice.diceFunction.ops.BlacklistStore.Type.GROUP
                    else -> return BLACK_USAGE
                }
                val reason = tokens.drop(3).joinToString(" ").ifEmpty { "未填写" }
                val entry = functions.blacklistStore.add(type, tokens[2], reason) ?: return "号码无效"
                "已加入本地黑名单：${entry.uuid}（${type.name} ${entry.number}）"
            }

            "rm", "del", "remove", "删除" -> {
                val uuid = tokens.getOrNull(1)?.toLongOrNull() ?: return BLACK_USAGE
                if (functions.blacklistStore.remove(uuid)) "已移除黑名单条目 $uuid" else "没有 uuid=$uuid 的条目"
            }

            "find", "查找" -> {
                val number = tokens.getOrNull(1) ?: return BLACK_USAGE
                val hits = functions.blacklistStore.find(number)
                if (hits.isEmpty()) {
                    "没有与 $number 相关的黑名单条目"
                } else {
                    hits.joinToString("\n") {
                        "${it.uuid} [${it.type.name}] ${it.number} ${it.reason}"
                    }
                }
            }

            "detail", "详情" -> {
                val uuid = tokens.getOrNull(1)?.toLongOrNull() ?: return BLACK_USAGE
                val entry = functions.blacklistStore.find(uuid)
                    ?: return "没有 uuid=$uuid 的条目"
                "uuid：${entry.uuid}\n类型：${entry.type.name}\n号码：${entry.number}\n" +
                    "理由：${entry.reason}\n时间：${java.util.Date(entry.createdAt)}"
            }

            "count", "数量" -> "本地黑名单共 ${functions.blacklistStore.count()} 条"

            "list", "列表" -> {
                val entries = functions.blacklistStore.list()
                if (entries.isEmpty()) {
                    "本地黑名单为空"
                } else {
                    entries.take(MAX_BLACK_LIST).joinToString("\n") {
                        "${it.uuid} [${it.type.name}] ${it.number} ${it.reason}"
                    } + if (entries.size > MAX_BLACK_LIST) "\n…共 ${entries.size} 条" else ""
                }
            }

            else -> BLACK_USAGE
        }
    }

    private fun guardCommand(param: String, message: MessageReceived): String {
        if (!message.isMaster) return MASTER_ONLY
        val status = "防刷屏：${if (functions.guardStore.enabled) "开启" else "关闭"}；" +
            "窗口 ${functions.guardStore.windowSeconds} 秒；阈值 ${functions.guardStore.maxMessages} 条；" +
            "提示：${functions.guardStore.warning.ifEmpty { "（无）" }}"
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return status
        return when (tokens[0].lowercase()) {
            "during", "窗口" -> {
                val seconds = tokens.getOrNull(1)?.toIntOrNull() ?: return GUARD_USAGE
                functions.guardStore.setWindowSeconds(seconds)
                "防刷屏窗口已设为 ${functions.guardStore.windowSeconds} 秒"
            }

            "times", "次数" -> {
                val count = tokens.getOrNull(1)?.toIntOrNull() ?: return GUARD_USAGE
                functions.guardStore.setMaxMessages(count)
                "防刷屏阈值已设为 ${functions.guardStore.maxMessages} 条"
            }

            "spam", "开关" -> {
                val action = tokens.getOrNull(1)?.lowercase() ?: return GUARD_USAGE
                when (action) {
                    "on", "开", "开启", "1" -> {
                        functions.guardStore.setEnabled(true)
                        functions.guardStore.clearRuntime()
                        "防刷屏已开启：${status}"
                    }

                    "off", "关", "关闭", "0" -> {
                        functions.guardStore.setEnabled(false)
                        functions.guardStore.clearRuntime()
                        "防刷屏已关闭"
                    }

                    else -> GUARD_USAGE
                }
            }

            "warning", "提示" -> {
                functions.guardStore.setWarning(tokens.drop(1).joinToString(" "))
                "防刷屏提示已更新：${functions.guardStore.warning.ifEmpty { "（无）" }}"
            }

            else -> GUARD_USAGE
        }
    }

    private fun groupCommand(param: String, message: MessageReceived): String {
        if (!message.isGroupMsg) return "请在群内使用 .group"
        if (!canAdministerGroup(message)) return SETTINGS_DENIED
        val tokens = param.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return "本群设置：jrrp=${if (groupFeatureDisabled(DiceConfigStore.DISABLE_JRRP, message)) "禁用" else "开启"}；" +
                "draw=${if (groupFeatureDisabled(DiceConfigStore.DISABLE_DRAW, message)) "禁用" else "开启"}；" +
                "help=${if (groupFeatureDisabled(DiceConfigStore.DISABLE_HELP, message)) "禁用" else "开启"}"
        }
        return when (tokens[0].lowercase()) {
            "card", "名片" -> {
                val at = message.atTargets.firstOrNull()
                val numeric = tokens.getOrNull(1)?.takeIf { it.all(Char::isDigit) }
                val target = at ?: numeric ?: return "用法：.group card @人 名片文本"
                val name = if (at != null) {
                    tokens.drop(1).joinToString(" ")
                } else {
                    tokens.drop(2).joinToString(" ")
                }
                if (name.isEmpty()) return "用法：.group card @人 名片文本"
                val bridge = intentEvent ?: return "宿主未接入，无法修改群名片"
                if (!bridge.isSelfGroupOperator(message.group)) {
                    return "骰娘不是本群管理员，无法修改群名片"
                }
                if (bridge.setGroupNumberName(name, target, message.group)) {
                    "已修改 $target 的群名片"
                } else {
                    "群名片修改失败"
                }
            }

            "+禁用jrrp" -> setGroupFeature(DiceConfigStore.DISABLE_JRRP, true, message)
            "-禁用jrrp" -> setGroupFeature(DiceConfigStore.DISABLE_JRRP, false, message)
            "+禁用draw" -> setGroupFeature(DiceConfigStore.DISABLE_DRAW, true, message)
            "-禁用draw" -> setGroupFeature(DiceConfigStore.DISABLE_DRAW, false, message)
            "+禁用help" -> setGroupFeature(DiceConfigStore.DISABLE_HELP, true, message)
            "-禁用help" -> setGroupFeature(DiceConfigStore.DISABLE_HELP, false, message)
            "+停用指令" -> {
                functions.configStore.writeGroup(message.group, DiceConfigStore.BOT_OFF, "1")
                "本群骰娘已停用"
            }

            "-停用指令" -> {
                functions.configStore.writeGroup(message.group, DiceConfigStore.BOT_OFF, "0")
                "本群骰娘已恢复"
            }

            "+禁用回复" -> {
                functions.configStore.writeGroup(message.group, DiceConfigStore.REPLY_ON, "0")
                "本群关键词自动回复已关闭"
            }

            "-禁用回复" -> {
                functions.configStore.writeGroup(message.group, DiceConfigStore.REPLY_ON, "1")
                "本群关键词自动回复已开启"
            }

            "+禁用ob" -> {
                functions.configStore.writeGroup(message.group, DiceConfigStore.OB_ON, "false")
                "本群 ob 已关闭"
            }

            "-禁用ob" -> {
                functions.configStore.writeGroup(message.group, DiceConfigStore.OB_ON, "true")
                "本群 ob 已开启"
            }

            "-词条", "+词条" -> updateSuppressedKeyword(
                tokens.drop(1).joinToString(" ").trim(),
                tokens[0].startsWith("+"),
                message
            )

            else -> GROUP_USAGE
        }
    }

    /**
     * `.group -词条 <关键词>` 只在本群屏蔽该词条（不动全局词条库），
     * `.group +词条 <关键词>` 取消屏蔽。空参数列出本群当前屏蔽项。
     */
    private fun updateSuppressedKeyword(
        keyword: String,
        remove: Boolean,
        message: MessageReceived
    ): String {
        val suppressed = suppressedKeywords(message.group)
        if (keyword.isEmpty()) {
            return if (suppressed.isEmpty()) {
                "本群没有屏蔽任何词条。用法：.group -词条 <关键词>"
            } else {
                "本群已屏蔽词条：${suppressed.joinToString("、")}"
            }
        }
        val changed = if (remove) suppressed.remove(keyword) else suppressed.add(keyword)
        if (!changed) {
            return if (remove) "本群没有屏蔽「$keyword」" else "「$keyword」已在本群屏蔽列表中"
        }
        val array = org.json.JSONArray()
        suppressed.forEach { array.put(it) }
        functions.configStore.writeGroup(
            message.group,
            DiceConfigStore.DISABLE_KEYWORDS,
            array.toString()
        )
        return if (remove) "已取消屏蔽词条「$keyword」" else "已在本群屏蔽词条「$keyword」"
    }

    private fun suppressedKeywords(group: String): MutableSet<String> {
        val raw = functions.configStore.readGroup(group, DiceConfigStore.DISABLE_KEYWORDS)
        if (raw.isEmpty()) return LinkedHashSet()
        return try {
            val array = org.json.JSONArray(raw)
            LinkedHashSet((0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) })
        } catch (t: Throwable) {
            LinkedHashSet()
        }
    }

    private fun setGroupFeature(key: String, disabled: Boolean, message: MessageReceived): String {
        functions.configStore.writeGroup(message.group, key, if (disabled) "1" else "0")
        return if (disabled) "已禁用 $key" else "已开启 $key"
    }

    private fun groupFeatureDisabled(key: String, message: MessageReceived): Boolean =
        message.isGroupMsg && functions.configStore.readGroup(message.group, key) == "1"

    /** `.rb`/`.rp`：普通骰点通道 + b/p 骰型，不是检定。 */
    private fun bonusPenaltyRoll(
        command: String,
        param: String,
        context: RespondContext,
        message: MessageReceived
    ): String {
        val profile = stellaris.dice.diceFunction.responder.RuleProfile.of(context.ruleMode)
        val bonus = command == "rb"
        profile.bonusPenaltyHint(bonus)?.let { return it }
        val expression = (if (bonus) "b" else "p") + param.trim()
        val rollContext = context.withParam(expression).withRollRecorder { value ->
            functions.hiyStore.record(message.triggerId, command, value)
        }
        return context.template.buildAll(
            stellaris.dice.diceFunction.responder.DiceResponder.r(rollContext, RollRules.R)
        )
    }


    private fun ruleModeLabel(mode: RuleMode): String = when (mode) {
        RuleMode.NONE -> "未设置（按 CoC 处理）"
        RuleMode.COC -> "CoC7"
        RuleMode.COC6 -> "CoC6"
        RuleMode.DND5E -> "DND 5e"
        RuleMode.DND3R -> "DND 3r"
        RuleMode.DG -> "Delta Green"
        RuleMode.FATE -> "FATE"
        RuleMode.DX -> "双十字"
        RuleMode.WX -> "无限流"
        RuleMode.CPR -> "Cyberpunk RED"
    }








    private fun canAdministerGroup(message: MessageReceived): Boolean =
        message.isMaster || message.isGroupOwner || message.isGroupAdmin






    private fun canChangeGroupSettings(message: MessageReceived): Boolean =
        !message.isGroupMsg || canAdministerGroup(message)







    private fun send(content: String, message: MessageReceived, target: DiceMessageSource): Boolean {
        val text = content.trim()
        if (text.isEmpty()) {
            return false
        }
        val bridge = intentEvent ?: return false
        bridge.sendMessage(text, message, target)

        if (target.isGroup()) {
            functions.logManager.appendBot(target.id, botId, text)
        }
        return true
    }








    override fun eventNewMemberJoin(message: MessageReceived) {
        if (!message.isGroupMsg) return
        if (functions.configStore.readGroup(message.group, DiceConfigStore.WELCOME_ON) != "1") {
            return
        }
        val displayName = message.nick.ifEmpty { message.groupNick }.ifEmpty { message.triggerId }
        val text = welcomeText(message.group)
            .replace("{user}", displayName)
            .replace("{player}", displayName)
            .replace("{group}", message.group)
        send(text, message, DiceMessageSource(DiceMessageSource.Type.GROUP, message.group))
    }

    override fun eventNudge(target: String, message: MessageReceived) {
        val text = functions.configStore.DICE_NUDGE_QQ_SAY
            .ifEmpty { "别戳啦，发送 .help 查看用法" }
            .replace("{player}", message.nick)
            .replace("{user}", message.nick)
        send(text, message, message.identity)
    }

    override fun eventJoinNewGroup(groupId: Long, operator: Long) {
        val group = groupId.toString()
        val text = functions.configStore.readGlobal(JOIN_GROUP_SAY)
            .ifEmpty { "感谢邀请，发送 .help 查看用法" }
        val context = MessageReceived.buildMessageEvent(
            instance = functions,
            content = "",
            group = group,
            triggerId = botId,
            nick = "",
            isGroupMsg = true
        )
        send(text, context, DiceMessageSource(DiceMessageSource.Type.GROUP, group))
    }

    override fun eventKick(operator: Long, groupId: Long) {
        autoBlacklist(operator, "踢出骰娘")
    }

    override fun eventMute(operator: Long, groupId: Long) {
        autoBlacklist(operator, "禁言骰娘")
    }

    override fun eventAddFriend(userId: Long, callback: DiceRequestCallback) {
        if (functions.configStore.readGlobal(AUTO_AGREE_FRIEND).ifEmpty { "1" } == "1") {
            callback.agree()
        } else {
            callback.ignore()
        }
    }

    override fun eventInviteInGroup(userId: Long, groupId: Long, callback: DiceRequestCallback) {
        if (functions.configStore.readGlobal(AUTO_AGREE_GROUP).ifEmpty { "0" } == "1") {
            callback.agree()
        } else {
            callback.ignore()
        }
    }

    private fun autoBlacklist(operator: Long, reason: String) {
        if (functions.configStore.readGlobal(DiceConfigStore.NO_LAWNOSKY) == "1") return
        if (operator <= 0) return
        functions.blacklistStore.add(
            stellaris.dice.diceFunction.ops.BlacklistStore.Type.USER,
            operator.toString(),
            reason
        )
    }

    private companion object {











        private val X_VARIANTS = mapOf(
            "rx" to "r",
            "rhx" to "rh",
            "wwx" to "ww",
            "rax" to "ra",
            "rcx" to "rc",
            "rabx" to "rab",
            "rapx" to "rap",
            "rcbx" to "rcb",
            "rcpx" to "rcp",
            "rahx" to "rah",
            "rchx" to "rch",
            "scx" to "sc",
            "enx" to "en"
        )


        private val ROLL_RULES = mapOf(
            "r" to RollRules.R,
            "w" to RollRules.WW_SIMPLE,
            "ww" to RollRules.WW,

            "www" to RollRules.WW,
            "dx" to RollRules.DX,
            "dxx" to RollRules.DX_WRAPPED_LINE,
            "fate" to RollRules.FATE,

            "rh" to RollRules.R,

            "rd" to RollRules.R
        )







        private val CHECK_RULES = mapOf(
            "ra" to RollRules.RA,
            "rc" to RollRules.RC,
            "rab" to RollRules.RAB,
            "rap" to RollRules.RAP,
            "rcb" to RollRules.RCB,
            "rcp" to RollRules.RCP,
            "rah" to RollRules.RAH,
            "rch" to RollRules.RCH
        )






        private val OPPOSED_RULES = mapOf(
            "rav" to RollRules.RAV,
            "rcv" to RollRules.RCV
        )









        private val MADNESS_RULES = mapOf(
            "ti" to Madness.Kind.TEMPORARY,
            "li" to Madness.Kind.LONG_TERM
        )







        private val HIDDEN_COMMANDS = setOf("rh", "rah", "rch")








        private val CARD_EDIT_COMMANDS = setOf("st", "hp")







        private const val PC_USAGE =
            "用法：.pc new <卡名> [规则] / list / show [卡名或阈值] / grp [clr] / tag [卡名] / " +
                "cpy [源] <目标> / rule <规则> / nn <新名> / del [卡名] / clr / " +
                "build / redo / stat"


        private const val DRAW_USAGE =
            "用法：.draw <牌堆名> [张数]（可先用 .deck set <牌堆名> 设默认牌堆）"


        private const val DECK_USAGE =
            "用法：.deck show / .deck set <牌堆名|1~100> / .deck new A|B|C / .deck reset [牌堆名]"


        private const val DECK_NEW_USAGE =
            "用法：.deck new 条目1|条目2|…（至少两条；临时牌堆，不落盘）"


        private const val MAX_INLINE_NUMERIC_DECK = 100


        private const val DRAW_HELP =
            "用法：.draw <牌堆名> [张数，最多 5] / .draw list / .draw search <关键词>；" +
                "裸 .draw 抽默认牌堆；裸 .deck 等同于 .draw；" +
                "临时牌堆：.deck new A|B|C（不落盘）"


        private const val MAX_DRAW_COUNT = 5


        private const val MIN_DICE_FACES = 1


        private const val MAX_DICE_FACES = 9999

        private const val SET_USAGE = "用法：.set <骰面数>（1~9999；不带参数=查看当前值）"


        private const val OPPOSED_USAGE =
            "对抗检定（先手登记、后手再发一次即结算）：.rav 技能/数值\n" +
                "例：.rav 侦查 / .rav 50 / .rav 困难侦查 / .rav 卡名:侦查+10（.rcv 为标准规则）"


        private const val OPPOSED_GROUP_ONLY = "本命令暂时只能群聊使用"


        private const val INITIATIVE_DICE_FACES = 20

        private const val INITIATIVE_GROUP_ONLY = "ri 指令仅限群聊使用"

        private const val INITIATIVE_GROUP_ONLY_INIT = "init 指令仅限群聊使用"

        private val INITIATIVE_TOO_MANY =
            "先攻列表数量不得超过${DiceConstant.MAX_FIRSTLY_ATTACKING_VALUE_COUNT}个，" +
                "请使用 init clr 进行清除"

        private const val INITIATIVE_EMPTY_END =
            "先攻列表还是空的，先骰先攻吧：ri 名字（多人 ri 名字A/名字B，加值 ri 名字+3）"

        private const val INITIATIVE_SET_USAGE =
            "使用范例：init set 名字 数值（如 init set 张果老 50）"

        private const val INITIATIVE_BUFF_USAGE =
            "使用范例：init buff 名字 +状态 / -状态 / 状态"

        private const val INITIATIVE_HP_USAGE =
            "先攻模式下用法：hp 名字 -2d6 / hp 名字 12 / hp 名字 12/12"

        private const val INITIATIVE_HP_NO_SELF = "未录入先攻数据"


        private const val WHO_MIN_NAMES = 2

        private const val WHO_USAGE = "用法：.who A B C…（至少两个名字，随机洗牌）"


        private const val HIDDEN_GROUP_ONLY = "暗骰仅在群聊中有效"


        private const val DEFAULT_PSY_SKILL = "心理学"

        private const val PSY_GROUP_ONLY = "psy 指令仅限群聊使用"

        private val PSY_USAGE = "用法：.psy @玩家 [技能名]（技能名留空 = 心理学）"

        private const val OB_DENIED = "没有权限"


        private const val LOG_GROUP_ONLY = "log 仅限群聊使用"

        private val LOG_USAGE = """
            请输入：
            log on [文件名?] 开始记录（省略名字 = 按时间自动命名）
            log off 暂停记录（文件保留，可用 log on 同名继续）
            log end [文件名?] 结束并上传为群文件
            log list 查看日志列表
            log rm 文件名 删除单份日志
            log clr 清空本群全部日志
            单群最多 ${DiceConstant.MAX_LOG_COUNT} 份；私聊不支持 log
        """.trimIndent()

        private val OB_USAGE = """
            旁观模式：.ob (exit/list/clr/on/off)
            .ob 加入旁观，可以看到本群暗骰结果
            .ob exit 退出旁观模式
            .ob list 查看群内旁观者
            .ob clr 清除所有旁观者（管理员）
            .ob on 全群允许旁观模式（管理员）
            .ob off 禁用旁观模式（管理员）
            暗骰与旁观仅在群聊中有效；一个群最多 10 个 OB 位
        """.trimIndent()


        private val INITIATIVE_SET_PATTERN = Regex("^(.+?)\\s+([+-]?\\d+)$")


        private val INITIATIVE_HP_SET_PATTERN = Regex("^(\\d+)/(\\d+)$")


        private val PURE_SIGNED_INT = Regex("^[+-]?\\d+$")

        private val INITIATIVE_WHITESPACE = Regex("\\s+")


        private val INITIATIVE_HELP_EMPTY = """
            空的先攻点列表。支持命令：
            骰先攻：ri 名字
            强制修改：init set 名字 数值
            强制清空：init clr
            查找并删除：init del 关键词
            设置状态：init buff 关键词 状态名
            下一个战士：init end
            先攻模式下血量：hp 关键词 -2d6 / hp 关键词 12/12
        """.trimIndent()


        private const val NAME_CONTENT_MISSING =
            "随机名字库尚未随包（需把 content/draw 播种到实例目录）；用法：.name [cn/jp/en] [数量]"

        private const val DEFAULT_NAME_COUNT = 10
        private const val MAX_NAME_COUNT = 20

        private const val SYSTEM_USAGE = "用法：.system load（重新加载牌堆文件）"

        private const val REPLY_USAGE =
            "用法：.reply on/off（全局，master） / .reply on/off this（本群，群管理） / .reply list"

        private const val MAX_REPLY_LIST = 50


        private const val SETTINGS_DENIED = "只有群主、群管理员或 master 能改本群设置"

        private const val MASTER_ONLY = "本命令只能 master 使用"

        private const val BLACK_USAGE =
            "用法：.black add user|group <号码> [理由] / rm <uuid> / find <号码> / " +
                "detail <uuid> / count / list（仅 master）"

        private const val GUARD_USAGE =
            "用法：.guard during <秒> / times <条数> / spam on|off / warning <文本>（仅 master）"

        private const val GROUP_USAGE =
            "用法：.group card @人 名片 / ±禁用jrrp|draw|help / ±停用指令 / ±禁用回复 / ±禁用ob"

        private const val MAX_BLACK_LIST = 50

        private const val MAX_BACKUP_LIST = 30

        private const val BACKUP_AUTO = "BACKUP_AUTO"

        private const val BACKUP_USAGE =
            "用法：.backup make / list / recovery [文件名] / del <文件名> / auto on|off / 30day（仅 master）"

        private const val RECOVERY_USAGE =
            "用法：.recovery list / export / import <文件名或路径> / overwrite <骰QQ>（仅 master；跨目录迁移用 export → import）"

        private const val JOIN_GROUP_SAY = "JOIN_GROUP_SAY"
        private const val AUTO_AGREE_FRIEND = "AUTO_AGREE_FRIEND"
        private const val AUTO_AGREE_GROUP = "AUTO_AGREE_GROUP"

        private const val DND_USAGE =
            "用法：.dnd mode on 5e / .dnd mode on 3r / .dnd mode off（不带参数=查看当前模式）"

        private const val COC_USAGE = "用法：.coc mode on / .coc mode off（不带参数=查看当前模式）"

        private const val COC6_USAGE = "用法：.coc6 mode on / .coc6 mode off（不带参数=查看当前模式）"

        private const val DG_USAGE =
            "用法：.dg mode on / .dg mode off / .setdg on|off（不带参数=查看当前模式）"

        private const val FATE_USAGE =
            "用法：.fate mode on / .fate mode off / .setfate on|off（不带参数=查看当前模式）"

        private const val DX_USAGE =
            "用法：.dx mode on / .dx mode off / .setdx on|off（不带参数=查看当前模式）"

        private const val WX_USAGE =
            "用法：.wx mode on / .wx mode off / .setwx on|off（不带参数=查看当前模式）"

        private const val CPR_USAGE =
            "用法：.cpr mode on / .cpr mode off / .setcpr on|off（不带参数=查看当前模式）"

        private val MODE_OFF_WORDS = setOf("off", "关", "关闭", "0", "n", "false")
        private val MODE_ON_WORDS = setOf("on", "开", "开启", "1", "y", "true")

        private val MODE_FAMILIES = setOf("coc", "coc6", "dnd", "dg", "fate", "dx", "wx", "ww", "w", "cpr")

        private val MODE_SET_ALIASES = mapOf(
            "setdnd" to "dnd",
            "setdg" to "dg",
            "setfate" to "fate",
            "setdx" to "dx",
            "setwx" to "wx",
            "setcpr" to "cpr"
        )

        private val STARTED_AT = System.currentTimeMillis()

        private const val DEFAULT_WELCOME_TEXT = "欢迎 {user} 加入本群！"

        private const val ABOUT_TEXT =
            "群星 · Stellaris Dice\n" +
                "构建：${BuildConfig.BUILD_ID}\n" +
                "骰核：本仓库重写的 Kotlin 内核（BSD-3-Clause）\n" +
                "宿主：PC（Swing / 控制台）与 Android\n" +
                "指令帮助：.help；词条检索：.find"

        private val HELP_OVERVIEW = """
            群星指令帮助
            骰点与检定：.r .ra .rc .rab .rap .rcb .rcp .rah .rch .rh .sc .range
            牌堆：.draw .deck       人物卡：.pc .st .nn .en .set .name
            先攻/对抗：.ri .init .rav .rcv
            跑团辅助：.log .ob .psy .who .jrrp .hiy
            查询与帮助：.find .查询 .查找 .rules .rule
            群/配置管理：.bot .group .admin .welcome .reply .send .dismiss
            用 .help find 查看词条检索用法；词条库随包在 content/find。
        """.trimIndent()

        private val HELP_TOPICS = mapOf(
            "r" to "`.r [骰式] [理由]`：普通骰，裸 .r 用默认骰面；.r 50 = 1d50。",
            "ra" to "`.ra [困难/极难] 技能名或数值 [+修正] [理由]`：COC 房规检定。",
            "rc" to "`.rc [困难/极难] 技能名或数值 [+修正] [理由]`：COC 规则书检定。",
            "sc" to "`.sc 成功损失/失败损失 [临时理智]`：理智检定并写回卡。",
            "en" to "`.en 技能名`：成长检定，高于技能值才成长。",
            "pc" to "`.pc new/list/show/grp/tag/cpy/rule/nn/del/clr/build/redo/stat`：角色卡管理。",
            "st" to "`.st 力量65`、`.st 侦查+10`、`.st 体力3d6`、`.st del 侦查`、`.st clr`。",
            "draw" to "`.draw [牌堆名] [张数≤5]`；`.draw help/list/search`。",
            "deck" to "`.deck show/set/reset/new`；裸 .deck = .draw。",
            "ri" to "`.ri [名字] [+3|d8]`、`.ri 哥布林/史莱姆`、`.ri 3#哥布林`。",
            "init" to "`.init set/buff/del/clr/end`：先攻列表管理。",
            "log" to "`.log on [名字]/off/end/list/rm/clr`：群内跑团日志。",
            "dnd" to "`.dnd mode on 5e/3r/off`：DND 规则模式。",
            "coc" to "`.coc mode on/off`：COC 规则模式。",
            "coc6" to "`.coc6 mode on/off`：COC6 规则模式。",
            "dg" to "`.dg mode on/off` / `.setdg on|off`：三角洲模式；b/p 折算为目标值 ±20% / ±40%。",
            "fate" to "`.fate mode on/off` / `.setfate on|off`：FATE 模式；默认 4dF，b/p 为援引 ±2。",
            "dx" to "`.dx mode on/off` / `.setdx on|off`：双重十字模式；默认 1c10。",
            "wx" to "`.wx mode on/off` / `.setwx on|off`：无限流模式；默认 1a10。",
            "cpr" to "`.cpr mode on/off` / `.setcpr on|off`：赛博朋克红模式；默认 1d10，10/1 暴击链。",
            "sn" to "`.sn coc|dnd|expr|on|off|set`：由默认插件 sn.toml 提供的自动群名片。",
            "black" to "`.black add user|group <号码> [理由]` / rm / find / detail / count / list（master）。",
            "guard" to "`.guard during <秒> / times <条数> / spam on|off / warning <文本>`（master）。",
            "group" to "`.group card @人 名片` / ±禁用jrrp|draw|help / ±停用指令 / ±禁用回复 / ±禁用ob。",
            "backup" to "`.backup make/list/recovery/del/auto/30day`（master）。",
            "recovery" to "`.recovery list/export/import <文件或路径>`（master）。",
            "reply" to "`.reply on|off [this]` / `.reply list`：关键词自动回复开关与列表。"
        )














        private val EXTRA_COMMANDS = listOf("blackcloud")






        private fun isCoreCommand(command: String): Boolean =
            EXTRA_COMMANDS.any { it.equals(command, ignoreCase = true) } ||
                DiceCommandDecoder.isBuiltInCommand(command)

        private fun stripLeadingTrigger(content: String): String =
            content.removePrefix(".").removePrefix("。")
    }
}
