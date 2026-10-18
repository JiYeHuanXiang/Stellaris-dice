package stellaris.dice.diceFunction.responder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test








class RollArgumentsTest {

    private fun parse(input: String) = RollArguments.parse(input)

    private fun expressionOf(input: String) = parse(input).expression

    private fun reasonOf(input: String) = parse(input).reason




    @Test
    fun `整段是表达式`() {
        assertEquals("3d6+2", expressionOf("3d6+2"))
        assertEquals("", reasonOf("3d6+2"))
        assertEquals("50", expressionOf("50"))
        assertEquals("1d100", expressionOf("1d100"))
    }

    @Test
    fun `表达式后面跟理由`() {
        val arguments = parse("3d6+2 去死吧")
        assertEquals("3d6+2", arguments.expression)
        assertEquals("去死吧", arguments.reason)

        assertEquals("3d6+2", expressionOf("3d6+2 这是一个 很长的 理由"))
        assertEquals("这是一个 很长的 理由", reasonOf("3d6+2 这是一个 很长的 理由"))
    }


    @Test
    fun `理由里的算式仍是理由`() {
        assertEquals("1d6", expressionOf("1d6 力量 1d4"))
        assertEquals("力量 1d4", reasonOf("1d6 力量 1d4"))
    }


    @Test
    fun `中文开头 骰式为空`() {
        assertEquals("", expressionOf("力量1d4"))
        assertEquals("力量1d4", reasonOf("力量1d4"))
    }


    @Test
    fun `表达式后粘中文 会切开`() {
        assertEquals("1d6+2", expressionOf("1d6+2去死"))
        assertEquals("去死", reasonOf("1d6+2去死"))
    }







    @Test
    fun `整行纯表达式字符 含空格也算`() {
        assertEquals("1d6 5", expressionOf("1d6 5"))
        assertEquals("", reasonOf("1d6 5"))

        assertEquals("1d6 abc", expressionOf("1d6 abc"))
        assertEquals("", reasonOf("1d6 abc"))

        assertEquals("1d6", expressionOf("1d6 www"))
        assertEquals("www", reasonOf("1d6 www"))
    }


    @Test
    fun `多余空白被吃掉`() {
        assertEquals("1d6", expressionOf("1d6 理由"))
        assertEquals("1d6", expressionOf(" 1d6 理由"))
        assertEquals("理由", reasonOf("1d6 理由"))

        assertEquals("理由 ", reasonOf("1d6  理由 "))

        assertEquals(" 3d6+2 ", expressionOf(" 3d6+2 "))
    }

    @Test
    fun `空输入`() {
        assertEquals("", expressionOf(""))
        assertEquals("", reasonOf(""))
    }



    @Test
    fun `重复次数 N 井 写法`() {
        val repeated = RollArguments.repeatTimes("3#1d6")
        assertEquals(3, repeated.times)
        assertEquals("1d6", repeated.remain)


        val withReason = RollArguments.repeatTimes("2#3d6 理由")
        assertEquals(2, withReason.times)
        assertEquals("3d6 理由", withReason.remain)
    }

    @Test
    fun `没有重复次数时按 1`() {
        val once = RollArguments.repeatTimes("1d6")
        assertEquals(1, once.times)
        assertEquals("1d6", once.remain)
    }







    @Test
    fun `重复次数 次数在后`() {
        val repeated = RollArguments.repeatTimes("1d100#3")
        assertEquals(3, repeated.times)
        assertEquals("1d100", repeated.remain)

        val withReason = RollArguments.repeatTimes("1d100#3 理由")
        assertEquals(3, withReason.times, "理由不影响次数")
        assertEquals("1d100 理由", withReason.remain)

        assertEquals(1, RollArguments.repeatTimes("1d100#0").times, "0 次视为 1 次")
        assertEquals(10, RollArguments.repeatTimes("3d6#10").times)
    }






    @Test
    fun `重复次数 不锚定且不夹限`() {
        val embedded = RollArguments.repeatTimes("abc2#1d6")
        assertEquals(2, embedded.times)
        assertEquals("1d6", embedded.remain, "前缀只吃掉 N# 与其之前的内容")

        assertEquals(0, RollArguments.repeatTimes("0#1d6").times)
        assertEquals(42, RollArguments.repeatTimes("42#1d6").times)
        assertEquals("1d6", RollArguments.repeatTimes("42#1d6").remain)
    }
}
