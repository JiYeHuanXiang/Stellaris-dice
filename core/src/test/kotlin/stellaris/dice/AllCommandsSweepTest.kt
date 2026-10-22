package stellaris.dice

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.DiceConfigStore
import stellaris.dice.diceFunction.commands.DiceCommandDecoder
import stellaris.dice.diceFunction.random.RandomService
import stellaris.dice.receive.MessageReceived
import java.util.Random














class AllCommandsSweepTest {

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

    @AfterEach
    fun restore() {
        RandomService.installTestProvider(null)
    }


    private enum class Outcome { IMPLEMENTED, PLACEHOLDER, SILENT, DEFECT }

    /** 允许的占位指令。 */
    private val KNOWN_PLACEHOLDERS = emptySet<String>()


    private val leakMarkers = listOf("Exception", "ERROR:", "at stellaris", "ResError")





    @Test
    fun `扩展指令各自被正确处理并回应`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1, 2)))
        DiceManager.install(DiceKernel)
        try {
            DiceManager.setBasePath(RUN_BASE_PATH)
        } catch (alreadySet: IllegalStateException) {

        }
        val event = RecordingIntentEvent()
        val helper = DiceManager.createDice("10000", event)

        val extras = listOf(
            "rb", "rp", "find", "查询", "查找",
            "dg", "setdg", "fate", "setfate", "wx", "setwx", "cpr", "setcpr", "setdx"
        )
        val defects = ArrayList<String>()
        extras.forEach { command ->
            event.sent.clear()
            helper.eventMessageReceive(
                MessageReceived.buildMessageEvent(
                    instance = helper.getDiceFunction(),
                    content = ".$command",
                    group = "20002",
                    triggerId = "10001",
                    nick = "巡检",
                    isGroupMsg = true
                )
            )
            when {
                event.sent.isEmpty() -> defects.add("$command → 静默（扩展指令必须被认出来）")
                event.sent.size > 1 -> defects.add("$command → 发了 ${event.sent.size} 条")
                event.sent.any { reply -> leakMarkers.any { reply.contains(it) } } ->
                    defects.add("$command → 出站含内部痕迹：${event.sent[0]}")

                else -> Unit
            }
        }
        assertTrue(defects.isEmpty(), "扩展指令巡检发现缺陷：\n" + defects.joinToString("\n"))

        fun send(content: String): String {
            event.sent.clear()
            helper.eventMessageReceive(
                MessageReceived.buildMessageEvent(
                    instance = helper.getDiceFunction(),
                    content = content,
                    group = "20002",
                    triggerId = "10001",
                    nick = "巡检",
                    isGroupMsg = true
                )
            )
            assertEquals(1, event.sent.size, "$content 应该只回一条：${event.sent}")
            return event.sent[0]
        }

        assertTrue(send(".rb 2").contains("奖励d100"), "`.rb 2` 应是普通奖励骰")
        assertTrue(send(".rp 2").contains("惩罚d100"), "`.rp 2` 应是普通惩罚骰")
        assertTrue(send(".dg").contains("当前规则模式"), "`.dg` 应回模式状态")
        assertTrue(send(".setdg").contains("当前规则模式"), "`.setdg` 与 `.dg` 同义")
        assertTrue(send(".fate").contains("f="), "`.fate` 该真掷")
    }








    private val sampleParams = mapOf(
        "sc" to "1/1d4",
        "scx" to "1/1d4",
        "en" to "力量",
        "enx" to "力量",
        "st" to "力量65",
        "stc" to "力量65",
        "hp" to "12",
        "san" to "60",
        "mp" to "12",
        "sp" to "12",
        "pc" to "list",

        "nn" to "",
        "rav" to "力量",
        "rcv" to "力量",
        "ri" to "哥布林",
        "init" to "clr",

        "ob" to "list",
        "psy" to "心理学",

        "log" to "list",

        "gas" to ""
    )






    private val multiSendCommands = setOf("rh", "rah", "rch", "rhx", "rahx", "rchx")







    private val sampleGroups = mapOf(
        "ri" to "29991",
        "init" to "29991"
    )

    @Test
    fun `指令表里每一条都被正确处理并回应`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1, 2, 3, 4)))
        DiceManager.install(DiceKernel)
        try {
            DiceManager.setBasePath(RUN_BASE_PATH)
        } catch (alreadySet: IllegalStateException) {

        }
        val event = RecordingIntentEvent()
        val helper = DiceManager.createDice("10000", event)
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, "10001")

        val implemented = ArrayList<String>()
        val placeholder = ArrayList<String>()
        val silent = ArrayList<String>()
        val defects = ArrayList<String>()

        DiceCommandDecoder.registeredCommands().forEach { command ->
            event.sent.clear()
            val message = MessageReceived.buildMessageEvent(
                instance = helper.getDiceFunction(),
                content = ".$command ${sampleParams[command] ?: "3d6"}",
                group = sampleGroups[command] ?: "20002",
                triggerId = "10001",
                nick = "巡检",
                isGroupMsg = true,
                atTargets = emptyList()
            )
            val crashed = try {
                helper.eventMessageReceive(message)
                false
            } catch (t: Throwable) {
                defects.add("$command → 抛异常：$t")
                true
            }
            if (crashed) {
                return@forEach
            }

            val outcome = when {
                event.sent.size > 1 && command !in multiSendCommands -> {
                    defects.add("$command → 发了 ${event.sent.size} 条（应恰好一条）")
                    Outcome.DEFECT
                }

                event.sent.isEmpty() -> Outcome.SILENT

                event.sent.any { reply -> leakMarkers.any { reply.contains(it) } } -> {
                    defects.add("$command → 出站含内部痕迹：${event.sent[0]}")
                    Outcome.DEFECT
                }

                event.sent[0] == "看不懂的指令呢" -> Outcome.PLACEHOLDER

                else -> Outcome.IMPLEMENTED
            }
            when (outcome) {
                Outcome.IMPLEMENTED -> implemented.add(command)
                Outcome.PLACEHOLDER -> placeholder.add(command)
                Outcome.SILENT -> silent.add(command)
                Outcome.DEFECT -> Unit
            }
        }


        val total = DiceCommandDecoder.registeredCommands().size
        println(
            buildString {
                appendLine("── 全指令巡检：共 $total 条 ──")
                appendLine("已实现（${implemented.size}）：${implemented.joinToString("、")}")
                appendLine("占位提示（${placeholder.size}）：${placeholder.joinToString("、")}")
                appendLine("静默（${silent.size}）：${silent.joinToString("、")}")
                appendLine("缺陷（${defects.size}）：${defects.joinToString("；")}")
            }
        )

        assertTrue(defects.isEmpty(), "巡检发现缺陷：\n" + defects.joinToString("\n"))
        assertEquals(total, implemented.size + placeholder.size + silent.size, "每条指令都要归入某一类")
        assertEquals(
            KNOWN_PLACEHOLDERS,
            placeholder.toSet(),
            "占位名单变化必须显式更新；不允许新占位悄悄混进来"
        )





        assertTrue(
            implemented.containsAll(listOf("r", "w", "ww", "www", "dx", "dxx", "range")),
            "已实现的指令少了：$implemented"
        )
    }

    private companion object {





        val RUN_BASE_PATH: String = "build/tmp/all-commands-sweep-${java.util.UUID.randomUUID()}"
    }
}
