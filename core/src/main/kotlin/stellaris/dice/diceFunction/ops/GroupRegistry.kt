package stellaris.dice.diceFunction.ops

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File

/**
 * 群聊注册表：记录群名与最后使用时间，供 `.master groupclr` 清理久未使用的群。
 *
 * 落盘 `data/groups.json`；[touch] 在内存里更新，最多每分钟落盘一次，避免每条消息都写文件。
 */
class GroupRegistry(private val rootPath: String) {

    class Entry(val id: String, var name: String, var lastUsage: Long)

    private val groups = LinkedHashMap<String, Entry>()
    private var lastPersist = 0L

    private val storeFile: File
        get() = File(File(rootPath, DiceConstant.SAVE_PATH_DATA), FILE_NAME)

    init {
        load()
    }

    @Synchronized
    fun size(): Int = groups.size

    @Synchronized
    fun touch(groupId: String, name: String = "") {
        if (groupId.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        val entry = groups.getOrPut(groupId) { Entry(groupId, "", 0L) }
        entry.lastUsage = now
        if (name.isNotEmpty() && entry.name != name) {
            entry.name = name
            persist()
            lastPersist = now
        } else if (now - lastPersist >= PERSIST_INTERVAL_MILLIS) {
            persist()
            lastPersist = now
        }
    }

    /** 用宿主群列表补齐群名；不覆盖已有的最后使用时间。 */
    @Synchronized
    fun seed(entries: List<Pair<String, String>>) {
        val now = System.currentTimeMillis()
        var changed = false
        entries.forEach { (id, name) ->
            if (id.isEmpty()) {
                return@forEach
            }
            val entry = groups[id]
            if (entry == null) {
                groups[id] = Entry(id, name, now)
                changed = true
            } else if (name.isNotEmpty() && entry.name != name) {
                entry.name = name
                changed = true
            }
        }
        if (changed) {
            persist()
        }
    }

    @Synchronized
    fun unused(days: Int, now: Long = System.currentTimeMillis()): List<Entry> {
        if (days <= 0) {
            return emptyList()
        }
        val threshold = now - days * 24L * 60L * 60L * 1000L
        return groups.values.filter { it.lastUsage > 0 && it.lastUsage <= threshold }
    }

    @Synchronized
    private fun persist() {
        try {
            val root = JSONObject()
            groups.forEach { (id, entry) ->
                root.put(
                    id,
                    JSONObject()
                        .put(FIELD_NAME, entry.name)
                        .put(FIELD_LAST_USAGE, entry.lastUsage)
                )
            }
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("群注册表落盘失败：$t", LOG_TAG)
        }
    }

    private fun load() {
        if (!storeFile.isFile) {
            return
        }
        try {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            root.keys().forEach { id ->
                val json = root.optJSONObject(id) ?: return@forEach
                groups[id] = Entry(
                    id,
                    json.optString(FIELD_NAME, ""),
                    json.optLong(FIELD_LAST_USAGE, 0L)
                )
            }
        } catch (t: Throwable) {
            DiceLogger.error("群注册表读取失败，按空表继续：$t", LOG_TAG)
        }
    }

    private companion object {
        const val FILE_NAME = "groups.json"
        const val FIELD_NAME = "name"
        const val FIELD_LAST_USAGE = "lastUsage"
        const val PERSIST_INTERVAL_MILLIS = 60_000L
        const val LOG_TAG = "GroupRegistry"
    }
}
