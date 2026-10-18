package stellaris.dice.diceFunction.lightScript

import com.moandjiezana.toml.Toml
import stellaris.dice.diceFunction.DiceCustomFileLoader
import stellaris.dice.diceFunction.DiceLogger
import stellaris.dice.diceFunction.calculation.DiceCalculation
import java.io.File

/**
 * TOML 插件加载器。解析 `[config]`、`[[define]]`、`[[auto]]`、`[[lua]]`、`[[timer]]`
 * 与 `program`。
 */
class PluginLoader(private val rootPath: String) : DiceCustomFileLoader {

    override val customCommands: ArrayList<String> = ArrayList()

    private val files = ArrayList<PluginFile>()
    private val luaRuntimes = ArrayList<LuaPluginRuntime>()
    private val timers = ArrayList<TimerItem>()
    private val timerFiredAt = HashMap<String, Long>()
    private val variables = ProgramVariables(File(File(rootPath, "custom"), "data"))

    private var timerExecutor: java.util.concurrent.ScheduledExecutorService? = null

    /** 由 DiceHelperImpl 在构造后注入；初始 reload 时允许为空。 */
    var host: PluginHost? = null

    val pluginCount: Int get() = files.size

    val autoCount: Int get() = files.sumOf { it.autos.size }

    @Synchronized
    fun reload() {
        files.clear()
        luaRuntimes.clear()
        timers.clear()
        customCommands.clear()
        val dir = File(rootPath, "custom")
        if (!dir.isDirectory) return
        dir.listFiles { file -> file.isFile && file.name.endsWith(".toml", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.forEach { load(it) }
    }

    private fun load(file: File) {
        try {
            val toml = Toml().read(file)
            val pluginId = toml.getTable("plugin")?.getString("id")
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val storageId = file.nameWithoutExtension
            variables.register(pluginId, storageId)
            variables.load(pluginId)

            val config = toml.getTable("config")?.toMap()
                ?.mapValues { it.value.toString() }
                ?: emptyMap()

            val defines = HashMap<String, String>()
            toml.getTables("define")?.forEach { table ->
                val name = table.getString("name")
                val content = table.getString("content")
                if (!name.isNullOrEmpty() && content != null) defines[name] = content
            }

            val autos = ArrayList<AutoItem>()
            toml.getTables("auto")?.forEach { table ->
                val full = strings(table, "keywordFull")
                val contained = strings(table, "keywordContained")
                val regexp = strings(table, "keywordRegexp")
                val content = strings(table, "content")
                val program = (table.toMap()["program"] as? String).orEmpty()
                if (full.isEmpty() && contained.isEmpty() && regexp.isEmpty()) return@forEach
                if (content.isEmpty() && program.isEmpty()) return@forEach
                autos.add(
                    AutoItem(
                        full = full,
                        contained = contained,
                        regexp = regexp,
                        content = content,
                        program = program,
                        disableGroup = boolean(table, "disableGroup"),
                        disableDialog = boolean(table, "disableDialog"),
                        config = table.toMap().entries
                            .filter { it.value is String }
                            .associate { it.key to it.value.toString() }
                    )
                )
            }

            toml.getTables("lua")?.forEach { luaTable ->
                val name = luaTable.getString("name").orEmpty()
                val code = luaTable.getString("lua").orEmpty()
                val after = luaTable.getString("after_message").orEmpty()
                if (name.isEmpty() || code.isEmpty()) return@forEach
                val runtime = LuaPluginRuntime(
                    pluginId = pluginId,
                    source = code,
                    afterMessageName = after,
                    host = host ?: NoopPluginHost,
                    dataDir = File(File(rootPath, "custom"), "data"),
                    storageId = storageId
                )
                runtime.load()
                if (runtime.loaded) {
                    luaRuntimes.add(runtime)
                    customCommands.addAll(runtime.commandNames())
                }
            }

            val jsRuntime = JsPluginRuntime(
                pluginId = pluginId,
                host = host ?: NoopPluginHost,
                defines = defines,
                config = config,
                variables = variables
            )

            toml.getTables("timer")?.forEach { timer ->
                val name = timer.getString("name").orEmpty()
                val expression = timer.getString("expression").orEmpty()
                val js = timer.getString("js").orEmpty()
                if (name.isNotEmpty() && expression.isNotEmpty() && js.isNotEmpty()) {
                    timers.add(TimerItem(pluginId, name, expression, js, jsRuntime))
                }
            }

            files.add(
                PluginFile(
                    id = pluginId,
                    path = file,
                    defines = defines,
                    config = config,
                    autos = autos,
                    jsRuntime = jsRuntime
                )
            )
        } catch (t: Throwable) {
            DiceLogger.error("载入自定义文件 ${file.name} 失败：$t")
        }
    }

    private fun strings(table: Toml, key: String): List<String> {
        val value = table.toMap()[key] ?: return emptyList()
        return when (value) {
            is String -> if (value.isEmpty()) emptyList() else listOf(value)
            is List<*> -> value.mapNotNull { it as? String }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun boolean(table: Toml, key: String): Boolean =
        when (val value = table.toMap()[key]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }

    /**
     * 非指令消息的插件关键词匹配。返回 null 表示没有插件接住，
     * 空串也视为没接住（继续问下一个）。
     */
    @Synchronized
    fun matchAuto(
        text: String,
        senderId: String,
        groupId: String,
        isGroup: Boolean
    ): AutoReply? {
        for (file in files) {
            for (auto in file.autos) {
                if (isGroup && auto.disableGroup) continue
                if (!isGroup && auto.disableDialog) continue
                val groups = auto.match(text) ?: continue
                val autoName = auto.full.firstOrNull()
                    ?: auto.contained.firstOrNull()
                    ?: auto.regexp.firstOrNull()
                    ?: "auto"
                val body = if (auto.program.isNotEmpty()) {
                    ProgramEngine.execute(
                        auto.program,
                        ProgramContext(
                            pluginId = file.id,
                            autoName = autoName,
                            senderId = senderId,
                            groupId = groupId,
                            isGroup = isGroup,
                            defines = file.defines,
                            config = file.config + auto.config,
                            regexGroups = groups,
                            variables = variables,
                            scriptExecutor = { script ->
                                file.jsRuntime.execute(
                                    script,
                                    JsPluginRuntime.JsContext(senderId, groupId, isGroup, autoName)
                                )
                            }
                        )
                    )
                } else {
                    auto.content[DiceCalculation.getRandomInt(0, auto.content.size - 1)]
                }
                if (body.isEmpty()) continue
                return AutoReply(body)
            }
        }
        return null
    }

    fun replace(
        text: String,
        senderId: String,
        groupId: String,
        isGroup: Boolean,
        nick: String = "",
        groupNick: String = "",
        isAtMe: Boolean = false
    ): String? {
        val message = LuaMessage(senderId, groupId, isGroup, text, text, nick = nick, groupNick = groupNick, isAtMe = isAtMe)
        luaRuntimes.forEach { runtime ->
            runtime.replace(text, message)?.let { return it }
        }
        return null
    }

    fun matchKeyword(
        text: String,
        senderId: String,
        groupId: String,
        isGroup: Boolean,
        nick: String = "",
        groupNick: String = "",
        isAtMe: Boolean = false
    ): String? {
        val message = LuaMessage(senderId, groupId, isGroup, text, text, nick = nick, groupNick = groupNick, isAtMe = isAtMe)
        luaRuntimes.forEach { runtime ->
            runtime.matchKeyword(text, message)?.let { return it }
        }
        return null
    }

    fun invokeCommand(
        command: String,
        params: String,
        senderId: String,
        groupId: String,
        isGroup: Boolean,
        fullText: String,
        nick: String = "",
        groupNick: String = "",
        isAtMe: Boolean = false
    ): String? {
        val message = LuaMessage(
            senderId,
            groupId,
            isGroup,
            fullText,
            params,
            command = command,
            nick = nick,
            groupNick = groupNick,
            isAtMe = isAtMe
        )
        luaRuntimes.forEach { runtime ->
            runtime.invokeCommand(command, message)?.let { return it }
        }
        return null
    }

    fun afterMessage(
        senderId: String,
        groupId: String,
        isGroup: Boolean,
        text: String,
        params: String,
        nick: String = "",
        groupNick: String = "",
        isAtMe: Boolean = false
    ) {
        val message = LuaMessage(
            senderId,
            groupId,
            isGroup,
            text,
            params,
            nick = nick,
            groupNick = groupNick,
            isAtMe = isAtMe
        )
        luaRuntimes.forEach { it.afterMessage(message) }
    }

    /**
     * 启动六域 cron 调度。宿主注入前不启动；重复调用只创建一个线程。
     */
    @Synchronized
    fun startTimers() {
        if (host == null || timerExecutor != null) return
        val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "stellaris-plugin-timer").apply { isDaemon = true }
        }
        executor.scheduleAtFixedRate(
            { tickTimers(java.time.ZonedDateTime.now()) },
            1,
            1,
            java.util.concurrent.TimeUnit.SECONDS
        )
        timerExecutor = executor
    }

    /** 供测试直接驱动；生产环境由每秒一次的调度器调用。 */
    @Synchronized
    fun tickTimers(now: java.time.ZonedDateTime) {
        val epoch = now.toEpochSecond()
        timers.forEach { timer ->
            if (!CronSchedule.matches(timer.expression, now)) return@forEach
            val key = "${timer.pluginId}\u0000${timer.name}"
            if (timerFiredAt[key] == epoch) return@forEach
            timerFiredAt[key] = epoch
            try {
                timer.runtime.execute(
                    timer.js,
                    JsPluginRuntime.JsContext("", "", false, timer.name)
                )
            } catch (t: Throwable) {
                DiceLogger.error("插件定时任务 ${timer.pluginId}/${timer.name} 失败：$t")
            }
        }
    }

    class AutoReply(val content: String)

    private object NoopPluginHost : PluginHost {
        override fun setGroupCard(user: String, text: String, group: String): Boolean = false
        override fun configureAutoName(
            user: String,
            group: String,
            format: String,
            enabled: Boolean
        ): AutoNameReceipt? = null

        override fun syncAutoName(user: String, group: String): Boolean = false
        override fun sendGroupMessage(text: String, group: String) {}
        override fun sendPrivateMessage(text: String, userId: String) {}
        override fun roll(expression: String): Int = 0
        override fun draw(group: String, name: String): String? = null
        override fun setGroupTitle(title: String, userId: String, group: String): Boolean = false
        override fun setGroupWholeBan(ban: Boolean, group: String): Boolean = false
        override fun reloadPlugins() {}
    }

    private class PluginFile(
        val id: String,
        val path: File,
        val defines: Map<String, String>,
        val config: Map<String, String>,
        val autos: List<AutoItem>,
        val jsRuntime: JsPluginRuntime
    )

    private class TimerItem(
        val pluginId: String,
        val name: String,
        val expression: String,
        val js: String,
        val runtime: JsPluginRuntime
    )

    private class AutoItem(
        val full: List<String>,
        val contained: List<String>,
        val regexp: List<String>,
        val content: List<String>,
        val program: String,
        val disableGroup: Boolean,
        val disableDialog: Boolean,
        val config: Map<String, String>
    ) {
        fun match(text: String): List<String>? {
            if (full.any { it == text }) return emptyList()
            if (contained.any { text.contains(it) }) return emptyList()
            for (pattern in regexp) {
                val match = try {
                    Regex(pattern, RegexOption.DOT_MATCHES_ALL).find(text)
                } catch (t: Throwable) {
                    null
                }
                if (match != null) return match.groupValues.drop(1)
            }
            return null
        }
    }
}
