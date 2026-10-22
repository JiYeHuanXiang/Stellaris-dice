package stellaris.dice.diceFunction

import org.json.JSONObject
import stellaris.dice.diceFunction.responder.DefaultSentences
import stellaris.dice.diceFunction.responder.RollSentences
import java.io.File




























class DiceConfigStore(private val rootPath: String) : DiceConfig, RollSentences {

    private val globalValues = HashMap<String, String>()
    private val groupValues = LinkedHashMap<String, HashMap<String, String>>()
    private val personValues = LinkedHashMap<String, HashMap<String, String>>()

    private val storeFile: File
        get() = File(rootPath, "${DiceConstant.SAVE_PATH_DATA}/$CONFIG_FILE_NAME")


    private val globalDefaults = HashMap<String, String>().apply {
        this[SENTENCE_ROLL] = DefaultSentences.sentenceRoll
        this[SENTENCE_ROLL_WITH_REASON] = DefaultSentences.sentenceRollWithReason
        this[SENTENCE_ILLEGAL] = DefaultSentences.sentenceIllegal
        this[SENTENCE_ILLEGAL_TOO_MUCH] = DefaultSentences.sentenceIllegalTooMuch
        this[SENTENCE_HIDDEN_ROLL_HINT] = DefaultSentences.sentenceHiddenRollHint
        this[SENTENCE_HIDDEN_ROLL] = DefaultSentences.sentenceHiddenRoll
        this[SENTENCE_RABP] = DefaultSentences.sentenceCheck
        this[SENTENCE_RABP_WITH_REASON] = DefaultSentences.sentenceCheckWithReason
        this[SENTENCE_RABP_MUTI] = DefaultSentences.sentenceCheckMultiple
        this[SENTENCE_RABP_MUTI_WITH_REASON] = DefaultSentences.sentenceCheckMultipleWithReason
        this[SENTENCE_CARD_UPDATED] = DefaultSentences.sentenceCardUpdated
        this[SENTENCE_CARD_SHOW] = DefaultSentences.sentenceCardShow
        this[SENTENCE_CARD_NO_SUCH_ABILITY] = DefaultSentences.sentenceCardNoSuchAbility
        this[SENTENCE_GROWTH] = DefaultSentences.sentenceGrowth
        this[SENTENCE_SAN_CHECK] = DefaultSentences.sentenceSanCheck
        this[SC_PRE_SENTENCE] = DefaultSentences.sentenceScPre
        this[SC_SUCCESS] = DefaultSentences.sentenceScSuccess
        this[SC_FAILURE] = DefaultSentences.sentenceScFailure
        this[SC_BIG_SUCCESS] = DefaultSentences.sentenceScBigSuccess
        this[SC_BIG_FAILURE] = DefaultSentences.sentenceScBigFailure
        this[SC_INNOCENT_CRAZY] = DefaultSentences.sentenceScInnocent
        this[SC_TEMPORARY_CRAZY] = DefaultSentences.sentenceScTemporary
        this[SC_RANDOM_CRAZY] = DefaultSentences.sentenceScRandom
        this[SC_PERMANENT_CRAZY] = DefaultSentences.sentenceScPermanent
        this[SENTENCE_BIG_SUCCESS] = DefaultSentences.sentenceBigSuccess
        this[SENTENCE_VERY_HARD_SUCCESS] = DefaultSentences.sentenceVeryHardSuccess
        this[SENTENCE_HARD_SUCCESS] = DefaultSentences.sentenceHardSuccess
        this[SENTENCE_SUCCESS] = DefaultSentences.sentenceSuccess
        this[SENTENCE_FAILURE] = DefaultSentences.sentenceFailure
        this[SENTENCE_BIG_FAILURE] = DefaultSentences.sentenceBigFailure
        this[SENTENCE_OPPOSED_START] = DefaultSentences.sentenceOpposedStart
        this[SENTENCE_OPPOSED_FIRST_WIN] = DefaultSentences.sentenceOpposedFirstWin
        this[SENTENCE_OPPOSED_LAST_WIN] = DefaultSentences.sentenceOpposedLastWin
        this[SENTENCE_OPPOSED_EQUAL] = DefaultSentences.sentenceOpposedEqual
        this[SENTENCE_OPPOSED_ALL_FAILURE] = DefaultSentences.sentenceOpposedAllFailure
        this[SENTENCE_FIRST_ATTACKING] = DefaultSentences.sentenceFirstAttacking
        this[SENTENCE_DRAW] = DefaultSentences.sentenceDraw
        this[SENTENCE_MADNESS] = DefaultSentences.sentenceMadness

        this[SENTENCE_DICE_OPEN] = "本群骰子已开启"
        this[SENTENCE_DICE_CLOSE] = "本群骰子已关闭（.bot on 可重新打开）"
        this[SENTENCE_DICE_DENIED] = "只有群主、群管理员或 master 能改本群设置"
        this[SENTENCE_DICE_HELP_TEXT] = "用法：.bot on / .bot off（群主/群管理员/master）"
        this[SENTENCE_DICE_ROBOT_TEXT] =
            "群星 Stellaris Dice · ${stellaris.dice.BuildConfig.BUILD_ID}"
        this[SENTENCE_OPEN_ALREADY] = "本群骰子已开启"
        this[SENTENCE_CLOSE_ALREADY] = "本群骰子已关闭（.bot on 可重新打开）"

        this[SENTENCE_LOG_OPEN] = "已开始记录日志：{name}（log end 结束并上传）"
        this[SENTENCE_LOG_CLOSE] = "已暂停记录日志：{name}（用 log on {name} 继续，log end 上传）"
        this[SENTENCE_LOG_END] = "日志已结束并上传：{name}"
        this[SENTENCE_LOG_RESUME] = "已继续记录日志：{name}"
        this[SENTENCE_LOG_ALREADY] = "本群已经有一份日志在记录中（先 log end/log off）"
        this[SENTENCE_LOG_NOT_OPEN] = "本群没有正在记录的日志"
        this[SENTENCE_LOG_NOT_FOUND] = "没有找到日志：{name}"
        this[SENTENCE_LOG_DENIED] = "只有群主、群管理员或 master 能改本群设置"

        this[SENTENCE_OB_JOIN] = "已将{player}加入OB位"
        this[SENTENCE_OB_JOIN_FAILURE] = "你已经处于OB位了，无法再次加入"
        this[SENTENCE_OB_EXIT] = "你不再处于OB位"
        this[SENTENCE_OB_EXIT_FAILURE] = "你已经不在OB位了"

        this[SENTENCE_NAME] = "为{player}生成了以下名字："
        this[SENTENCE_JRRP] = "{player}今日人品：{value}（1~100）"
        this[SENTENCE_CHANGE_CARD] = "本群已切换到人物卡：{name}"
        this[SENTENCE_CHANGE_NAME] = "已将人物卡「{from}」改名为「{to}」"
        this[SENTENCE_SETCOC_DENIED] = "只有群主、群管理员或 master 能改本群设置"
        this[SENTENCE_RULE_QUERY_FAILURE] = "{result}"
        this[SENTENCE_DRAW_SUCCESS] = "{result}"
        this[SENTENCE_DRAW_FAILURE] = "{result}"

        this[AVOID_CLEARING_GROUP_LIST] = ""
        this[GROUP_CLEANED_SORRY_REPLY] =
            "本群已经 {days} 天没有使用过我了，我先退群啦。需要我的时候再拉我回来～"
        this[PREFIX] = ".。!！"
        this[DEFAULT_DICE_NUMBER] = stellaris.dice.diceFunction.responder.RespondContext.DEFAULT_DICE_NUMBER.toString()
    }

    init {
        load()
    }




    fun readGlobal(key: String): String = globalValues[key] ?: globalDefaults[key] ?: ""


    @Synchronized
    fun writeGlobal(key: String, value: String) {
        globalValues[key] = value
        flush()
    }


    fun readGroup(group: String, key: String): String =
        groupValues[group]?.get(key) ?: readGlobal(key)


    @Synchronized
    fun writeGroup(group: String, key: String, value: String) {
        groupValues.getOrPut(group) { HashMap() }[key] = value
        flush()
    }


    fun readPerson(person: String, key: String): String =
        personValues[person]?.get(key) ?: readGlobal(key)


    @Synchronized
    fun writePerson(person: String, key: String, value: String) {
        personValues.getOrPut(person) { HashMap() }[key] = value
        flush()
    }


    fun defaultDiceNumber(isGroupMsg: Boolean, group: String, person: String): Int {
        val raw = if (isGroupMsg) readGroup(group, DEFAULT_DICE_NUMBER) else readPerson(person, DEFAULT_DICE_NUMBER)
        return raw.toIntOrNull() ?: stellaris.dice.diceFunction.responder.RespondContext.DEFAULT_DICE_NUMBER
    }







    fun cocHouseRule(isGroupMsg: Boolean, group: String, person: String): stellaris.dice.diceFunction.responder.CocHouseRule {
        val raw = if (isGroupMsg) readGroup(group, COC_HOUSE_RULE) else readPerson(person, COC_HOUSE_RULE)
        return stellaris.dice.diceFunction.responder.CocHouseRule.parse(raw)
            ?: stellaris.dice.diceFunction.responder.CocHouseRule.DEFAULT
    }


    fun writeCocHouseRule(
        isGroupMsg: Boolean,
        group: String,
        person: String,
        rule: stellaris.dice.diceFunction.responder.CocHouseRule
    ) {
        if (isGroupMsg) {
            writeGroup(group, COC_HOUSE_RULE, rule.code.toString())
        } else {
            writePerson(person, COC_HOUSE_RULE, rule.code.toString())
        }
    }


    /** 当前房规的原始值：数字（旧数据）或规则名（含自定义）。 */
    fun cocJudgeName(isGroupMsg: Boolean, group: String, person: String): String =
        if (isGroupMsg) readGroup(group, COC_HOUSE_RULE) else readPerson(person, COC_HOUSE_RULE)


    fun writeCocJudgeName(isGroupMsg: Boolean, group: String, person: String, name: String) {
        if (isGroupMsg) {
            writeGroup(group, COC_HOUSE_RULE, name)
        } else {
            writePerson(person, COC_HOUSE_RULE, name)
        }
    }






    fun drawDefaultDeck(isGroupMsg: Boolean, group: String, person: String): String =
        if (isGroupMsg) readGroup(group, DRAW_DEFAULT_DECK) else readPerson(person, DRAW_DEFAULT_DECK)






    fun writeDrawDefaultDeck(isGroupMsg: Boolean, group: String, person: String, name: String) {
        if (isGroupMsg) {
            writeGroup(group, DRAW_DEFAULT_DECK, name)
        } else {
            writePerson(person, DRAW_DEFAULT_DECK, name)
        }
    }







    fun ruleMode(isGroupMsg: Boolean, group: String, person: String): RuleMode {
        val raw = if (isGroupMsg) readGroup(group, RULE_MODE) else readPerson(person, RULE_MODE)
        return RuleMode.parse(raw) ?: RuleMode.DEFAULT
    }


    fun writeRuleMode(isGroupMsg: Boolean, group: String, person: String, mode: RuleMode) {
        if (isGroupMsg) {
            writeGroup(group, RULE_MODE, mode.word)
        } else {
            writePerson(person, RULE_MODE, mode.word)
        }
    }






    fun observerEnabled(group: String): Boolean =
        readGroup(group, OB_ON).toBooleanStrictOrNull() ?: true


    fun writeObserverEnabled(group: String, enabled: Boolean) {
        writeGroup(group, OB_ON, enabled.toString())
    }









    override fun isMaster(id: String): Boolean {
        if (id.isEmpty()) {
            return false
        }
        return readGlobal(MASTER_QQ)
            .split('\n')
            .map { it.trim() }
            .any { it.isNotEmpty() && it == id }
    }


    override val DICE_NUDGE_QQ_SAY: String get() = readGlobal(NUDGE_QQ_SAY)



    override val sentenceRoll: String get() = readGlobal(SENTENCE_ROLL)

    override val sentenceRollWithReason: String get() = readGlobal(SENTENCE_ROLL_WITH_REASON)

    override val sentenceIllegal: String get() = readGlobal(SENTENCE_ILLEGAL)

    override val sentenceIllegalTooMuch: String get() = readGlobal(SENTENCE_ILLEGAL_TOO_MUCH)

    override val sentenceHiddenRollHint: String get() = readGlobal(SENTENCE_HIDDEN_ROLL_HINT)

    override val sentenceHiddenRoll: String get() = readGlobal(SENTENCE_HIDDEN_ROLL)

    override val sentenceCheck: String get() = readGlobal(SENTENCE_RABP)

    override val sentenceCheckWithReason: String get() = readGlobal(SENTENCE_RABP_WITH_REASON)

    override val sentenceCheckMultiple: String get() = readGlobal(SENTENCE_RABP_MUTI)

    override val sentenceCheckMultipleWithReason: String
        get() = readGlobal(SENTENCE_RABP_MUTI_WITH_REASON)

    override val sentenceCardUpdated: String get() = readGlobal(SENTENCE_CARD_UPDATED)

    override val sentenceCardShow: String get() = readGlobal(SENTENCE_CARD_SHOW)

    override val sentenceCardNoSuchAbility: String get() = readGlobal(SENTENCE_CARD_NO_SUCH_ABILITY)

    override val sentenceGrowth: String get() = readGlobal(SENTENCE_GROWTH)

    override val sentenceSanCheck: String get() = readGlobal(SENTENCE_SAN_CHECK)

    override val sentenceScPre: String get() = readGlobal(SC_PRE_SENTENCE)

    override val sentenceScSuccess: String get() = readGlobal(SC_SUCCESS)

    override val sentenceScFailure: String get() = readGlobal(SC_FAILURE)

    override val sentenceScBigSuccess: String get() = readGlobal(SC_BIG_SUCCESS)

    override val sentenceScBigFailure: String get() = readGlobal(SC_BIG_FAILURE)

    override val sentenceScInnocent: String get() = readGlobal(SC_INNOCENT_CRAZY)

    override val sentenceScTemporary: String get() = readGlobal(SC_TEMPORARY_CRAZY)

    override val sentenceScRandom: String get() = readGlobal(SC_RANDOM_CRAZY)

    override val sentenceScPermanent: String get() = readGlobal(SC_PERMANENT_CRAZY)

    override val sentenceBigSuccess: String get() = readGlobal(SENTENCE_BIG_SUCCESS)

    override val sentenceVeryHardSuccess: String get() = readGlobal(SENTENCE_VERY_HARD_SUCCESS)

    override val sentenceHardSuccess: String get() = readGlobal(SENTENCE_HARD_SUCCESS)

    override val sentenceSuccess: String get() = readGlobal(SENTENCE_SUCCESS)

    override val sentenceFailure: String get() = readGlobal(SENTENCE_FAILURE)

    override val sentenceBigFailure: String get() = readGlobal(SENTENCE_BIG_FAILURE)

    override val sentenceOpposedStart: String get() = readGlobal(SENTENCE_OPPOSED_START)

    override val sentenceOpposedFirstWin: String get() = readGlobal(SENTENCE_OPPOSED_FIRST_WIN)

    override val sentenceOpposedLastWin: String get() = readGlobal(SENTENCE_OPPOSED_LAST_WIN)

    override val sentenceOpposedEqual: String get() = readGlobal(SENTENCE_OPPOSED_EQUAL)

    override val sentenceOpposedAllFailure: String get() = readGlobal(SENTENCE_OPPOSED_ALL_FAILURE)

    override val sentenceFirstAttacking: String get() = readGlobal(SENTENCE_FIRST_ATTACKING)

    override val sentenceDraw: String get() = readGlobal(SENTENCE_DRAW)

    override val sentenceMadness: String get() = readGlobal(SENTENCE_MADNESS)

    val sentenceDiceOpen: String get() = readGlobal(SENTENCE_DICE_OPEN)

    val sentenceDiceClose: String get() = readGlobal(SENTENCE_DICE_CLOSE)

    val sentenceDiceDenied: String get() = readGlobal(SENTENCE_DICE_DENIED)

    val sentenceDiceHelpText: String get() = readGlobal(SENTENCE_DICE_HELP_TEXT)

    val sentenceDiceRobotText: String get() = readGlobal(SENTENCE_DICE_ROBOT_TEXT)

    val sentenceOpenAlready: String get() = readGlobal(SENTENCE_OPEN_ALREADY)

    val sentenceCloseAlready: String get() = readGlobal(SENTENCE_CLOSE_ALREADY)

    val sentenceLogOpen: String get() = readGlobal(SENTENCE_LOG_OPEN)

    val sentenceLogClose: String get() = readGlobal(SENTENCE_LOG_CLOSE)

    val sentenceLogEnd: String get() = readGlobal(SENTENCE_LOG_END)

    val sentenceLogResume: String get() = readGlobal(SENTENCE_LOG_RESUME)

    val sentenceLogAlready: String get() = readGlobal(SENTENCE_LOG_ALREADY)

    val sentenceLogNotOpen: String get() = readGlobal(SENTENCE_LOG_NOT_OPEN)

    val sentenceLogNotFound: String get() = readGlobal(SENTENCE_LOG_NOT_FOUND)

    val sentenceLogDenied: String get() = readGlobal(SENTENCE_LOG_DENIED)

    val sentenceObJoin: String get() = readGlobal(SENTENCE_OB_JOIN)

    val sentenceObJoinFailure: String get() = readGlobal(SENTENCE_OB_JOIN_FAILURE)

    val sentenceObExit: String get() = readGlobal(SENTENCE_OB_EXIT)

    val sentenceObExitFailure: String get() = readGlobal(SENTENCE_OB_EXIT_FAILURE)

    val sentenceName: String get() = readGlobal(SENTENCE_NAME)

    val sentenceJrrp: String get() = readGlobal(SENTENCE_JRRP)

    val sentenceChangeCard: String get() = readGlobal(SENTENCE_CHANGE_CARD)

    val sentenceChangeName: String get() = readGlobal(SENTENCE_CHANGE_NAME)

    val sentenceSetcocDenied: String get() = readGlobal(SENTENCE_SETCOC_DENIED)

    val sentenceRuleQueryFailure: String get() = readGlobal(SENTENCE_RULE_QUERY_FAILURE)

    val sentenceDrawSuccess: String get() = readGlobal(SENTENCE_DRAW_SUCCESS)

    val sentenceDrawFailure: String get() = readGlobal(SENTENCE_DRAW_FAILURE)









    @Synchronized
    fun flushNow() {
        flush()
    }


    @Synchronized
    private fun flush() {
        try {
            val root = JSONObject()
            root.put(FIELD_GLOBAL, JSONObject(globalValues as Map<*, *>))
            root.put(FIELD_GROUPS, nested(groupValues))
            root.put(FIELD_PERSONS, nested(personValues))
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("配置落盘失败（本次运行的内存配置不受影响）：$t", LOG_TAG)
        }
    }


    private fun nested(source: Map<String, Map<String, String>>): JSONObject {
        val root = JSONObject()
        source.forEach { (name, values) -> root.put(name, JSONObject(values as Map<*, *>)) }
        return root
    }


    private fun load() {
        if (!storeFile.isFile) {
            return
        }
        try {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            root.optJSONObject(FIELD_GLOBAL)?.let { json ->
                json.keys().forEach { key -> globalValues[key] = json.optString(key) }
            }
            root.optJSONObject(FIELD_GROUPS)?.let { groups ->
                groups.keys().forEach { group ->
                    val json = groups.optJSONObject(group) ?: return@forEach
                    val values = HashMap<String, String>()
                    json.keys().forEach { key -> values[key] = json.optString(key) }
                    groupValues[group] = values
                }
            }
            root.optJSONObject(FIELD_PERSONS)?.let { persons ->
                persons.keys().forEach { person ->
                    val json = persons.optJSONObject(person) ?: return@forEach
                    val values = HashMap<String, String>()
                    json.keys().forEach { key -> values[key] = json.optString(key) }
                    personValues[person] = values
                }
            }
        } catch (t: Throwable) {

            DiceLogger.error("配置读取失败，按出厂默认继续：$t", LOG_TAG)
        }
    }

    companion object {


        const val MASTER_QQ = "MASTER_QQ"


        const val DEFAULT_DICE_NUMBER = "DEFAULT_DICE_NUMBER"


        const val NUDGE_QQ_SAY = "DICE_NUDGE_QQ_SAY"









        const val COC_HOUSE_RULE = "COC_HOUSE_RULE"






        const val DRAW_DEFAULT_DECK = "DRAW_DEFAULT_DECK"






        const val RULE_MODE = "RULE_MODE"


        const val OB_ON = "OB_ON"


        const val FIND_GROUP = "FIND_GROUP"


        const val BOT_OFF = "BOT_OFF"

        /** 关键词自动回复总开关/群开关，"1" 开、"0" 关。 */
        const val REPLY_ON = "REPLY_ON"

        const val DISABLE_JRRP = "DISABLE_JRRP"
        const val DISABLE_DRAW = "DISABLE_DRAW"
        const val DISABLE_HELP = "DISABLE_HELP"

        /** 本群屏蔽的关键词（JSON 数组），由 `.group -词条` 维护。 */
        const val DISABLE_KEYWORDS = "DISABLE_KEYWORDS"

        /** 插件网络是否允许访问环回/内网地址（"1" 允许）。 */
        const val ALLOW_LOCAL_NETWORK = "ALLOW_LOCAL_NETWORK"


        const val WELCOME_ON = "WELCOME_ON"
        const val WELCOME_TEXT = "WELCOME_TEXT"


        const val NO_LAWNOSKY = "NO_LAWNOSKY"


        const val DEBUG_ON = "DEBUG_ON"

        /** `.system split on/off`：长输出里的系统分段符是否拆成多条消息。 */
        const val SYSTEM_SPLIT = "SYSTEM_SPLIT"

        /** `.system censorship on/off`：出口敏感词审查总开关。 */
        const val SYSTEM_CENSORSHIP = "SYSTEM_CENSORSHIP"

        /** 每行一个敏感词；命中替换为等长 `*`。 */
        const val FORBIDDEN_WORDS = "FORBIDDEN_WORDS"

        /** 每行 `词=替换`；替换写 `-` 表示整条拦截。 */
        const val REPLACE_FORBIDDEN_WORDS = "REPLACE_FORBIDDEN_WORDS"

        /** `.master groupclr` 的免清群名单，每行一个群号。 */
        const val AVOID_CLEARING_GROUP_LIST = "AVOID_CLEARING_GROUP_LIST"

        /** 清理久未使用群时发送的告别语，可用 `{days}`。 */
        const val GROUP_CLEANED_SORRY_REPLY = "GROUP_CLEANED_SORRY_REPLY"

        /** 指令触发符集合，默认 `.。!！`；每个字符都算触发符。 */
        const val PREFIX = "PREFIX"

        const val SENTENCE_ROLL = "SENTENCE_ROLL"
        const val SENTENCE_ROLL_WITH_REASON = "SENTENCE_ROLL_WITH_REASON"
        const val SENTENCE_ILLEGAL = "SENTENCE_ILLEGAL"
        const val SENTENCE_ILLEGAL_TOO_MUCH = "SENTENCE_ILLEGAL_TOO_MUCH"
        const val SENTENCE_HIDDEN_ROLL_HINT = "SENTENCE_HIDDEN_ROLL_HINT"


        const val SENTENCE_HIDDEN_ROLL = "SENTENCE_HIDDEN_ROLL"


        const val SENTENCE_RABP = "SENTENCE_RABP"


        const val SENTENCE_RABP_WITH_REASON = "SENTENCE_RABP_WITH_REASON"


        const val SENTENCE_RABP_MUTI = "SENTENCE_RABP_MUTI"


        const val SENTENCE_RABP_MUTI_WITH_REASON = "SENTENCE_RABP_MUTI_WITH_REASON"


        const val SENTENCE_CARD_UPDATED = "SENTENCE_CARD_UPDATED"


        const val SENTENCE_CARD_SHOW = "SENTENCE_CARD_SHOW"


        const val SENTENCE_CARD_NO_SUCH_ABILITY = "SENTENCE_CARD_NO_SUCH_ABILITY"


        const val SENTENCE_GROWTH = "SENTENCE_GROWTH"


        const val SENTENCE_SAN_CHECK = "SENTENCE_SAN_CHECK"


        const val SC_PRE_SENTENCE = "SC_PRE_SENTENCE"
        const val SC_SUCCESS = "SC_SUCCESS"
        const val SC_FAILURE = "SC_FAILURE"
        const val SC_BIG_SUCCESS = "SC_BIG_SUCCESS"
        const val SC_BIG_FAILURE = "SC_BIG_FAILURE"
        const val SC_INNOCENT_CRAZY = "SC_INNOCENT_CRAZY"
        const val SC_TEMPORARY_CRAZY = "SC_TEMPORARY_CRAZY"
        const val SC_RANDOM_CRAZY = "SC_RANDOM_CRAZY"
        const val SC_PERMANENT_CRAZY = "SC_PERMANENT_CRAZY"

        const val SENTENCE_BIG_SUCCESS = "SENTENCE_BIG_SUCCESS"
        const val SENTENCE_VERY_HARD_SUCCESS = "SENTENCE_VERY_HARD_SUCCESS"
        const val SENTENCE_HARD_SUCCESS = "SENTENCE_HARD_SUCCESS"
        const val SENTENCE_SUCCESS = "SENTENCE_SUCCESS"
        const val SENTENCE_FAILURE = "SENTENCE_FAILURE"
        const val SENTENCE_BIG_FAILURE = "SENTENCE_BIG_FAILURE"







        const val SENTENCE_OPPOSED_START = "SENTENCE_RACV_START"
        const val SENTENCE_OPPOSED_FIRST_WIN = "SENTENCE_RACV_FIRST_WIN"
        const val SENTENCE_OPPOSED_LAST_WIN = "SENTENCE_RACV_LAST_WIN"
        const val SENTENCE_OPPOSED_EQUAL = "SENTENCE_RACV_EQUAL"
        const val SENTENCE_OPPOSED_ALL_FAILURE = "SENTENCE_RACV_ALL_FAILURE"


        const val SENTENCE_FIRST_ATTACKING = "FIRST_ATTACKING_SENTENCE"


        const val SENTENCE_DRAW = "SENTENCE_DRAW"


        const val SENTENCE_MADNESS = "SENTENCE_MADNESS"


        const val SENTENCE_DICE_OPEN = "SENTENCE_DICE_OPEN"
        const val SENTENCE_DICE_CLOSE = "SENTENCE_DICE_CLOSE"
        const val SENTENCE_DICE_DENIED = "SENTENCE_DICE_DENIED"
        const val SENTENCE_DICE_HELP_TEXT = "SENTENCE_DICE_HELP_TEXT"
        const val SENTENCE_DICE_ROBOT_TEXT = "SENTENCE_DICE_ROBOT_TEXT"
        const val SENTENCE_OPEN_ALREADY = "SENTENCE_OPEN_ALREADY"
        const val SENTENCE_CLOSE_ALREADY = "SENTENCE_CLOSE_ALREADY"

        const val SENTENCE_LOG_OPEN = "SENTENCE_LOG_OPEN"
        const val SENTENCE_LOG_CLOSE = "SENTENCE_LOG_CLOSE"
        const val SENTENCE_LOG_END = "SENTENCE_LOG_END"
        const val SENTENCE_LOG_RESUME = "SENTENCE_LOG_RESUME"
        const val SENTENCE_LOG_ALREADY = "SENTENCE_LOG_ALREADY"
        const val SENTENCE_LOG_NOT_OPEN = "SENTENCE_LOG_NOT_OPEN"
        const val SENTENCE_LOG_NOT_FOUND = "SENTENCE_LOG_NOT_FOUND"
        const val SENTENCE_LOG_DENIED = "SENTENCE_LOG_DENIED"

        const val SENTENCE_OB_JOIN = "SENTENCE_OB_JOIN"
        const val SENTENCE_OB_JOIN_FAILURE = "SENTENCE_OB_JOIN_FAILURE"
        const val SENTENCE_OB_EXIT = "SENTENCE_OB_EXIT"
        const val SENTENCE_OB_EXIT_FAILURE = "SENTENCE_OB_EXIT_FAILURE"

        const val SENTENCE_NAME = "SENTENCE_NAME"
        const val SENTENCE_JRRP = "SENTENCE_JRRP"
        const val SENTENCE_CHANGE_CARD = "SENTENCE_CHANGE_CARD"
        const val SENTENCE_CHANGE_NAME = "SENTENCE_CHANGE_NAME"
        const val SENTENCE_SETCOC_DENIED = "SENTENCE_SETCOC_DENIED"
        const val SENTENCE_RULE_QUERY_FAILURE = "SENTENCE_RULE_QUERY_FAILURE"
        const val SENTENCE_DRAW_SUCCESS = "SENTENCE_DRAW_SUCCESS"
        const val SENTENCE_DRAW_FAILURE = "SENTENCE_DRAW_FAILURE"


        private const val CONFIG_FILE_NAME = "config.json"

        private const val FIELD_GLOBAL = "global"
        private const val FIELD_GROUPS = "groups"
        private const val FIELD_PERSONS = "persons"

        private const val LOG_TAG = "DiceConfigStore"
    }
}
