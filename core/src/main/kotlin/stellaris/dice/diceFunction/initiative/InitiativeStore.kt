package stellaris.dice.diceFunction.initiative

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File






















class InitiativeList {


    val entries = ArrayList<InitiativeEntry>()


    var currentIndex = 0
        private set


    var rounds = 1
        private set



    fun size(): Int = entries.size

    fun isEmpty(): Boolean = entries.isEmpty()


    fun findExact(name: String): InitiativeEntry? =
        entries.firstOrNull { it.name.equals(name, ignoreCase = true) }


    fun search(keyword: String): List<InitiativeEntry> {
        if (keyword.isEmpty()) {
            return emptyList()
        }
        return entries.filter { it.name.contains(keyword, ignoreCase = true) }
    }

    fun current(): InitiativeEntry? = entries.getOrNull(currentIndex)


    fun upcoming(): InitiativeEntry? =
        if (entries.isEmpty()) null else entries[(currentIndex + 1) % entries.size]








    fun put(name: String, expression: String, value: Int, ownerId: String): Boolean {
        val existing = findExact(name)
        if (existing != null) {
            existing.expression = expression
            existing.value = value
        } else {
            if (entries.size >= DiceConstant.MAX_FIRSTLY_ATTACKING_VALUE_COUNT) {
                return false
            }
            entries.add(InitiativeEntry(name, expression, value, ownerId))
        }
        sort()
        return true
    }






    fun next(): InitiativeEntry? {
        if (entries.isEmpty()) {
            return null
        }
        val nextIndex = (currentIndex + 1) % entries.size
        if (nextIndex == 0) {
            rounds++
        }
        currentIndex = nextIndex
        return current()
    }


    fun remove(entry: InitiativeEntry): Boolean {
        val index = entries.indexOf(entry)
        if (index < 0) {
            return false
        }
        entries.removeAt(index)
        if (index < currentIndex) {
            currentIndex--
        }
        coerceIndex()
        return true
    }


    fun clear() {
        entries.clear()
        currentIndex = 0
        rounds = 1
    }


    fun sort() {
        entries.sortWith(compareByDescending { it.value })
        coerceIndex()
    }


    fun restore(index: Int, rounds: Int) {
        currentIndex = if (entries.isEmpty()) 0 else index.coerceIn(0, entries.size - 1)
        this.rounds = rounds.coerceAtLeast(1)
    }

    private fun coerceIndex() {
        currentIndex = if (entries.isEmpty()) 0 else currentIndex.coerceIn(0, entries.size - 1)
    }
}













class InitiativeEntry(
    val name: String,
    var expression: String,
    var value: Int,
    val ownerId: String,
    var hp: Int? = null,
    var maxHp: Int? = null,
    var damage: Int = 0,
    val buffs: MutableList<String> = ArrayList()
) {


    fun setHp(current: Int, max: Int?) {
        maxHp = max ?: maxHp
        damage = 0
        hp = if (maxHp != null) current.coerceAtMost(maxHp!!) else current
    }






    fun addHp(delta: Int): Boolean {
        val current = hp
        if (current == null) {
            damage = (damage - delta).coerceAtLeast(0)
            return false
        }
        var next = current + delta
        maxHp?.let { if (next > it) next = it }
        hp = next
        return next <= 0
    }







    fun applyBuff(mode: BuffMode, status: String) {
        if (status.isEmpty()) {
            buffs.clear()
            return
        }
        when (mode) {
            BuffMode.ADD -> if (status !in buffs && buffs.size < MAX_BUFFS) buffs.add(status)
            BuffMode.REMOVE -> buffs.removeAll { it == status }
            BuffMode.OVERWRITE -> {
                buffs.clear()
                buffs.add(status)
            }
        }
    }


    fun statText(): String {
        val parts = ArrayList<String>()
        val currentHp = hp
        if (currentHp != null) {
            parts.add(if (maxHp != null) "血量:$currentHp/$maxHp" else "血量:$currentHp")
        } else if (damage > 0) {
            parts.add("扣血:$damage")
        }
        if (buffs.isNotEmpty()) {
            parts.add(buffs.joinToString(" "))
        }
        return parts.joinToString(" ")
    }

    override fun toString(): String {
        val stat = statText()
        return "$name 先攻:$value" + if (stat.isEmpty()) "" else " $stat"
    }


    enum class BuffMode { ADD, REMOVE, OVERWRITE }

    private companion object {

        const val MAX_BUFFS = 14
    }
}







class InitiativeStore(private val rootPath: String) {

    private val groups = LinkedHashMap<String, InitiativeList>()

    private val storeFile: File
        get() = File(rootPath, "${DiceConstant.SAVE_PATH_DATA}/$FILE_NAME")

    init {
        load()
    }


    @Synchronized
    fun get(group: String): InitiativeList = groups.getOrPut(group) { InitiativeList() }


    @Synchronized
    fun peek(group: String): InitiativeList? = groups[group]


    @Synchronized
    fun save(group: String) {
        flush()
    }

    private fun flush() {
        try {
            val groupsJson = JSONObject()
            groups.forEach { (group, list) ->
                if (list.isEmpty()) {
                    return@forEach
                }
                val entries = org.json.JSONArray()
                list.entries.forEach { entry ->
                    entries.put(
                        JSONObject().apply {
                            put("name", entry.name)
                            put("expression", entry.expression)
                            put("value", entry.value)
                            put("ownerId", entry.ownerId)
                            entry.hp?.let { put("hp", it) }
                            entry.maxHp?.let { put("maxHp", it) }
                            put("damage", entry.damage)
                            put("buffs", org.json.JSONArray(entry.buffs))
                        }
                    )
                }
                groupsJson.put(
                    group,
                    JSONObject().apply {
                        put("currentIndex", list.currentIndex)
                        put("rounds", list.rounds)
                        put("entries", entries)
                    }
                )
            }
            val root = JSONObject().put("groups", groupsJson)
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("先攻列表落盘失败（本次运行的内存状态不受影响）：$t", LOG_TAG)
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
                    val json = groupsJson.optJSONObject(group) ?: return@forEach
                    val list = InitiativeList()
                    val entries = json.optJSONArray("entries")
                    if (entries != null) {
                        for (index in 0 until entries.length()) {
                            val entryJson = entries.optJSONObject(index) ?: continue
                            val entry = InitiativeEntry(
                                name = entryJson.optString("name"),
                                expression = entryJson.optString("expression"),
                                value = entryJson.optInt("value"),
                                ownerId = entryJson.optString("ownerId"),
                                hp = if (entryJson.has("hp")) entryJson.optInt("hp") else null,
                                maxHp = if (entryJson.has("maxHp")) entryJson.optInt("maxHp") else null,
                                damage = entryJson.optInt("damage")
                            )
                            entryJson.optJSONArray("buffs")?.let { buffs ->
                                for (buffIndex in 0 until buffs.length()) {
                                    entry.buffs.add(buffs.optString(buffIndex))
                                }
                            }
                            if (entry.name.isNotEmpty()) {
                                list.entries.add(entry)
                            }
                        }
                    }
                    list.sort()
                    list.restore(
                        index = json.optInt("currentIndex", 0),
                        rounds = json.optInt("rounds", 1)
                    )
                    groups[group] = list
                }
            }
        } catch (t: Throwable) {
            DiceLogger.error("先攻列表读取失败，按空库继续：$t", LOG_TAG)
        }
    }

    private companion object {
        const val FILE_NAME = "initiative.json"
        const val LOG_TAG = "InitiativeStore"
    }
}
