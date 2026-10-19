package stellaris.dice.diceFunction.lightScript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LuaSandboxProbeTest {

    @TempDir
    lateinit var root: File

    private class CaptureHost : PluginHost {
        var lastPrivate = ""
        override fun setGroupCard(user: String, text: String, group: String): Boolean = true
        override fun configureAutoName(
            user: String,
            group: String,
            format: String,
            enabled: Boolean
        ): AutoNameReceipt = AutoNameReceipt("卡", enabled, format)

        override fun syncAutoName(user: String, group: String): Boolean = true
        override fun sendGroupMessage(text: String, group: String) {}
        override fun sendPrivateMessage(text: String, userId: String) {
            lastPrivate = text
        }

        override fun roll(expression: String): Int = 0
        override fun draw(group: String, name: String): String? = null
        override fun setGroupTitle(title: String, userId: String, group: String): Boolean = true
        override fun setGroupWholeBan(ban: Boolean, group: String): Boolean = true
        override fun reloadPlugins() {}
    }

    @Test
    fun `lua 沙箱看不到宿主文件与 java 互操作入口`() {
        val dir = File(root, "custom")
        dir.mkdirs()
        File(dir, "probe.toml").writeText(
            """
            [[lua]]
            name = "probe"
            lua = '''
            local parts = {}
            local function add(k, v) parts[#parts+1] = k .. "=" .. tostring(v) end
            add("luajava", luajava ~= nil)
            add("io", io ~= nil)
            add("os_execute", os ~= nil and os.execute ~= nil)
            add("os_time", os ~= nil and os.time ~= nil)
            add("os_time_number", os ~= nil and type(os.time()) == "number")
            add("loadfile", loadfile ~= nil)
            add("dofile", dofile ~= nil)
            add("require_luajava", pcall(require, "luajava"))
            add("require_lib_class", pcall(require, "org.luaj.vm2.lib.jse.JseIoLib"))
            ZhaoDiceSDK.chat.sendToPerson(table.concat(parts, ","), "10001", "")
            '''
            """.trimIndent(),
            Charsets.UTF_8
        )
        val host = CaptureHost()
        PluginLoader(root.path).apply {
            this.host = host
            reload()
        }
        assertEquals(
            "luajava=false,io=false,os_execute=false,os_time=true,os_time_number=true," +
                "loadfile=false,dofile=false,require_luajava=false,require_lib_class=false",
            host.lastPrivate
        )
    }
}
