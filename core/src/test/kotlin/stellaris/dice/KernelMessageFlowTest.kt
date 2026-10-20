package stellaris.dice

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.DiceConfigStore
import stellaris.dice.diceFunction.random.RandomService
import stellaris.dice.receive.MessageReceived
import java.io.File
import java.util.Random













class KernelMessageFlowTest {


    private class RecordingIntentEvent : DiceIntentEvent {
        val sent = ArrayList<String>()


        val targets = ArrayList<DiceMessageSource>()


        val uploads = ArrayList<Triple<String, String, String>>()

        override fun sendMessage(sendContent: String, context: MessageReceived, target: DiceMessageSource) {
            sent.add(sendContent)
            targets.add(target)
        }

        override fun sendGroupFile(filePath: String, fileName: String, groupId: String): Boolean {
            uploads.add(Triple(filePath, fileName, groupId))
            return true
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

    private fun boot(basePath: String = RUN_BASE_PATH): Pair<DiceHelper, RecordingIntentEvent> {


        DiceManager.install(DiceKernel)
        try {
            DiceManager.setBasePath(basePath)
        } catch (alreadySet: IllegalStateException) {

        }
        val event = RecordingIntentEvent()
        return DiceManager.createDice(BOT_ID, event) to event
    }


    private fun inbound(
        helper: DiceHelper,
        content: String,
        atTargets: List<String> = emptyList(),
        group: String = GROUP_ID,
        admin: Boolean = false,
        sender: String = SENDER_ID,
        nick: String = "甲"
    ): MessageReceived = MessageReceived.buildMessageEvent(
        instance = helper.getDiceFunction(),
        content = content,
        group = group,
        triggerId = sender,
        nick = nick,
        isGroupAdmin = admin,
        isGroupMsg = true,
        atTargets = atTargets
    )




    @Test
    fun `普通骰 一条消息进一条回复出`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1, 2)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".r 3d6+2 为了测试"))
        assertEquals(1, event.sent.size, "只该发一条：${event.sent}")
        assertEquals("由于为了测试，甲进行的骰点结果:3d6+2={1+2+3}(6)+2=6+2=8", event.sent[0])
    }


    @Test
    fun `全角触发符同样认`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, "。r 1d6"))
        assertEquals(1, event.sent.size)
        assertTrue(event.sent[0].contains("1d6=3"), event.sent[0])
    }


    @Test
    fun `普通聊天不回复`() {
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, "今天天气不错"))
        assertTrue(event.sent.isEmpty(), "不该回复普通聊天：${event.sent}")
    }


    @Test
    fun `未知指令不响应`() {
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".notacommand"))
        assertTrue(event.sent.isEmpty())
    }


    @Test
    fun `骰式不成立 回中性提示`() {
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".r 1d0"))
        assertEquals(listOf("看不懂的指令呢"), event.sent)
    }


    @Test
    fun `空内容不出站`() {
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, "."))
        assertTrue(event.sent.isEmpty())
    }




    @Test
    fun `重复建实例 幂等且可换桥`() {
        val (first, _) = boot()
        val replacement = RecordingIntentEvent()
        val again = DiceManager.createDice(BOT_ID, replacement)
        assertSame(first, again, "同一 QQ 只该有一个实例")
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        again.eventMessageReceive(inbound(again, ".r 1d6"))
        assertEquals(1, replacement.sent.size, "消息该走到新桥上")
    }


    @Test
    fun `无桥时静默`() {
        boot()
        val noBridge = DiceManager.createDice("19999", null)
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        noBridge.eventMessageReceive(inbound(noBridge, ".r 1d6"))
    }


    @Test
    fun `实例清单`() {
        boot()
        DiceManager.createDice("19998", null)
        val list = DiceManager.getDiceList()
        val mine = list.first { it.botId == "19998" }
        assertFalse(mine.ready, "没桥 = 未就位")
        assertTrue(list.any { it.botId == BOT_ID && it.ready })
    }


    @Test
    fun `配置读写生效`() {
        val (helper, event) = boot()
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, SENDER_ID)
        assertTrue(helper.getDiceFunction().diceConfig.isMaster(SENDER_ID), "写进去的 master 要认")
        assertFalse(helper.getDiceFunction().diceConfig.isMaster("1"), "不在名单里的不是 master")

        helper.writeDiceConfig(DiceConfigStore.DEFAULT_DICE_NUMBER, "20")
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        helper.eventMessageReceive(inbound(helper, ".r"))
        assertTrue(event.sent.last().contains("1d20"), "默认骰该变成 20：${event.sent.last()}")
    }


    @Test
    fun `db 用角色卡的真值`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()

        val cardStore = (helper as DiceHelperImpl).functions.cardManager
        val card = cardStore.getCurrentPlayer(SENDER_ID, GROUP_ID)
        card.setProperty("力量", 90)
        card.setProperty("体型", 90)
        cardStore.savePlayer(card)


        val isolated = stellaris.dice.diceFunction.calculation.DiceCalculation.abilitiesGetDB(
            cardStore.getCurrentPlayer(SENDER_ID, GROUP_ID).asAbilities()
        )
        assertEquals("1d6", isolated, "卡上的力量/体型没读出来")

        helper.eventMessageReceive(inbound(helper, ".r 1d6+db"))
        assertEquals(1, event.sent.size)
        assertTrue(event.sent[0].contains("1d6+1d6"), "力量+体型=180 → db=1d6：${event.sent[0]}")
    }


    @Test
    fun `检定 一条消息进一条回复出`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".ra 50"))
        assertEquals(1, event.sent.size, "只该发一条：${event.sent}")
        assertEquals("甲进行的数值检定结果为:1d100=40/50 成功", event.sent[0])
        assertTrue(event.targets[0].isGroup(), "明检定回群")
    }


    @Test
    fun `暗检定 去向是发起者私聊`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".rah 50"))
        assertEquals(2, event.sent.size, "群里占位 + 私聊结果")
        assertEquals("有人进行了一次暗骰，会是啥捏？", event.sent[0])
        assertTrue(event.targets[0].isGroup(), "占位上群")

        assertTrue(event.sent[1].contains("1d100=40/50 成功"), event.sent[1])
        assertTrue(event.sent[1].contains("甲在群${GROUP_ID}的暗骰结果"), event.sent[1])
        assertTrue(event.targets[1].isPerson(), "暗检定该走私聊：${event.targets[1].type}")
        assertEquals(SENDER_ID, event.targets[1].id, "私聊窗口是发起者本人")
    }


    @Test
    fun `暗骰 去向是发起者私聊`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".rh 1d6"))
        assertEquals(2, event.sent.size)
        assertEquals("有人进行了一次暗骰，会是啥捏？", event.sent[0])
        assertTrue(event.sent[1].contains("1d6=3"), event.sent[1])
        assertTrue(event.targets[1].isPerson(), "暗骰该走私聊")
    }


    @Test
    fun `暗骰仅限群聊`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        helper.eventMessageReceive(
            MessageReceived.buildMessageEvent(
                instance = helper.getDiceFunction(),
                content = ".rah 50",
                group = "",
                triggerId = SENDER_ID,
                nick = "甲",
                isGroupMsg = false
            )
        )
        assertEquals("暗骰仅在群聊中有效", event.sent[0])
    }


    @Test
    fun `ob 旁观者收到暗骰结果`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()
        val group = "26691"
        val observerId = "10106"

        fun reset() {
            event.sent.clear()
            event.targets.clear()
        }

        helper.eventMessageReceive(inbound(helper, ".ob", group = group, sender = observerId, nick = "乙"))
        assertEquals("已将乙加入OB位", event.sent[0])

        reset()
        helper.eventMessageReceive(inbound(helper, ".ob list", group = group, sender = observerId, nick = "乙"))
        assertTrue(event.sent[0].contains("乙($observerId)"), event.sent[0])

        reset()
        helper.eventMessageReceive(inbound(helper, ".rah 50", group = group))
        assertEquals(3, event.sent.size, "群里占位 + 发起者私聊 + OB 私聊")
        assertEquals("有人进行了一次暗骰，会是啥捏？", event.sent[0])
        assertTrue(event.targets[0].isGroup())
        assertEquals(SENDER_ID, event.targets[1].id)
        assertEquals(observerId, event.targets[2].id, "OB 收到私聊")
        assertTrue(event.sent[2].contains("1d100=40/50"), event.sent[2])

        reset()
        helper.eventMessageReceive(inbound(helper, ".ob exit", group = group, sender = observerId, nick = "乙"))
        assertEquals("你不再处于OB位", event.sent[0])


        reset()
        helper.eventMessageReceive(inbound(helper, ".rah 50", group = group))
        assertEquals(2, event.sent.size)
    }


    @Test
    fun `psy 对指定玩家做暗骰`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()
        val group = "26692"
        val targetId = "10107"
        val store = (helper as DiceHelperImpl).functions.cardManager
        store.getCurrentPlayer(targetId, group).let {
            it.setProperty("心理学", 50)
            store.savePlayer(it)
        }

        helper.eventMessageReceive(
            inbound(helper, ".psy 心理学", group = group, atTargets = listOf(targetId))
        )
        assertEquals(2, event.sent.size, "群里占位 + 发起者私聊")
        assertEquals("有人进行了一次暗骰，会是啥捏？", event.sent[0])
        assertTrue(event.sent[1].contains("技能 心理学：1d100=40/50 成功"), event.sent[1])
        assertEquals(SENDER_ID, event.targets[1].id)

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".psy", group = group))
        assertTrue(event.sent[0].contains("用法"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(
            inbound(helper, ".psy 不存在", group = group, atTargets = listOf(targetId))
        )
        assertTrue(event.sent[0].contains("未录入属性"), event.sent[0])
    }


    @Test
    fun `ob 开关要管理员`() {
        val (helper, event) = boot()
        val group = "26693"
        val plainMember = "10108"

        helper.eventMessageReceive(inbound(helper, ".ob off", group = group, sender = plainMember))
        assertEquals("没有权限", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ob off", group = group, admin = true))
        assertEquals("已关闭OB功能", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ob", group = group, sender = plainMember))
        assertEquals("ob功能已被管理员禁用", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ob on", group = group, admin = true))
        assertEquals("已打开OB功能", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ob", group = group, sender = plainMember))
        assertTrue(event.sent[0].contains("加入OB位"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ob clr", group = group, admin = true))
        assertEquals("已清空OB位", event.sent[0])
    }


    @Test
    fun `setcoc 改档位后 ra 与 rc 分家`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(4)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".ra 3"))
        assertTrue(event.sent[0].contains("失败"), "默认档位（规则书）：5 > 3 → 失败：${event.sent[0]}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".setcoc 3", admin = true))
        assertEquals(1, event.sent.size)
        assertEquals("COC检定房规已设为：房规3（3）", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ra 3"))
        assertTrue(event.sent[0].contains("大成功"), "房规3：1~5 大成功（无视判定）：${event.sent[0]}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".rc 3"))
        assertTrue(event.sent[0].contains("失败"), "`.rc` 恒按规则书：${event.sent[0]}")



        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".setcoc 0", admin = true))
        assertEquals("COC检定房规已设为：规则书（0）", event.sent[0])
    }


    @Test
    fun `setcoc 非法参数给用法提示`() {
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".setcoc 9", admin = true))
        assertEquals(1, event.sent.size)
        assertTrue(event.sent[0].contains("用法"), event.sent[0])
    }


    @Test
    fun `st 改卡后检定读到新值`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".st 力量65体质50侦查70"))
        assertEquals(1, event.sent.size)
        assertEquals("甲的人物卡属性已更新:力量65 体质50 侦查70", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ra 侦查"))
        assertEquals("甲进行的侦查检定结果为:1d100=40/70 成功", event.sent[0])
    }


    @Test
    fun `st 增量与查看`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".st 力量65"))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st 力量+10"))
        assertEquals("甲的人物卡属性已更新:力量75", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st show 力量"))
        assertEquals("甲的人物卡属性:力量75", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st show 不存在"))
        assertEquals("甲的人物卡上没有「不存在」这一项", event.sent[0])
    }


    @Test
    fun `虚拟指令 hp 与 san 都落到 st`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".hp 12"))
        assertEquals("甲的人物卡属性已更新:hp12", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".san 60"))
        assertEquals("甲的人物卡属性已更新:理智60", event.sent[0], "`.san` 落到正式名「理智」")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".hp"))
        assertEquals("甲的人物卡属性:hp12", event.sent[0])
    }


    @Test
    fun `st 看不懂的参数回中性提示`() {
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".st 3d6"))
        assertEquals(listOf("看不懂的指令呢"), event.sent)
    }


    @Test
    fun `pc 建卡写属性看卡删卡`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".pc new 夜刃 coc7"))
        assertEquals("已新建人物卡：夜刃（COC7）", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st 力量70侦查80"))
        assertEquals("甲的人物卡属性已更新:力量70 侦查80", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc show 夜刃"))
        assertEquals("人物卡「夜刃」（COC7）：力量70 侦查80", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc list"))
        assertTrue(event.sent[0].contains("夜刃(COC7)"), event.sent[0])
        assertTrue(event.sent[0].contains("当前：夜刃"), event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc del 夜刃"))
        assertEquals("已删除人物卡：夜刃", event.sent[0])
    }


    @Test
    fun `pc 复制切卡改规则与失败路径`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".pc new 源卡"))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st 侦查55"))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc cpy 副本"))
        assertEquals("已复制人物卡：源卡 → 副本", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc grp 副本"))
        assertEquals("本群已切换到人物卡：副本", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc rule dg"))
        assertEquals("人物卡「副本」的规则已设为：DG", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc new 源卡"))
        assertEquals("同名人物卡已存在：源卡", event.sent[0])
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc show 没这张"))
        assertEquals("没有这张人物卡：没这张", event.sent[0])
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc"))
        assertTrue(event.sent[0].contains("用法："), event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc del 源卡"))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc del 副本"))
    }


    @Test
    fun `en 成长检定写回技能值`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(44, 6)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".st 斗殴30"))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".en 斗殴"))
        assertEquals("甲的斗殴成长检定:1d100=45/30 成长+7，30→37", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st show 斗殴"))
        assertEquals("甲的人物卡属性:斗殴37", event.sent[0], "成长要写回卡")
    }


    @Test
    fun `sc 理智检定写回理智`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".st san55"))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".sc 1/1d4"))
        assertEquals("甲的理智检定:1d100=40/55 成功，理智减少1点（55→54）", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st show san"))
        assertEquals("甲的人物卡属性:理智54", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".sc 1"))
        assertEquals("看不懂的指令呢", event.sent[0])
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st show san"))
        assertEquals("甲的人物卡属性:理智54", event.sent[0], "参数不合法时不该动卡")
    }


    @Test
    fun `en 与 sc 缺项时不改卡`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".pc new 空卡"))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".en 不存在的技能"))
        assertEquals("甲的人物卡上没有「不存在的技能」这一项", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".sc 1/1d4"))
        assertEquals("甲的人物卡上没有「理智」这一项", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc del 空卡"))
    }







    @Test
    fun `对抗检定 先手登记 后手结算`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39, 59)))
        val (helper, event) = boot()
        val group = "26661"
        val store = (helper as DiceHelperImpl).functions.cardManager
        store.getCurrentPlayer(SENDER_ID, group).let { it.setProperty("斗殴", 60); store.savePlayer(it) }
        store.getCurrentPlayer(OPPONENT_ID, group).let { it.setProperty("斗殴", 30); store.savePlayer(it) }

        helper.eventMessageReceive(inbound(helper, ".rav 斗殴", group = group))
        assertEquals(listOf("先手甲发起了斗殴对抗"), event.sent, "第一发只登记，不掷骰")

        event.sent.clear()
        helper.eventMessageReceive(
            inbound(helper, ".rav 斗殴", group = group, sender = OPPONENT_ID, nick = "乙")
        )
        assertEquals(1, event.sent.size)
        assertEquals(
            "先手(甲)：斗殴 1d100=40/60 成功\n" +
                "后手(乙)：斗殴 1d100=60/30 失败\n先手胜利",
            event.sent[0]
        )
    }


    @Test
    fun `对抗检定 两边各用各的技能`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(59, 39)))
        val (helper, event) = boot()
        val group = "26662"
        val store = (helper as DiceHelperImpl).functions.cardManager
        store.getCurrentPlayer(SENDER_ID, group).let { it.setProperty("斗殴", 30); store.savePlayer(it) }
        store.getCurrentPlayer(OPPONENT_ID, group).let { it.setProperty("闪避", 70); store.savePlayer(it) }

        helper.eventMessageReceive(inbound(helper, ".rav 斗殴", group = group))
        event.sent.clear()
        helper.eventMessageReceive(
            inbound(helper, ".rav 闪避", group = group, sender = OPPONENT_ID, nick = "乙")
        )
        assertEquals(
            "先手(甲)：斗殴 1d100=60/30 失败\n" +
                "后手(乙)：闪避 1d100=40/70 成功\n后手胜利",
            event.sent[0]
        )
    }


    @Test
    fun `对抗检定 双方都失败`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(95, 95)))
        val (helper, event) = boot()
        val group = "26663"
        val store = (helper as DiceHelperImpl).functions.cardManager
        store.getCurrentPlayer(SENDER_ID, group).let { it.setProperty("力量", 40); store.savePlayer(it) }
        store.getCurrentPlayer(OPPONENT_ID, group).let { it.setProperty("力量", 30); store.savePlayer(it) }

        helper.eventMessageReceive(inbound(helper, ".rav 力量", group = group))
        event.sent.clear()
        helper.eventMessageReceive(
            inbound(helper, ".rav 力量", group = group, sender = OPPONENT_ID, nick = "乙")
        )
        assertEquals(
            "先手(甲)：力量 1d100=96/40 大失败\n" +
                "后手(乙)：力量 1d100=96/30 大失败\n全部失败",
            event.sent[0]
        )
    }


    @Test
    fun `对抗检定 空参数给用法 私聊拒绝`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".rav", group = "26664"))
        assertTrue(event.sent[0].contains("对抗检定"), "空参给用法：${event.sent[0]}")

        event.sent.clear()
        val privateMessage = MessageReceived.buildMessageEvent(
            instance = helper.getDiceFunction(),
            content = ".rav 斗殴",
            group = "",
            triggerId = SENDER_ID,
            nick = "甲",
            isGroupMsg = false
        )
        helper.eventMessageReceive(privateMessage)
        assertEquals("本命令暂时只能群聊使用", event.sent[0])
    }


    @Test
    fun `简化输出 x 后缀只回结果`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2, 39)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".rx 1d6"))
        assertEquals("1d6=3", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".rax 50"))
        assertEquals("1d100=40/50 成功", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".dx 1"))
        assertTrue(event.sent[0].contains("c10"), "dx 仍是双十字骰池：${event.sent[0]}")
    }


    @Test
    fun `简化输出 scx 只回结果`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        val (helper, event) = boot()
        helper.eventMessageReceive(inbound(helper, ".st san55"))
        event.sent.clear()

        helper.eventMessageReceive(inbound(helper, ".scx 1/1d4"))
        assertEquals("1d100=40/55 成功，理智减少1点（55→54）", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st show san"))
        assertEquals("甲的人物卡属性:理智54", event.sent[0], "简化输出不影响写回")
    }








    @Test
    fun `draw 与 deck 按手册的用法`() {
        val (helper, event) = boot()
        val impl = helper as DiceHelperImpl
        val drawDir = File(impl.functions.root(), stellaris.dice.diceFunction.DiceConstant.SAVE_PATH_DRAW)
        drawDir.mkdirs()
        File(drawDir, "flow-deck.json")
            .writeText("""{"流程堆": ["条目甲","条目乙"]}""", Charsets.UTF_8)
        impl.functions.drawManager.reload()

        helper.eventMessageReceive(inbound(helper, ".deck show"))
        assertTrue(event.sent[0].contains("流程堆"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".draw list"))
        assertTrue(event.sent[0].contains("流程堆"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".draw search 流程"))
        assertTrue(event.sent[0].contains("流程堆"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".draw help"))
        assertTrue(event.sent[0].contains("用法："), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck set 流程堆", admin = true))
        assertEquals("默认牌堆已设为：流程堆", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".draw"))
        assertTrue(event.sent[0].startsWith("甲的抽牌结果:"), event.sent[0])
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck"))
        assertTrue(event.sent[0].startsWith("甲的抽牌结果:"), "裸 .deck = .draw：${event.sent[0]}")


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck reset 流程堆", admin = true))
        assertEquals("已重置牌堆「流程堆」的抽取进度", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".draw 流程堆 2"))
        assertTrue(event.sent[0].contains("条目甲"), event.sent[0])
        assertTrue(event.sent[0].contains("条目乙"), "两张该都抽到：${event.sent[0]}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".draw 没有的堆"))
        assertEquals("没有这个牌堆：没有的堆", event.sent[0])
    }


    @Test
    fun `deck new 临时牌堆`() {
        val (helper, event) = boot()
        val group = "26690"
        val plainMember = "10105"

        helper.eventMessageReceive(inbound(helper, ".deck new A|B|C", group = group, sender = plainMember))
        assertEquals("只有群主、群管理员或 master 能改本群设置", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck new A|B|C", group = group, admin = true))
        assertEquals("已创建临时牌堆并设为默认（3 条，抽取不放回）", event.sent[0])


        event.sent.clear()
        repeat(3) { helper.eventMessageReceive(inbound(helper, ".draw", group = group)) }
        val drawn = event.sent.joinToString("")
        listOf("A", "B", "C").forEach { assertTrue(drawn.contains(it), "临时堆条目：$drawn") }

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck reset", group = group, admin = true))
        assertTrue(event.sent[0].contains("已重置本群全部牌堆的抽取进度"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck set 3", group = group, admin = true))
        assertEquals("默认牌堆已设为：1~3（共 3 张，临时）", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck new 只有一条", group = group, admin = true))
        assertTrue(event.sent[0].contains("至少两条"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".deck set 150", group = group, admin = true))
        assertTrue(event.sent[0].contains("1~100"), event.sent[0])
    }








    @Test
    fun `ti 与 li 不随规则模式变`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2, 6, 0, 3)))
        val (helper, event) = boot()
        val group = "28888"

        helper.eventMessageReceive(inbound(helper, ".ti", group = group))
        assertTrue(event.sent[0].contains("临时性疯狂"), event.sent[0])
        assertTrue(event.sent[0].contains("暴力倾向"), event.sent[0])
        assertTrue(event.sent[0].contains("轮"), "临时性疯狂按轮计：${event.sent[0]}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".li", group = group))
        assertTrue(event.sent[0].contains("长期性疯狂"), event.sent[0])
        assertTrue(event.sent[0].contains("小时"), "长期性疯狂按小时计：${event.sent[0]}")


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".dnd mode on 5e", group = group, admin = true))
        assertEquals("规则模式已设为：DND 5e", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ti", group = group))
        assertTrue(event.sent[0].contains("临时性疯狂"), "ti/li 不随模式变：${event.sent[0]}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".dnd", group = group))
        assertEquals("当前规则模式：DND 5e", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".dnd mode off", group = group, admin = true))
        assertEquals("规则模式已关闭（按 CoC 处理）", event.sent[0])
    }






    @Test
    fun `先攻 掷点 列列表 推进回合`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(14, 4)))
        val (helper, event) = boot()
        val group = "26670"

        helper.eventMessageReceive(inbound(helper, ".ri 哥布林/史莱姆", group = group))
        assertEquals(
            "哥布林、史莱姆的先攻点为: \n哥布林: d20=15\n史莱姆: d20=5",
            event.sent[0]
        )

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init", group = group))
        assertEquals(
            "本群先攻列表（ROUND 1）：\n->1.哥布林 先攻:15\n2.史莱姆 先攻:5\n\n现在是 哥布林 的回合",
            event.sent[0]
        )

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init end", group = group))
        assertEquals(
            "——ROUND 1——\n哥布林的回合结束\n史莱姆的回合开始\n哥布林请做好准备",
            event.sent[0]
        )

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init end", group = group))
        assertEquals(
            "——ROUND 2——\n史莱姆的回合结束\n哥布林的回合开始\n史莱姆请做好准备",
            event.sent[0]
        )


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init clr", group = group))
    }


    @Test
    fun `先攻 批量生成与共用骰点`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1, 2)))
        val (helper, event) = boot()
        val group = "26671"

        helper.eventMessageReceive(inbound(helper, ".ri 3#哥布林", group = group))
        assertEquals(
            "哥布林A、哥布林B、哥布林C的先攻点为: \n" +
                "哥布林A: d20=1\n哥布林B: d20=2\n哥布林C: d20=3",
            event.sent[0]
        )

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init clr", group = group))
        event.sent.clear()
        RandomService.installTestProvider(ScriptedRandom(listOf(19)))
        helper.eventMessageReceive(inbound(helper, ".ri =3#哥布林", group = group))
        assertTrue(
            event.sent[0].contains("哥布林A 哥布林B 哥布林C: d20=20"),
            "共用骰点要只掷一次：${event.sent[0]}"
        )

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init clr", group = group))
    }


    @Test
    fun `先攻 自己的换骰`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(4)))
        val (helper, event) = boot()
        val group = "26672"

        helper.eventMessageReceive(inbound(helper, ".ri d8", group = group))
        assertEquals("甲的先攻点为: \n甲: d8=5", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init clr", group = group))
    }


    @Test
    fun `先攻 列表管理子命令`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(9, 4)))
        val (helper, event) = boot()
        val group = "26673"

        helper.eventMessageReceive(inbound(helper, ".init", group = group))
        assertTrue(event.sent[0].contains("空的先攻点列表"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init end", group = group))
        assertTrue(event.sent[0].contains("先攻列表还是空的"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ri 甲/乙", group = group))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init set 张三 50", group = group))
        assertEquals("已设置 张三 的先攻点为 50", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init", group = group))
        assertTrue(event.sent[0].contains("->1.张三 先攻:50"), event.sent[0])
        assertTrue(event.sent[0].contains("现在是 张三 的回合"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init buff 甲 +中毒", group = group))
        assertTrue(event.sent[0].contains("『甲 先攻:10 中毒』 状态已更新"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init buff 甲 -中毒", group = group))
        assertTrue(event.sent[0].contains("『甲 先攻:10』 状态已更新"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init del 甲", group = group))
        assertEquals("以下条目被删除:\n甲", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init clr", group = group))
        assertEquals("已清空先攻列表。", event.sent[0])
    }


    @Test
    fun `先攻态 hp 设置 扣血 死亡自动清除`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val group = "26674"

        helper.eventMessageReceive(inbound(helper, ".ri 哥布林", group = group))
        event.sent.clear()

        helper.eventMessageReceive(inbound(helper, ".hp 哥布林 12/12", group = group))
        assertEquals(
            "『哥布林 先攻:1 血量:12/12』的生命值设置为 12/12",
            event.sent[0]
        )

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".hp 哥布林 -5", group = group))
        assertEquals(
            "『哥布林 先攻:1 血量:7/12』的生命值减少了5",
            event.sent[0]
        )

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".hp 哥布林 0", group = group))
        assertTrue(event.sent[0].contains("已自动清除 1 条死亡对象"), event.sent[0])
        assertTrue(event.sent[0].contains("先攻列表已为空"), event.sent[0])
    }


    @Test
    fun `先攻态 hp 没设血量时只记扣血`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 2, 3)))
        val (helper, event) = boot()
        val group = "26675"

        helper.eventMessageReceive(inbound(helper, ".ri 史莱姆", group = group))
        event.sent.clear()

        helper.eventMessageReceive(inbound(helper, ".hp 史莱姆 -2d6", group = group))
        assertTrue(event.sent[0].contains("扣血:7"), event.sent[0])
        assertTrue(event.sent[0].contains("减少了7"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init clr", group = group))
    }


    @Test
    fun `先攻 列表上限二十条`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val group = "26676"

        helper.eventMessageReceive(inbound(helper, ".ri 20#哥布林", group = group))
        assertEquals(1, event.sent.size)

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ri 史莱姆", group = group))
        assertTrue(event.sent[0].contains("不得超过20个"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".init clr", group = group))
    }


    @Test
    fun `先攻族仅限群聊`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        fun privateMessage(content: String) = MessageReceived.buildMessageEvent(
            instance = helper.getDiceFunction(),
            content = content,
            group = "",
            triggerId = SENDER_ID,
            nick = "甲",
            isGroupMsg = false
        )

        helper.eventMessageReceive(privateMessage(".ri"))
        assertEquals("ri 指令仅限群聊使用", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(privateMessage(".init"))
        assertEquals("init 指令仅限群聊使用", event.sent[0])
    }


    @Test
    fun `st 骰式值 删除 清空`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(1, 2, 3, 4)))
        val (helper, event) = boot()
        val group = "26680"
        val sender = "10101"

        helper.eventMessageReceive(inbound(helper, ".st 体力3d6", group = group, sender = sender))
        assertEquals("甲的人物卡属性已更新:体力9", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st san55", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st san-1d6", group = group, sender = sender))
        assertEquals("甲的人物卡属性已更新:理智50", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st del 体力", group = group, sender = sender))
        assertEquals("已删除属性：体力", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st del 体力", group = group, sender = sender))
        assertEquals("甲的人物卡上没有「体力」这一项", event.sent[0], "删不存在的项不静默")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st clr", group = group, sender = sender))
        assertEquals("已清空人物卡的所有属性", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st show 理智", group = group, sender = sender))
        assertEquals("甲的人物卡上没有「理智」这一项", event.sent[0], "清空后一项都不剩")
    }


    @Test
    fun `nn 改名与切换`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val group = "26681"
        val sender = "10102"

        helper.eventMessageReceive(inbound(helper, ".pc new 夜刃", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".nn 银翼", group = group, sender = sender))
        assertEquals("已将人物卡「夜刃」改名为「银翼」", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc new 备胎", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".nn 银翼", group = group, sender = sender))
        assertEquals("已切换到人物卡：银翼", event.sent[0], "名字已存在 = 切换，不是改名")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".nn", group = group, sender = sender))
        assertEquals("当前人物卡：银翼（用 .nn 新名字 改名）", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc clr", group = group, sender = sender))
        assertEquals("已清空你的全部人物卡与绑定关系", event.sent[0])
    }


    @Test
    fun `pc 绑定关系 标签 阈值 清空`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val group = "26682"
        val sender = "10103"

        helper.eventMessageReceive(inbound(helper, ".pc new 甲卡", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st 力量70体质40", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc new 乙卡", group = group, sender = sender))

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc tag 甲卡", group = group, sender = sender))
        assertEquals("本群已切换到人物卡：甲卡", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc grp", group = group, sender = sender))
        assertTrue(event.sent[0].contains("群 $group → 甲卡"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc show 50", group = group, sender = sender))
        assertTrue(event.sent[0].contains("大于 50 的属性：力量70"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc tag", group = group, sender = sender))
        assertEquals("已解除本场会话的人物卡绑定（回到默认卡）", event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc grp clr", group = group, sender = sender))
        assertEquals("已清空所有群的绑定关系", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc clr", group = group, sender = sender))
        assertEquals("已清空你的全部人物卡与绑定关系", event.sent[0])
    }


    @Test
    fun `who 随机洗牌与 jrrp 今日人品`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()

        helper.eventMessageReceive(inbound(helper, ".who A B C"))
        assertTrue(event.sent[0].startsWith("随机分配结果："), event.sent[0])
        listOf("A", "B", "C").forEach { name ->
            assertTrue(event.sent[0].contains(name), "洗牌不能丢名字：${event.sent[0]}")
        }

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".who A"))
        assertTrue(event.sent[0].contains("至少两个名字"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".jrrp", sender = "10104"))
        val first = event.sent[0]
        val value = Regex("今日人品：(\\d+)").find(first)?.groupValues?.get(1)?.toInt()
        assertTrue(value != null && value in 1..100, first)

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".jrrp", sender = "10104"))
        assertEquals(first, event.sent[0], "同一天同一个人结果固定")
    }


    @Test
    fun `pc build 与 redo`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val group = "26695"
        val sender = "10110"

        helper.eventMessageReceive(inbound(helper, ".pc new 调查员 coc7", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc build", group = group, sender = sender))
        assertTrue(event.sent[0].contains("属性生成"), event.sent[0])
        assertTrue(event.sent[0].contains("力量15"), "3d6×5：${event.sent[0]}")
        assertTrue(event.sent[0].contains("体型40"), "(2d6+6)×5：${event.sent[0]}")
        assertTrue(event.sent[0].contains("build：只补缺失值"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".st 力量99", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc build", group = group, sender = sender))
        assertTrue(event.sent[0].contains("力量99"), "build 不覆盖已有值：${event.sent[0]}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc redo", group = group, sender = sender))
        assertTrue(event.sent[0].contains("力量15"), "redo 强制重算：${event.sent[0]}")
        assertTrue(event.sent[0].contains("redo：强制重算"), event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc clr", group = group, sender = sender))
    }


    @Test
    fun `hiy 统计按 QQ 汇总`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2, 39)))
        val (helper, event) = boot()
        val group = "26696"
        val sender = "10109"

        helper.eventMessageReceive(inbound(helper, ".r 1d6", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".ra 50", group = group, sender = sender))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".hiy", group = group, sender = sender))
        val expected = "甲 的骰点统计（按 QQ 汇总）：\n" +
            "共 2 次，平均 21.5，最小 3，最大 40\n按指令：r 1 次、ra 1 次"
        assertEquals(expected, event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".pc stat", group = group, sender = sender))
        assertEquals(expected, event.sent[0], "`.pc stat` 与 `.hiy` 同源")
    }


    @Test
    fun `log 记录 结束上传`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val (helper, event) = boot()
        val group = "26697"
        val sender = "10111"
        val plainMember = "10112"


        helper.eventMessageReceive(
            MessageReceived.buildMessageEvent(
                instance = helper.getDiceFunction(),
                content = ".log on 私人",
                group = "",
                triggerId = sender,
                nick = "甲",
                isGroupMsg = false
            )
        )
        assertEquals("log 仅限群聊使用", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".log on 夜袭", group = group, sender = plainMember))
        assertEquals("只有群主、群管理员或 master 能改本群设置", event.sent[0])


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".log on 夜袭", group = group, admin = true))
        assertEquals("已开始记录日志：夜袭（log end 结束并上传）", event.sent[0])


        helper.eventMessageReceive(inbound(helper, "今天天气不错", group = group, sender = sender))
        helper.eventMessageReceive(inbound(helper, ".r 1d6", group = group, sender = sender))

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".log list", group = group, sender = sender))
        assertTrue(event.sent[0].contains("->夜袭"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".log end", group = group, admin = true))
        assertEquals("日志已结束并上传：夜袭.txt", event.sent[0])
        assertEquals(1, event.uploads.size)
        assertEquals("夜袭.txt", event.uploads[0].second)
        assertEquals(group, event.uploads[0].third)

        val file = File(event.uploads[0].first)
        assertTrue(file.isFile, "日志文件要在：${file.absolutePath}")
        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("今天天气不错"), text)
        assertTrue(text.contains(".r 1d6"), text)
        assertTrue(text.contains("1d6=3"), "骰娘回复也要进日志：$text")


        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".log list", group = group, sender = sender))
        assertTrue(event.sent[0].contains("夜袭"), event.sent[0])
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".log rm 夜袭", group = group, admin = true))
        assertEquals("已删除日志：夜袭", event.sent[0])
        assertTrue(!file.exists())
    }





    @Test
    fun `ti 优先抽症状牌堆`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val (helper, event) = boot()
        val impl = helper as DiceHelperImpl
        val drawDir = File(impl.functions.root(), stellaris.dice.diceFunction.DiceConstant.SAVE_PATH_DRAW)
        drawDir.mkdirs()
        val deck = File(drawDir, "symptom-flow.json")
        val group = "26665"
        try {
            deck.writeText("""{"临时症状":["自定义症状[1d6]轮"]}""", Charsets.UTF_8)
            impl.functions.drawManager.reload()

            helper.eventMessageReceive(inbound(helper, ".ti", group = group))
            assertEquals("自定义症状3轮", event.sent[0])
        } finally {

            deck.delete()
            impl.functions.drawManager.reload()
            impl.functions.drawManager.reset(group)
        }
    }


    @Test
    fun `改本群设置要管理员`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val group = "27777"
        val denied = "只有群主、群管理员或 master 能改本群设置"

        val plainMember = "10009"

        helper.eventMessageReceive(inbound(helper, ".setcoc 3", group = group, sender = plainMember))
        assertEquals(denied, event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(
            inbound(helper, ".dnd mode on 5e", group = group, sender = plainMember)
        )
        assertEquals(denied, event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".set", group = group, sender = plainMember))
        assertTrue(event.sent[0].startsWith("当前默认骰面数："), "查看不该被拦：${event.sent[0]}")
    }


    @Test
    fun `七体系模式指令真正接到投掷与检定上`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(19, 0, 19)))
        val (helper, event) = boot()

        fun say(content: String, group: String): String {
            event.sent.clear()
            helper.eventMessageReceive(inbound(helper, content, group = group, admin = true))
            assertEquals(1, event.sent.size, "$content 应该只回一条：${event.sent}")
            return event.sent[0]
        }

        val dnd = "31001"
        assertTrue(say(".dnd mode on 5e", dnd).contains("DND 5e"))
        assertTrue(say(".r", dnd).contains("1d20"), "DND 裸 r 应是 d20")
        assertTrue(say(".ra 15", dnd).contains("1d20"), "DND .ra 应是 d20 检定")
        assertTrue(say(".rab 15", dnd).contains("2d20k1"), "DND .rab 应是优势 2d20 取高")

        val dg = "31002"
        assertTrue(say(".setdg on", dg).contains("Delta Green"))
        assertTrue(say(".rab 2 50", dg).contains("/50+(40)"), "DG 两个奖励折算 +40")

        val fate = "31003"
        assertTrue(say(".setfate on", fate).contains("FATE"))
        assertTrue(say(".r", fate).contains("f="), "FATE 裸 r 应是 4dF")

        val dx = "31004"
        assertTrue(say(".setdx on", dx).contains("双十字"))
        assertTrue(say(".r", dx).contains("1c10"), "DX 裸 r 应是 1c10")

        val wx = "31005"
        assertTrue(say(".setwx on", wx).contains("无限流"))
        assertTrue(say(".r", wx).contains("1a10"), "WX 裸 r 应是 1a10")

        val cpr = "31006"
        assertTrue(say(".setcpr on", cpr).contains("Cyberpunk RED"))
        assertTrue(say(".r", cpr).contains("1d10"), "CPR 裸 r 应是 1d10")

        val off = "31007"
        say(".setdnd 5e", off)
        assertTrue(say(".setdnd off", off).contains("已关闭"))
        assertTrue(say(".r", off).contains("1d100"), "关模式后回落默认 d100")
    }

    @Test
    fun `rb rp 是一等普通骰 不再回看不懂`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1)))
        val (helper, event) = boot()
        val group = "32001"

        helper.eventMessageReceive(inbound(helper, ".rb 2", group = group))
        assertEquals(1, event.sent.size)
        assertTrue(event.sent[0].contains("奖励d100"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".rp 2", group = group))
        assertTrue(event.sent[0].contains("惩罚d100"), event.sent[0])
    }

    @Test
    fun `关键词自动回复与 reply 开关`() {
        val (helper, event) = boot()
        val group = "33001"
        val manager = helper.getDiceFunction().diceKeywordsReplyManager
        manager.add(
            stellaris.dice.diceFunction.database.entity.data.KeywordsReplyData.ReplyType.MATCH_FULL,
            0,
            "你好",
            "你好呀 {player}",
            false
        )

        helper.eventMessageReceive(inbound(helper, "你好", group = group))
        assertEquals(1, event.sent.size, "关键词应回一条：${event.sent}")
        assertTrue(event.sent[0].contains("你好呀 甲"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".你好", group = group))
        assertTrue(event.sent.isEmpty(), "带触发符的未知指令不应落入关键词通道：${event.sent}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".reply off this", group = group, admin = true))
        assertTrue(event.sent[0].contains("已关闭"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, "你好", group = group))
        assertTrue(event.sent.isEmpty(), "关闭后不应自动回复：${event.sent}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".reply on this", group = group, admin = true))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, "你好", group = group))
        assertEquals(1, event.sent.size, "重新开启后应恢复：${event.sent}")
    }

    @Test
    fun `本地黑名单静默拦截与 uuid 解黑`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, SENDER_ID)
        val group = "34001"

        helper.eventMessageReceive(inbound(helper, ".black add user 10999 刷屏"))
        assertTrue(event.sent[0].contains("已加入"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".r 1d6", group = group, sender = "10999"))
        assertTrue(event.sent.isEmpty(), "黑名单用户应被静默拦截：${event.sent}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".black list"))
        val uuid = Regex("(\\d+) \\[USER\\] 10999").find(event.sent[0])?.groupValues?.get(1)
            ?: error("列表里找不到条目：${event.sent[0]}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".black rm $uuid"))
        assertTrue(event.sent[0].contains("已移除"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".r 1d6", group = group, sender = "10999"))
        assertEquals(1, event.sent.size, "解黑后应恢复：${event.sent}")
    }

    @Test
    fun `防刷屏阈值生效且 master 豁免`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, SENDER_ID)
        val group = "34002"

        helper.eventMessageReceive(inbound(helper, ".guard during 60"))
        helper.eventMessageReceive(inbound(helper, ".guard times 1"))
        helper.eventMessageReceive(inbound(helper, ".guard spam on"))
        assertTrue(event.sent.last().contains("已开启"), event.sent.last())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".r 1d6", group = group, sender = "10998"))
        assertEquals(1, event.sent.size, "第一条应正常：${event.sent}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".r 1d6", group = group, sender = "10998"))
        assertTrue(event.sent.isEmpty(), "超过阈值应静默：${event.sent}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".r 1d6", group = group, sender = SENDER_ID))
        assertEquals(1, event.sent.size, "master 应豁免：${event.sent}")

        helper.eventMessageReceive(inbound(helper, ".guard spam off"))
    }

    @Test
    fun `group 开关能禁用 jrrp`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val group = "34003"

        helper.eventMessageReceive(inbound(helper, ".group +禁用jrrp", group = group, admin = true))
        assertTrue(event.sent[0].contains("已禁用"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".jrrp", group = group))
        assertTrue(event.sent[0].contains("已禁用今日人品"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".group -禁用jrrp", group = group, admin = true))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".jrrp", group = group))
        assertTrue(event.sent[0].contains("今日人品"), event.sent[0])
    }

    @Test
    fun `踢人与禁言会自动拉黑 除非 nolawnosky`() {
        val (helper, _) = boot()
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, SENDER_ID)

        helper.eventKick(10888, 1L)
        assertTrue(helper.getDiceFunction().let {
            (it as stellaris.dice.diceFunction.DiceFunctionImpl).blacklistStore
                .isUserBlocked("10888")
        })

        helper.writeDiceConfig(DiceConfigStore.NO_LAWNOSKY, "1")
        helper.eventMute(10887, 1L)
        assertFalse(
            (helper.getDiceFunction() as stellaris.dice.diceFunction.DiceFunctionImpl)
                .blacklistStore.isUserBlocked("10887")
        )
    }

    @Test
    fun `rd 跟随模式 set 提醒 master rh 与 sn 兜底`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        helper.writeDiceConfig(DiceConfigStore.MASTER_QQ, SENDER_ID)
        helper.writeDiceConfig(DiceConfigStore.DEFAULT_DICE_NUMBER, "100")
        val group = "35001"

        helper.eventMessageReceive(inbound(helper, ".rd 随便聊聊", group = group))
        assertTrue(event.sent[0].contains("1d100"), event.sent[0])
        assertTrue(event.sent[0].contains("随便聊聊"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".dnd mode on 5e", group = group, admin = true))
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".rd 随便聊聊", group = group))
        assertTrue(event.sent[0].contains("1d20"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".set 50", group = group))
        assertTrue(event.sent[0].contains("模式开着"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".help dg", group = group))
        assertTrue(event.sent[0].contains("三角洲"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".master rh off"))
        assertTrue(event.sent[0].contains("暗骰总开关已关闭"), event.sent[0])
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".rh 1d6", group = group))
        assertEquals(1, event.sent.size, "关闭后只应回提示：${event.sent}")
        assertTrue(event.sent[0].contains("已被 master 关闭"), event.sent[0])
        helper.eventMessageReceive(inbound(helper, ".master rh on"))

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".sn coc", group = group))
        assertTrue(event.sent[0].contains("sn.toml"), event.sent[0])

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".black cloud"))
        assertTrue(event.sent[0].contains("未启用"), event.sent[0])
    }

    @Test
    fun `配置总表是宿主可解析的 JSON`() {
        val (helper, _) = boot()
        val json = org.json.JSONObject(helper.getDiceConfigsIndicator())
        assertTrue(json.keys().asSequence().toList().isNotEmpty())
        val keys = json.keys().asSequence().flatMap { group ->
            val array = json.optJSONArray(group) ?: org.json.JSONArray()
            (0 until array.length()).mapNotNull { index ->
                array.optJSONArray(index)?.optString(0)
            }
        }.toSet()
        assertTrue(keys.contains(DiceConfigStore.RULE_MODE), "应包含规则模式键")
        assertTrue(keys.contains(DiceConfigStore.REPLY_ON), "应包含自动回复开关")
        assertTrue(keys.contains(DiceConfigStore.SENTENCE_ROLL), "应包含普通骰文本")
    }

    @Test
    fun `group -词条 只屏蔽本群关键词`() {
        val (helper, event) = boot()
        helper.getDiceFunction().diceKeywordsReplyManager.add(
            stellaris.dice.diceFunction.database.entity.data.KeywordsReplyData.ReplyType.MATCH_FULL,
            0,
            "本群专属签到",
            "签到成功",
            false
        )
        helper.eventMessageReceive(inbound(helper, "本群专属签到"))
        assertEquals(listOf("签到成功"), event.sent, "屏蔽前应正常回复")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".group -词条 本群专属签到", admin = true))
        assertTrue(event.sent.single().contains("已在本群屏蔽"), event.sent.single())

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, "本群专属签到"))
        assertTrue(event.sent.isEmpty(), "屏蔽后本群不该再回复：${event.sent}")

        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, ".group +词条 本群专属签到", admin = true))
        assertTrue(event.sent.single().contains("已取消屏蔽"), event.sent.single())
        event.sent.clear()
        helper.eventMessageReceive(inbound(helper, "本群专属签到"))
        assertEquals(listOf("签到成功"), event.sent)
    }

    @Test
    fun `插件不能覆盖内置投掷指令`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val (helper, event) = boot()
        val impl = helper as DiceHelperImpl
        val customDir = File(impl.functions.root(), "custom")
        customDir.mkdirs()
        val pluginFile = File(customDir, "command-boundary.toml")
        pluginFile.writeText(
            """
            [[lua]]
            name = "command-boundary"
            lua = "function hijack_r(msg) return '插件劫持了投掷' end function hijack_cloud(msg) return '插件劫持了云能力' end function plugin_probe(msg) return '插件自定义指令' end commands = { r = 'hijack_r', blackcloud = 'hijack_cloud', plugin_probe = 'plugin_probe' }"
            """.trimIndent(),
            Charsets.UTF_8
        )
        impl.functions.pluginLoader.reload()
        try {
            helper.eventMessageReceive(inbound(helper, ".r 1d1", group = "29997"))
            assertTrue(event.sent.isNotEmpty())
            assertFalse(event.sent.joinToString("").contains("插件劫持了投掷"))

            event.sent.clear()
            helper.eventMessageReceive(inbound(helper, ".blackcloud", group = "29997"))
            assertTrue(event.sent.single().contains("未启用"))
            assertFalse(event.sent.single().contains("插件劫持了云能力"))

            event.sent.clear()
            helper.eventMessageReceive(inbound(helper, ".plugin_probe", group = "29997"))
            assertEquals(listOf("插件自定义指令"), event.sent)
        } finally {
            pluginFile.delete()
            impl.functions.pluginLoader.reload()
        }
    }

    @Test
    fun `数据根只允许设一次`() {
        boot("build/tmp/kernel-flow-a")
        assertThrows(IllegalStateException::class.java) { DiceManager.setBasePath("build/tmp/another") }
        assertNotNull(DiceManager.getBasePath())
    }


    @Test
    fun `实例目录用新前缀`() {
        val (helper, _) = boot()
        assertTrue(
            helper.getDiceFunction().root().endsWith("StellarisData_$BOT_ID"),
            "实例根：${helper.getDiceFunction().root()}"
        )
    }

    private companion object {
        const val BOT_ID = "10000"
        const val SENDER_ID = "10001"
        const val GROUP_ID = "20002"
        const val OPPONENT_ID = "10002"








        val RUN_BASE_PATH: String = "build/tmp/kernel-flow-${java.util.UUID.randomUUID()}"
    }
}
