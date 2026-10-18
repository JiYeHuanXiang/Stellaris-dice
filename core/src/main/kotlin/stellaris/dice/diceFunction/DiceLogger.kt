package stellaris.dice.diceFunction
















object DiceLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }


    fun interface Sink {
        fun write(level: Level, tag: String, message: String)
    }


    private const val DEFAULT_TAG = "Core"

    @Volatile
    private var sink: Sink? = null


    @JvmStatic
    fun installSink(sink: Sink?) {
        this.sink = sink
    }


    @JvmStatic
    fun log(message: String, tag: String = DEFAULT_TAG) = emit(Level.INFO, tag, message)


    @JvmStatic
    fun warn(message: String, tag: String = DEFAULT_TAG) = emit(Level.WARN, tag, message)


    @JvmStatic
    fun error(message: String, tag: String = DEFAULT_TAG) = emit(Level.ERROR, tag, message)

    private fun emit(level: Level, tag: String, message: String) {
        val s = sink
        if (s != null) {
            try {
                s.write(level, tag, message)
            } catch (t: Throwable) {

                System.err.println("[${level.name}] [$tag] (sink 失败: $t) $message")
            }
            return
        }
        System.err.println("[${level.name}] [$tag] $message")
    }
}
