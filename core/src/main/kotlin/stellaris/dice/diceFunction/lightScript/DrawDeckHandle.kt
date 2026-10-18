package stellaris.dice.diceFunction.lightScript

import stellaris.dice.diceFunction.draw.DrawData

/**
 * 把牌核的 [DrawData] 包成 Lua `draw.*` 可用的句柄。
 * 传入工作副本（`clone()`）。
 */
class DrawDeckHandle(private val data: DrawData) : PluginDeck {

    @Synchronized
    override fun read(putBack: Boolean): String? = try {
        if (data.isEmpty()) null else data.draw(putBack)
    } catch (t: Throwable) {
        null
    }

    @Synchronized
    override fun write(content: String, weight: String): Boolean {
        if (content.isEmpty()) return false
        data.appendData(content, weight.ifEmpty { "1" })
        data.freshPriorities()
        return true
    }

    @Synchronized
    override fun remain(): Int = data.remain()

    override fun recycle() {
    }
}
