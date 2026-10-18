package stellaris.dice.diceFunction.card

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.random.RandomService
import java.util.Random







class CardEditorTest {

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




    @Test
    fun `批量赋值 连写与空格都一样`() {
        listOf("力量65体质50", "力量65 体质50", " 力量 65 体质 50 ").forEach { param ->
            val request = CardEditor.parse(param)
            assertTrue(request is CardEditor.Request.Set, "「$param」该解析成改属性")
            val edits = (request as CardEditor.Request.Set).edits
            assertEquals(listOf("力量", "体质"), edits.map { it.name }, "「$param」")
            assertEquals(listOf(65, 50), edits.map { it.value }, "「$param」")
            assertTrue(edits.none { it.isDelta }, "不带符号 = 赋值，不是增量：$param")
        }
    }


    @Test
    fun `带符号是增量`() {
        (CardEditor.parse("侦查+10") as CardEditor.Request.Set).edits.let {
            assertEquals(1, it.size)
            assertEquals("侦查", it[0].name)
            assertEquals(10, it[0].value)
            assertTrue(it[0].isDelta)
        }
        (CardEditor.parse("侦查-5") as CardEditor.Request.Set).edits.let {
            assertEquals(-5, it[0].value)
            assertTrue(it[0].isDelta)
        }

        (CardEditor.parse("力量65侦查+10") as CardEditor.Request.Set).edits.let {
            assertEquals(listOf("力量", "侦查"), it.map { edit -> edit.name })
            assertEquals(listOf(65, 10), it.map { edit -> edit.value })
            assertEquals(listOf(false, true), it.map { edit -> edit.isDelta })
        }
    }




    @Test
    fun `看一项与看全部`() {
        assertEquals(CardEditor.Request.Show("侦查"), CardEditor.parse("侦查"))
        assertEquals(CardEditor.Request.Show("侦查"), CardEditor.parse("show侦查"))
        assertEquals(CardEditor.Request.Show("侦查"), CardEditor.parse("show 侦查"))
        assertEquals(CardEditor.Request.Show("侦查"), CardEditor.parse("SHOW侦查"), "show 不区分大小写")
        assertEquals(CardEditor.Request.ShowAll, CardEditor.parse("show"))
        assertEquals(CardEditor.Request.ShowAll, CardEditor.parse(""))
        assertEquals(CardEditor.Request.ShowAll, CardEditor.parse("   "))
    }


    @Test
    fun `看不懂的参数`() {
        assertEquals(CardEditor.Request.Usage, CardEditor.parse("3d6"))
        assertEquals(CardEditor.Request.Usage, CardEditor.parse("65"))
        assertEquals(CardEditor.Request.Usage, CardEditor.parse("+10"))
    }


    @Test
    fun `前半段能解出改动时尾巴被忽略`() {
        (CardEditor.parse("力量65@@@") as CardEditor.Request.Set).edits.let {
            assertEquals(1, it.size)
            assertEquals("力量", it[0].name)
        }
    }




    @Test
    fun `赋值与增量`() {
        val card = PlayerCardData.create("10001", "测试卡")
        card.setProperty("侦查", 30)

        CardEditor.apply(card, "侦查+10")
        assertEquals(40, card.readProperty("侦查"), "增量：30 + 10")

        CardEditor.apply(card, "侦查-5")
        assertEquals(35, card.readProperty("侦查"), "增量：40 - 5")

        CardEditor.apply(card, "侦查50")
        assertEquals(50, card.readProperty("侦查"), "赋值：直接覆盖成 50")
    }


    @Test
    fun `增量用到卡上没的属性时按缺省值算`() {
        val card = PlayerCardData.create("10001", "测试卡")
        val outcome = CardEditor.apply(card, "斗殴+10") as CardEditor.Outcome.Updated
        assertEquals(listOf("斗殴"), outcome.appliedNames)
        assertEquals(
            1 + 10,
            card.readProperty("斗殴"),
            "上游标准技能表未落，故按 PlayerCardData.DEFAULT_PROPERTY_VALUE 起算"
        )
    }


    @Test
    fun `名字过长不写并报告`() {
        val card = PlayerCardData.create("10001", "测试卡")
        val tooLong = "一二三四五六七八九"
        val outcome = CardEditor.apply(card, "$tooLong 50 力量60") as CardEditor.Outcome.Updated
        assertEquals(listOf("力量"), outcome.appliedNames, "只有 力量 写进去了")
        assertEquals(listOf(tooLong), outcome.rejectedNames, "过长的名字要报告")
        assertTrue(!card.hasProperty(tooLong), "过长的名字不该出现在卡上")
        assertEquals(60, card.readProperty("力量"))
    }


    @Test
    fun `看与改的分界`() {
        val card = PlayerCardData.create("10001", "测试卡")
        card.setProperty("侦查", 30)
        assertEquals(CardEditor.Outcome.Shown("侦查"), CardEditor.apply(card, "侦查"))
        assertEquals(CardEditor.Outcome.ShowAll, CardEditor.apply(card, "show"))
        assertEquals(30, card.readProperty("侦查"), "看不会改卡")
    }


    @Test
    fun `渲染成可回填的形态`() {
        val card = PlayerCardData.create("10001", "测试卡")
        card.setProperty("力量", 65)
        card.setProperty("体质", 50)
        assertEquals("力量65 体质50", CardEditor.render(card, listOf("力量", "体质")))
        assertEquals("", CardEditor.render(card, emptyList()))
        assertEquals(
            "侦查${PlayerCardData.DEFAULT_PROPERTY_VALUE}",
            CardEditor.render(card, listOf("侦查")),
            "卡上没有的项渲染成缺省值（与读卡口径一致）"
        )
    }


    @Test
    fun `名字长度上限用卡实现的同一个常量`() {
        val card = PlayerCardData.create("10001", "测试卡")
        val exactlyAtLimit = "一".repeat(DiceConstant.MAX_SKILL_LENGTH)
        val outcome = CardEditor.apply(card, "${exactlyAtLimit}50") as CardEditor.Outcome.Updated
        assertEquals(listOf(exactlyAtLimit), outcome.appliedNames, "正好等于上限要能写进去")
    }




    @Test
    fun `骰式值 解析`() {
        (CardEditor.parse("体力3d6") as CardEditor.Request.Set).edits.single().let {
            assertEquals("体力", it.name)
            assertEquals("3d6", it.text)
            assertTrue(!it.isDelta, "不带符号 = 赋值")
        }
        (CardEditor.parse("san-1d6") as CardEditor.Request.Set).edits.single().let {
            assertEquals("理智", it.name, "san 别名落到理智")
            assertEquals("-1d6", it.text)
            assertTrue(it.isDelta, "带符号 = 增量")
        }
        (CardEditor.parse("体力3d6+1d4") as CardEditor.Request.Set).edits.single().let {
            assertEquals("3d6+1d4", it.text, "带算子的骰式要整段吃掉")
        }

        (CardEditor.parse("delta5") as CardEditor.Request.Set).edits.single().let {
            assertEquals("delta", it.name)
            assertEquals(5, it.value)
        }
    }

    @Test
    fun `删除与清空 解析`() {
        assertEquals(CardEditor.Request.Delete("侦查"), CardEditor.parse("del 侦查"))
        assertEquals(CardEditor.Request.Delete("侦查"), CardEditor.parse("DEL 侦查"))
        assertEquals(CardEditor.Request.Clear, CardEditor.parse("clr"))
        assertEquals(CardEditor.Request.Clear, CardEditor.parse("CLR"))
    }


    @Test
    fun `骰式值 真掷后写卡`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(1, 2, 3)))
        val card = PlayerCardData.create("10001", "测试卡")
        CardEditor.apply(card, "体力3d6")
        assertEquals(9, card.readProperty("体力"))

        RandomService.installTestProvider(ScriptedRandom(listOf(4)))
        card.setProperty("理智", 55)
        CardEditor.apply(card, "san-1d6")
        assertEquals(50, card.readProperty("理智"), "55 - 5")
    }


    @Test
    fun `骰式不成立时整条不动卡`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val card = PlayerCardData.create("10001", "测试卡")
        card.setProperty("力量", 50)
        assertEquals(
            CardEditor.Outcome.Usage,
            CardEditor.apply(card, "力量60体力3d0"),
            "后一项不成立，前一项也不该写进去"
        )
        assertEquals(50, card.readProperty("力量"), "整条没生效")
        assertTrue(!card.hasProperty("体力"))
    }

    @Test
    fun `删除与清空 施加`() {
        val card = PlayerCardData.create("10001", "测试卡")
        card.setProperty("侦查", 70)
        card.setProperty("力量", 50)

        assertEquals(CardEditor.Outcome.Deleted("侦查"), CardEditor.apply(card, "del 侦查"))
        assertTrue(!card.hasProperty("侦查"))
        assertEquals(
            CardEditor.Outcome.Missing("侦查"),
            CardEditor.apply(card, "del 侦查"),
            "再删一次 = 不存在"
        )

        assertEquals(CardEditor.Outcome.Cleared, CardEditor.apply(card, "clr"))
        assertEquals(0, card.propertyCount)
    }
}
