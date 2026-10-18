package stellaris.dice.diceFunction

import stellaris.dice.diceFunction.database.entity.data.KeywordsReplyData











class DiceFunctionImpl(private val rootPath: String) : DiceFunctions {







    val configStore = DiceConfigStore(rootPath)







    val cardManager = stellaris.dice.diceFunction.card.PlayerCardManager(rootPath)







    val drawManager = stellaris.dice.diceFunction.draw.DrawFileManager(rootPath)




    val findEngine = stellaris.dice.diceFunction.find.FindEngine(rootPath)







    val opposedStore = stellaris.dice.diceFunction.opposed.OpposedContestStore(rootPath)







    val initiativeStore = stellaris.dice.diceFunction.initiative.InitiativeStore(rootPath)







    val observerStore = stellaris.dice.diceFunction.observer.ObserverStore(rootPath)






    val hiyStore = stellaris.dice.diceFunction.hiy.HiyStore(rootPath)

    val lastAppearedStore = stellaris.dice.diceFunction.lightScript.LastAppearedStore(rootPath)







    val logManager = stellaris.dice.diceFunction.log.LogManager(rootPath)

    override fun root(): String = rootPath

    override val diceConfig: DiceConfig get() = configStore







    val pluginLoader =
        stellaris.dice.diceFunction.lightScript.PluginLoader(rootPath).apply { reload() }

    override val diceCustomFileLoader: DiceCustomFileLoader get() = pluginLoader

    val keywordManager =
        stellaris.dice.diceFunction.keywords.JsonKeywordsReplyManager(rootPath)

    val blacklistStore = stellaris.dice.diceFunction.ops.BlacklistStore(rootPath)

    val guardStore = stellaris.dice.diceFunction.ops.GuardStore(rootPath)

    val recoveryManager = stellaris.dice.diceFunction.ops.RecoveryManager(rootPath)

    override val diceKeywordsReplyManager: DiceKeywordsReplyManager get() = keywordManager
}







object EmptyKeywordsReplyManager : DiceKeywordsReplyManager {

    override fun list(): ArrayList<KeywordsReplyData> = ArrayList()

    override fun find(mode: KeywordsReplyData.ReplyType, keyword: String): KeywordsReplyData? = null

    override fun find(uuid: Long): KeywordsReplyData? = null

    override fun update(obj: KeywordsReplyData): Boolean = false

    override fun add(
        mode: KeywordsReplyData.ReplyType,
        weight: Int,
        keyword: String,
        content: String,
        legacy: Boolean
    ): KeywordsReplyData? = null

    override fun remove(uuid: Long): Boolean = false
}
