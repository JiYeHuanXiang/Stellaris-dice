package stellaris.dice.diceFunction.ops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.DiceConfigStore
import java.nio.file.Path

class CensorshipStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private fun boot(): Pair<DiceConfigStore, CensorshipStore> {
        val config = DiceConfigStore(tempDir.toString())
        return config to CensorshipStore(config)
    }

    @Test
    fun `关闭时不处理`() {
        val (config, store) = boot()
        config.writeGlobal(DiceConfigStore.FORBIDDEN_WORDS, "禁词")
        assertFalse(store.enabled())

        val result = store.check("这里有一个禁词")
        assertEquals("这里有一个禁词", result.text)
        assertFalse(result.blocked)
    }

    @Test
    fun `普通词替换为等长星号`() {
        val (config, store) = boot()
        config.writeGlobal(DiceConfigStore.SYSTEM_CENSORSHIP, "1")
        config.writeGlobal(DiceConfigStore.FORBIDDEN_WORDS, "禁词\n秘密")

        val result = store.check("禁词和秘密")
        assertEquals("**和**", result.text)
        assertEquals(setOf("禁词", "秘密"), result.matched.toSet())
    }

    @Test
    fun `替换规则优先于星号`() {
        val (config, store) = boot()
        config.writeGlobal(DiceConfigStore.SYSTEM_CENSORSHIP, "1")
        config.writeGlobal(DiceConfigStore.REPLACE_FORBIDDEN_WORDS, "混蛋=友善的人")

        val result = store.check("你这个混蛋")
        assertEquals("你这个友善的人", result.text)
        assertEquals("混蛋", result.matched.single())
    }

    @Test
    fun `替换为减号时整条拦截`() {
        val (config, store) = boot()
        config.writeGlobal(DiceConfigStore.SYSTEM_CENSORSHIP, "1")
        config.writeGlobal(DiceConfigStore.REPLACE_FORBIDDEN_WORDS, "禁词=-")

        val result = store.check("包含禁词的整条消息")
        assertTrue(result.blocked)
        assertEquals("", result.text)
    }

    @Test
    fun `状态包含词表规模`() {
        val (config, store) = boot()
        config.writeGlobal(DiceConfigStore.SYSTEM_CENSORSHIP, "1")
        config.writeGlobal(DiceConfigStore.FORBIDDEN_WORDS, "甲,乙,丙")
        config.writeGlobal(DiceConfigStore.REPLACE_FORBIDDEN_WORDS, "丁=戊")

        val status = store.status()
        assertTrue(status.contains("开启"), status)
        assertTrue(status.contains("词表 3 条"), status)
        assertTrue(status.contains("规则 1 条"), status)
    }
}
