package stellaris.dice.diceFunction.lightScript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import javax.script.ScriptException

class JsSandboxProbeTest {

    private fun probe(script: String): String =
        DiceJsSandbox.create().eval(script)?.toString() ?: "nil"

    @Test
    fun `java 包入口不可见`() {
        assertEquals("undefined/undefined", probe("typeof Packages + '/' + typeof java"))
        assertEquals("undefined", probe("typeof javax"))
        assertEquals("undefined", probe("typeof org"))
        assertEquals("undefined", probe("typeof com"))
        assertEquals("undefined", probe("typeof net"))
        assertEquals("undefined", probe("typeof edu"))
    }

    @Test
    fun `宿主加载与进程入口不可见`() {
        for (name in listOf(
            "load", "loadClass", "defineClass", "quit", "runCommand", "spawn",
            "importClass", "importPackage", "JavaAdapter", "MWArray", "doctest"
        )) {
            assertEquals("undefined", probe("typeof $name"), name)
        }
    }

    @Test
    @Timeout(5)
    fun `死循环会被执行时限终止`() {
        assertThrows(ScriptException::class.java) {
            DiceJsSandbox.create(executionTimeoutMillis = 80)
                .eval("try { while (true) {} } catch (e) {}")
        }
    }

    @Test
    fun `超长脚本在进入引擎前被拒绝`() {
        assertThrows(ScriptException::class.java) {
            DiceJsSandbox.create(maxScriptChars = 16).eval("var marker = '0123456789';")
        }
    }

    @Test
    fun `引擎之间作用域互不可见`() {
        val first = DiceJsSandbox.create()
        first.eval("var leakedMarker = 42")
        val second = DiceJsSandbox.create()
        assertEquals("undefined", second.eval("typeof leakedMarker")?.toString())
        assertEquals("42", first.eval("leakedMarker")?.toString())
    }
}
