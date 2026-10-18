package stellaris.dice.receive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import stellaris.dice.DiceHelper
import stellaris.dice.DiceIntentEvent
import stellaris.dice.DiceKernel
import stellaris.dice.DiceManager
import stellaris.dice.DiceMessageSource
import stellaris.dice.diceFunction.DiceConfigStore
import stellaris.dice.diceFunction.random.RandomService
import java.util.Random







class MessageSanitizerTest {




    @Test
    fun `内联调用被打成占位符`() {
        assertEquals("*CODE*", MessageSanitizer.sanitize("#{x-y}"))
        assertEquals("说 *CODE* 话", MessageSanitizer.sanitize("说 #{roll-1d6} 话"))
        assertEquals("*CODE*和*CODE*", MessageSanitizer.sanitize("#{a-b}和#{c-d}"))
    }





    @Test
    fun `裸花括号原样留下`() {
        assertEquals("{player} 掷骰", MessageSanitizer.sanitize("{player} 掷骰"))
        assertEquals("我的{卡名}", MessageSanitizer.sanitize("我的{卡名}"))
    }

    @Test
    fun `普通文本与空串不受影响`() {
        assertEquals("3d6+2 力量", MessageSanitizer.sanitize("3d6+2 力量"))
        assertEquals("", MessageSanitizer.sanitize(""))
    }


    @Test
    fun `净化是幂等的`() {
        val once = MessageSanitizer.sanitize("#{x-y} 理由")
        assertEquals(once, MessageSanitizer.sanitize(once))
    }



    private class RecordingIntentEvent : DiceIntentEvent {
        val sent = ArrayList<String>()
        override fun sendMessage(sendContent: String, context: MessageReceived, target: DiceMessageSource) {
            sent.add(sendContent)
        }
    }

    private class ScriptedRandom(private val units: List<Long>) : Random() {
        private var cursor = 0
        override fun nextLong(): Long {
            val unit = units[cursor % units.size]
            cursor++
            return unit shl 1
        }
    }

    private fun boot(nick: String = "甲"): Pair<DiceHelper, RecordingIntentEvent> {
        DiceManager.install(DiceKernel)
        try {
            DiceManager.setBasePath("build/tmp/sanitizer-flow")
        } catch (alreadySet: IllegalStateException) {

        }
        val event = RecordingIntentEvent()
        val helper = DiceManager.createDice("10000", event)
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, "")
        return helper to event
    }

    private fun inbound(helper: DiceHelper, content: String, nick: String): MessageReceived =
        MessageReceived.buildMessageEvent(
            instance = helper.getDiceFunction(),
            content = content,
            group = "20002",
            triggerId = "10001",
            nick = nick,
            isGroupMsg = true
        )


    @Test
    fun `非 master 的理由被净化`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".r 1d6 #{危险-参数}", "甲"))
        assertEquals(1, event.sent.size)
        assertEquals("由于*CODE*，甲进行的骰点结果:1d6=3", event.sent[0].trim())
    }


    @Test
    fun `非 master 的昵称被净化`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".r 1d6", "#{危险-参数}"))
        assertEquals(1, event.sent.size)
        assertEquals("*CODE*进行的骰点结果:1d6=3", event.sent[0].trim())
    }


    @Test
    fun `master 不被净化`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val (helper, event) = boot()
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, "10001")
        helper.eventMessageReceive(inbound(helper, ".r 1d6 #{原样-保留}", "甲"))
        assertEquals(1, event.sent.size)
        assertEquals("由于#{原样-保留}，甲进行的骰点结果:1d6=3", event.sent[0].trim())
    }
}
