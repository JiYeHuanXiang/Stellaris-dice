package stellaris.dice.diceFunction.draw

import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File






















class DrawFileManager(private val rootPath: String) {

    private var files: ArrayList<DrawFile> = ArrayList()


    private var engine = DrawEngine(files)

    init {
        reload()
    }


    private val drawDirectory: File
        get() = File(rootPath, DiceConstant.SAVE_PATH_DRAW)






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
                            hidden = false
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








    @Synchronized
    fun setInlineDeck(group: String, entries: List<String>, minimumEntries: Int = 2): Boolean {
        if (entries.size < minimumEntries) {
            return false
        }
        val deck = DrawData()
        entries.forEach { deck.appendData(it) }
        deck.freshPriorities()
        inlineMasters[group] = deck
        workingDecks[group]?.remove(INLINE_DECK_NAME)
        defaultNames[group] = INLINE_DECK_NAME
        return true
    }


    @Synchronized
    fun inlineRemain(group: String): Int = inlineMasters[group]?.remain() ?: 0


    @Synchronized
    fun runtimeDefaultName(group: String): String? = defaultNames[group]


    @Synchronized
    fun clearRuntimeDefault(group: String) {
        defaultNames.remove(group)
    }






    @Synchronized
    fun draw(group: String, name: String, count: Int): List<String>? =
        drawResolved(group, name, count, linkedSetOf(name), 0)











    private fun drawResolved(
        group: String,
        name: String,
        count: Int,
        seen: Set<String>,
        depth: Int
    ): List<String>? {
        val master = if (name == INLINE_DECK_NAME) {
            inlineMasters[group] ?: return null
        } else {
            find(name) ?: return null
        }
        val decks = workingDecks.getOrPut(group) { HashMap() }
        val drawn = ArrayList<String>()
        repeat(count.coerceAtLeast(1)) {
            var deck = decks[name]
            if (deck == null || deck.isEmpty()) {

                deck = master.clone()
                decks[name] = deck
            }
            drawn.add(resolveReferences(group, deck.draw(putBack = false), seen, depth))
        }
        return drawn
    }

    private fun resolveReferences(
        group: String,
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
                group,
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
    fun remain(group: String, name: String): Int? {
        val master = if (name == INLINE_DECK_NAME) {
            inlineMasters[group] ?: return null
        } else {
            find(name) ?: return null
        }
        return workingDecks[group]?.get(name)?.remain() ?: master.remain()
    }







    @Synchronized
    fun reset(group: String, name: String? = null): Int {
        val decks = workingDecks[group] ?: return 0
        return if (name == null) {
            val size = decks.size
            decks.clear()
            size
        } else {
            if (decks.remove(name) != null) 1 else 0
        }
    }

    private companion object {
        const val LOG_TAG = "DrawFiles"


        val REFERENCE = Regex("\\{([^{}]+)}")


        const val MAX_REFERENCE_DEPTH = 8







        const val INLINE_DECK_NAME = "#inline"
    }
}
