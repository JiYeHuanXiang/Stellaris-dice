package stellaris.dice.diceFunction.lightScript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PluginLoaderTest {

    @TempDir
    lateinit var root: File

    private fun plugin(content: String): File {
        val dir = File(root, "custom")
        dir.mkdirs()
        val file = File(dir, "test.toml")
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    @Test
    fun `TOML 全文 包含 正则 与 program 都能命中`() {
        plugin(
            """
            [config]
            mood = "开心"

            [[define]]
            name = "问候"
            content = "早上好"

            [[auto]]
            keywordFull = "早安"
            program = "【常量 问候】【读项目 mood】【发送者QQ】"

            [[auto]]
            keywordContained = ["签到"]
            content = ["签到成功", "已签到"]

            [[auto]]
            keywordRegexp = ["^查(\\d+)$"]
            program = "编号【正则匹配到的 0】"
            """.trimIndent()
        )
        val loader = PluginLoader(root.path)
        loader.reload()
        assertEquals(1, loader.pluginCount)

        assertEquals("早上好开心10001", loader.matchAuto("早安", "10001", "20002", true)?.content)
        val signed = loader.matchAuto("今天签到", "10001", "20002", true)?.content
        assertTrue(signed == "签到成功" || signed == "已签到", signed)
        assertEquals("编号42", loader.matchAuto("查42", "10001", "20002", true)?.content)
        assertNull(loader.matchAuto("无关", "10001", "20002", true))
    }

    @Test
    fun `坏 TOML 整份跳过 不拖垮其他插件`() {
        val dir = File(root, "custom")
        dir.mkdirs()
        File(dir, "bad.toml").writeText("[auto\nkeywordFull = 1", Charsets.UTF_8)
        plugin(
            """
            [[auto]]
            keywordFull = "好"
            content = "好呀"
            """.trimIndent()
        )
        val loader = PluginLoader(root.path)
        loader.reload()
        assertEquals(1, loader.pluginCount, "坏文件应被跳过")
        assertEquals("好呀", loader.matchAuto("好", "1", "2", true)?.content)
    }

    @Test
    fun `program 常用指令与错误提示`() {
        val variables = ProgramVariables(File(root, "custom/data"))
        val context = ProgramContext(
            pluginId = "test",
            autoName = "demo",
            senderId = "10001",
            groupId = "20002",
            isGroup = true,
            defines = mapOf("问候" to "你好"),
            config = mapOf("mood" to "开心"),
            regexGroups = listOf("42"),
            variables = variables
        )
        assertEquals("14", ProgramEngine.execute("【计算 2+3*4】", context))
        assertEquals("否", ProgramEngine.execute("【判断 a,a,是,否】", context))
        assertEquals("你好", ProgramEngine.execute("【常量 问候】", context))
        assertEquals("42", ProgramEngine.execute("【正则匹配到的 0】", context))
        assertEquals("1", ProgramEngine.execute("【赋值变量 x,1】【变量 x】", context))
        assertEquals("10001", ProgramEngine.execute("【发送者QQ】", context))
        assertTrue(ProgramEngine.execute("【计算 1+】", context).contains("无法执行"), "坏算式应给标准错误")
        assertTrue(ProgramEngine.execute("【没有这个指令】", context).contains("无法执行"))
    }

    @Test
    fun `固态变量落盘并可重新载入`() {
        val dataDir = File(root, "custom/data")
        val first = ProgramVariables(dataDir)
        val context = ProgramContext(
            "test", "demo", "10001", "20002", true,
            emptyMap(), emptyMap(), emptyList(), first
        )
        ProgramEngine.execute("【赋值变量 x,持久值】【设置固态变量 x】", context)
        assertNotNull(File(dataDir, "test.json").takeIf { it.isFile }, "固态变量应落盘")

        val second = ProgramVariables(dataDir)
        second.load("test")
        val secondContext = ProgramContext(
            "test", "demo", "10001", "20002", true,
            emptyMap(), emptyMap(), emptyList(), second
        )
        assertEquals("持久值", ProgramEngine.execute("【变量 x】", secondContext))
    }
}
