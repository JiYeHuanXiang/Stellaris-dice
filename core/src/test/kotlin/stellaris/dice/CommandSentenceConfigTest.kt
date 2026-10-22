package stellaris.dice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.DiceConfigStore
import stellaris.dice.receive.MessageReceived
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption








class CommandSentenceConfigTest {

    @TempDir
    lateinit var tempDir: Path

    private class RecordingIntentEvent : DiceIntentEvent {
        val sent = ArrayList<String>()

        override fun sendMessage(sendContent: String, context: MessageReceived, target: DiceMessageSource) {
            sent.add(sendContent)
        }
    }

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

    private fun inbound(
        helper: DiceHelperImpl,
        content: String,
        admin: Boolean = true,
        sender: String = "10001",
        nick: String = "甲"
    ): MessageReceived = MessageReceived.buildMessageEvent(
        instance = helper.getDiceFunction(),
        content = content,
        group = "20002",
        triggerId = sender,
        nick = nick,
        isGroupAdmin = admin,
        isGroupMsg = true
    )

    private fun helper(): Pair<DiceHelperImpl, RecordingIntentEvent> {
        val event = RecordingIntentEvent()
        return DiceHelperImpl("9999", tempDir.toString(), event) to event
    }

    @Test
    fun `bot 开关文案读配置且默认不变`() {
        val (helper, event) = helper()

        helper.eventMessageReceive(inbound(helper, ".bot on"))
        assertEquals("本群骰子已开启", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".bot on"))
        assertEquals("本群骰子已开启", event.sent.last(), "重复开启沿用默认文案：${event.sent}")

        helper.writeDiceConfig(DiceConfigStore.SENTENCE_DICE_OPEN, "自定义开启")
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_DICE_CLOSE, "自定义关闭")

        helper.eventMessageReceive(inbound(helper, ".bot off"))
        assertEquals("自定义关闭", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".bot on"))
        assertEquals("自定义开启", event.sent.last(), event.sent.toString())
    }

    @Test
    fun `log 开关文案读配置且默认不变`() {
        val (helper, event) = helper()

        helper.eventMessageReceive(inbound(helper, ".log on 甲"))
        assertEquals(
            "已开始记录日志：甲（log end 结束并上传）",
            event.sent.last(),
            event.sent.toString()
        )

        helper.eventMessageReceive(inbound(helper, ".log off"))
        assertEquals(
            "已暂停记录日志：甲（用 log on 甲 继续，log end 上传）",
            event.sent.last(),
            event.sent.toString()
        )

        helper.writeDiceConfig(DiceConfigStore.SENTENCE_LOG_OPEN, "日志开始：{name}")
        helper.eventMessageReceive(inbound(helper, ".log on 乙"))
        assertEquals("日志开始：乙", event.sent.last(), event.sent.toString())
    }

    @Test
    fun `ob 加入文案读配置且默认不变`() {
        val (helper, event) = helper()

        helper.eventMessageReceive(inbound(helper, ".ob"))
        assertEquals("已将甲加入OB位", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".ob exit"))
        assertEquals("你不再处于OB位", event.sent.last(), event.sent.toString())

        helper.writeDiceConfig(DiceConfigStore.SENTENCE_OB_JOIN, "欢迎{player}加入旁观")
        helper.eventMessageReceive(inbound(helper, ".ob"))
        assertEquals("欢迎甲加入旁观", event.sent.last(), event.sent.toString())
    }

    @Test
    fun `name 标题文案读配置且默认不变`() {
        seedDraw()
        val (helper, event) = helper()

        helper.eventMessageReceive(inbound(helper, ".name jp 2"))
        assertEquals(1, event.sent.size, event.sent.toString())
        assertEquals("为甲生成了以下名字：", event.sent[0].lines().first(), event.sent[0])

        helper.writeDiceConfig(DiceConfigStore.SENTENCE_NAME, "给{player}的名字：")
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".name jp 2"))
        assertEquals(1, event.sent.size, event.sent.toString())
        assertEquals("给甲的名字：", event.sent[0].lines().first(), event.sent[0])
        assertTrue(event.sent[0].lines().size > 1, event.sent[0])
    }

    @Test
    fun `rules 与 draw 的失败提示读配置`() {
        val (helper, event) = helper()

        helper.eventMessageReceive(inbound(helper, ".rules 绝对不存在的规则xyz"))
        assertTrue(event.sent.last().contains("没有找到"), event.sent.toString())

        helper.writeDiceConfig(DiceConfigStore.SENTENCE_RULE_QUERY_FAILURE, "规则查询失败")
        helper.eventMessageReceive(inbound(helper, ".rules 绝对不存在的规则xyz"))
        assertEquals("规则查询失败", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".draw 绝对不存在的牌堆"))
        assertEquals(
            "没有这个牌堆：绝对不存在的牌堆",
            event.sent.last(),
            event.sent.toString()
        )

        helper.writeDiceConfig(DiceConfigStore.SENTENCE_DRAW_FAILURE, "抽牌失败")
        helper.eventMessageReceive(inbound(helper, ".draw 绝对不存在的牌堆"))
        assertEquals("抽牌失败", event.sent.last(), event.sent.toString())
    }

    @Test
    fun `替换值里的占位符不会被二次展开`() {
        val (helper, event) = helper()

        helper.eventMessageReceive(inbound(helper, ".jrrp", nick = "{value}"))
        assertTrue(
            event.sent.last().startsWith("{value}今日人品："),
            "昵称里的占位符不应被当成模板再替换：${event.sent}"
        )
    }

    @Test
    fun `恒等失败模板不会重扫底层消息`() {
        val (helper, event) = helper()
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_RULE_QUERY_FAILURE, "{result}")

        helper.eventMessageReceive(inbound(helper, ".rules X{query}"))
        val text = event.sent.last()
        assertTrue(text.contains("X{query}"), "查询串应原样保留：$text")
        assertFalse(text.contains("XX{query}"), "查询串不应被二次展开：$text")
    }

    @Test
    fun `bot 重复开关走 already 键`() {
        val (helper, event) = helper()
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_OPEN_ALREADY, "本来就开着")
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_CLOSE_ALREADY, "本来就关着")

        helper.eventMessageReceive(inbound(helper, ".bot on"))
        assertEquals("本来就开着", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".bot off"))
        helper.eventMessageReceive(inbound(helper, ".bot off"))
        assertEquals("本来就关着", event.sent.last(), event.sent.toString())
    }

    @Test
    fun `log 关闭 未打开 找不到 与拒绝文案读配置`() {
        val (helper, event) = helper()
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_LOG_NOT_OPEN, "没有在记的日志")
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_LOG_OPEN, "开始记录：{name}")
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_LOG_CLOSE, "暂停记录：{name}")
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_LOG_NOT_FOUND, "查无日志：{name}")
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_LOG_DENIED, "日志没权限")

        helper.eventMessageReceive(inbound(helper, ".log off"))
        assertEquals("没有在记的日志", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".log on 甲"))
        assertEquals("开始记录：甲", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".log off"))
        assertEquals("暂停记录：甲", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".log rm 不存在"))
        assertEquals("查无日志：不存在", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".log on 乙", admin = false))
        assertEquals("日志没权限", event.sent.last(), event.sent.toString())
    }

    @Test
    fun `ob 退出文案读配置`() {
        val (helper, event) = helper()
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_OB_EXIT, "你已离开旁观席")
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_OB_EXIT_FAILURE, "你本来就不在旁观席")

        helper.eventMessageReceive(inbound(helper, ".ob exit"))
        assertEquals("你本来就不在旁观席", event.sent.last(), event.sent.toString())

        helper.eventMessageReceive(inbound(helper, ".ob"))
        helper.eventMessageReceive(inbound(helper, ".ob exit"))
        assertEquals("你已离开旁观席", event.sent.last(), event.sent.toString())
    }

    @Test
    fun `jrrp 文案可用 player 与 value`() {
        val (helper, event) = helper()
        helper.writeDiceConfig(DiceConfigStore.SENTENCE_JRRP, "{player}的运气是{value}")

        helper.eventMessageReceive(inbound(helper, ".jrrp"))
        val text = event.sent.last()
        assertTrue(text.startsWith("甲的运气是"), text)
        assertTrue(Regex("^甲的运气是\\d+$").matches(text), text)
    }

    @Test
    fun `新增命令文案键均已登记且有默认值`() {
        val store = DiceConfigStore(tempDir.toString())
        val indicators = stellaris.dice.diceFunction.ConfigIndicators.json()
        val keys = listOf(
            DiceConfigStore.SENTENCE_DICE_OPEN,
            DiceConfigStore.SENTENCE_DICE_CLOSE,
            DiceConfigStore.SENTENCE_DICE_DENIED,
            DiceConfigStore.SENTENCE_DICE_HELP_TEXT,
            DiceConfigStore.SENTENCE_DICE_ROBOT_TEXT,
            DiceConfigStore.SENTENCE_OPEN_ALREADY,
            DiceConfigStore.SENTENCE_CLOSE_ALREADY,
            DiceConfigStore.SENTENCE_LOG_OPEN,
            DiceConfigStore.SENTENCE_LOG_CLOSE,
            DiceConfigStore.SENTENCE_LOG_END,
            DiceConfigStore.SENTENCE_LOG_RESUME,
            DiceConfigStore.SENTENCE_LOG_ALREADY,
            DiceConfigStore.SENTENCE_LOG_NOT_OPEN,
            DiceConfigStore.SENTENCE_LOG_NOT_FOUND,
            DiceConfigStore.SENTENCE_LOG_DENIED,
            DiceConfigStore.SENTENCE_OB_JOIN,
            DiceConfigStore.SENTENCE_OB_JOIN_FAILURE,
            DiceConfigStore.SENTENCE_OB_EXIT,
            DiceConfigStore.SENTENCE_OB_EXIT_FAILURE,
            DiceConfigStore.SENTENCE_NAME,
            DiceConfigStore.SENTENCE_JRRP,
            DiceConfigStore.SENTENCE_CHANGE_CARD,
            DiceConfigStore.SENTENCE_CHANGE_NAME,
            DiceConfigStore.SENTENCE_SETCOC_DENIED,
            DiceConfigStore.SENTENCE_RULE_QUERY_FAILURE,
            DiceConfigStore.SENTENCE_DRAW_SUCCESS,
            DiceConfigStore.SENTENCE_DRAW_FAILURE
        )

        keys.forEach { key ->
            assertTrue(store.readGlobal(key).isNotEmpty(), "$key 没有出厂默认值")
            assertTrue(indicators.contains("\"$key\""), "$key 未登记到配置总表")
        }
    }
}
