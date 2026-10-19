package stellaris.dice.diceFunction.lightScript

import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import stellaris.dice.diceFunction.DiceLogger
import stellaris.dice.diceFunction.calculation.DiceCalculation
import stellaris.dice.diceFunction.draw.DrawData
import java.util.Base64

/**
 * 插件运行时对宿主的依赖。只暴露必要的动作。
 *
 * 新增方法都带默认实现。
 */
interface PluginHost {
    fun setGroupCard(user: String, text: String, group: String): Boolean
    fun configureAutoName(user: String, group: String, format: String, enabled: Boolean): AutoNameReceipt?
    fun syncAutoName(user: String, group: String): Boolean
    fun sendGroupMessage(text: String, group: String)
    fun sendPrivateMessage(text: String, userId: String)
    fun roll(expression: String): Int
    fun draw(group: String, name: String): String?
    fun setGroupTitle(title: String, userId: String, group: String): Boolean
    fun setGroupWholeBan(ban: Boolean, group: String): Boolean
    fun reloadPlugins()

    fun readPlayerName(user: String, group: String): String? = null
    fun readPlayerCard(user: String, name: String): Map<String, Int>? = null
    fun writePlayerCard(user: String, name: String, properties: Map<String, Int>): Boolean = false
    fun readLastAppearedGroup(user: String): String? = null
    fun readSystemConfig(key: String): String = ""
    fun openDeck(group: String, name: String): PluginDeck? = null
    fun httpGet(url: String): String = ""
    fun httpPost(url: String, data: String, headers: Map<String, String>): String? = null
    fun allowLocalNetwork(): Boolean = false
    fun requestRestart() {}
}

/**
 * Lua `draw.*` 的牌堆句柄。每次 `draw.open` 得到一份独立的工作副本。
 */
interface PluginDeck {
    fun read(putBack: Boolean): String?
    fun write(content: String, weight: String): Boolean
    fun remain(): Int
    fun recycle()
}

class AutoNameReceipt(val playerName: String, val enabled: Boolean, val format: String)

data class LuaMessage(
    val senderId: String,
    val groupId: String,
    val isGroup: Boolean,
    val text: String,
    val params: String,
    val command: String = "",
    val nick: String = "",
    val groupNick: String = "",
    val isAtMe: Boolean = false,
    val regexMiddle: String = "",
    val regexFinds: List<String> = emptyList()
)

/**
 * 单个 `[[lua]]` 条目的运行实例。解析 `commands` / `msg_order` / `replaces`
 * 三个钩子表，并在消息收尾时调用 `after_message`。
 */
class LuaPluginRuntime(
    private val pluginId: String,
    private val source: String,
    private val afterMessageName: String,
    private val host: PluginHost,
    private val dataDir: java.io.File,
    /** 数据文件名（插件文件名去后缀）。 */
    private val storageId: String = pluginId
) {

    private var globals: Globals? = null
    private val commands = LinkedHashMap<String, String>()
    private val keywords = LinkedHashMap<String, String>()
    private val replaces = LinkedHashMap<String, String>()

    /** 本条消息的上下文。`draw.open` 需要群号。 */
    private val activeMessage = ThreadLocal<LuaMessage?>()

    private val decks = HashMap<Int, PluginDeck>()
    private var nextDeckId = 1
    private val loadedDecks = HashMap<String, DrawData>()

    /** 构造时建好数据目录并载入存量。 */
    private val storage = LuaStorage(storageId, dataDir)

    val loaded: Boolean
        get() = globals != null

    fun load() {
        try {
            val g = JsePlatform.standardGlobals()
            applySandboxLimits(g)
            val sdk = sdk(g)
            g.set("ZhaoDiceSDK", sdk)
            g.set("json", jsonLibrary())
            g.set("LINE", LuaValue.valueOf("\n"))
            g.load(source, pluginId).call()
            globals = g
            readHooks(g)
        } catch (t: Throwable) {
            DiceLogger.error("Lua 插件 $pluginId 载入失败：$t")
            globals = null
        }
    }

    /** 沙箱收敛：移除 Java 互操作、模块加载与宿主文件/进程入口，os 只保留 time/clock/date。 */
    private fun applySandboxLimits(g: Globals) {
        g.set("luajava", LuaValue.NIL)
        g.set("io", LuaValue.NIL)
        g.set("require", LuaValue.NIL)
        g.set("package", LuaValue.NIL)
        g.set("loadfile", LuaValue.NIL)
        g.set("dofile", LuaValue.NIL)
        g.set("os", restrictedOs())
    }

    private fun restrictedOs(): LuaTable {
        val os = LuaTable()
        os.set("time", function { args ->
            if (args.narg() >= 1 && args.arg1().istable()) {
                val t = args.arg1()
                val at = java.time.LocalDateTime.of(
                    t.get("year").optint(0),
                    t.get("month").optint(1),
                    t.get("day").optint(1),
                    t.get("hour").optint(0),
                    t.get("min").optint(0),
                    t.get("sec").optint(0)
                )
                LuaValue.valueOf(at.atZone(java.time.ZoneId.systemDefault()).toEpochSecond().toDouble())
            } else {
                LuaValue.valueOf((System.currentTimeMillis() / 1000).toDouble())
            }
        })
        os.set("clock", function { _ ->
            LuaValue.valueOf(System.nanoTime() / 1e9)
        })
        os.set("date", function { args ->
            val format = if (args.narg() >= 1 && args.arg1().isstring()) {
                args.arg1().tojstring()
            } else {
                "%Y-%m-%d %H:%M:%S"
            }
            val instant = if (args.narg() >= 2 && args.arg(2).isnumber()) {
                java.time.Instant.ofEpochSecond(args.arg(2).tolong())
            } else {
                java.time.Instant.now()
            }
            val t = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            LuaValue.valueOf(
                format
                    .replace("%F", String.format("%04d-%02d-%02d", t.year, t.monthValue, t.dayOfMonth))
                    .replace("%T", String.format("%02d:%02d:%02d", t.hour, t.minute, t.second))
                    .replace("%Y", String.format("%04d", t.year))
                    .replace("%m", String.format("%02d", t.monthValue))
                    .replace("%d", String.format("%02d", t.dayOfMonth))
                    .replace("%H", String.format("%02d", t.hour))
                    .replace("%M", String.format("%02d", t.minute))
                    .replace("%S", String.format("%02d", t.second))
            )
        })
        return os
    }

    private fun readHooks(g: Globals) {
        commands.clear()
        keywords.clear()
        replaces.clear()
        readStringTable(g.get("commands"), commands)
        readStringTable(g.get("msg_order"), keywords)
        readStringTable(g.get("replaces"), replaces)
    }

    private fun readStringTable(value: LuaValue, target: MutableMap<String, String>) {
        if (!value.istable()) return
        val table = value.checktable()
        val keys = table.keys()
        keys.forEach { key ->
            val name = table.get(key)
            if (name.isstring()) target[key.tojstring()] = name.tojstring()
        }
    }

    fun commandNames(): Set<String> = commands.keys.toSet()

    fun invokeCommand(command: String, message: LuaMessage): String? {
        val function = commands[command] ?: return null
        return call(function, message)
    }

    fun matchKeyword(text: String, message: LuaMessage): String? {
        for ((keyword, function) in keywords) {
            if (keyword.isEmpty() || text.startsWith(keyword)) {
                val result = call(function, message)
                if (!result.isNullOrEmpty()) return result
            }
        }
        return null
    }

    fun replace(text: String, message: LuaMessage): String? {
        for ((prefix, function) in replaces) {
            if (prefix.isEmpty() || text.startsWith(prefix, ignoreCase = true)) {
                val body = if (prefix.isEmpty()) text else text.substring(prefix.length).trim()
                return call(function, message.copy(params = body, command = ""))
            }
        }
        return null
    }

    fun afterMessage(message: LuaMessage) {
        if (afterMessageName.isEmpty()) return
        call(afterMessageName, message)
    }

    private fun call(function: String, message: LuaMessage): String? {
        val g = globals ?: return null
        return try {
            val value = g.get(function)
            if (!value.isfunction()) return null
            activeMessage.set(message)
            val result = value.call(messageTable(message))
            if (result.isnil()) null else result.tojstring()
        } catch (t: Throwable) {
            DiceLogger.error("Lua 插件 $pluginId 的 $function 执行失败：$t")
            null
        } finally {
            activeMessage.remove()
        }
    }

    private fun messageTable(message: LuaMessage): LuaTable {
        val table = LuaTable()
        table.set("fromMsg", LuaValue.valueOf(message.text))
        table.set("fromParams", LuaValue.valueOf(message.params))
        table.set("command", LuaValue.valueOf(message.command))
        if (message.senderId.isNotEmpty()) {
            table.set("fromQQ", LuaValue.valueOf(message.senderId))
        }
        if (message.isGroup && message.groupId.isNotEmpty()) {
            table.set("fromGroup", LuaValue.valueOf(message.groupId))
        }
        table.set("fromNick", LuaValue.valueOf(message.nick))
        table.set("fromGroupNick", LuaValue.valueOf(message.groupNick))
        table.set("isGroup", LuaValue.valueOf(message.isGroup))
        table.set("isAtMe", LuaValue.valueOf(message.isAtMe))
        table.set("isEmpty", LuaValue.valueOf(message.text.isBlank()))
        val regexStructure = LuaTable()
        regexStructure.set("middle", LuaValue.valueOf(message.regexMiddle))
        val finds = LuaTable()
        message.regexFinds.forEachIndexed { index, value ->
            finds.set(index, LuaValue.valueOf(value))
        }
        regexStructure.set("finds", finds)
        table.set("regexStructure", regexStructure)
        return table
    }

    private fun sdk(g: Globals): LuaTable {
        val sdk = LuaTable()

        val group = LuaTable()
        group.set("setCard", function { args ->
            LuaValue.valueOf(
                host.setGroupCard(
                    args.arg(1).tojstring(),
                    args.arg(2).tojstring(),
                    args.arg(3).tojstring()
                )
            )
        })
        group.set("setTitle", function { args ->
            LuaValue.valueOf(
                host.setGroupTitle(
                    args.arg(1).tojstring(),
                    args.arg(2).tojstring(),
                    args.arg(3).tojstring()
                )
            )
        })
        group.set("setWholeBan", function { args ->
            LuaValue.valueOf(
                host.setGroupWholeBan(
                    args.arg(1).toboolean(),
                    args.arg(2).tojstring()
                )
            )
        })
        sdk.set("group", group)

        val playerCard = LuaTable()
        playerCard.set("configureAutoName", function { args ->
            val receipt = host.configureAutoName(
                args.arg(1).tojstring(),
                args.arg(2).tojstring(),
                args.arg(3).tojstring(),
                args.arg(4).toboolean()
            )
            if (receipt == null) {
                LuaValue.NIL
            } else {
                LuaTable().apply {
                    set("playerName", LuaValue.valueOf(receipt.playerName))
                    set("autoNameStatus", LuaValue.valueOf(receipt.enabled))
                    set("autoNameFormat", LuaValue.valueOf(receipt.format))
                }
            }
        })
        playerCard.set("syncAutoName", function { args ->
            LuaValue.valueOf(
                host.syncAutoName(args.arg(1).tojstring(), args.arg(2).tojstring())
            )
        })
        sdk.set("playerCard", playerCard)

        sdk.set("readPlayerName", function { args ->
            val name = host.readPlayerName(args.arg(1).tojstring(), args.arg(2).tojstring())
            if (name.isNullOrEmpty()) LuaValue.NIL else LuaValue.valueOf(name)
        })
        sdk.set("readPlayerCard", function { args ->
            val card = host.readPlayerCard(args.arg(1).tojstring(), args.arg(2).tojstring())
                ?: return@function LuaValue.NIL
            LuaTable().apply {
                card.forEach { (name, value) -> set(name, LuaValue.valueOf(value)) }
            }
        })
        sdk.set("writePlayerCard", function { args ->
            val table = args.arg(3)
            if (!table.istable()) return@function LuaValue.FALSE
            val properties = LinkedHashMap<String, Int>()
            table.checktable().keys().forEach { key ->
                properties[key.tojstring()] = table.get(key).toint()
            }
            LuaValue.valueOf(
                host.writePlayerCard(
                    args.arg(1).tojstring(),
                    args.arg(2).tojstring(),
                    properties
                )
            )
        })
        sdk.set("readLastAppearedGroup", function { args ->
            val groupId = host.readLastAppearedGroup(args.arg(1).tojstring())
            if (groupId.isNullOrEmpty()) LuaValue.NIL else LuaValue.valueOf(groupId)
        })
        sdk.set("readSystemConfig", function { args ->
            LuaValue.valueOf(host.readSystemConfig(args.arg(1).tojstring()))
        })

        val storageTable = LuaTable()
        storageTable.set("read", function { args ->
            val value = storage.read(args.arg(1).tojstring())
            if (value == null) LuaValue.NIL else LuaValue.valueOf(value)
        })
        storageTable.set("write", function { args ->
            storage.write(
                args.arg(1).tojstring(),
                args.arg(2).tojstring(),
                if (args.narg() >= 3) args.arg(3).tolong() else 0L
            )
            LuaValue.TRUE
        })
        storageTable.set("path", function { _ -> LuaValue.valueOf(storage.path) })
        sdk.set("storage", storageTable)

        val chat = LuaTable()
        chat.set("sendToGroup", function { args ->
            host.sendGroupMessage(args.arg(1).tojstring(), args.arg(2).tojstring())
            LuaValue.NIL
        })
        chat.set("sendToPerson", function { args ->
            if (args.narg() < 3 || args.arg(3).isnil()) {
                throw LuaError("chat.sendToPerson 需要显式传群号（私聊可传空串）")
            }
            host.sendPrivateMessage(args.arg(1).tojstring(), args.arg(2).tojstring())
            LuaValue.NIL
        })
        sdk.set("chat", chat)

        val draw = LuaTable()
        draw.set("open", function { args ->
            val name = args.arg(1).tojstring()
            val deck = loadedDecks[name]?.clone()?.let { DrawDeckHandle(it) }
                ?: host.openDeck(activeMessage.get()?.groupId.orEmpty(), name)
            if (deck == null) {
                LuaValue.NIL
            } else {
                val id = nextDeckId++
                decks[id] = deck
                LuaValue.valueOf(id)
            }
        })
        draw.set("load", function { args ->
            if (args.narg() < 2) throw LuaError("draw.load 需要 hidden 参数")
            if (!loadDecks(args.arg(1).tojstring())) return@function LuaValue.FALSE
            LuaValue.TRUE
        })
        draw.set("read", function { args ->
            if (args.narg() < 2) throw LuaError("draw.read 需要「是否放回」参数")
            val deck = decks[args.arg(1).toint()] ?: return@function LuaValue.NIL
            val text = deck.read(args.arg(2).toboolean())
            if (text == null) LuaValue.NIL else LuaValue.valueOf(text)
        })
        draw.set("write", function { args ->
            val deck = decks[args.arg(1).toint()] ?: return@function LuaValue.FALSE
            val weight = if (args.narg() >= 3) args.arg(3).tojstring() else "1"
            LuaValue.valueOf(deck.write(args.arg(2).tojstring(), weight))
        })
        draw.set("remain", function { args ->
            val deck = decks[args.arg(1).toint()] ?: return@function LuaValue.valueOf(0)
            LuaValue.valueOf(deck.remain())
        })
        draw.set("recycle", function { args ->
            val deck = decks.remove(args.arg(1).toint()) ?: return@function LuaValue.FALSE
            deck.recycle()
            LuaValue.TRUE
        })
        sdk.set("draw", draw)

        val lifecycle = LuaTable()
        lifecycle.set("write", function { args ->
            LifecycleStore.write(args.arg(1).tojstring(), args.arg(2))
            LuaValue.NIL
        })
        lifecycle.set("read", function { args ->
            LifecycleStore.read(args.arg(1).tojstring())
        })
        lifecycle.set("push", function { args ->
            LifecycleStore.push(args.arg(1))
            LuaValue.NIL
        })
        lifecycle.set("pop", function { _ -> LifecycleStore.pop() })
        sdk.set("lifecycle", lifecycle)

        val network = LuaTable()
        network.set("httpGet", function { args ->
            LuaValue.valueOf(host.httpGet(args.arg(1).tojstring()))
        })
        network.set("httpPost", function { args ->
            val headers = LinkedHashMap<String, String>()
            val table = args.arg(3)
            if (table.istable()) {
                table.checktable().keys().forEach { key ->
                    headers[key.tojstring()] = table.get(key).tojstring()
                }
            }
            val result = host.httpPost(args.arg(1).tojstring(), args.arg(2).tojstring(), headers)
            if (result == null) LuaValue.NIL else LuaValue.valueOf(result)
        })
        sdk.set("network", network)

        sdk.set("system", LuaTable().apply {
            set("load", function { _ ->
                host.reloadPlugins()
                LuaValue.NIL
            })
            set("reload", function { _ ->
                host.requestRestart()
                LuaValue.NIL
            })
        })
        sdk.set("framework", LuaTable())
        sdk.set("eval", function { args ->
            val code = args.arg(1).tojstring()
            g.load(code, "eval").call()
            LuaValue.NIL
        })

        val base64 = LuaTable()
        base64.set("encode", function { args ->
            LuaValue.valueOf(
                Base64.getEncoder().encodeToString(args.arg(1).tojstring().toByteArray(Charsets.UTF_8))
            )
        })
        base64.set("decode", function { args ->
            try {
                LuaString.valueOf(Base64.getDecoder().decode(args.arg(1).tojstring()))
            } catch (t: Throwable) {
                LuaValue.NIL
            }
        })
        sdk.set("base64", base64)

        sdk.set("regex", function { args ->
            regex(args.arg(1).tojstring(), args.arg(2))
        })
        sdk.set("contains", function { args ->
            val data = args.arg(2)
            val middle = if (data.istable()) data.get("middle").tojstring() else data.tojstring()
            LuaValue.valueOf(middle.contains(args.arg(1).tojstring()))
        })
        sdk.set("trim", function { args ->
            LuaValue.valueOf(args.arg(1).tojstring().trim())
        })
        sdk.set("toNumber", function { args ->
            val value = args.arg(1)
            if (value.isnumber()) {
                value
            } else {
                value.tojstring().toDoubleOrNull()?.let { LuaValue.valueOf(it) } ?: LuaValue.valueOf(0)
            }
        })
        sdk.set("toString", function { args ->
            val value = args.arg(1)
            if (value.istable()) LuaValue.valueOf("") else LuaValue.valueOf(value.tojstring())
        })
        sdk.set("randomInt", function { args ->
            val min = args.arg(1).toint()
            val max = args.arg(2).toint()
            LuaValue.valueOf(DiceCalculation.getRandomInt(min, max))
        })
        sdk.set("roll", function { args ->
            if (args.narg() < 2) throw LuaError("roll 需要默认值参数")
            val fallback = args.arg(2).toint()
            val result = LuaTable()
            val calculated = try {
                DiceCalculation.rollCalculation(args.arg(1).tojstring())
            } catch (t: Throwable) {
                null
            }
            result.set("number", LuaValue.valueOf(calculated?.intNumber ?: fallback))
            result.set("process", LuaValue.valueOf(calculated?.toString() ?: ""))
            result
        })

        val constants = LuaTable()
        constants.set("START", LuaValue.valueOf("^"))
        constants.set("SIGN", LuaValue.valueOf("[+\\-]"))
        constants.set("NUMBER", LuaValue.valueOf("\\d+"))
        constants.set("END", LuaValue.valueOf("$"))
        sdk.set("RegexExpressionConstant", constants)

        return sdk
    }

    private fun regex(expression: String, data: LuaValue): LuaValue {
        if (!data.istable()) return LuaValue.FALSE
        val middle = data.get("middle").tojstring()
        val match = try {
            Regex(expression, RegexOption.DOT_MATCHES_ALL).find(middle)
        } catch (t: Throwable) {
            null
        } ?: return LuaValue.FALSE
        data.set("middle", LuaValue.valueOf(middle.removeRange(match.range)))
        val finds = LuaTable()
        finds.set(0, LuaValue.valueOf(match.value))
        match.groupValues.forEachIndexed { index, value ->
            if (index > 0) finds.set(index, LuaValue.valueOf(value))
        }
        data.set("finds", finds)
        return LuaValue.TRUE
    }

    /** `draw.load`：JSON 对象里每个键是一副内存牌堆。 */
    private fun loadDecks(jsonText: String): Boolean {
        return try {
            val json = JSONObject(jsonText)
            val parsed = HashMap<String, DrawData>()
            json.keys().forEach { title ->
                val array = json.optJSONArray(title) ?: return@forEach
                val deck = DrawData(title = arrayListOf(title))
                for (index in 0 until array.length()) {
                    deck.appendData(array.optString(index))
                }
                deck.freshPriorities()
                parsed[title] = deck
            }
            if (parsed.isEmpty()) return false
            loadedDecks.putAll(parsed)
            true
        } catch (t: Throwable) {
            DiceLogger.error("Lua 插件 $pluginId 载入 JSON 牌堆失败：$t")
            false
        }
    }

    private fun function(block: (Varargs) -> LuaValue): VarArgFunction =
        object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = block(args)
        }

    private class LuaStorage(private val storageId: String, private val dataDir: java.io.File) {
        val path: String = java.io.File(dataDir, "$storageId.json").path
        private val file = java.io.File(path)
        private val values = PluginStorageFile.readLua(file)

        init {
            try {
                dataDir.mkdirs()
            } catch (t: Throwable) {
                DiceLogger.error("Lua 插件 $storageId 数据目录创建失败：$t")
            }
        }

        @Synchronized
        fun read(key: String): String? {
            val entry = values[key] ?: return null
            if (entry.expireAt > 0 && System.currentTimeMillis() > entry.expireAt) {
                values.remove(key)
                return null
            }
            return entry.value
        }

        @Synchronized
        fun write(key: String, value: String, ttlMillis: Long) {
            if (key.isEmpty()) return
            values[key] = PluginStorageFile.LuaValue(
                value,
                if (ttlMillis > 0) System.currentTimeMillis() + ttlMillis else 0L
            )
            PluginStorageFile.writeLua(file, values)
        }
    }

    /** 进程内对象暂存；不落盘，重启即失。 */
    private object LifecycleStore {
        private val values = HashMap<String, LuaValue>()
        private val stack = ArrayDeque<LuaValue>()

        @Synchronized
        fun write(id: String, value: LuaValue) {
            values[id] = value
        }

        @Synchronized
        fun read(id: String): LuaValue =
            values[id] ?: throw LuaError("lifecycle.read: 没有这个键：$id")

        @Synchronized
        fun push(value: LuaValue) {
            stack.addLast(value)
        }

        @Synchronized
        fun pop(): LuaValue =
            stack.removeLastOrNull() ?: throw LuaError("lifecycle.pop: 栈是空的")
    }

    private fun jsonLibrary(): LuaTable {
        val json = LuaTable()
        json.set("encode", function { args ->
            LuaValue.valueOf(toJson(args.arg(1)).toString())
        })
        json.set("decode", function { args ->
            try {
                fromJson(JSONObject(args.arg(1).tojstring()))
            } catch (t: Throwable) {
                try {
                    fromJson(org.json.JSONTokener(args.arg(1).tojstring()).nextValue())
                } catch (inner: Throwable) {
                    LuaValue.NIL
                }
            }
        })
        return json
    }

    private fun toJson(value: LuaValue): Any {
        if (value.isnil()) return JSONObject.NULL
        if (value.isboolean()) return value.toboolean()
        if (value.isnumber()) return value.todouble()
        if (value.isstring()) return value.tojstring()
        if (!value.istable()) return value.tojstring()
        val table = value.checktable()
        val keys = table.keys()
        val numeric = keys.isNotEmpty() && keys.all { it.isint() && it.toint() > 0 }
        return if (numeric) {
            val array = JSONArray()
            keys.sortedBy { it.toint() }.forEach { key -> array.put(toJson(table.get(key))) }
            array
        } else {
            JSONObject().apply {
                keys.forEach { key -> put(key.tojstring(), toJson(table.get(key))) }
            }
        }
    }

    private fun fromJson(value: Any?): LuaValue = when (value) {
        null, JSONObject.NULL -> LuaValue.NIL
        is JSONObject -> LuaTable().apply {
            value.keys().forEach { key -> set(key, fromJson(value.opt(key))) }
        }

        is JSONArray -> LuaTable().apply {
            for (index in 0 until value.length()) {
                set(index + 1, fromJson(value.opt(index)))
            }
        }

        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Double -> LuaValue.valueOf(value)
        is Number -> LuaValue.valueOf(value.toDouble())
        else -> LuaValue.valueOf(value.toString())
    }
}
