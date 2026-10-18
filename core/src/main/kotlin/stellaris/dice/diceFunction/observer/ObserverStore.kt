package stellaris.dice.diceFunction.observer

import org.json.JSONArray
import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File
















class ObserverStore(private val rootPath: String) {


    class Observer(val id: String, val name: String)


    enum class JoinResult { JOINED, ALREADY, FULL }

    private val groups = LinkedHashMap<String, MutableList<Observer>>()

    private val storeFile: File
        get() = File(rootPath, "${DiceConstant.SAVE_PATH_DATA}/$FILE_NAME")

    init {
        load()
    }


    @Synchronized
    fun join(group: String, id: String, name: String): JoinResult {
        val observers = groups.getOrPut(group) { ArrayList() }
        if (observers.any { it.id == id }) {
            return JoinResult.ALREADY
        }
        if (observers.size >= MAX_OBSERVERS) {
            return JoinResult.FULL
        }
        observers.add(Observer(id, name))
        flush()
        return JoinResult.JOINED
    }


    @Synchronized
    fun leave(group: String, id: String): Boolean {
        val observers = groups[group] ?: return false
        val removed = observers.removeAll { it.id == id }
        if (removed) {
            flush()
        }
        return removed
    }


    @Synchronized
    fun clear(group: String) {
        if (groups.remove(group) != null) {
            flush()
        }
    }


    @Synchronized
    fun list(group: String): List<Observer> = groups[group]?.toList() ?: emptyList()


    @Synchronized
    fun isObserver(group: String, id: String): Boolean =
        groups[group]?.any { it.id == id } == true






    @Synchronized
    fun removeEverywhere(id: String): Int {
        var count = 0
        groups.values.forEach { observers ->
            if (observers.removeAll { it.id == id }) {
                count++
            }
        }
        if (count > 0) {
            flush()
        }
        return count
    }

    private fun flush() {
        try {
            val groupsJson = JSONObject()
            groups.forEach { (group, observers) ->
                if (observers.isEmpty()) {
                    return@forEach
                }
                val array = JSONArray()
                observers.forEach { observer ->
                    array.put(
                        JSONObject().apply {
                            put("id", observer.id)
                            put("name", observer.name)
                        }
                    )
                }
                groupsJson.put(group, array)
            }
            val root = JSONObject().put("groups", groupsJson)
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("OB 位落盘失败（本次运行的内存状态不受影响）：$t", LOG_TAG)
        }
    }

    private fun load() {
        if (!storeFile.isFile) {
            return
        }
        try {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            root.optJSONObject("groups")?.let { groupsJson ->
                groupsJson.keys().forEach { group ->
                    val array = groupsJson.optJSONArray(group) ?: return@forEach
                    val observers = ArrayList<Observer>()
                    for (index in 0 until array.length()) {
                        val json = array.optJSONObject(index) ?: continue
                        val id = json.optString("id")
                        if (id.isNotEmpty()) {
                            observers.add(Observer(id, json.optString("name")))
                        }
                    }
                    if (observers.isNotEmpty()) {
                        groups[group] = observers.take(MAX_OBSERVERS).toMutableList()
                    }
                }
            }
        } catch (t: Throwable) {
            DiceLogger.error("OB 位读取失败，按空库继续：$t", LOG_TAG)
        }
    }

    companion object {

        const val MAX_OBSERVERS = 10

        private const val FILE_NAME = "observers.json"
        private const val LOG_TAG = "ObserverStore"
    }
}
