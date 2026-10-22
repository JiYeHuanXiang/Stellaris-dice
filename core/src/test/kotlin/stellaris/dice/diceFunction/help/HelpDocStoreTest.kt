package stellaris.dice.diceFunction.help

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class HelpDocStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `写入后可精确与包含查询`() {
        val store = HelpDocStore(tempDir.toString())
        assertTrue(store.setEntry("追仙子", "追仙后，先过一个聆听。"))

        assertEquals("追仙后，先过一个聆听。", store.find("追仙子").single().content)
        assertEquals("追仙子", store.find("追仙").single().keyword)
        assertEquals(0, store.find("不存在").size)
    }

    @Test
    fun `重定向解析到最终词条`() {
        val store = HelpDocStore(tempDir.toString())
        store.setEntry("追仙后", "最终内容")
        assertTrue(store.addRedirect("追仙子", "追仙后"))

        assertEquals("追仙后", store.resolveRedirect("追仙子"))
        assertEquals("追仙后", store.resolveRedirect("追仙后"), "没有重定向时原样返回")
    }

    @Test
    fun `重定向可链式解析`() {
        val store = HelpDocStore(tempDir.toString())
        store.setEntry("终点", "内容")
        store.addRedirect("甲", "乙")
        store.addRedirect("乙", "终点")

        assertEquals("终点", store.resolveRedirect("甲"))
    }

    @Test
    fun `删除词条与重定向`() {
        val store = HelpDocStore(tempDir.toString())
        store.setEntry("词条", "内容")
        store.addRedirect("旧名", "词条")

        assertTrue(store.deleteEntry("词条"))
        assertTrue(store.deleteRedirect("旧名"))
        assertFalse(store.deleteEntry("词条"), "重复删除返回 false")
    }

    @Test
    fun `关键词不得超过上限`() {
        val store = HelpDocStore(tempDir.toString())
        assertTrue(store.validateKeyword("1234567890") != null)
        assertEquals(null, store.validateKeyword("12345678901"))
        assertEquals(null, store.validateKeyword("   "))
        assertFalse(store.setEntry("12345678901", "内容"))
    }

    @Test
    fun `落盘后新实例能读回`() {
        val store = HelpDocStore(tempDir.toString())
        store.setEntry("词条", "内容")
        store.addRedirect("别名", "词条")

        val restarted = HelpDocStore(tempDir.toString())
        assertEquals(1, restarted.size())
        assertEquals("内容", restarted.find("词条").single().content)
        assertEquals("词条", restarted.resolveRedirect("别名"))
    }

    @Test
    fun `展开引用 且不无限递归`() {
        val store = HelpDocStore(tempDir.toString())
        store.setEntry("甲", "甲的内容")
        store.setEntry("乙", "见{甲}")
        store.setEntry("自环", "{自环}")

        assertEquals("见甲的内容", store.expand("{乙}"))
        assertEquals("{自环}", store.expand("{自环}"), "自引用保持原样，不递归爆栈")
    }
}
