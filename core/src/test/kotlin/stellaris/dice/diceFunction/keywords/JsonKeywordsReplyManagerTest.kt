package stellaris.dice.diceFunction.keywords

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.database.entity.data.KeywordsReplyData
import java.io.File

class JsonKeywordsReplyManagerTest {

    @TempDir
    lateinit var root: File

    @Test
    fun `五种匹配模式各自生效`() {
        val manager = JsonKeywordsReplyManager(root.path)
        manager.add(KeywordsReplyData.ReplyType.MATCH_FULL, 0, "你好", "全文", false)
        manager.add(KeywordsReplyData.ReplyType.MATCH_BLUR, 0, "签到", "包含", false)
        manager.add(KeywordsReplyData.ReplyType.MATCH_REGEX, 10, "^签到\\d+$", "正则", false)
        manager.add(KeywordsReplyData.ReplyType.MATCH_PREFIX, 0, "查询", "前缀", false)
        manager.add(KeywordsReplyData.ReplyType.MATCH_SUFFIX, 0, "结束", "后缀", false)

        assertEquals("全文", manager.findMatch("你好")?.content)
        assertEquals("包含", manager.findMatch("今天签到一下")?.content)
        assertEquals("正则", manager.findMatch("签到7")?.content)
        assertEquals("前缀", manager.findMatch("查询天气")?.content)
        assertEquals("后缀", manager.findMatch("本回合结束")?.content)
        assertNull(manager.findMatch("无关消息"))
    }

    @Test
    fun `权重高的条目优先`() {
        val manager = JsonKeywordsReplyManager(root.path)
        manager.add(KeywordsReplyData.ReplyType.MATCH_BLUR, 1, "签到", "低", false)
        manager.add(KeywordsReplyData.ReplyType.MATCH_BLUR, 100, "签到", "高", false)
        assertEquals("高", manager.findMatch("签到")?.content)
    }

    @Test
    fun `增删改与重新载入`() {
        val manager = JsonKeywordsReplyManager(root.path)
        val entry = manager.add(KeywordsReplyData.ReplyType.MATCH_FULL, 0, "测试", "旧", false)
        assertNotNull(entry)

        entry!!.content = "新"
        assertTrue(manager.update(entry))
        assertEquals("新", manager.find(entry.uuid)?.content)

        val reloaded = JsonKeywordsReplyManager(root.path)
        assertEquals(1, reloaded.list().size)
        assertEquals("新", reloaded.find(entry.uuid)?.content)

        assertTrue(reloaded.remove(entry.uuid))
        assertEquals(0, JsonKeywordsReplyManager(root.path).list().size)
    }

    @Test
    fun `非法正则与空关键词不会命中`() {
        val manager = JsonKeywordsReplyManager(root.path)
        manager.add(KeywordsReplyData.ReplyType.MATCH_REGEX, 0, "([", "坏正则", false)
        manager.add(KeywordsReplyData.ReplyType.MATCH_FULL, 0, "", "空", false)
        assertNull(manager.findMatch("(["))
        assertNull(manager.findMatch(""))
    }
}
