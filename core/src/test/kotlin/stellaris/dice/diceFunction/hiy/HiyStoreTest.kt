package stellaris.dice.diceFunction.hiy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID




class HiyStoreTest {

    private fun withStore(test: (HiyStore, String) -> Unit) {
        val root = "build/tmp/hiy-store-${UUID.randomUUID()}"
        try {
            test(HiyStore(root), root)
        } finally {
            File(root).deleteRecursively()
        }
    }

    @Test
    fun `按 QQ 汇总`() = withStore { store, _ ->
        store.record("10001", "r", 3)
        store.record("10001", "r", 5)
        store.record("10001", "ra", 40)
        store.record("10002", "r", 1)

        store.get("10001")!!.let { stats ->
            assertEquals(3, stats.rolls)
            assertEquals(48, stats.sum)
            assertEquals(3, stats.min)
            assertEquals(40, stats.max)
            assertEquals(mapOf("r" to 2, "ra" to 1), stats.commands)
        }
        assertEquals(1, store.get("10002")!!.rolls, "不同 QQ 互不影响")
        assertNull(store.get("10003"))
    }

    @Test
    fun `重启后统计还在`() = withStore { store, root ->
        store.record("10001", "r", 7)
        store.record("10001", "rav", 60)

        val reloaded = HiyStore(root).get("10001")!!
        assertEquals(2, reloaded.rolls)
        assertEquals(67, reloaded.sum)
        assertEquals(7, reloaded.min)
        assertEquals(60, reloaded.max)
        assertEquals(mapOf("r" to 1, "rav" to 1), reloaded.commands, "按指令分组要保留")
    }


    @Test
    fun `只存聚合值`() = withStore { store, root ->
        repeat(100) { store.record("10001", "r", 50) }
        val text = File(root, "data/hiy.json").readText(Charsets.UTF_8)
        assertTrue(text.contains("\"n\":100"), text)
        assertTrue(!text.contains("["), "不该出现每次出目的数组：$text")
        assertEquals(5000, store.get("10001")!!.sum)
    }

    @Test
    fun `坏文件按空库继续`() = withStore { store, root ->
        store.record("10001", "r", 3)
        File(root, "data/hiy.json").writeText("{ 不是 JSON", Charsets.UTF_8)
        assertNull(HiyStore(root).get("10001"))
    }
}
