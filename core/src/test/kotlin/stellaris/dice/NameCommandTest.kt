package stellaris.dice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.receive.MessageReceived
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption








class NameCommandTest {

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

    private fun inbound(helper: DiceHelperImpl, content: String): MessageReceived =
        MessageReceived.buildMessageEvent(
            instance = helper.getDiceFunction(),
            content = content,
            group = "20002",
            triggerId = "10001",
            nick = "甲",
            isGroupMsg = true
        )

    @Test
    fun `name 按语种与数量生成`() {
        seedDraw()
        val event = RecordingIntentEvent()
        val helper = DiceHelperImpl("9999", tempDir.toString(), event)

        helper.eventMessageReceive(inbound(helper, ".name jp 3"))

        assertEquals(1, event.sent.size, "只该回一条：${event.sent}")
        val lines = event.sent[0].lines()
        assertEquals(4, lines.size, "标题 + 3 个名字：${event.sent[0]}")
        assertTrue(lines[0].contains("为甲生成了以下名字"), event.sent[0])
        assertTrue(lines.drop(1).all { it.isNotBlank() })
    }

    @Test
    fun `name encn 生成中欧名`() {
        seedDraw()
        val event = RecordingIntentEvent()
        val helper = DiceHelperImpl("9997", tempDir.toString(), event)

        helper.eventMessageReceive(inbound(helper, ".name encn 2"))

        assertEquals(1, event.sent.size, "只该回一条：${event.sent}")
        val lines = event.sent[0].lines()
        assertEquals(3, lines.size, "标题 + 2 个名字：${event.sent[0]}")
        assertTrue(lines[0].contains("为甲生成了以下名字"), event.sent[0])
    }

    @Test
    fun `name 缺内容时如实提示`() {
        val event = RecordingIntentEvent()
        val helper = DiceHelperImpl("9998", tempDir.toString(), event)

        helper.eventMessageReceive(inbound(helper, ".name"))

        assertEquals(1, event.sent.size)
        assertTrue(event.sent[0].contains("名字库尚未随包"), event.sent[0])
    }
}
