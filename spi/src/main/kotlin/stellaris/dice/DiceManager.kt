package stellaris.dice



















object DiceManager {

    @Volatile
    private var provider: KernelProvider? = null


    private fun kernel(): KernelProvider =
        provider ?: throw IllegalStateException(
            "KernelProvider 未注入：请在启动序列中先调用 DiceManager.install(...)（见 DiceManager 头部注释）"
        )


    @JvmStatic
    fun install(provider: KernelProvider) {
        this.provider = provider
    }


    @JvmStatic
    fun setBasePath(path: String) = kernel().setBasePath(path)


    @JvmStatic
    fun getBasePath(): String = kernel().getBasePath()












    @JvmStatic
    @Keep
    fun createDice(id: Any, diceIntentEvent: DiceIntentEvent?): DiceHelper =
        kernel().createDice(id, diceIntentEvent)


    @JvmStatic
    fun getDice(id: Any): DiceHelper? = kernel().getDice(id)


    @JvmStatic
    fun getDiceList(): List<DiceBrief> = kernel().getDiceList()
}







interface KernelProvider {


    fun setBasePath(path: String)


    fun getBasePath(): String


    fun createDice(id: Any, diceIntentEvent: DiceIntentEvent?): DiceHelper


    fun getDice(id: Any): DiceHelper?


    fun getDiceList(): List<DiceBrief>
}
