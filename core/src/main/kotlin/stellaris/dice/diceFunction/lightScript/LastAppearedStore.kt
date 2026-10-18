package stellaris.dice.diceFunction.lightScript

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceLogger
import java.io.File

/**
 * 每个用户最后发过言的群。Lua SDK 的 `readLastAppearedGroup(id)` 使用。
 *
 * 只记录群聊消息；私聊不改写。落 `data/last_group.json`。
 */
class LastAppearedStore(private val rootPath: String) {

    private val values = HashMap<String, String>()

    private val storeFile: File
        get() = File(rootPath, "data/last_group.json")

    init {
        load()
    }

    @Synchronized
    fun record(userId: String, groupId: String) {
        if (userId.isEmpty() || groupId.isEmpty()) return
        if (values[userId] == groupId) return
        values[userId] = groupId
        flush()
    }

    @Synchronized
    fun read(userId: String): String? = values[userId]

    private fun load() {
        if (!storeFile.isFile) return
        try {
            val json = JSONObject(storeFile.readText(Charsets.UTF_8))
            json.keys().forEach { key -> values[key] = json.optString(key) }
        } catch (t: Throwable) {
            DiceLogger.error("最后活跃群读取失败：$t")
        }
    }

    private fun flush() {
        try {
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(JSONObject(values as Map<*, *>).toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("最后活跃群写入失败：$t")
        }
    }
}
