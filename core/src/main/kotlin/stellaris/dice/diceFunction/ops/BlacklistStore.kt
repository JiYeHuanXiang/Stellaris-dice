package stellaris.dice.diceFunction.ops

import org.json.JSONArray
import org.json.JSONObject
import stellaris.dice.diceFunction.DiceLogger
import java.io.File

/**
 * 本地黑名单：用户与群两类，落 `data/blacklist.json`。
 * 解黑使用 uuid。
 */
class BlacklistStore(rootPath: String) {

    enum class Type { USER, GROUP }

    class Entry(
        val uuid: Long,
        val type: Type,
        val number: String,
        val reason: String,
        val createdAt: Long
    )

    private val file = File(File(rootPath, "data"), "blacklist.json")
    private val entries = ArrayList<Entry>()

    init {
        load()
    }

    @Synchronized
    fun list(): List<Entry> = entries.sortedBy { it.uuid }

    @Synchronized
    fun count(): Int = entries.size

    @Synchronized
    fun find(number: String): List<Entry> =
        entries.filter { it.number == number.trim() }

    @Synchronized
    fun find(uuid: Long): Entry? = entries.firstOrNull { it.uuid == uuid }

    @Synchronized
    fun isUserBlocked(userId: String): Boolean =
        entries.any { it.type == Type.USER && it.number == userId }

    @Synchronized
    fun isGroupBlocked(groupId: String): Boolean =
        entries.any { it.type == Type.GROUP && it.number == groupId }

    @Synchronized
    fun add(type: Type, number: String, reason: String): Entry? {
        val normalized = number.trim()
        if (normalized.isEmpty()) return null
        val existing = entries.firstOrNull { it.type == type && it.number == normalized }
        if (existing != null) return existing
        val entry = Entry(
            uuid = nextUuid(),
            type = type,
            number = normalized,
            reason = reason.trim(),
            createdAt = System.currentTimeMillis()
        )
        entries.add(entry)
        flush()
        return entry
    }

    @Synchronized
    fun remove(uuid: Long): Boolean {
        val removed = entries.removeIf { it.uuid == uuid }
        if (removed) flush()
        return removed
    }

    private fun nextUuid(): Long = (entries.maxOfOrNull { it.uuid } ?: 0L) + 1L

    private fun load() {
        if (!file.isFile) return
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val array = root.optJSONArray(FIELD_ENTRIES) ?: JSONArray()
            for (i in 0 until array.length()) {
                val json = array.optJSONObject(i) ?: continue
                val type = runCatching {
                    Type.valueOf(json.optString("type", Type.USER.name))
                }.getOrDefault(Type.USER)
                entries.add(
                    Entry(
                        uuid = json.optLong("uuid"),
                        type = type,
                        number = json.optString("number"),
                        reason = json.optString("reason"),
                        createdAt = json.optLong("createdAt")
                    )
                )
            }
        } catch (t: Throwable) {
            DiceLogger.error("黑名单载入失败：${file.path}：$t")
        }
    }

    private fun flush() {
        try {
            val array = JSONArray()
            entries.forEach {
                array.put(
                    JSONObject()
                        .put("uuid", it.uuid)
                        .put("type", it.type.name)
                        .put("number", it.number)
                        .put("reason", it.reason)
                        .put("createdAt", it.createdAt)
                )
            }
            val root = JSONObject().put("version", FORMAT_VERSION).put(FIELD_ENTRIES, array)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            DiceLogger.error("黑名单写入失败：${file.path}：$t")
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val FIELD_ENTRIES = "entries"
    }
}
