package stellaris.dice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.receive.MessageReceived
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption





class FindCommandTest {

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

    private fun seedFind() {
        val src = repoContent().resolve("find")
        val dst = tempDir.resolve(DiceConstant.SAVE_PATH_FIND)
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
    fun `find 系列与 rules 的帮助入口`() {
        seedFind()
        val event = RecordingIntentEvent()
        val helper = DiceHelperImpl("9999", tempDir.toString(), event)

        helper.eventMessageReceive(inbound(helper, ".find 火球"))
        assertEquals(1, event.sent.size, event.sent.toString())
        assertTrue(event.sent.last().contains("火球"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".查询 火球"))
        assertEquals(1, event.sent.size)
        assertTrue(event.sent.last().contains("火球"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".find group 5E"))
        assertTrue(event.sent.last().contains("默认分组已设为：5E"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".rules 5E:火球"))
        assertTrue(event.sent.last().contains("火球"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".help find"))
        assertTrue(event.sent.last().contains(".find"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".help"))
        assertTrue(event.sent.last().contains("群星指令帮助"), event.sent.last())
        assertTrue(event.sent.last().contains(".find"), event.sent.last())
    }
}
