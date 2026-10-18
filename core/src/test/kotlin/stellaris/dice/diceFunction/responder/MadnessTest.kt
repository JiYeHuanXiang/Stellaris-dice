package stellaris.dice.diceFunction.responder

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.random.RandomService
import java.util.Random







class MadnessTest {

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
    fun `两张表都是十行`() {
        assertEquals(10, Madness.REAL_TIME.size)
        assertEquals(10, Madness.SUMMARY.size)
        (Madness.REAL_TIME + Madness.SUMMARY).forEach { entry ->
            assertTrue(entry.label.isNotEmpty(), "表目名不能为空")
            assertTrue(entry.summary.isNotEmpty(), "「${entry.label}」缺一句话说明")
        }
    }


    @Test
    fun `ti 走即时症状表并以轮计`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2, 6)))
        val result = Madness.roll(Madness.Kind.TEMPORARY)

        assertEquals(3, result.tableRoll)
        assertEquals("暴力倾向", result.entry.label, "即时症状表的第 3 条")
        assertEquals(7, result.durationRoll)
        assertEquals("1d10=7 轮", result.durationText)
    }


    @Test
    fun `li 走总结症状表并以小时计`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 9)))
        val result = Madness.roll(Madness.Kind.LONG_TERM)

        assertEquals(1, result.tableRoll)
        assertEquals("失忆", result.entry.label)
        assertEquals(10, result.durationRoll)
        assertEquals("1d10=10 小时", result.durationText)
    }





    @Test
    fun `两张表内容不同`() {
        assertEquals("假性残疾", Madness.REAL_TIME[1].label)
        assertEquals("被窃", Madness.SUMMARY[1].label)
        assertEquals("失忆", Madness.REAL_TIME[0].label, "两边第 1 条都是失忆（规则书如此）")
        assertEquals("失忆", Madness.SUMMARY[0].label)
    }


    @Test
    fun `先掷表目再掷时长`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(9, 0)))
        val result = Madness.roll(Madness.Kind.TEMPORARY)
        assertEquals(10, result.tableRoll)
        assertEquals("躁狂症", result.entry.label)
        assertEquals(1, result.durationRoll)
    }
}
