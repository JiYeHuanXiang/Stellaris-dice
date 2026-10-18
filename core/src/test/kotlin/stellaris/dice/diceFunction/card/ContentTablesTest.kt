package stellaris.dice.diceFunction.card

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption







class ContentTablesTest {

    @TempDir
    lateinit var tempDir: Path

    private fun repoContent(): Path =
        sequenceOf(Path.of("content"), Path.of("..", "content"))
            .first { Files.isDirectory(it) }

    private fun seedData() {
        val src = repoContent().resolve("data")
        val dst = tempDir.resolve("data")
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
    fun `技能表与别名表被读入并用于读卡`() {
        seedData()
        val manager = PlayerCardManager(tempDir.toString())
        val card = manager.getCurrentPlayer("10001", "20002")

        assertEquals(25, card.readProperty("侦查"), "侦查的标准值应为 25")
        assertEquals(0, card.readProperty("克苏鲁神话"), "克苏鲁神话的标准值应为 0")

        assertTrue(CardEditor.apply(card, "str65") is CardEditor.Outcome.Updated)
        assertEquals(65, card.readProperty("力量"), "str 应归一到力量")

        assertTrue(CardEditor.apply(card, "san50") is CardEditor.Outcome.Updated)
        assertEquals(50, card.readProperty("理智"), "san 应归一到理智")

        assertTrue(CardEditor.apply(card, "hp-5") is CardEditor.Outcome.Updated)
        assertEquals(-4, card.readProperty("体力"), "hp 应归一到体力，并从缺省值 1 起算")
    }

    @Test
    fun `内容表缺席时按旧口径回退`() {
        val manager = PlayerCardManager(tempDir.toString())
        val card = manager.getCurrentPlayer("10001", "20002")

        assertEquals(1, card.readProperty("侦查"), "没有技能表时缺省值为 1")
        assertTrue(CardEditor.apply(card, "str65") is CardEditor.Outcome.Updated)
        assertEquals(65, card.readProperty("str"), "没有别名表时属性名原样保留")
        assertTrue(CardEditor.apply(card, "san50") is CardEditor.Outcome.Updated)
        assertEquals(50, card.readProperty("理智"), "san 是手册明确的最小例外，始终归一")
    }
}
