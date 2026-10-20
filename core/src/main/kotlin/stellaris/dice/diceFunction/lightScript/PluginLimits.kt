package stellaris.dice.diceFunction.lightScript

/** 插件单次执行与输出的统一硬上限。 */
internal object PluginLimits {
    const val DEFAULT_EXECUTION_TIMEOUT_MILLIS = 2_000L
    const val MAX_SCRIPT_CHARS = 512 * 1024
    const val MAX_RESULT_CHARS = 256 * 1024

    private const val TRUNCATED_SUFFIX = "\n……（插件输出过长，已截断）"

    fun limitResult(value: String?): String? {
        if (value == null || value.length <= MAX_RESULT_CHARS) return value
        val keep = (MAX_RESULT_CHARS - TRUNCATED_SUFFIX.length).coerceAtLeast(0)
        return value.take(keep) + TRUNCATED_SUFFIX
    }
}

internal class PluginExecutionTimeout(message: String) : Error(message)
