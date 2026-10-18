package stellaris.dice.diceFunction.hiy

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File



























class HiyStore(private val rootPath: String) {


    class Stats(
        val rolls: Long,
        val sum: Long,
        val min: Int,
        val max: Int,
        val commands: Map<String, Int>
    )

    private class MutableStats(
        var rolls: Long = 0,
        var sum: Long = 0,
        var min: Int = Int.MAX_VALUE,
        var max: Int = Int.MIN_VALUE,
        val commands: LinkedHashMap<String, Int> = LinkedHashMap()
    )

    private val users = LinkedHashMap<String, MutableStats>()

    private val storeFile: File
        get() = File(rootPath, "${DiceConstant.SAVE_PATH_DATA}/$FILE_NAME")

    init {
        load()
    }








    @Synchronized
    fun record(user: String, command: String, value: Int) {
        if (user.isEmpty()) {
            return
        }
        val stats = users.getOrPut(user) { MutableStats() }
        stats.rolls++
        stats.sum += value
        if (value < stats.min) stats.min = value
        if (value > stats.max) stats.max = value
        stats.commands[command] = (stats.commands[command] ?: 0) + 1
        flush()
    }


    @Synchronized
    fun get(user: String): Stats? {
        val stats = users[user] ?: return null
        if (stats.rolls == 0L) {
            return null
        }
        return Stats(
            rolls = stats.rolls,
            sum = stats.sum,
            min = stats.min,
            max = stats.max,
            commands = LinkedHashMap(stats.commands)
        )
    }

    private fun flush() {
        try {
            val usersJson = JSONObject()
            users.forEach { (user, stats) ->
                if (stats.rolls == 0L) {
                    return@forEach
                }
                val commands = JSONObject()
                stats.commands.forEach { (command, count) -> commands.put(command, count) }
                usersJson.put(
                    user,
                    JSONObject().apply {
                        put("n", stats.rolls)
                        put("s", stats.sum)
                        put("min", stats.min)
                        put("max", stats.max)
                        put("c", commands)
                    }
                )
            }
            val root = JSONObject().put("users", usersJson)
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("骰点统计落盘失败（本次运行的内存统计不受影响）：$t", LOG_TAG)
        }
    }

    private fun load() {
        if (!storeFile.isFile) {
            return
        }
        try {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            root.optJSONObject("users")?.let { usersJson ->
                usersJson.keys().forEach { user ->
                    val json = usersJson.optJSONObject(user) ?: return@forEach
                    val stats = MutableStats(
                        rolls = json.optLong("n"),
                        sum = json.optLong("s"),
                        min = json.optInt("min", Int.MAX_VALUE),
                        max = json.optInt("max", Int.MIN_VALUE)
                    )
                    json.optJSONObject("c")?.let { commands ->
                        commands.keys().forEach { command ->
                            stats.commands[command] = commands.optInt(command)
                        }
                    }
                    if (stats.rolls > 0) {
                        users[user] = stats
                    }
                }
            }
        } catch (t: Throwable) {
            DiceLogger.error("骰点统计读取失败，按空库继续：$t", LOG_TAG)
        }
    }

    private companion object {
        const val FILE_NAME = "hiy.json"
        const val LOG_TAG = "HiyStore"
    }
}
