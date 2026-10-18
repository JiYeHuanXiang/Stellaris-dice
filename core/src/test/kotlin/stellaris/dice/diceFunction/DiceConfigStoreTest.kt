package stellaris.dice.diceFunction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.responder.CocHouseRule
import stellaris.dice.diceFunction.responder.DefaultSentences
import java.io.File
import java.nio.file.Path







class DiceConfigStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private fun store() = DiceConfigStore(tempDir.toString())

    private fun configFile() = File(tempDir.toString(), "${DiceConstant.SAVE_PATH_DATA}/config.json")


    @Test
    fun `全局配置落盘后能读回`() {
        store().apply {
            writeGlobal(DiceConfigStore.MASTER_QQ, "10001\n10002")
            writeGlobal(DiceConfigStore.SENTENCE_ROLL, "自定义{roll}")
        }

        store().let { reloaded ->
            assertTrue(reloaded.isMaster("10002"), "master 名单要能读回")
            assertFalse(reloaded.isMaster("9"), "不在名单里的不是 master")
            assertEquals("自定义{roll}", reloaded.readGlobal(DiceConfigStore.SENTENCE_ROLL))
            assertEquals("自定义{roll}", reloaded.sentenceRoll, "措辞面跟着配置走")
        }
    }


    @Test
    fun `群与个人配置落盘后按回退链读回`() {
        store().apply {
            writeGroup("20002", DiceConfigStore.DEFAULT_DICE_NUMBER, "20")
            writePerson("10003", DiceConfigStore.COC_HOUSE_RULE, "3")
        }

        store().let { reloaded ->
            assertEquals(20, reloaded.defaultDiceNumber(true, "20002", "10003"), "群配置")
            assertEquals(CocHouseRule.THREE, reloaded.cocHouseRule(false, "20002", "10003"), "个人配置")
            assertEquals(
                100,
                reloaded.defaultDiceNumber(true, "99999", "10003"),
                "别的群没配过 → 回落全局出厂默认"
            )
        }
    }


    @Test
    fun `房规档位落盘`() {
        store().writeCocHouseRule(true, "20002", "10003", CocHouseRule.FIVE)
        assertEquals(CocHouseRule.FIVE, store().cocHouseRule(true, "20002", "10003"))
    }






    @Test
    fun `出厂默认不落盘`() {

        assertEquals(DefaultSentences.sentenceRoll, store().sentenceRoll)
        store().writeGlobal("CUSTOM_KEY", "1")

        val text = configFile().readText(Charsets.UTF_8)
        assertTrue(text.contains("CUSTOM_KEY"), "写进去的键要在文件里：$text")
        assertFalse(
            text.contains(DiceConfigStore.SENTENCE_ROLL),
            "没写过的默认值不该被落盘：$text"
        )
    }


    @Test
    fun `坏文件按默认继续`() {
        configFile().parentFile?.mkdirs()
        configFile().writeText("{ 这不是合法 JSON", Charsets.UTF_8)

        store().let {
            assertEquals(DefaultSentences.sentenceRoll, it.sentenceRoll)
            assertFalse(it.isMaster("10001"))
        }
    }


    @Test
    fun `没有配置文件时用出厂默认`() {
        assertFalse(configFile().exists())
        store().let {
            assertEquals(DefaultSentences.sentenceRoll, it.sentenceRoll)
            assertEquals("100", it.readGlobal(DiceConfigStore.DEFAULT_DICE_NUMBER))
            assertEquals(100, it.defaultDiceNumber(true, "20002", "10003"))
        }
    }


    @Test
    fun `同名键后写覆盖先写`() {
        store().apply {
            writeGlobal(DiceConfigStore.SENTENCE_ILLEGAL, "第一版")
            writeGlobal(DiceConfigStore.SENTENCE_ILLEGAL, "第二版")
        }
        assertEquals("第二版", store().sentenceIllegal)
    }
}
