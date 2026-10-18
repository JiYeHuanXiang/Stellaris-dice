package onedice

import onedice.data.DiceConfigs
import onedice.data.ResRecursive
import onedice.defines.ResErrorException
import onedice.defines.ResErrorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.draw.DrawFile
import stellaris.dice.diceFunction.draw.RollWeightEvaluator
import java.util.Random















class RollTest {











    private class ScriptedRandom(private val units: List<Long>) : Random() {

        private var cursor = 0

        override fun nextLong(): Long {
            val unit = units[cursor % units.size]
            cursor++
            return unit shl 1
        }
    }


    private fun roll(expression: String, vararg randomValues: Long): ResRecursive =
        Roll(expression, DiceConfigs(timeout = 5_000, random = ScriptedRandom(randomValues.toList()))).start()


    private fun assertRollFails(expression: String, expected: ResErrorType, vararg randomValues: Long) {
        val failure = assertThrows(ResErrorException::class.java) { roll(expression, *randomValues) }
        assertEquals(expected, failure.type, "错误类别不符，简述=${failure.brief}")
    }



    @Test
    fun `字面量 的取值域就是它自己`() {
        val result = roll("7")
        assertEquals(7, result.resInt)
        assertEquals("7", result.resDetail)
        assertEquals(7, result.resIntMin)
        assertEquals(7, result.resIntMax)
    }

    @Test
    fun `四则运算 结果与明细`() {
        assertEquals(7, roll("1+2*3").resInt)

        assertEquals("1+2*3", roll("1+2*3").resDetail)

        assertEquals(9, roll("(1+2)*3").resInt)

        assertEquals("(1+2)*3", roll("(1+2)*3").resDetail)

        assertEquals(0, roll("2-(3-1)").resInt)

        assertEquals("2-(3-1)", roll("2-(3-1)").resDetail)

        assertEquals(9, roll("2+3+4").resInt)

        assertEquals("2+3+4", roll("2+3+4").resDetail)

        assertEquals(3, roll("7/2").resInt, "整除向下取整")
    }


    @Test
    fun `一元负号 绑定松于乘方`() {
        assertEquals(-5, roll("-5").resInt)
        assertEquals("0-5", roll("-5").resDetail)
        assertEquals(-4, roll("-2^2").resInt)
        assertEquals(-8, roll("-2^3").resInt)
        assertEquals(-6, roll("-2*3").resInt)
    }

    @Test
    fun `乘方 左结合且夹住溢出`() {
        assertEquals(1024, roll("2^10").resInt)

        assertEquals(64, roll("2^3^2").resInt)
        assertEquals(Int.MAX_VALUE, roll("99^99").resInt, "溢出夹到哨兵而不是回绕")
    }

    @Test
    fun `阶乘 与前缀逻辑非`() {
        assertEquals(120, roll("5!").resInt)
        assertEquals("5!", roll("5!").resDetail)
        assertEquals(1, roll("0!").resInt)
        assertEquals(Int.MAX_VALUE, roll("20!").resInt, "阶乘溢出夹到哨兵")

        assertEquals(1, roll("!0").resInt)
        assertEquals(0, roll("!5").resInt)
    }

    @Test
    fun `比较与逻辑运算 归到 0 或 1`() {
        assertEquals(1, roll("3=3").resInt)
        assertEquals(0, roll("3=4").resInt)
        assertEquals(1, roll("3<4").resInt)
        assertEquals(1, roll("4>3").resInt)
        assertEquals(1, roll("1&2").resInt)
        assertEquals(0, roll("0&2").resInt)
        assertEquals(1, roll("0|2").resInt)
        assertEquals("{3 equ 3}(1)", roll("3=3").resDetail)
    }

    @Test
    fun `三元式`() {
        assertEquals(2, roll("1?2:3").resInt)
        assertEquals(3, roll("0?2:3").resInt)

        assertEquals(2, roll("1?2:3+4").resInt)
        assertEquals(7, roll("0?2:3+4").resInt)
    }



    @Test
    fun `1d6 出目与明细`() {

        val result = roll("1d6", 2)
        assertEquals(3, result.resInt)
        assertEquals("{3}(3)", result.resDetail)
        assertEquals(1, result.resIntMin)
        assertEquals(6, result.resIntMax)
    }

    @Test
    fun `XdY 求和 区间是颗数到颗数乘面数`() {
        val result = roll("3d6+2", 0, 1, 2)
        assertEquals(8, result.resInt, "1+2+3 再加 2")
        assertEquals("{1+2+3}(6)+2", result.resDetail)
        assertEquals(5, result.resIntMin)
        assertEquals(20, result.resIntMax)
    }


    @Test
    fun `20 颗以上才排序`() {
        assertTrue(roll("3d6", 0, 0, 0).resDetail.startsWith("{1+1+1}"))

        val expected = "{${"1+".repeat(19)}1}(20)"
        assertEquals(expected, roll("20d6", *LongArray(20) { 0 }).resDetail)
    }


    @Test
    fun `k 子参 取前几大之和`() {
        val result = roll("5d6k3", 0, 1, 2, 3, 4)
        assertEquals(12, result.resInt, "5+4+3")
        assertEquals("{MAX 5,4,3 | 2,1 }(12)", result.resDetail)
        assertEquals(3, result.resIntMin)
        assertEquals(18, result.resIntMax)
    }

    @Test
    fun `q 子参 取前几小之和`() {
        val result = roll("5d6q2", 0, 1, 2, 3, 4)
        assertEquals(3, result.resInt, "1+2")
        assertEquals("{MIN 1,2 | 3,4,5 }(3)", result.resDetail)
    }


    @Test
    fun `a 子参 数达到阈值的个数`() {
        val result = roll("5d6a5", 0, 1, 2, 3, 4)
        assertEquals(1, result.resInt, "只有 5 达到 5")
        assertEquals("{COUNT 5 | 4,3,2,1}(1)", result.resDetail)
        assertEquals(0, result.resIntMin)
        assertEquals(5, result.resIntMax)
    }

    @Test
    fun `d 的默认值 d 即 1d100 d6 即 1d6`() {
        assertEquals(1, roll("d", 0).resIntMin, "裸 d 是 1d100")
        assertEquals(100, roll("d", 0).resIntMax)
        assertEquals(6, roll("d6", 0).resIntMax)
        assertEquals("{1}(1)", roll("d6", 0).resDetail, "左值省略即为 1 颗")
    }

    @Test
    fun `房规可改写 d 的默认骰`() {
        val configs = DiceConfigs(timeout = 5_000, random = ScriptedRandom(listOf(0)))
        configs.setDefaultDiceNumber(2, 10)
        val result = Roll("d", configs).start()
        assertEquals("{1+1}(2)", result.resDetail, "2d10 两颗")
    }

    @Test
    fun `d 的参数越界与子参冲突`() {
        assertRollFails("0d6", ResErrorType.NODE_LEFT_VAL_INVALID)
        assertRollFails("10000d6", ResErrorType.NODE_LEFT_VAL_INVALID)
        assertRollFails("1d0", ResErrorType.NODE_RIGHT_VAL_INVALID)
        assertRollFails("1d6k3q2", ResErrorType.NODE_NONSENSE, 0, 0, 0, 0, 0, 0)
    }




    @Test
    fun `无限流 加骰直到无人达标`() {


        val result = roll("5a5k5m6", 0, 1, 2, 3, 4, 0)
        assertEquals(1, result.resInt)
        assertTrue(result.resDetail.contains("<[5]>"), "既加骰又算成功要标成 <[n]>：${result.resDetail}")
        assertEquals("{{1,2,3,4,<[5]>},{1}}(1)", result.resDetail)
    }


    @Test
    fun `双十字 出目由轮数与末轮最大决定`() {


        val result = roll("10c10m10", *LongArray(10) { it.toLong() }, 2)
        assertEquals(13, result.resInt)
    }

    @Test
    fun `加骰池 的边界检查`() {

        assertRollFails("1a10k20m10", ResErrorType.NODE_NONSENSE)

        assertRollFails("1a1k1m6", ResErrorType.NODE_RIGHT_VAL_INVALID)
    }




    @Test
    fun `奖励骰 十位替换`() {

        val result = roll("b2", 89, 1, 1)
        assertEquals(20, result.resInt, "90 的十位被换成 2 → 20")
        assertEquals("{奖励d100=90|2 2}(20)", result.resDetail)
    }

    @Test
    fun `惩罚骰 取更劣的十位`() {

        val result = roll("p1", 0, 9)
        assertEquals(91, result.resInt)
        assertEquals("{惩罚d100=1|9}(91)", result.resDetail)
    }

    @Test
    fun `奖惩骰个数上限是 9`() {
        assertRollFails("b10", ResErrorType.NODE_RIGHT_VAL_INVALID)
        assertRollFails("b0", ResErrorType.NODE_RIGHT_VAL_INVALID, 0, 0)
    }


    @Test
    fun `FATE 骰 三面写符号串`() {

        val result = roll("4f3", 1, 2, 0, 1)
        assertEquals(0, result.resInt)
        assertEquals("{0 + - 0}(0)", result.resDetail)
        assertEquals(-4, result.resIntMin)
        assertEquals(4, result.resIntMax)
    }


    @Test
    fun `FATE 骰 省略操作数即 4f3`() {
        val result = roll("f", 1, 1, 1, 1)
        assertEquals(0, result.resInt)
        assertEquals("{0 0 0 0}(0)", result.resDetail, "右值默认 3 → 每颗取 -1/0/+1")
        assertEquals(4, result.resIntMax)
        assertEquals("{+ + + +}(4)", roll("f", 2, 2, 2, 2).resDetail)
    }

    @Test
    fun `FATE 骰 面数必须是奇数`() {
        assertRollFails("5f4", ResErrorType.NODE_RIGHT_VAL_INVALID)
    }




    @Test
    fun `临时变量 在同一表达式内可先写后读`() {
        assertEquals(6, roll("$0e5;$0+1").resInt)
    }

    @Test
    fun `读未赋值的临时变量 是业务错误`() {
        assertRollFails("\$3", ResErrorType.NODE_SUB_VAL_INVALID)
    }


    @Test
    fun `多行表达式 取最后一行`() {
        assertEquals(4, roll("1d6;2+2", 0).resInt)
    }

    @Test
    fun `表达式不成立时报业务错误`() {
        assertRollFails("1+", ResErrorType.INPUT_RAW_INVALID)
        assertRollFails("(1+2", ResErrorType.INPUT_CHILD_PARA_INVALID)
        assertRollFails("1x2", ResErrorType.INPUT_RAW_INVALID)
        assertRollFails("", ResErrorType.INPUT_RAW_INVALID)
    }




    @Test
    fun `归一化 抹空白与大小写`() {
        assertEquals("3d6+2", Roll(" 3D6 + 2 ").originData)
        assertEquals("3d6", Roll("+3d6").originData)
        assertEquals(7, roll(" 1 + 2 * 3 ").resInt)
    }


    @Test
    fun `超时 中断为业务错误`() {
        val failure = assertThrows(ResErrorException::class.java) {
            Roll("999a99m999k999", DiceConfigs(timeout = 1, random = Random())).start()
        }
        assertEquals(ResErrorType.NODE_INTERRUPTED, failure.type)
    }


    @Test
    fun `默认随机源 出目落在区间内`() {
        repeat(50) {
            val result = Roll("3d6+2").start()
            assertTrue(result.resInt in result.resIntMin..result.resIntMax, "越界：${result.resInt}")
        }
    }








    @Test
    fun `规范示例表达式 不产生引擎缺陷`() {
        val defects = setOf(
            ResErrorType.UNKNOWN_GENERATE_FATAL,
            ResErrorType.UNKNOWN_COMPLETE_FATAL,
            ResErrorType.NODE_INTERRUPTED,
            ResErrorType.NODE_STACK_EMPTY
        )
        val examples = listOf(
            "10d10-7a5k7*10+6",
            "1d(7a5k7)a(1d4)",
            "(1d100)^(7a5k7)",
            "10d10",
            "7a5k7",
            "b+5",
            "4f3",
            "10c10m10",
            "(2+3)*4-1",
            "1d6;2d6k1"
        )
        examples.forEach { expression ->
            try {
                Roll(expression, DiceConfigs(timeout = 5_000, random = Random(20261016L))).start()
            } catch (e: ResErrorException) {
                assertTrue(e.type !in defects, "示例 '$expression' 触发了引擎缺陷类错误：${e.type}")
            }
        }
    }








    @Test
    fun `牌堆权重表达式 装上引擎才生效`() {
        val text = """{"w":["::2d1::甲"]}"""

        RollWeightEvaluator.uninstall()
        assertEquals(1, DrawFile(text, "w.json", 0L, false).findData("w")!!.remain())

        RollWeightEvaluator.install()
        try {
            assertEquals(2, DrawFile(text, "w.json", 0L, false).findData("w")!!.remain())
        } finally {
            RollWeightEvaluator.uninstall()
        }

        assertEquals(1, DrawFile(text, "w.json", 0L, false).findData("w")!!.remain())
    }


    @Test
    fun `权重求值口 只认正值且不抛异常`() {
        val evaluator = RollWeightEvaluator()
        assertEquals(2.0, evaluator.evaluate("2d1"))
        assertEquals(1.0, evaluator.evaluate("1d1"))
        assertNull(evaluator.evaluate("0d1"), "零权重会让条目永远抽不到，按无法求值处理")
        assertNull(evaluator.evaluate("2-5"), "负权重会算错区间，按无法求值处理")
        assertNull(evaluator.evaluate("不是骰式"))
        assertNull(evaluator.evaluate("1d0"))
        assertNotNull(evaluator.evaluate("1d6"))
    }
}
