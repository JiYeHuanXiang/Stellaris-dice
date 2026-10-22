package stellaris.dice.diceFunction.judge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.responder.CheckLevel
import java.nio.file.Files
import java.nio.file.Path

class JudgeRegistryTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeRule(name: String, script: String) {
        val dir = tempDir.resolve("rules")
        Files.createDirectories(dir)
        Files.write(dir.resolve(name), script.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `内置房规按数字与名字都能找到`() {
        val registry = JudgeRegistry(tempDir.toString())
        assertEquals("规则书", registry.find("0")?.name)
        assertEquals("房规3", registry.find("3")?.name)
        assertEquals("房规3", registry.find("房规3")?.name)
        assertNull(registry.find("不存在"))
        assertTrue(registry.listing().contains("规则书"))
    }

    @Test
    fun `加载 JS 自定义房规并判档`() {
        writeRule(
            "always.js",
            """
            var name = "恒成功";
            var describe = "测试用：一律成功";
            function onJudgeExtremeLevel(rand, ability) { return null; }
            function onJudgeNormalLevel(rand, ability) { return "SUCCESS"; }
            """.trimIndent()
        )
        val registry = JudgeRegistry(tempDir.toString())
        val rule = registry.find("恒成功")
        assertTrue(rule != null, "自定义房规应被加载")
        assertEquals(CheckLevel.SUCCESS, rule!!.judge(90, 50))
        assertEquals(1, registry.customCount())
        assertTrue(registry.listing().contains("恒成功"))
    }

    @Test
    fun `极端档优先于普通档`() {
        writeRule(
            "crit.js",
            """
            var name = "极端优先";
            var describe = "测试用";
            function onJudgeExtremeLevel(rand, ability) {
              if (rand <= 5) return "BIG_SUCCESS";
              return null;
            }
            function onJudgeNormalLevel(rand, ability) { return "FAILURE"; }
            """.trimIndent()
        )
        val rule = JudgeRegistry(tempDir.toString()).find("极端优先")!!
        assertEquals(CheckLevel.CRITICAL_SUCCESS, rule.judge(1, 50))
        assertEquals(CheckLevel.FAILURE, rule.judge(50, 50))
    }

    @Test
    fun `坏脚本被跳过`() {
        writeRule("bad.js", "function ( {")
        writeRule(
            "good.js",
            """
            var name = "好房规";
            function onJudgeExtremeLevel(r, a) { return null; }
            function onJudgeNormalLevel(r, a) { return null; }
            """.trimIndent()
        )
        val registry = JudgeRegistry(tempDir.toString())
        assertEquals(1, registry.customCount())
        assertEquals("好房规", registry.find("好房规")?.name)
    }

    @Test
    fun `没有 rules 目录时只有内置`() {
        val registry = JudgeRegistry(tempDir.toString())
        assertEquals(0, registry.customCount())
        assertEquals("规则书", registry.default().name)
    }
}
