package stellaris.dice.diceFunction.draw

import org.json.JSONArray
import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File






















class DrawFileManager(private val rootPath: String) {

    private var files: ArrayList<DrawFile> = ArrayList()


    private var engine = DrawEngine(files)


    private val drawDirectory: File
        get() = File(rootPath, DiceConstant.SAVE_PATH_DRAW)


    private val stateFile: File
        get() = File(File(rootPath, DiceConstant.SAVE_PATH_DATA), STATE_FILE_NAME)






    @Synchronized
    fun reload(): Int {
        val loaded = ArrayList<DrawFile>()
        val directory = drawDirectory
        directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                try {
                    loaded.add(
                        DrawFile(
                            text = file.readText(Charsets.UTF_8),
                            fileName = file.name,

                            uuid = file.name.hashCode().toLong(),
                            hidden = file.name.startsWith('_')
                        )
                    )
                } catch (t: Throwable) {
                    DiceLogger.error("牌堆文件加载失败，已跳过：${file.name}（$t）", LOG_TAG)
                }
            }
        files = loaded
        engine = DrawEngine(files)
        return loaded.size
    }


    fun find(name: String): DrawData? = engine.getDrawData(name)


    val fileCount: Int
        get() = files.size


    val deckCount: Int
        get() = files.sumOf { it.size }







    fun titles(): List<String> = files.flatMap { it.search("") }


    fun search(keyword: String): List<String> = files.flatMap { it.search(keyword) }









    private val workingDecks = HashMap<String, HashMap<String, DrawData>>()







    private val inlineMasters = HashMap<String, DrawData>()


    private val defaultNames = HashMap<String, String>()

    init {
        reload()
        loadState()
    }








    @Synchronized
    fun setInlineDeck(identity: String, entries: List<String>, minimumEntries: Int = 2): Boolean {
        if (entries.size < minimumEntries) {
            return false
        }
        val deck = DrawData()
        entries.forEach { deck.appendData(it) }
        deck.freshPriorities()
        inlineMasters[identity] = deck
        workingDecks[identity]?.remove(INLINE_DECK_NAME)
        defaultNames[identity] = INLINE_DECK_NAME
        return true
    }


    @Synchronized
    fun inlineRemain(identity: String): Int = inlineMasters[identity]?.remain() ?: 0


    @Synchronized
    fun runtimeDefaultName(identity: String): String? = defaultNames[identity]


    @Synchronized
    fun clearRuntimeDefault(identity: String) {
        defaultNames.remove(identity)
    }






    @Synchronized
    fun draw(identity: String, name: String, count: Int): List<String>? {
        val drawn = drawResolved(identity, name, count, linkedSetOf(name), 0)
        if (drawn != null) {
            persist()
        }
        return drawn
    }











    private fun drawResolved(
        identity: String,
        name: String,
        count: Int,
        seen: Set<String>,
        depth: Int
    ): List<String>? {
        val master = if (name == INLINE_DECK_NAME) {
            inlineMasters[identity] ?: return null
        } else {
            find(name) ?: return null
        }
        val decks = workingDecks.getOrPut(identity) { HashMap() }
        val drawn = ArrayList<String>()
        repeat(count.coerceAtLeast(1)) {
            var deck = decks[name]
            if (deck == null || deck.isEmpty()) {

                deck = master.clone()
                decks[name] = deck
            }
            drawn.add(resolveReferences(identity, deck.draw(putBack = false), seen, depth))
        }
        return drawn
    }

    private fun resolveReferences(
        identity: String,
        text: String,
        seen: Set<String>,
        depth: Int
    ): String {
        if (depth >= MAX_REFERENCE_DEPTH || !text.contains('{')) {
            return text
        }
        var result = text
        REFERENCE.findAll(text).forEach { match ->
            val refName = match.groupValues[1].trim().trimStart('%', '$')
            if (refName.isEmpty() || refName in seen || find(refName) == null) {
                return@forEach
            }
            val nested = drawResolved(
                identity,
                refName,
                1,
                seen + refName,
                depth + 1
            )?.firstOrNull() ?: return@forEach
            result = result.replaceFirst(match.value, nested)
        }
        return result
    }






    @Synchronized
    fun remain(identity: String, name: String): Int? {
        val master = if (name == INLINE_DECK_NAME) {
            inlineMasters[identity] ?: return null
        } else {
            find(name) ?: return null
        }
        return workingDecks[identity]?.get(name)?.remain() ?: master.remain()
    }







    @Synchronized
    fun reset(identity: String, name: String? = null): Int {
        val decks = workingDecks[identity] ?: return 0
        val cleared = if (name == null) {
            val size = decks.size
            decks.clear()
            if (size > 0) workingDecks.remove(identity)
            size
        } else {
            if (decks.remove(name) != null) 1 else 0
        }
        if (cleared > 0) {
            persist()
        }
        return cleared
    }

    @Synchronized
    private fun loadState() {
        if (!stateFile.isFile) {
            return
        }
        try {
            val root = JSONObject(stateFile.readText(Charsets.UTF_8))
            val identities = root.keys()
            while (identities.hasNext()) {
                val identity = identities.next()
                val entry = root.optJSONObject(identity) ?: continue
                val decks = entry.optJSONObject(FIELD_DECKS) ?: continue
                val restored = HashMap<String, DrawData>()
                val names = decks.keys()
                while (names.hasNext()) {
                    val name = names.next()
                    val master = find(name) ?: continue
                    val array = decks.optJSONArray(name) ?: continue
                    val entries = ArrayList<Pair<String, Long>>()
                    for (i in 0 until array.length()) {
                        val pair = array.optJSONArray(i) ?: continue
                        val text = pair.optString(0, "")
                        val weight = pair.optLong(1, 1L)
                        if (text.isNotEmpty()) {
                            entries.add(text to weight)
                        }
                    }
                    if (entries.isNotEmpty()) {
                        restored[name] = DrawData.restore(
                            master.parent,
                            master.title,
                            master.hidden,
                            entries
                        )
                    }
                }
                if (restored.isNotEmpty()) {
                    workingDecks[identity] = restored
                }
            }
        } catch (t: Throwable) {
            DiceLogger.error("牌堆进度读取失败，按满堆继续：$t", LOG_TAG)
        }
    }

    @Synchronized
    private fun persist() {
        try {
            val root = JSONObject()
            workingDecks.forEach { (identity, decks) ->
                val out = JSONObject()
                decks.forEach { (name, deck) ->
                    if (deck.isEmpty()) {
                        return@forEach
                    }
                    val array = JSONArray()
                    deck.snapshot().forEach { (text, weight) ->
                        array.put(JSONArray().put(text).put(weight))
                    }
                    out.put(name, array)
                }
                if (out.length() > 0) {
                    root.put(identity, JSONObject().put(FIELD_DECKS, out))
                }
            }
            stateFile.parentFile?.mkdirs()
            stateFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("牌堆进度落盘失败（本次运行的内存进度不受影响）：$t", LOG_TAG)
        }
    }

    private companion object {
        const val LOG_TAG = "DrawFiles"

        const val STATE_FILE_NAME = "deckstate.json"

        const val FIELD_DECKS = "decks"


        val REFERENCE = Regex("\\{([^{}]+)}")


        const val MAX_REFERENCE_DEPTH = 8







        const val INLINE_DECK_NAME = "#inline"
    }
}
