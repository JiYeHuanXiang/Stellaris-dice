package stellaris.dice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.DiceConfigStore
import stellaris.dice.receive.MessageReceived
import java.nio.file.Path







class GroupCommandTest {

    @TempDir
    lateinit var tempDir: Path

    private class RecordingIntentEvent : DiceIntentEvent {
        val sent = ArrayList<String>()
        val targets = ArrayList<DiceMessageSource>()
        val dismissed = ArrayList<String>()

        override fun sendMessage(sendContent: String, context: MessageReceived, target: DiceMessageSource) {
            sent.add(sendContent)
            targets.add(target)
        }

        override fun setDismiss(groupId: String, callback: ((result: Boolean) -> Unit)?) {
            dismissed.add(groupId)
        }
    }

    private fun inbound(
        helper: DiceHelperImpl,
        content: String,
        admin: Boolean = false,
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
    fun `bot off 关闭本群 除 bot 自身外不响应`() {
        val (helper, event) = helper()
        helper.eventMessageReceive(inbound(helper, ".bot off", admin = true))
        assertTrue(event.sent.last().contains("已关闭"), event.sent.toString())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".r d6"))
        assertTrue(event.sent.isEmpty(), event.sent.toString())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".bot on", admin = true))
        assertTrue(event.sent.last().contains("已开启"), event.sent.toString())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".r d6"))
        assertEquals(1, event.sent.size, event.sent.toString())
    }

    @Test
    fun `欢迎语写入并在入群事件发送`() {
        val (helper, event) = helper()
        helper.eventMessageReceive(inbound(helper, ".welcome 欢迎{user}", admin = true))
        assertTrue(event.sent.last().contains("已开启"), event.sent.toString())

        event.sent.clear()
        helper.eventNewMemberJoin(
            MessageReceived.buildMessageEvent(
                instance = helper.getDiceFunction(),
                content = "",
                group = "20002",
                triggerId = "10002",
                nick = "乙",
                isGroupMsg = true
            )
        )
        assertEquals(1, event.sent.size, event.sent.toString())
        assertTrue(event.sent[0].contains("欢迎乙"), event.sent[0])
    }

    @Test
    fun `send 走私聊给 master`() {
        val (helper, event) = helper()
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, "12345")
        helper.eventMessageReceive(inbound(helper, ".send 你好"))
        assertEquals(1, event.sent.size, event.sent.toString())
        assertTrue(event.sent.last().contains("你好"), event.sent.last())
        assertEquals(DiceMessageSource.Type.PERSON, event.targets.last().type)
        assertEquals("12345", event.targets.last().id)
    }

    @Test
    fun `dismiss 交给宿主桥`() {
        val (helper, event) = helper()
        helper.eventMessageReceive(inbound(helper, ".dismiss", admin = true))
        assertEquals(listOf("20002"), event.dismissed)
        assertTrue(event.sent.last().contains("已请求退群"), event.sent.last())
    }

    @Test
    fun `admin runtime about coc6 均有回执`() {
        val (helper, event) = helper()
        helper.eventMessageReceive(inbound(helper, ".admin state"))
        assertTrue(event.sent.last().contains("骰子开关"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".runtime"))
        assertTrue(event.sent.last().contains("牌堆"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".about"))
        assertTrue(event.sent.last().contains("群星"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".coc6 mode on", admin = true))
        assertTrue(event.sent.last().contains("CoC6"), event.sent.last())
    }
}
