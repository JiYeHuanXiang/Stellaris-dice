package stellaris.dice.diceFunction.opposed

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File























class OpposedContestStore(private val rootPath: String) {










    class Pending(
        val senderId: String,
        val senderName: String,
        val skill: String,
        val target: Int,
        val createdAt: Long = System.currentTimeMillis()
    )


    private val pending = LinkedHashMap<String, Pending>()

    private val storeFile: File
        get() = File(rootPath, "${DiceConstant.SAVE_PATH_DATA}/$FILE_NAME")

    init {
        load()
    }


    @Synchronized
    fun join(group: String, contest: Pending) {
        pending[group] = contest
        flush()
    }


    @Synchronized
    fun take(group: String): Pending? {
        val contest = pending.remove(group) ?: return null
        flush()
        return contest
    }


    @Synchronized
    fun peek(group: String): Pending? = pending[group]


    @Synchronized
    fun clear(group: String): Boolean {
        val removed = pending.remove(group) != null
        if (removed) {
            flush()
        }
        return removed
    }

    private fun flush() {
        try {
            val groups = JSONObject()
            pending.forEach { (group, contest) ->
                groups.put(
                    group,
                    JSONObject().apply {
                        put("senderId", contest.senderId)
                        put("senderName", contest.senderName)
                        put("skill", contest.skill)
                        put("target", contest.target)
                        put("createdAt", contest.createdAt)
                    }
                )
            }
            val root = JSONObject().put("groups", groups)
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("对抗登记落盘失败（本次运行的内存登记不受影响）：$t", LOG_TAG)
        }
    }

    private fun load() {
        if (!storeFile.isFile) {
            return
        }
        try {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            root.optJSONObject("groups")?.let { groups ->
                groups.keys().forEach { group ->
                    val json = groups.optJSONObject(group) ?: return@forEach
                    pending[group] = Pending(
                        senderId = json.optString("senderId"),
                        senderName = json.optString("senderName"),
                        skill = json.optString("skill"),
                        target = json.optInt("target"),
                        createdAt = json.optLong("createdAt")
                    )
                }
            }
        } catch (t: Throwable) {

            DiceLogger.error("对抗登记读取失败，按空库继续：$t", LOG_TAG)
        }
    }

    private companion object {
        const val FILE_NAME = "opposed.json"
        const val LOG_TAG = "OpposedStore"
    }
}
