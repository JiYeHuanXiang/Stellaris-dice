package stellaris.dice.diceFunction.responder

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.random.RandomService
import stellaris.dice.diceFunction.template.DiceMessageTemplate
import java.util.Random











class DiceResponderTest {

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







    private fun reply(param: String, rule: RollRules = RollRules.R, defaultDice: Int = 100): String {
        val template = DiceMessageTemplate()
        RespondContext.seedTemplate(template, senderNick = "甲", senderId = "10001", groupId = "20002")
        val context = RespondContext(
            param = param,
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = template,
            sentences = DefaultSentences,
            defaultDiceNumber = defaultDice
        )
        return template.buildAll(DiceResponder.r(context, rule))
    }




    @Test
    fun `普通骰 无理由`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1, 2)))
        assertEquals("甲进行的骰点结果:3d6={1+2+3}(6)=6\n", reply("3d6"))
    }


    @Test
    fun `普通骰 带理由`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1, 2)))
        assertEquals("由于为了测试，甲进行的骰点结果:3d6={1+2+3}(6)=6\n", reply("3d6 为了测试"))
    }

    @Test
    fun `带理由时理由里的换行占位被还原`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val text = reply("d 第一行\\n第二行")
        assertTrue(text.contains("第一行\n第二行"), "理由里的 \\n 要变成真换行：$text")
    }







    @Test
    fun `裸 d 补默认面数`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertTrue(reply("d6").contains("d6="), "右值已给定时原样")
        assertTrue(reply("3d").contains("3d100="), "右值缺省时补默认面数")
        assertTrue(reply("d", defaultDice = 20).contains("d20="), "默认面数可配")
    }


    @Test
    fun `裸数字改写成 1dN`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertTrue(reply("50").contains("1d50"), "纯数字 → 1d50")
        assertTrue(reply("50+5").contains("1d50+5"), "数字开头的算式 → 1d50+5")
    }


    @Test
    fun `骰式不被误当成裸数字`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 0, 0)))
        assertTrue(reply("3d6").contains("3d6"), "骰式原样")
        assertTrue(!reply("3d6").contains("1d3"), "不能被改写成 1d3d6")
    }


    @Test
    fun `空参数掷默认骰`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertTrue(reply("").contains("1d100"), "光杆 .r 掷默认骰")
    }


    @Test
    fun `悬空算子被去掉`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 0, 0)))
        assertTrue(reply("3d6+").contains("3d6"), "尾巴上的 + 被去掉")
    }




    @Test
    fun `无限流 双十字 FATE 的默认表达式`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertTrue(reply("", RollRules.WW).contains("10a10"), "无限流默认 10a10")
        assertTrue(reply("", RollRules.DX).contains("1c10"), "双十字默认 1c10")

        assertTrue(reply("", RollRules.FATE).contains("f="), "FATE 默认 f（即 4f3）")
    }







    @Test
    fun `检定族不该走普通骰那一支`() {
        assertEquals(DefaultSentences.sentenceIllegal, DiceResponder.r(contextOf("侦查"), RollRules.RA))
        assertEquals(DefaultSentences.sentenceIllegal, DiceResponder.r(contextOf("侦查"), RollRules.RAH))
    }




    @Test
    fun `重复掷 N 次`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val text = reply("3#1d6")
        assertTrue(text.contains("1d6={ "), "重复掷要标出表达式：$text")
        assertTrue(text.contains("}(3)"), "三次各掷出 1，合计 3：$text")
        assertTrue(text.contains("1、1、1"), "简略形态用顿号分隔：$text")
    }


    @Test
    fun `重复掷 次数在后`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val text = reply("1d6#3")
        assertTrue(text.contains("1d6={ "), "重复掷要标出表达式：$text")
        assertTrue(text.contains("}(3)"), "三次各掷出 1，合计 3：$text")
        assertTrue(!text.contains("#"), "次数标记不该回显：$text")
    }

    @Test
    fun `重复次数过多`() {
        assertEquals(
            DefaultSentences.sentenceIllegalTooMuch,
            DiceResponder.r(contextOf("11#1d6"), RollRules.R),
            "11 次超过上限"
        )
    }


    @Test
    fun `骰式不成立给中性提示`() {
        assertEquals(DefaultSentences.sentenceIllegal, DiceResponder.r(contextOf("1d0"), RollRules.R))
        assertEquals(DefaultSentences.sentenceIllegal, DiceResponder.r(contextOf("0d6"), RollRules.R))
    }


    @Test
    fun `简化模式 不给开场句`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val template = DiceMessageTemplate()
        RespondContext.seedTemplate(template, "甲", "10001", "20002")
        val context = RespondContext(
            param = "1d6",
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = template,
            sentences = DefaultSentences,
            simplify = true
        )
        val text = template.buildAll(DiceResponder.r(context))
        assertTrue(!text.contains("进行的骰点结果"), "简化模式不该有开场句：$text")
        assertTrue(text.contains("1d6=3"), "要给出表达式与结果：$text")
    }


    @Test
    fun `range 报取值区间`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 1, 2)))
        assertEquals(
            "取值范围：3 ~ 18\n运算细节：3d6 = {1+2+3}(6) = 6",
            DiceResponder.range(contextOf("3d6"))
        )
    }


    @Test
    fun `range 明细截断`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val text = DiceResponder.range(contextOf("20d6"))
        assertTrue(text.contains("..."), text)
    }


    @Test
    fun `range 不改写表达式`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val context = RespondContext(
            param = "d",
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = DiceMessageTemplate(),
            sentences = DefaultSentences,
            defaultDiceNumber = 20
        )
        val text = DiceResponder.range(context)
        assertTrue(text.contains("1 ~ 100"), "裸 d 的区间是 1~100（引擎默认），不是群配置的 20：$text")
    }


    @Test
    fun `range 非法表达式给中性提示`() {
        assertEquals(DefaultSentences.sentenceIllegal, DiceResponder.range(contextOf("1d0")))
    }


    @Test
    fun `db 替换伤害加值`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        assertTrue(reply("1d6+db").contains("1d6+0"), "无卡时 db=0：${reply("1d6+db")}")

        val negative = RespondContext(
            param = "1d6+db",
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = DiceMessageTemplate(),
            sentences = DefaultSentences,
            damageBonus = "-1"
        )

        val rendered = negative.template.buildAll(DiceResponder.r(negative))
        assertTrue(
            rendered.contains("1d6+(-1)"),
            "负的 db 要括起来，否则引擎不认 `+-`：$rendered"
        )
    }

    private fun contextOf(param: String): RespondContext {
        val template = DiceMessageTemplate()
        RespondContext.seedTemplate(template, "甲", "10001", "20002")
        return RespondContext(
            param = param,
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = template,
            sentences = DefaultSentences
        )
    }
}
