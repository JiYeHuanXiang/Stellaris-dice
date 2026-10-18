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
        this[SENTENCE_OPPOSED_START] = DefaultSentences.sentenceOpposedStart
        this[SENTENCE_OPPOSED_FIRST_WIN] = DefaultSentences.sentenceOpposedFirstWin
        this[SENTENCE_OPPOSED_LAST_WIN] = DefaultSentences.sentenceOpposedLastWin
        this[SENTENCE_OPPOSED_EQUAL] = DefaultSentences.sentenceOpposedEqual
        this[SENTENCE_OPPOSED_ALL_FAILURE] = DefaultSentences.sentenceOpposedAllFailure
        this[SENTENCE_FIRST_ATTACKING] = DefaultSentences.sentenceFirstAttacking
        this[SENTENCE_DRAW] = DefaultSentences.sentenceDraw
        this[SENTENCE_MADNESS] = DefaultSentences.sentenceMadness
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

    override val sentenceOpposedStart: String get() = readGlobal(SENTENCE_OPPOSED_START)

    override val sentenceOpposedFirstWin: String get() = readGlobal(SENTENCE_OPPOSED_FIRST_WIN)

    override val sentenceOpposedLastWin: String get() = readGlobal(SENTENCE_OPPOSED_LAST_WIN)

    override val sentenceOpposedEqual: String get() = readGlobal(SENTENCE_OPPOSED_EQUAL)

    override val sentenceOpposedAllFailure: String get() = readGlobal(SENTENCE_OPPOSED_ALL_FAILURE)

    override val sentenceFirstAttacking: String get() = readGlobal(SENTENCE_FIRST_ATTACKING)

    override val sentenceDraw: String get() = readGlobal(SENTENCE_DRAW)

    override val sentenceMadness: String get() = readGlobal(SENTENCE_MADNESS)









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







        const val SENTENCE_OPPOSED_START = "SENTENCE_RACV_START"
        const val SENTENCE_OPPOSED_FIRST_WIN = "SENTENCE_RACV_FIRST_WIN"
        const val SENTENCE_OPPOSED_LAST_WIN = "SENTENCE_RACV_LAST_WIN"
        const val SENTENCE_OPPOSED_EQUAL = "SENTENCE_RACV_EQUAL"
        const val SENTENCE_OPPOSED_ALL_FAILURE = "SENTENCE_RACV_ALL_FAILURE"


        const val SENTENCE_FIRST_ATTACKING = "FIRST_ATTACKING_SENTENCE"


        const val SENTENCE_DRAW = "SENTENCE_DRAW"


        const val SENTENCE_MADNESS = "SENTENCE_MADNESS"


        private const val CONFIG_FILE_NAME = "config.json"

        private const val FIELD_GLOBAL = "global"
        private const val FIELD_GROUPS = "groups"
        private const val FIELD_PERSONS = "persons"

        private const val LOG_TAG = "DiceConfigStore"
    }
}
