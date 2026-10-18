package stellaris.dice.diceFunction.lightScript

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceLogger
import java.io.File

/**
 * 插件数据文件 `custom/data/<插件>.json` 的读写器。
 *
 * 同一个文件同时承载两套互不相通的命名空间：
 * - `vars`：JS 变量系统（键里已带插件名前缀与 tag）
 * - `lua`：Lua `storage.*`（固定 tag `"lua"`）
 *
 * 旧的平铺格式（`{key: value}`）按 `vars` 读取。
 */
internal object PluginStorageFile {

    private val lock = Any()

    data class LuaValue(var value: String, var expireAt: Long)

    fun readLua(file: File): MutableMap<String, LuaValue> = synchronized(lock) {
        val result = LinkedHashMap<String, LuaValue>()
        val root = readRoot(file) ?: return result
        val now = System.currentTimeMillis()
        root.optJSONObject(KEY_LUA)?.let { lua ->
            lua.keys().forEach { key ->
                val entry = lua.opt(key)
                val stored = when (entry) {
                    is JSONObject -> LuaValue(
                        entry.optString("v"),
                        entry.optLong("e", 0L)
                    )

                    is String -> LuaValue(entry, 0L)
                    else -> LuaValue(entry?.toString().orEmpty(), 0L)
                }
                if (stored.expireAt > 0 && now > stored.expireAt) return@forEach
                if (stored.value.isEmpty()) return@forEach
                result[key] = stored
            }
        }
        result
    }

    fun writeLua(file: File, values: Map<String, LuaValue>) = synchronized(lock) {
        val root = readRoot(file) ?: JSONObject()
        val lua = JSONObject()
        values.forEach { (key, entry) ->
            lua.put(
                key,
                JSONObject().apply {
                    put("v", entry.value)
                    if (entry.expireAt > 0) put("e", entry.expireAt)
                }
            )
        }
        root.put(KEY_LUA, lua)
        writeRoot(file, root)
    }

    fun readVars(file: File): MutableMap<String, String> = synchronized(lock) {
        val result = LinkedHashMap<String, String>()
        val root = readRoot(file) ?: return result
        val vars = root.optJSONObject(KEY_VARS)
        if (vars != null) {
            vars.keys().forEach { key ->
                val value = vars.opt(key)
                if (value is String && value.isNotEmpty()) result[key] = value
            }
            return result
        }
        // 旧平铺格式：顶层字符串值都是变量。
        root.keys().forEach { key ->
            if (key == KEY_LUA || key == KEY_VARS) return@forEach
            val value = root.opt(key)
            if (value is String) result[key] = value
        }
        result
    }

    fun writeVars(file: File, prefix: String, values: Map<String, String>) = synchronized(lock) {
        val root = readRoot(file) ?: JSONObject()
        val vars = JSONObject()
        val existing = readVars(file)
        existing.forEach { (key, value) ->
            if (!key.startsWith(prefix)) vars.put(key, value)
        }
        values.forEach { (key, value) ->
            if (key.startsWith(prefix) && value.isNotEmpty()) vars.put(key, value)
        }
        root.put(KEY_VARS, vars)
        writeRoot(file, root)
    }

    private fun readRoot(file: File): JSONObject? {
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (t: Throwable) {
            DiceLogger.error("插件数据文件读取失败：${file.name}（$t）")
            null
        }
    }

    private fun writeRoot(file: File, root: JSONObject) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("插件数据文件写入失败：${file.name}（$t）")
        }
    }

    private const val KEY_LUA = "lua"
    private const val KEY_VARS = "vars"
}
