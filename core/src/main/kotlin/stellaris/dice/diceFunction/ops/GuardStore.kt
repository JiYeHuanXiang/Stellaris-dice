package stellaris.dice.diceFunction.ops

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceLogger
import java.io.File
import java.util.ArrayDeque

/**
 * 防刷屏：同一群/同一用户在窗口内超过条数后不再响应。
 * 配置落 `data/guard.json`，运行期窗口状态只在内存里。
 */
class GuardStore(rootPath: String) {

    class Decision(val blocked: Boolean, val warning: String?) {
        companion object {
            val ALLOW = Decision(false, null)
        }
    }

    private val file = File(File(rootPath, "data"), "guard.json")
    private val windows = HashMap<String, ArrayDeque<Long>>()
    private val lastWarn = HashMap<String, Long>()

    var enabled: Boolean = false
        private set

    var windowSeconds: Int = 10
        private set

    var maxMessages: Int = 5
        private set

    var warning: String = ""
        private set

    init {
        load()
    }

    @Synchronized
    fun onMessage(groupId: String, userId: String, now: Long = System.currentTimeMillis()): Decision {
        if (!enabled || groupId.isEmpty()) return Decision.ALLOW
        val key = "$groupId\u0000$userId"
        val window = windows.getOrPut(key) { ArrayDeque() }
        val cutoff = now - windowSeconds * 1000L
        while (window.isNotEmpty() && window.first < cutoff) {
            window.removeFirst()
        }
        window.addLast(now)
        if (window.size <= maxMessages) return Decision.ALLOW

        val last = lastWarn[key] ?: 0L
        val shouldWarn = warning.isNotEmpty() && now - last >= windowSeconds * 1000L
        if (shouldWarn) lastWarn[key] = now
        return Decision(true, if (shouldWarn) warning else null)
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        flush()
    }

    @Synchronized
    fun setWindowSeconds(value: Int) {
        windowSeconds = value.coerceIn(1, 3600)
        flush()
    }

    @Synchronized
    fun setMaxMessages(value: Int) {
        maxMessages = value.coerceIn(1, 1000)
        flush()
    }

    @Synchronized
    fun setWarning(value: String) {
        warning = value.trim()
        flush()
    }

    @Synchronized
    fun clearRuntime() {
        windows.clear()
        lastWarn.clear()
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            enabled = json.optBoolean("enabled", false)
            windowSeconds = json.optInt("windowSeconds", 10).coerceIn(1, 3600)
            maxMessages = json.optInt("maxMessages", 5).coerceIn(1, 1000)
            warning = json.optString("warning", "")
        } catch (t: Throwable) {
            DiceLogger.error("防刷屏配置载入失败：${file.path}：$t")
        }
    }

    private fun flush() {
        try {
            val json = JSONObject()
                .put("version", 1)
                .put("enabled", enabled)
                .put("windowSeconds", windowSeconds)
                .put("maxMessages", maxMessages)
                .put("warning", warning)
            file.parentFile?.mkdirs()
            file.writeText(json.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("防刷屏配置写入失败：${file.path}：$t")
        }
    }
}
