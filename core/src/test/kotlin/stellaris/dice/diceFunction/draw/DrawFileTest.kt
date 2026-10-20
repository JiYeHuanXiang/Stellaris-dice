package stellaris.dice.diceFunction.draw

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File







class DrawFileTest {

    private fun file(text: String) = DrawFile(text, "test.json", 0L, false)

    @Test
    fun `JSON 方言 顶层键即牌堆`() {
        val f = file("""{"水果":["苹果","香蕉"]}""")
        assertEquals(DrawFileFormat.Json, f.format)
        assertEquals(1, f.size)
        val deck = f.findData("水果")
        assertNotNull(deck)
        assertEquals(2, deck!!.remain())
        assertTrue(deck.draw(true) in setOf("苹果", "香蕉"))
    }


    @Test
    fun `权重前缀写法 抽干后次数等于权重`() {
        val f = file("""{"w":["::3::甲","::1::乙"]}""")
        val drained = drain(f.findData("w")!!)
        assertEquals(mapOf("甲" to 3, "乙" to 1), drained)
        assertEquals(4, drained.values.sum())
    }


    @Test
    fun `权重方括号写法 等价于不加括号`() {
        val f = file("""{"w":["::[2]::甲","乙"]}""")
        assertEquals(mapOf("甲" to 2, "乙" to 1), drain(f.findData("w")!!))
    }


    @Test
    fun `JSON 里混入非数组键 整份改走 JSON5`() {
        val f = file("""{"a":["x"],"meta":"不是数组"}""")
        assertEquals(DrawFileFormat.Json5, f.format)
        assertEquals(1, f.size)
        assertNotNull(f.findData("a"))

        assertTrue(f.metaData.isEmpty())
    }


    @Test
    fun `YAML 方言 command 与权重后缀`() {
        val f = file(
            """
            command: draw
            includes:
              - default
            default:
              - 苹果
              - 香蕉***2***
            自定义:
              - 梨
            """.trimIndent()
        )
        assertEquals(DrawFileFormat.Yaml, f.format)
        assertEquals("draw", f.metaData["command"])
        assertEquals(mapOf("苹果" to 1, "香蕉" to 2), drain(f.findData("draw")!!))

        assertNotNull(f.findData("draw 自定义"))
    }


    @Test
    fun `YAML 缺 command 则加载失败`() {

        assertThrows(IllegalStateException::class.java) { file("default:\n  - 甲\n") }
    }

    @Test
    fun `YAML 重复键被拒绝`() {
        assertThrows(IllegalStateException::class.java) {
            file(
                """
                command: draw
                command: draw
                default:
                  - 甲
                """.trimIndent()
            )
        }
    }


    @TempDir
    lateinit var temp: File

    @Test
    fun `yaml 反序列化 gadget 被拒绝且不落地文件`() {
        val canary = File(temp, "canary.txt")
        val source = "!!java.io.FileOutputStream [ !!str \"${canary.path.replace('\\', '/')}\" ]"
        assertThrows(IllegalStateException::class.java) {
            DrawFile(source, "evil.yaml", 1L, false)
        }
        assertFalse(canary.exists(), "gadget 类不应被实例化：${canary.path}")
    }


    @Test
    fun `下划线开头的牌堆隐藏但仍可抽取`() {
        val f = file("""{"_内":["秘"],"外":["显"]}""")
        assertNotNull(f.findData("_内"))
        assertTrue(f.getCommands().contains("draw 外"))
        assertTrue(!f.getCommands().contains("draw _内"))
        assertTrue(f.search("内").isEmpty())
    }


    @Test
    fun `标题匹配忽略大小写`() {
        val f = file("""{"Apple":["x"]}""")
        assertNotNull(f.findData("apple"))
        assertNotNull(f.findData("APPLE"))
    }


    @Test
    fun `clone 是独立副本 母堆不受影响`() {
        val f = file("""{"w":["甲","乙"]}""")
        val deck = f.findData("w")!!
        val copy = deck.clone()
        assertEquals(2, drain(copy).values.sum())
        assertEquals(2, deck.remain(), "抽干副本不该动到母堆")
    }


    @Test
    fun `抽空后再抽抛异常`() {
        val f = file("""{"w":["甲"]}""")
        val deck = f.findData("w")!!
        drain(deck)
        assertThrows(IllegalStateException::class.java) { deck.draw(true) }
    }


    @Test
    fun `空内容得到空牌堆`() {
        val f = DrawFile(null, "empty.json", 0L, false)
        assertEquals(DrawFileFormat.Unknown, f.format)
        assertEquals(0, f.size)
        assertNull(f.findData("任何"))
    }


    @Test
    fun `找不到的牌堆返回 null`() {
        assertNull(file("""{"w":["甲"]}""").findData("不存在"))
    }


    private fun drain(deck: DrawData): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        while (!deck.isEmpty()) {
            val item = deck.draw(false)
            counts[item] = (counts[item] ?: 0) + 1
        }
        return counts
    }
}
