package stellaris.dice.diceFunction.lightScript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginLimitsTest {

    @Test
    fun `插件输出超过上限时被截断`() {
        val normal = "正常输出"
        assertSame(normal, PluginLimits.limitResult(normal))

        val oversized = "x".repeat(PluginLimits.MAX_RESULT_CHARS + 100)
        val limited = PluginLimits.limitResult(oversized)!!
        assertEquals(PluginLimits.MAX_RESULT_CHARS, limited.length)
        assertTrue(limited.endsWith("已截断）"))
    }
}
