package stellaris.dice.diceFunction.card

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.random.RandomService
import java.util.Random








class CardBuilderTest {

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
    fun `COC7 生成九属性`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val card = PlayerCardData.create("10001", "调查员")
        card.rule = PlayerCardData.RuleType.COC7

        CardBuilder.build(card, force = false)!!
        assertEquals(15, card.readProperty("力量"), "3d6×5")
        assertEquals(15, card.readProperty("体质"))
        assertEquals(15, card.readProperty("敏捷"))
        assertEquals(15, card.readProperty("外貌"))
        assertEquals(15, card.readProperty("意志"))
        assertEquals(15, card.readProperty("幸运"))
        assertEquals(40, card.readProperty("体型"), "(2d6+6)×5")
        assertEquals(40, card.readProperty("智力"))
        assertEquals(40, card.readProperty("教育"))
    }

    @Test
    fun `build 只补缺失 redo 强制重算`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val card = PlayerCardData.create("10001", "调查员")
        card.setProperty("力量", 99)

        CardBuilder.build(card, force = false)
        assertEquals(99, card.readProperty("力量"), "build 不覆盖已有值")
        assertEquals(15, card.readProperty("体质"), "缺失的才补")

        CardBuilder.build(card, force = true)
        assertEquals(15, card.readProperty("力量"), "redo 覆盖全部")
    }

    @Test
    fun `DND5E 六属性与派生字段`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val card = PlayerCardData.create("10001", "冒险者")
        card.rule = PlayerCardData.RuleType.DND5E

        val result = CardBuilder.build(card, force = false)!!
        listOf("力量", "敏捷", "体质", "智力", "感知", "魅力").forEach {
            assertEquals(3, card.readProperty(it), "4d6k3 全 1 = 3")
        }
        assertEquals(6, card.readProperty("dndmaxhp"), "10 + 体质调整(-4)")
        assertEquals(6, card.readProperty("ac"), "10 + 敏捷调整(-4)")
        assertEquals(6, card.readProperty("pp"), "10 + 感知调整(-4)")
        assertEquals(6, card.readProperty("体力"), "名片 hp 读「体力」")
        assertEquals(true, result.summary.contains("-4"))
    }

    @Test
    fun `DND3R 不生成被动察觉`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val card = PlayerCardData.create("10001", "冒险者")
        card.rule = PlayerCardData.RuleType.DND3R

        CardBuilder.build(card, force = false)
        assertEquals(6, card.readProperty("dndmaxhp"))
        assertEquals(false, card.hasProperty("pp"), "3R 没有被动察觉字段")
    }

    @Test
    fun `不支持的卡型返回 null`() {
        val card = PlayerCardData.create("10001", "赛博人")
        card.rule = PlayerCardData.RuleType.CPR
        assertNull(CardBuilder.build(card, force = false))
    }

    @Test
    fun `调整值向下取整`() {
        assertEquals(-4, CardBuilder.modifier(3))
        assertEquals(-1, CardBuilder.modifier(9))
        assertEquals(0, CardBuilder.modifier(10))
        assertEquals(0, CardBuilder.modifier(11))
        assertEquals(1, CardBuilder.modifier(12))
        assertEquals("+2", CardBuilder.modifierText(2))
        assertEquals("-1", CardBuilder.modifierText(-1))
    }
}
