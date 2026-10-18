package stellaris.dice.diceFunction.opposed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID







class OpposedContestStoreTest {

    private fun tempRoot(): String =
        "build/tmp/opposed-store-${UUID.randomUUID()}"

    @Test
    fun `登记 取出 清掉`() {
        val root = tempRoot()
        try {
            val store = OpposedContestStore(root)
            store.join("20002", OpposedContestStore.Pending("10001", "甲", "斗殴(+10)", 70))
            assertEquals(70, store.peek("20002")?.target)
            assertEquals("斗殴(+10)", store.peek("20002")?.skill)

            val taken = store.take("20002")
            assertEquals("甲", taken?.senderName)
            assertEquals(70, taken?.target)
            assertNull(store.peek("20002"), "取出即清掉")
            assertNull(store.take("20002"), "再取没有")
        } finally {
            File(root).deleteRecursively()
        }
    }

    @Test
    fun `重启后登记还在`() {
        val root = tempRoot()
        try {
            OpposedContestStore(root).join(
                "20002",
                OpposedContestStore.Pending("10001", "甲", "困难侦查", 25)
            )
            val reloaded = OpposedContestStore(root)
            assertEquals("困难侦查", reloaded.peek("20002")?.skill)
            assertEquals(25, reloaded.take("20002")?.target)
        } finally {
            File(root).deleteRecursively()
        }
    }

    @Test
    fun `一个群一条 新登记覆盖旧登记`() {
        val root = tempRoot()
        try {
            val store = OpposedContestStore(root)
            store.join("20002", OpposedContestStore.Pending("10001", "甲", "斗殴", 50))
            store.join("20002", OpposedContestStore.Pending("10003", "丙", "闪避", 60))
            assertEquals("丙", store.peek("20002")?.senderName)
            assertEquals(60, store.peek("20002")?.target)
        } finally {
            File(root).deleteRecursively()
        }
    }

    @Test
    fun `坏文件按空库继续`() {
        val root = tempRoot()
        try {
            val file = File(root, "data/opposed.json")
            file.parentFile.mkdirs()
            file.writeText("{ 这不是 JSON", Charsets.UTF_8)
            val store = OpposedContestStore(root)
            assertNull(store.peek("20002"))
            store.join("20002", OpposedContestStore.Pending("10001", "甲", "斗殴", 50))
            assertTrue(OpposedContestStore(root).peek("20002") != null, "之后的写入要把坏文件覆盖掉")
        } finally {
            File(root).deleteRecursively()
        }
    }
}
