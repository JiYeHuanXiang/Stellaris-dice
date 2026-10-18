package stellaris.dice.diceFunction.draw

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.DiceConstant
import java.nio.file.Files
import java.nio.file.Path








class DrawFileManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeDeck(fileName: String, text: String) {
        val dir = tempDir.resolve(DiceConstant.SAVE_PATH_DRAW)
        Files.createDirectories(dir)
        Files.write(dir.resolve(fileName), text.toByteArray(Charsets.UTF_8))
    }


    @Test
    fun `装入牌堆并能抽到条目`() {
        writeDeck("d1.json", """{"测试堆": ["甲","乙"]}""")
        val manager = DrawFileManager(tempDir.toString())

        assertEquals(1, manager.fileCount)
        assertEquals(1, manager.deckCount)
        assertEquals(listOf("测试堆"), manager.titles())

        val deck = manager.find("测试堆")!!
        val drawn = deck.draw(putBack = true)
        assertTrue(drawn == "甲" || drawn == "乙", "抽到的该是本堆的条目：$drawn")
        assertEquals(2, deck.remain(), "放回抽取不该消耗牌堆")
    }


    @Test
    fun `标题忽略大小写`() {
        writeDeck("d1.json", """{"Deck": ["x"]}""")
        assertEquals("x", DrawFileManager(tempDir.toString()).find("deck")!!.draw(true))
    }


    @Test
    fun `隐藏牌堆可抽不可列`() {
        writeDeck("d1.json", """{"_隐藏": ["暗"], "公开": ["明"]}""")
        val manager = DrawFileManager(tempDir.toString())

        assertEquals(listOf("公开"), manager.titles(), "隐藏堆不该出现在清单里")
        assertEquals("暗", manager.find("_隐藏")!!.draw(true), "但按名字要抽得到")
    }


    @Test
    fun `坏文件跳过 其余照常`() {
        writeDeck("bad.json", "{ 这不是合法 JSON")
        writeDeck("good.json", """{"好堆": ["a"]}""")
        val manager = DrawFileManager(tempDir.toString())

        assertEquals(1, manager.fileCount, "坏文件不该被算进去")
        assertEquals("a", manager.find("好堆")!!.draw(true))
    }


    @Test
    fun `重扫能发现新文件`() {
        val manager = DrawFileManager(tempDir.toString())
        assertEquals(0, manager.fileCount)

        writeDeck("late.json", """{"后来的": ["z"]}""")
        assertEquals(1, manager.reload())
        assertEquals("z", manager.find("后来的")!!.draw(true))
    }


    @Test
    fun `没有 draw 目录时不报错`() {
        val manager = DrawFileManager(tempDir.toString())
        assertEquals(0, manager.fileCount)
        assertEquals(0, manager.deckCount)
        assertTrue(manager.titles().isEmpty())
        assertNull(manager.find("任何堆"))
    }





    @Test
    fun `sdeck 文件不再被当作牌堆装入`() {
        writeDeck("secret.sdeck", "not-a-real-sdeck")
        assertEquals(0, DrawFileManager(tempDir.toString()).fileCount)
    }


    @Test
    fun `不放回抽取 抽空自动回满`() {
        writeDeck("d1.json", """{"小堆": ["甲","乙"]}""")
        val manager = DrawFileManager(tempDir.toString())

        val first = manager.draw("20002", "小堆", 1)!!.single()
        val second = manager.draw("20002", "小堆", 1)!!.single()
        assertTrue(first != second, "不放回：两次抽到的条目要不同（$first / $second）")
        assertEquals(0, manager.remain("20002", "小堆"), "两张都抽完")

        val third = manager.draw("20002", "小堆", 1)!!.single()
        assertTrue(third == "甲" || third == "乙")
        assertEquals(1, manager.remain("20002", "小堆"), "回满后又剩一张")
    }


    @Test
    fun `一次抽多张`() {
        writeDeck("d1.json", """{"三张": ["a","b","c"]}""")
        val drawn = DrawFileManager(tempDir.toString()).draw("20002", "三张", 3)!!
        assertEquals(setOf("a", "b", "c"), drawn.toSet())
        assertEquals(3, drawn.size, "三张互不重复")
    }


    @Test
    fun `抽取进度按群分开`() {
        writeDeck("d1.json", """{"小堆": ["甲","乙"]}""")
        val manager = DrawFileManager(tempDir.toString())
        manager.draw("20002", "小堆", 2)
        assertEquals(0, manager.remain("20002", "小堆"))
        assertEquals(2, manager.remain("30003", "小堆"), "另一个群还是满的")
    }


    @Test
    fun `重置抽取进度`() {
        writeDeck("d1.json", """{"小堆": ["甲","乙"]}""")
        val manager = DrawFileManager(tempDir.toString())
        manager.draw("20002", "小堆", 2)
        assertEquals(0, manager.remain("20002", "小堆"))

        assertEquals(1, manager.reset("20002", "小堆"))
        assertEquals(2, manager.remain("20002", "小堆"))

        manager.draw("20002", "小堆", 1)
        manager.draw("20002", "不存在的堆", 1)
        assertEquals(1, manager.reset("20002"), "不带名字 = 清本群全部（实际只有 小堆 一份）")
    }


    @Test
    fun `抽不存在的堆返回 null`() {
        assertNull(DrawFileManager(tempDir.toString()).draw("20002", "没有这堆", 1))
    }


    @Test
    fun `同名堆取文件名靠前的`() {
        writeDeck("a.json", """{"同名": ["先"]}""")
        writeDeck("b.json", """{"同名": ["后"]}""")
        assertEquals("先", DrawFileManager(tempDir.toString()).find("同名")!!.draw(true))
    }




    @Test
    fun `临时牌堆 抽不放回且自动回满`() {
        val manager = DrawFileManager(tempDir.toString())
        assertTrue(manager.setInlineDeck("20002", listOf("甲", "乙", "丙")))
        assertEquals(3, manager.inlineRemain("20002"))
        assertEquals("#inline", manager.runtimeDefaultName("20002"))

        val first = manager.draw("20002", "#inline", 3)!!
        assertEquals(setOf("甲", "乙", "丙"), first.toSet())
        assertEquals(0, manager.remain("20002", "#inline"))

        val refilled = manager.draw("20002", "#inline", 1)!!
        assertEquals(1, refilled.size, "抽空后自动回满")
        assertEquals(3, manager.inlineRemain("20002"), "母堆不被消耗")
    }


    @Test
    fun `临时牌堆 条目数与覆盖`() {
        val manager = DrawFileManager(tempDir.toString())
        assertTrue(!manager.setInlineDeck("20002", listOf("只有一条")))
        assertTrue(manager.setInlineDeck("20002", listOf("1"), minimumEntries = 1))


        manager.draw("20002", "#inline", 1)
        assertTrue(manager.setInlineDeck("20002", listOf("a", "b")))
        assertEquals(2, manager.remain("20002", "#inline"), "新建的堆从满堆开始")
    }


    @Test
    fun `临时牌堆 按群分开与清除默认`() {
        val manager = DrawFileManager(tempDir.toString())
        manager.setInlineDeck("20002", listOf("甲", "乙"))
        assertEquals(0, manager.inlineRemain("30003"), "另一个群没有临时堆")
        assertNull(manager.draw("30003", "#inline", 1))

        manager.clearRuntimeDefault("20002")
        assertNull(manager.runtimeDefaultName("20002"))
    }
}
