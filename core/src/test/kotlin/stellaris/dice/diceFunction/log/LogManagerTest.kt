package stellaris.dice.diceFunction.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID






class LogManagerTest {

    private fun withManager(test: (LogManager, String) -> Unit) {
        val root = "build/tmp/log-manager-${UUID.randomUUID()}"
        try {
            test(LogManager(root), root)
        } finally {
            File(root).deleteRecursively()
        }
    }

    @Test
    fun `开始 写入 暂停 继续`() = withManager { manager, _ ->
        assertEquals(LogManager.StartResult.STARTED, manager.start("20002", "夜袭"))
        assertEquals("夜袭", manager.activeName("20002"))

        manager.appendUser("20002", "甲", "10001", ".r 1d6")
        manager.appendBot("20002", "10000", "1d6=3")

        val file = manager.activeFile("20002")!!
        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("甲(10001)"), text)
        assertTrue(text.contains(".r 1d6"), text)
        assertTrue(text.contains("骰娘(10000)"), text)
        assertTrue(text.contains("1d6=3"), text)

        assertEquals("夜袭", manager.stop("20002"))
        assertNull(manager.activeName("20002"))
        assertTrue(file.exists(), "暂停不删文件")

        assertEquals(LogManager.StartResult.RESUMED, manager.start("20002", "夜袭"))
        assertNotNull(manager.activeFile("20002"))
    }

    @Test
    fun `正在记录时不能重复开始`() = withManager { manager, _ ->
        manager.start("20002", "战斗")
        assertEquals(LogManager.StartResult.ALREADY, manager.start("20002", "另一份"))
    }

    @Test
    fun `单群最多五份`() = withManager { manager, _ ->
        repeat(5) { index ->
            assertEquals(LogManager.StartResult.STARTED, manager.start("20002", "日志$index"))
            manager.stop("20002")
        }
        assertEquals(LogManager.StartResult.TOO_MANY, manager.start("20002", "第六份"))
    }

    @Test
    fun `名字清洗与非法名`() = withManager { manager, _ ->
        assertEquals(LogManager.StartResult.STARTED, manager.start("20002", "a/b:c"))
        assertEquals("a_b_c", manager.activeName("20002"), "路径分隔符要换成下划线")
        manager.stop("20002")

        assertEquals(
            LogManager.StartResult.INVALID_NAME,
            manager.start("20002", ".."),
            "`.`/`..` 不接受"
        )


        assertEquals(LogManager.StartResult.STARTED, manager.start("20002", ""))
        assertTrue(manager.activeName("20002")!!.startsWith("log-"))
    }

    @Test
    fun `列表 删除 清空`() = withManager { manager, _ ->
        manager.start("20002", "甲")
        manager.stop("20002")
        manager.start("20002", "乙")
        manager.stop("20002")

        val listed = manager.list("20002")
        assertEquals(setOf("甲", "乙"), listed.map { it.name }.toSet())
        assertTrue(listed.none { it.active })

        assertTrue(manager.remove("20002", "甲"))
        assertEquals(listOf("乙"), manager.list("20002").map { it.name })
        assertEquals(1, manager.clear("20002"))
        assertTrue(manager.list("20002").isEmpty())
    }

    @Test
    fun `按群分开`() = withManager { manager, _ ->
        manager.start("20002", "群一")
        manager.start("30003", "群二")
        assertEquals("群一", manager.activeName("20002"))
        assertEquals("群二", manager.activeName("30003"))
        assertEquals(listOf("群二"), manager.list("30003").map { it.name })
    }
}
