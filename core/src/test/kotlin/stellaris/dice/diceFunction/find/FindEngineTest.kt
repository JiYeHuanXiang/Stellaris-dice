package stellaris.dice.diceFunction.find

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.DiceConstant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption





class FindEngineTest {

    @TempDir
    lateinit var tempDir: Path

    private fun repoContent(): Path =
        sequenceOf(Path.of("content"), Path.of("..", "content"))
            .first { Files.isDirectory(it) }

    private fun engine(): FindEngine {
        val src = repoContent().resolve("find")
        val dst = tempDir.resolve(DiceConstant.SAVE_PATH_FIND)
        Files.createDirectories(dst)
        Files.list(src).use { stream ->
            stream.forEach { file ->
                if (Files.isRegularFile(file)) {
                    Files.copy(file, dst.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        val entries = (1..12).joinToString(",") { """ "甲%02d":"共同内容" """.trim().format(it) }
        Files.write(
            dst.resolve("TEST-测试.json"),
            """{"mod":"测试","helpdoc":{$entries}}""".toByteArray(Charsets.UTF_8)
        )
        return FindEngine(tempDir.toString())
    }

    @Test
    fun `装载并检索火球术`() {
        val engine = engine()
        assertTrue(engine.groups().contains("5E"), engine.groups().toString())
        assertTrue(engine.groups().contains("COC7"), engine.groups().toString())

        val result = engine.search(engine.parse("火球"), null, "20002")
        assertTrue(result is FindEngine.Result.One, result.toString())
        val text = engine.render(result)
        assertTrue(text.contains("火球"), text)
    }

    @Test
    fun `分组限定与同义词`() {
        val engine = engine()
        val result = engine.search(engine.parse("#5E 火球"), null, "20002")
        assertTrue(result is FindEngine.Result.One, result.toString())


        val synonym = engine.search(engine.parse("侦察"), null, "20002")
        assertTrue(synonym is FindEngine.Result.One || synonym is FindEngine.Result.ListPage, synonym.toString())
        assertTrue(engine.render(synonym).contains("侦查"), engine.render(synonym))
    }

    @Test
    fun `分页编号与随机`() {
        val engine = engine()
        val page = engine.search(engine.parse("#TEST 共同 -n 5 -p 2"), null, "20002")
        assertTrue(page is FindEngine.Result.ListPage, page.toString())
        val list = page as FindEngine.Result.ListPage
        assertEquals(5, list.pageSize)
        assertTrue(list.page >= 1)

        val random = engine.search(engine.parse("#TEST --rand"), null, "20002")
        assertTrue(random is FindEngine.Result.One, random.toString())

        val recall = engine.search(engine.parse("1"), null, "20002")
        assertTrue(recall is FindEngine.Result.One, recall.toString())
    }

    @Test
    fun `找不到时给出分组提示`() {
        val engine = engine()
        val result = engine.search(engine.parse("不存在的词条xyz"), null, "20002")
        assertTrue(result is FindEngine.Result.Error, result.toString())
        assertTrue(engine.render(result).contains("可用分组"), engine.render(result))
    }
}
