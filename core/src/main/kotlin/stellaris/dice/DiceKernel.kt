package stellaris.dice

import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.draw.RollWeightEvaluator















object DiceKernel : KernelProvider {

    init {

        RollWeightEvaluator.install()
    }


    private val instances = LinkedHashMap<String, DiceHelperImpl>()

    private var basePath: String? = null





    @Synchronized
    override fun setBasePath(path: String) {
        if (basePath != null && basePath != path) {
            throw IllegalStateException("数据根只允许设置一次：已设为 $basePath，拒绝改为 $path")
        }
        basePath = path
    }

    @Synchronized
    override fun getBasePath(): String =
        basePath ?: throw IllegalStateException("数据根未设置：请先调用 DiceManager.setBasePath(...)")













    @Synchronized
    override fun createDice(id: Any, diceIntentEvent: DiceIntentEvent?): DiceHelper {
        val key = id.toString()
        val existing = instances[key]
        if (existing != null) {
            if (diceIntentEvent != null) {
                existing.intentEvent = diceIntentEvent
            }
            return existing
        }
        val helper = DiceHelperImpl(
            botId = key,
            rootPath = instanceRoot(key),
            intentEvent = diceIntentEvent
        )
        instances[key] = helper
        return helper
    }

    @Synchronized
    override fun getDice(id: Any): DiceHelper? = instances[id.toString()]

    @Synchronized
    override fun getDiceList(): List<DiceBrief> = instances.values.map {
        DiceBrief(ready = it.intentEvent != null, botId = it.botId, nick = "")
    }








    private fun instanceRoot(botId: String): String = "${getBasePath()}/${DiceConstant.DATA_DIR_PREFIX}$botId"


    @Synchronized
    fun instanceCount(): Int = instances.size
}
