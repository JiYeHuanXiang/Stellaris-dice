package stellaris.dice.diceFunction.draw

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption







class DeckContentTest {

    @TempDir
    lateinit var tempDir: Path

    private fun repoContent(): Path =
        sequenceOf(Path.of("content"), Path.of("..", "content"))
            .first { Files.isDirectory(it) }

    private fun seedDraw() {
        val src = repoContent().resolve("draw")
        val dst = tempDir.resolve("draw")
        Files.createDirectories(dst)
        Files.list(src).use { stream ->
            stream.forEach { file ->
                if (Files.isRegularFile(file)) {
                    Files.copy(file, dst.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    @Test
    fun `内容牌堆全部能装入`() {
        seedDraw()
        val manager = DrawFileManager(tempDir.toString())

        assertEquals(5, manager.fileCount, "content/draw 目前是 5 份文件")
        assertEquals(39, manager.deckCount, "公开内容共 39 堆")
        assertNotNull(manager.find("煤气灯"))
        assertNotNull(manager.find("即时症状"))
        assertNotNull(manager.find("随机姓名_cn"))
    }

    @Test
    fun `姓名组合堆的引用被解析`() {
        seedDraw()
        val manager = DrawFileManager(tempDir.toString())

        val names = manager.draw("20002", "随机姓名_cn", 10)
        assertNotNull(names)
        assertEquals(10, names!!.size)
        names.forEach { name ->
            assertFalse(name.contains('{') || name.contains('}'), "引用未被解析：$name")
        }
    }

    @Test
    fun `疯狂与煤气灯可以直接抽`() {
        seedDraw()
        val manager = DrawFileManager(tempDir.toString())

        assertNotNull(manager.draw("20002", "煤气灯", 1))
        assertNotNull(manager.draw("20002", "即时症状", 1))
        assertNotNull(manager.draw("20002", "总结症状", 1))
    }

    @Test
    fun `找不到的子堆引用原样保留`() {
        val draw = tempDir.resolve("draw")
        Files.createDirectories(draw)
        Files.write(
            draw.resolve("refs.json"),
            """{"引用测试": ["前{不存在的堆}后"]}""".toByteArray(Charsets.UTF_8)
        )
        val manager = DrawFileManager(tempDir.toString())

        assertEquals("前{不存在的堆}后", manager.draw("20002", "引用测试", 1)!!.single())
    }

    @Test
    fun `成环引用不会死循环`() {
        val draw = tempDir.resolve("draw")
        Files.createDirectories(draw)
        Files.write(
            draw.resolve("cycle.json"),
            """{"甲堆": ["{乙堆}"], "乙堆": ["{甲堆}"]}""".toByteArray(Charsets.UTF_8)
        )
        val manager = DrawFileManager(tempDir.toString())

        val result = manager.draw("20002", "甲堆", 1)!!.single()
        assertTrue(result.contains('{') || result.contains('}'), "成环时应保留未解析的引用：$result")
    }

    @Test
    fun `pending 牌堆不随包`() {
        seedDraw()
        val manager = DrawFileManager(tempDir.toString())
        assertNull(manager.find("amgc"))
        assertNull(manager.find("人偶暗示"))
    }
}
