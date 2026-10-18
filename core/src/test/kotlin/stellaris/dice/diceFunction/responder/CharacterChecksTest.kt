package stellaris.dice.diceFunction.responder

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.random.RandomService
import java.util.Random









class CharacterChecksTest {

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
    fun `出目高于技能值就成长`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(44, 6)))
        val result = CharacterChecks.growth(30)
        assertEquals(45, result.roll)
        assertTrue(result.grown)
        assertEquals(7, result.growth)
        assertEquals(37, result.newValue, "30 + 7")
        assertEquals("1d100=45/30 成长+7，30→37", result.rollSegment)
    }


    @Test
    fun `出目不够就不成长`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(24)))
        val result = CharacterChecks.growth(70)
        assertEquals(25, result.roll)
        assertTrue(!result.grown)
        assertEquals(0, result.growth)
        assertEquals(70, result.newValue, "不改卡")
    }





    @Test
    fun `不额外加九十六条款`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(95)))
        val result = CharacterChecks.growth(99)
        assertTrue(!result.grown, "96 不大于 99，按手册不成长")
    }


    @Test
    fun `成长不超过九十九`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(98, 7)))
        val result = CharacterChecks.growth(95)
        assertEquals(8, result.growth)
        assertEquals(CharacterChecks.SKILL_CAP, result.newValue, "95 + 8 = 103 → 夹到 99")
    }




    @Test
    fun `成功与失败各取一个损失表达式`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        CharacterChecks.sanity(CharacterChecks.parseSan("1/1d4")!!, 55)!!.let {
            assertEquals(CheckLevel.SUCCESS, it.level)
            assertEquals(1, it.loss)
            assertEquals(54, it.newValue)
            assertEquals("1d100=40/55 成功，理智减少1点（55→54）", it.rollSegment)
        }

        RandomService.installTestProvider(ScriptedRandom(listOf(69, 3)))
        CharacterChecks.sanity(CharacterChecks.parseSan("1/1d4")!!, 55)!!.let {
            assertEquals(CheckLevel.FAILURE, it.level)
            assertEquals(4, it.loss)
            assertEquals(51, it.newValue)
        }
    }


    @Test
    fun `大成功取下界 大失败取上界`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        CharacterChecks.sanity(CharacterChecks.parseSan("1d4/1d10")!!, 55)!!.let {
            assertEquals(CheckLevel.CRITICAL_SUCCESS, it.level)
            assertEquals(1, it.loss, "成功式 1d4 的最小值")
        }

        RandomService.installTestProvider(ScriptedRandom(listOf(99)))
        CharacterChecks.sanity(CharacterChecks.parseSan("1d4/1d10")!!, 55)!!.let {
            assertEquals(CheckLevel.CRITICAL_FAILURE, it.level)
            assertEquals(10, it.loss, "失败式 1d10 的最大值")
        }
    }


    @Test
    fun `按房规判档`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(4)))
        CharacterChecks.sanity(
            CharacterChecks.parseSan("1d6/1d10")!!,
            3,
            CocHouseRule.THREE
        )!!.let {
            assertEquals(CheckLevel.CRITICAL_SUCCESS, it.level, "房规3：1~5 就是大成功")
            assertEquals(1, it.loss, "大成功取成功式最小值")
        }
    }


    @Test
    fun `末尾数字是临时理智`() {
        assertEquals(60, CharacterChecks.parseSan("1/1d4 60")!!.sanOverride)
        assertNull(CharacterChecks.parseSan("1/1d4")!!.sanOverride)
    }


    @Test
    fun `末尾非数字不认`() {
        assertNull(CharacterChecks.parseSan("1/1d6 张三"))
        assertNull(CharacterChecks.parseSan("1/1d6 60 70"))
    }


    @Test
    fun `理智参数不合法`() {
        listOf("", "1", "1/", "/1d4").forEach { param ->
            assertNull(CharacterChecks.parseSan(param), "「$param」该判不合法")
        }
    }


    @Test
    fun `损失骰式不合法`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39)))
        assertNull(CharacterChecks.sanity(CharacterChecks.parseSan("abc/1d4")!!, 55))
    }


    @Test
    fun `损失表达式可以带算子`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(39, 2)))
        val result = CharacterChecks.sanity(CharacterChecks.parseSan("1d6+1/1d10")!!, 80)!!
        assertTrue(result.success)
        assertEquals(4, result.loss, "1d6=3，+1 = 4")
        assertEquals(76, result.newValue)
    }


    @Test
    fun `理智不低于零`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(69, 3)))
        val result = CharacterChecks.sanity(CharacterChecks.parseSan("1/1d4")!!, 2)!!
        assertEquals(4, result.loss)
        assertEquals(0, result.newValue, "2 - 4 → 夹到 0")
    }


    @Test
    fun `理智的键是理智`() {
        assertEquals("理智", CharacterChecks.SAN_ATTRIBUTE)
    }
}
