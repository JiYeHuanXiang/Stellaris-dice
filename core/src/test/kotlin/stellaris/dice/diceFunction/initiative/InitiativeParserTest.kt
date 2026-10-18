package stellaris.dice.diceFunction.initiative

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test






class InitiativeParserTest {

    private fun parse(input: String) = InitiativeParser.parse(input, "甲")

    @Test
    fun `空参数 掷自己 默认 d20`() {
        val spec = parse("").specs.single()
        assertEquals("甲", spec.name)
        assertEquals(1, spec.count)
        assertFalse(spec.shared)
        assertEquals("d20", spec.expression)
    }


    @Test
    fun `自己的加值与换骰`() {
        assertEquals("d20+3", parse("+3").specs.single().expression)
        assertEquals("d20-2", parse("-2").specs.single().expression)
        assertEquals("d8", parse("d8").specs.single().expression)

        assertEquals("50", parse("50").specs.single().expression)
    }

    @Test
    fun `斜杠分隔多个名字`() {
        val specs = parse("哥布林/史莱姆").specs
        assertEquals(listOf("哥布林", "史莱姆"), specs.map { it.name })
        assertTrue(specs.all { it.expression == "d20" })
    }


    @Test
    fun `单个名字带修正`() {
        val spec = parse("哥布林+3").specs.single()
        assertEquals("哥布林", spec.name)
        assertEquals("d20+3", spec.expression)
    }


    @Test
    fun `单个名字换骰`() {
        val spec = parse("哥布林d8").specs.single()
        assertEquals("哥布林", spec.name)
        assertEquals("d8", spec.expression)
    }


    @Test
    fun `末尾全局修正`() {
        val specs = parse("哥布林/史莱姆 +3").specs
        assertEquals(listOf("哥布林", "史莱姆"), specs.map { it.name })
        assertTrue(specs.all { it.expression == "d20+3" })
    }


    @Test
    fun `次数在前 批量生成`() {
        val spec = parse("3#哥布林").specs.single()
        assertEquals("哥布林", spec.name)
        assertEquals(3, spec.count)
        assertFalse(spec.shared)
    }

    @Test
    fun `等号前缀 共用一次骰点`() {
        val spec = parse("=3#哥布林").specs.single()
        assertEquals(3, spec.count)
        assertTrue(spec.shared)
        assertEquals("哥布林", spec.name)
    }


    @Test
    fun `批量生成带修正`() {
        val spec = parse("3#哥布林+2").specs.single()
        assertEquals("哥布林", spec.name)
        assertEquals(3, spec.count)
        assertEquals("d20+2", spec.expression)
    }


    @Test
    fun `次数上限`() {
        assertEquals(20, parse("99#哥布林").specs.single().count)
    }
}
