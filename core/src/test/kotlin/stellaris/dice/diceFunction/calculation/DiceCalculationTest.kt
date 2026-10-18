package stellaris.dice.diceFunction.calculation

import onedice.defines.ResErrorException
import onedice.defines.ResErrorType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.card.CardAbilities
import stellaris.dice.diceFunction.random.RandomService
import stellaris.dice.diceFunction.responder.RollOptions
import java.util.Random










class DiceCalculationTest {








    private class ScriptedRandom(private val units: List<Long>) : Random() {
        private var cursor = 0
        override fun nextLong(): Long {
            val unit = units[cursor % units.size]
            cursor++
            return unit shl 1
        }
    }

    private fun install(vararg units: Long) {
        RandomService.installTestProvider(ScriptedRandom(units.toList()))
    }

    @AfterEach
    fun restore() {
        RandomService.installTestProvider(null)
    }




    @Test
    fun `单颗骰 的明细不重复报数`() {
        install(2)
        val result = DiceCalculation.rollCalculation("1d6")
        assertEquals(3, result.intNumber)
        assertEquals("3", result.numberString)
        assertEquals("3", result.detail)
        assertEquals("1d6=3", result.toString())
    }

    @Test
    fun `带加法的表达式 明细保留过程与数字明细`() {
        install(0, 1, 2)
        val result = DiceCalculation.rollCalculation("3d6+2")
        assertEquals(8, result.intNumber)
        assertEquals("{1+2+3}(6)+2=6+2", result.detail)
        assertEquals("3d6+2={1+2+3}(6)+2=6+2=8", result.toString())
    }


    @Test
    fun `简化模式 只给结果`() {
        install(2)
        val result = DiceCalculation.rollCalculation("1d6", RollOptions(simple = true))
        assertEquals(3, result.intNumber)
        assertEquals("1d6=3", result.toString())
    }




    @Test
    fun `取上界与下界 不掷骰`() {
        assertEquals(18, DiceCalculation.rollCalculation("3d6", RollOptions(dxd_mode = RollOptions.DxDModes.MAXIMIZE)).intNumber)
        assertEquals(3, DiceCalculation.rollCalculation("3d6", RollOptions(dxd_mode = RollOptions.DxDModes.MINIMIZE)).intNumber)
        assertEquals(2, DiceCalculation.rollCalculation("1d6+1d6", RollOptions(dxd_mode = RollOptions.DxDModes.MINIMIZE)).intNumber)
    }


    @Test
    fun `裸 d 的面数可配`() {
        assertEquals(100, DiceCalculation.rollCalculation("d", RollOptions(dxd_mode = RollOptions.DxDModes.MAXIMIZE)).intNumber)
        assertEquals(
            20,
            DiceCalculation.rollCalculation(
                "d",
                RollOptions(dxd_mode = RollOptions.DxDModes.MAXIMIZE, dxd_defaultNumber = 20)
            ).intNumber
        )
    }


    @Test
    fun `负值处理`() {
        assertEquals(0, DiceCalculation.rollCalculation("2-5").intNumber)
        assertEquals(-3, DiceCalculation.rollCalculation("2-5", RollOptions(allowNegativeNumber = true)).intNumber)
    }







    @Test
    fun `加骰池 明细带上加骰标记`() {

        install(9, 0)
        val result = DiceCalculation.rollCalculation("1a10")
        assertEquals(1, result.intNumber, "只有那颗 10 同时够加骰线与成功线")
        assertTrue(result.detail.contains("<10*>"), "加骰标记应带星号：${result.detail}")
    }


    @Test
    fun `明细过长会截断`() {
        val result = DiceCalculation.rollCalculation("5000d6", RollOptions(dxd_mode = RollOptions.DxDModes.MAXIMIZE))
        assertTrue(result.detail.contains("..."), "应在上限处截断：长度=${result.detail.length}")
        assertTrue(result.detail.length < DiceConstant.DICE_MAX_ROLL_STRING_LENGTH + 100)
    }


    @Test
    fun `骰式不成立抛业务异常`() {
        val failure = assertThrows(ResErrorException::class.java) {
            DiceCalculation.rollCalculation("1+")
        }
        assertEquals(ResErrorType.INPUT_RAW_INVALID, failure.type)
    }




    @Test
    fun `stringToInt 取开头整数`() {
        assertEquals(50, DiceCalculation.stringToInt("50斗殴", 1))
        assertEquals(50, DiceCalculation.stringToInt("50", 1))
        assertEquals(-3, DiceCalculation.stringToInt("-3", 0))
        assertEquals(12, DiceCalculation.stringToInt("12abc", 0))
        assertEquals(7, DiceCalculation.stringToInt("", 7))
        assertEquals(7, DiceCalculation.stringToInt(null, 7))
        assertEquals(7, DiceCalculation.stringToInt("abc", 7))
        assertEquals(42, DiceCalculation.stringToInt(42, 7), "已经是整数就直接用")
    }

    @Test
    fun `isNumber 与 isStartWithNumber`() {
        assertTrue(DiceCalculation.isNumber("12"))
        assertTrue(DiceCalculation.isNumber("007"))
        assertFalse(DiceCalculation.isNumber("1a"))
        assertFalse(DiceCalculation.isNumber(""))
        assertFalse(DiceCalculation.isNumber(null))

        assertTrue(DiceCalculation.isStartWithNumber("50斗殴"))
        assertFalse(DiceCalculation.isStartWithNumber("斗殴50"))
        assertFalse(DiceCalculation.isStartWithNumber(""), "空串不以数字开头（上游这里会越界）")
    }


    @Test
    fun `toDBC 全角转半角`() {
        assertEquals("123+4.", DiceCalculation.toDBC("１２３＋４。"))
        assertEquals("1 2", DiceCalculation.toDBC("1\u30002"), "全角空格转半角")
        assertEquals("1d6+2", DiceCalculation.toDBC("1d6+2"), "半角原样")
    }

    @Test
    fun `文本与数值助手`() {
        assertTrue(DiceCalculation.textIsEmpty(""))
        assertTrue(DiceCalculation.textIsEmpty(null))
        assertFalse(DiceCalculation.textIsEmpty(" "), "只管 null 与空串，与既有调用点一致")

        assertEquals(listOf("a", "b"), DiceCalculation.notEmptyArray(listOf(" a ", "", "b")).toList())
        assertEquals(0, DiceCalculation.notEmptyArray(emptyList()).size)

        assertEquals(0L, DiceCalculation.stringToLong(""))
        assertEquals(12L, DiceCalculation.stringToLong("12"))
        assertEquals(7L, DiceCalculation.stringToLong(7))
        assertEquals(0L, DiceCalculation.stringToLong("abc"))

        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(DiceCalculation.date()))
    }


    @Test
    fun `getRandomInt 落在闭区间内`() {
        install(0)
        assertEquals(1, DiceCalculation.getRandomInt(1, 100))
        assertEquals(0, DiceCalculation.getRandomInt(0, 9))
        install(9)
        assertEquals(9, DiceCalculation.getRandomInt(0, 9))
        install(99)
        assertEquals(100, DiceCalculation.getRandomInt(1, 100))
    }




    @Test
    fun `最大 SAN`() {
        assertEquals(99, DiceCalculation.abilitiesGetMaxSan(0))
        assertEquals(69, DiceCalculation.abilitiesGetMaxSan(30))
        assertEquals(0, DiceCalculation.abilitiesGetMaxSan(99))
        assertEquals(0, DiceCalculation.abilitiesGetMaxSan(150), "超过 99 要夹取")
    }


    @Test
    fun `最大 HP`() {
        assertEquals(10, DiceCalculation.abilitiesGetMaxHp(50, 50))
        assertEquals(0, DiceCalculation.abilitiesGetMaxHp(0, 0), "0 是'属性很低'的合法结果，不是'算不出来'")
        assertEquals(9, DiceCalculation.abilitiesGetMaxHp(55, 44), "99/10 向下取整")
    }


    @Test
    fun `伤害加值 db 的档位边界`() {
        assertEquals("-2", DiceCalculation.abilitiesGetDB(32, 32))
        assertEquals("-1", DiceCalculation.abilitiesGetDB(32, 33))
        assertEquals("-1", DiceCalculation.abilitiesGetDB(42, 42))
        assertEquals("0", DiceCalculation.abilitiesGetDB(42, 43))
        assertEquals("0", DiceCalculation.abilitiesGetDB(62, 62))
        assertEquals("1d4", DiceCalculation.abilitiesGetDB(62, 63))
        assertEquals("1d4", DiceCalculation.abilitiesGetDB(82, 82))
        assertEquals("1d6", DiceCalculation.abilitiesGetDB(82, 83))
        assertEquals("1d6", DiceCalculation.abilitiesGetDB(102, 102))
        assertEquals("2d6", DiceCalculation.abilitiesGetDB(142, 142))
    }


    private class StubCard(private val values: Map<String, Int>) : CardAbilities {
        override fun readProperty(name: String): Int = values[name] ?: 0
        override fun hasProperty(name: String): Boolean = values.containsKey(name)
    }


    @Test
    fun `从卡上读派生数值`() {
        val full = StubCard(
            mapOf(
                CardAbilities.POWER to 50,
                CardAbilities.SIZE to 60,
                CardAbilities.BODY_QUALITY to 55,
                CardAbilities.CTHULHU_MYTHOS to 30
            )
        )
        assertEquals(69, DiceCalculation.abilitiesGetMaxSan(full), "99 - 30")
        assertEquals(11, DiceCalculation.abilitiesGetMaxHp(full), "(55+60)/10 向下取整")
        assertEquals("0", DiceCalculation.abilitiesGetDB(full), "50+60=110 → 0")


        val noSize = StubCard(mapOf(CardAbilities.POWER to 50, CardAbilities.BODY_QUALITY to 55))
        assertEquals(-1, DiceCalculation.abilitiesGetMaxHp(noSize))
        assertTrue(
            DiceCalculation.abilitiesGetMaxHp(noSize) != 0,
            "0 是'属性很低'的合法结果，与'算不出来'必须区分"
        )


        val zeroSize = StubCard(mapOf(CardAbilities.BODY_QUALITY to 0, CardAbilities.SIZE to 0))
        assertEquals(0, DiceCalculation.abilitiesGetMaxHp(zeroSize))


        assertEquals("-2", DiceCalculation.abilitiesGetDB(StubCard(emptyMap())))
    }

    @Test
    fun `RollOptions 的默认值`() {
        val defaults = RollOptions()
        assertEquals(100, defaults.dxd_defaultNumber)
        assertEquals(DiceConstant.DICE_SINGLE_ROLL_TIME_OUT, defaults.timeout)
        assertEquals(RollOptions.DxDModes.RANDOM, defaults.dxd_mode)
        assertFalse(defaults.allowNegativeNumber)
        assertFalse(defaults.simple)
    }
}
