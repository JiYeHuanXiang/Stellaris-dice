package stellaris.dice.diceFunction.lightScript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LuaPluginRuntimeTest {

    @TempDir
    lateinit var root: File

    private class FakeDeck(private val items: MutableList<String>) : PluginDeck {
        override fun read(putBack: Boolean): String? {
            if (items.isEmpty()) return null
            return if (putBack) items.first() else items.removeAt(0)
        }

        override fun write(content: String, weight: String): Boolean {
            items.add(content)
            return true
        }

        override fun remain(): Int = items.size

        override fun recycle() {}
    }

    private class FakeHost : PluginHost {
        var cardCalls = 0
        var autoNameCalls = 0
        var synced = 0
        var lastFormat = ""
        var reloads = 0
        var restarts = 0
        var titleCalls = 0
        var banCalls = 0
        var lastPrivate = ""
        val properties = mapOf("力量" to 60)

        override fun setGroupCard(user: String, text: String, group: String): Boolean {
            cardCalls++
            return true
        }

        override fun configureAutoName(
            user: String,
            group: String,
            format: String,
            enabled: Boolean
        ): AutoNameReceipt {
            autoNameCalls++
            lastFormat = format
            return AutoNameReceipt("测试卡", enabled, format)
        }

        override fun syncAutoName(user: String, group: String): Boolean {
            synced++
            return true
        }

        override fun sendGroupMessage(text: String, group: String) {}
        override fun sendPrivateMessage(text: String, userId: String) {
            lastPrivate = text
        }

        override fun roll(expression: String): Int = 0
        override fun draw(group: String, name: String): String? = null
        override fun setGroupTitle(title: String, userId: String, group: String): Boolean {
            titleCalls++
            return true
        }

        override fun setGroupWholeBan(ban: Boolean, group: String): Boolean {
            banCalls++
            return true
        }
        override fun reloadPlugins() {
            reloads++
        }

        override fun readPlayerName(user: String, group: String): String? = "卡A"
        override fun readPlayerCard(user: String, name: String): Map<String, Int>? =
            if (name == "卡A") properties else null

        override fun readLastAppearedGroup(user: String): String? = "20002"
        override fun readSystemConfig(key: String): String = "配置:$key"
        override fun openDeck(group: String, name: String): PluginDeck? =
            if (name == "主机堆") FakeDeck(mutableListOf("主机甲")) else null

        override fun httpGet(url: String): String = "get:$url"
        override fun httpPost(url: String, data: String, headers: Map<String, String>): String? =
            "post:$url:$data:${headers["X-Test"]}"
        override fun allowLocalNetwork(): Boolean = true
        override fun requestRestart() {
            restarts++
        }
    }

    private fun writePlugin(code: String) {
        val dir = File(root, "custom")
        dir.mkdirs()
        File(dir, "lua-test.toml").writeText(
            """
            [[lua]]
            name = "lua-test"
            after_message = "onAfter"
            lua = '''
            $code
            '''
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun boot(code: String, host: PluginHost = FakeHost()): PluginLoader {
        writePlugin(code)
        return PluginLoader(root.path).apply {
            this.host = host
            reload()
        }
    }

    @Test
    fun `Lua 的 commands msg_order replaces 与 after_message`() {
        val code = """
            commands = { ["hi"] = "onHi" }
            msg_order = { ["签到"] = "onSign" }
            replaces = { ["老指令"] = "onReplace" }
            function onHi(msg) return "hi:" .. msg.fromParams end
            function onSign(msg) return "sign:" .. msg.fromQQ end
            function onReplace(msg) return "r " .. msg.fromParams end
            function onAfter(msg) end
        """.trimIndent()
        val host = FakeHost()
        val loader = boot(code, host)
        assertTrue(loader.customCommands.contains("hi"), "Lua commands 应注册进指令表")
        assertEquals("hi:abc", loader.invokeCommand("hi", "abc", "10001", "20002", true, "hi abc"))
        assertEquals("sign:10001", loader.matchKeyword("签到", "10001", "20002", true))
        assertEquals("r xxx", loader.replace("老指令 xxx", "10001", "20002", true))
    }

    @Test
    fun `默认 sn 插件能通过 Lua 注册并调用`() {
        val source = File("../content/custom/sn.toml")
        if (!source.isFile) return
        val dir = File(root, "custom")
        dir.mkdirs()
        source.copyTo(File(dir, "sn.toml"), overwrite = true)

        val host = FakeHost()
        val loader = PluginLoader(root.path).apply {
            this.host = host
            reload()
        }
        assertTrue(loader.customCommands.contains("sn"), "sn.toml 应注册 .sn")
        val reply = loader.invokeCommand("sn", "coc", "10001", "20002", true, "sn coc")
        assertTrue(reply != null && reply.contains("名片"), "应得到自动名片回执：$reply")
        assertEquals(1, host.cardCalls)
        assertEquals(1, host.autoNameCalls)
        assertTrue(host.lastFormat.contains("{pc_name}"))
    }

    @Test
    fun `msg 字段 群聊与私聊的 fromGroup`() {
        val loader = boot(
            """
            commands = { ["fields"] = "onFields" }
            function onFields(msg)
                return tostring(msg.fromGroup) .. ":" .. msg.command .. ":" .. msg.fromParams
            end
            """.trimIndent()
        )
        assertEquals(
            "20002:fields:abc",
            loader.invokeCommand("fields", "abc", "10001", "20002", true, "fields abc")
        )
        assertEquals(
            "nil:fields:abc",
            loader.invokeCommand("fields", "abc", "10001", "", false, "fields abc")
        )
    }

    @Test
    fun `storage 过期 持久化 与 JSON 牌堆`() {
        val host = FakeHost()
        val code = """
            commands = { ["stw"] = "onWrite", ["str"] = "onRead",
                         ["ttlw"] = "onTtlWrite", ["ttlr"] = "onTtlRead", ["drawt"] = "onDraw" }
            function onWrite(msg)
                ZhaoDiceSDK.storage.write("k", msg.fromParams, 0)
                return "ok"
            end
            function onRead(msg) return tostring(ZhaoDiceSDK.storage.read("k")) end
            function onTtlWrite(msg)
                ZhaoDiceSDK.storage.write("t", "x", 1)
                return "written"
            end
            function onTtlRead(msg)
                return tostring(ZhaoDiceSDK.storage.read("t"))
            end
            function onDraw(msg)
                ZhaoDiceSDK.draw.load('{"测试堆":["甲","乙"]}', false)
                local h = ZhaoDiceSDK.draw.open("测试堆")
                if h == nil then return "no" end
                local first = ZhaoDiceSDK.draw.read(h, false)
                local remain = ZhaoDiceSDK.draw.remain(h)
                ZhaoDiceSDK.draw.write(h, "丙", "1")
                local remain2 = ZhaoDiceSDK.draw.remain(h)
                ZhaoDiceSDK.draw.recycle(h)
                return first .. "/" .. remain .. "/" .. remain2
            end
        """.trimIndent()
        val first = boot(code, host)
        assertEquals("ok", first.invokeCommand("stw", "值", "10001", "20002", true, "stw 值"))

        val second = PluginLoader(root.path).apply {
            this.host = host
            reload()
        }
        assertEquals("值", second.invokeCommand("str", "", "10001", "20002", true, "str"))

        assertEquals("written", second.invokeCommand("ttlw", "", "10001", "20002", true, "ttlw"))
        Thread.sleep(30)
        assertEquals("nil", second.invokeCommand("ttlr", "", "10001", "20002", true, "ttlr"))

        val drawn = second.invokeCommand("drawt", "", "10001", "20002", true, "drawt")
        assertTrue(drawn == "甲/1/2" || drawn == "乙/1/2", "动态牌堆：$drawn")
        assertNull(second.invokeCommand("drawt2", "", "1", "2", true, "x"))
    }

    @Test
    fun `lifecycle playerCard json 与网络`() {
        val host = FakeHost()
        val loader = boot(
            """
            commands = { ["misc"] = "onMisc" }
            function onMisc(msg)
                ZhaoDiceSDK.lifecycle.write("x", { a = 7 })
                local v = ZhaoDiceSDK.lifecycle.read("x")
                ZhaoDiceSDK.lifecycle.push("p")
                local p = ZhaoDiceSDK.lifecycle.pop()
                local missing = pcall(function() ZhaoDiceSDK.lifecycle.read("nope") end)
                local card = ZhaoDiceSDK.readPlayerCard("10001", "卡A")
                local name = ZhaoDiceSDK.readPlayerName("10001", "20002")
                local config = ZhaoDiceSDK.readSystemConfig("MASTER_QQ")
                local group = ZhaoDiceSDK.readLastAppearedGroup("10001")
                local encoded = json.encode({ b = "c" })
                local decoded = json.decode('{"n":3}').n
                local net = ZhaoDiceSDK.network.httpGet("https://example.com")
                local posted = ZhaoDiceSDK.network.httpPost("https://example.com", "d", { ["X-Test"] = "h" })
                ZhaoDiceSDK.system.load()
                return v.a .. p .. tostring(missing) .. card["力量"] .. name .. config .. group ..
                    encoded .. decoded .. net .. posted
            end
            """.trimIndent(),
            host
        )
        val result = loader.invokeCommand("misc", "", "10001", "20002", true, "misc")
        assertEquals(
            "7pfalse60卡A配置:MASTER_QQ20002{\"b\":\"c\"}3get:https://example.compost:https://example.com:d:h",
            result
        )
        assertEquals(1, host.reloads)
    }

    @Test
    fun `群管理 SDK 接线`() {
        val host = FakeHost()
        val loader = boot(
            """
            commands = { ["grp"] = "onGrp" }
            function onGrp(msg)
                local title = ZhaoDiceSDK.group.setTitle(msg.fromQQ, "头衔", msg.fromGroup)
                local ban = ZhaoDiceSDK.group.setWholeBan(true, msg.fromGroup)
                local card = ZhaoDiceSDK.group.setCard(msg.fromQQ, "", msg.fromGroup)
                return tostring(title) .. tostring(ban) .. tostring(card)
            end
            """.trimIndent(),
            host
        )
        assertEquals(
            "truetruetrue",
            loader.invokeCommand("grp", "", "10001", "20002", true, "grp")
        )
        assertEquals(1, host.titleCalls)
        assertEquals(1, host.banCalls)
        assertEquals(1, host.cardCalls)
    }

    @Test
    fun `regex 从 middle 抠掉匹配段并写入 finds`() {
        val loader = boot(
            """
            commands = { ["rex"] = "onRex" }
            function onRex(msg)
                msg.regexStructure.middle = "查42号"
                local ok = ZhaoDiceSDK.regex("查(\\d+)", msg.regexStructure)
                return tostring(ok) .. "/" .. msg.regexStructure.middle .. "/" ..
                    tostring(msg.regexStructure.finds[1]) .. "/" ..
                    tostring(ZhaoDiceSDK.contains("号", msg.regexStructure))
            end
            """.trimIndent()
        )
        assertEquals("true/号/42/true", loader.invokeCommand("rex", "", "1", "2", true, "rex"))
    }

    @Test
    fun `chat sendToPerson 缺群号时报错 不发送`() {
        val host = FakeHost()
        val loader = boot(
            """
            commands = { ["pm"] = "onPm" }
            function onPm(msg)
                ZhaoDiceSDK.chat.sendToPerson("hi", "10001")
                return "after"
            end
            """.trimIndent(),
            host
        )
        assertNull(loader.invokeCommand("pm", "", "10001", "20002", true, "pm"))
        assertEquals("", host.lastPrivate)
    }

    @Test
    fun `storage 与 JS 变量共用文件但互不可见`() {
        val dataDir = File(root, "custom/data")
        val variables = ProgramVariables(dataDir)
        val context = ProgramContext(
            "test", "demo", "10001", "20002", true,
            emptyMap(), emptyMap(), emptyList(), variables
        )
        ProgramEngine.execute("【赋值变量 k,JS值】【设置固态变量 k】", context)
        assertNotNull(File(dataDir, "test.json").takeIf { it.isFile })

        val dir = File(root, "custom")
        dir.mkdirs()
        File(dir, "test.toml").writeText(
            """
            [plugin]
            id = "test"

            [[lua]]
            name = "shared"
            lua = '''
            commands = { ["st"] = "onSt" }
            function onSt(msg) return tostring(ZhaoDiceSDK.storage.read("k")) end
            '''
            """.trimIndent(),
            Charsets.UTF_8
        )
        val loader = PluginLoader(root.path).apply {
            host = FakeHost()
            reload()
        }
        assertEquals("nil", loader.invokeCommand("st", "", "1", "2", true, "st"))

        val reloaded = ProgramVariables(dataDir)
        reloaded.load("test")
        val second = ProgramContext(
            "test", "demo", "10001", "20002", true,
            emptyMap(), emptyMap(), emptyList(), reloaded
        )
        assertEquals("JS值", ProgramEngine.execute("【变量 k】", second))
    }
}
