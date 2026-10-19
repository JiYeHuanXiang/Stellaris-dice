package stellaris.dice.diceFunction.lightScript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
    fun `引擎之间作用域互不可见`() {
        val first = DiceJsSandbox.create()
        first.eval("var leakedMarker = 42")
        val second = DiceJsSandbox.create()
        assertEquals("undefined", second.eval("typeof leakedMarker")?.toString())
        assertEquals("42", first.eval("leakedMarker")?.toString())
    }
}
