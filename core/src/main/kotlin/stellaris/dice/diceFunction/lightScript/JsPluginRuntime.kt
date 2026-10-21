package stellaris.dice.diceFunction.lightScript

import stellaris.dice.diceFunction.DiceLogger
import stellaris.dice.diceFunction.calculation.DiceCalculation
import java.time.LocalDate

/**
 * JS 插件执行器。`【执行脚本】` 与 `[[timer]].js` 共用；每次执行新建一个
 * `DiceJsSandbox` 引擎并按上下文绑定 `Lib`。
 */
class JsPluginRuntime(
    private val pluginId: String,
    private val host: PluginHost,
    private val defines: Map<String, String>,
    private val config: Map<String, String>,
    private val variables: ProgramVariables
) {

    fun execute(script: String, context: JsContext): String? {
        return try {
            val engine = DiceJsSandbox.create()
            engine.put("Lib", Lib(context))
            engine.put("StellarisSDK", Sdk(context))
            val result = engine.eval(script)
            val text = result?.toString()
            if (text == null || text == "undefined" || text == "null" || text == "kotlin.Unit") {
                null
            } else {
                PluginLimits.limitResult(text)
            }
        } catch (t: Throwable) {
            DiceLogger.error("JS 插件 $pluginId 执行失败：$t")
            null
        }
    }

    fun evalIsolated(script: String): String? {
        return try {
            PluginLimits.limitResult(DiceJsSandbox.create().eval(script)?.toString())
        } catch (t: Throwable) {
            DiceLogger.error("JS eval 失败：$t")
            null
        }
    }

    class JsContext(
        val senderId: String,
        val groupId: String,
        val isGroup: Boolean,
        val autoName: String
    )

    inner class Lib(private val context: JsContext) {
        fun roll(expression: String): Int = try {
            DiceCalculation.rollCalculation(expression).intNumber
        } catch (t: Throwable) {
            0
        }

        fun rollDetail(expression: String): String = try {
            DiceCalculation.rollCalculation(expression).toString()
        } catch (t: Throwable) {
            ""
        }

        fun draw(name: String): String? = host.draw(context.groupId, name)

        fun jrrp(salt: String?): Int {
            val seed = "${LocalDate.now()}:${context.senderId}:${salt ?: ""}".hashCode().toLong()
            return ((seed and 0xFFFFFFFFL) % 100).toInt() + 1
        }

        fun getConfig(key: String): String? = config[key]
        fun getConstant(key: String): String? = defines[key]
        fun getValue(key: String): String = variables.get(keyOf(key)) ?: ""

        fun setValue(key: String, value: String) {
            variables.set(keyOf(key), value)
        }

        fun setAsSolidValue(key: String) {
            variables.markSolid(keyOf(key), pluginId)
        }

        fun setTag(key: String, tag: String) {
            // 标签作为命名空间前缀。
        }

        fun setValueExpireTime(key: String, time: Long) {
            variables.setExpiry(keyOf(key), time)
        }

        fun sendGroupMessage(message: String, group: String) {
            host.sendGroupMessage(message, group)
        }

        fun sendPrivateMessage(message: String, id: String) {
            host.sendPrivateMessage(message, id)
        }

        fun sendPrivateMessage(message: String, group: String, id: String) {
            host.sendPrivateMessage(message, id)
        }

        fun sendFriendPrivateMessage(message: String, id: String) {
            host.sendPrivateMessage(message, id)
        }

        fun reply(message: String) {
            if (context.isGroup) {
                if (context.groupId.isNotEmpty()) host.sendGroupMessage(message, context.groupId)
            } else if (context.senderId.isNotEmpty()) {
                host.sendPrivateMessage(message, context.senderId)
            }
        }

        fun eval(script: String): String? = evalIsolated(script)

        fun getSender(): String = context.senderId
        fun getGroup(): String = context.groupId
        fun groupChat(): Boolean = context.isGroup

        fun sleep(millis: Long) {
            if (millis in 1..4999) {
                try {
                    Thread.sleep(millis)
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        fun wget(url: String): String =
            NetworkAccess.httpGet(url, host.allowLocalNetwork())

        fun getStringMiddle(text: String, left: String, right: String): String {
            if (left.isEmpty() || right.isEmpty()) return ""
            val start = text.indexOf(left)
            if (start < 0) return ""
            val contentStart = start + left.length
            val end = text.indexOf(right, contentStart)
            if (end < 0) return ""
            return text.substring(contentStart, end)
        }

        fun setGroupTitle(user: String, title: String, group: String): Boolean =
            host.setGroupTitle(title, user, group)

        fun setGroupWholeBan(enable: Boolean, group: String): Boolean =
            host.setGroupWholeBan(enable, group)

        private fun keyOf(key: String): String =
            "$pluginId\u0000${context.senderId}\u0000${context.groupId}\u0000$key"
    }

    inner class Sdk(private val context: JsContext) {
        fun roll(expression: String): Int = Lib(context).roll(expression)
        fun draw(name: String): String? = host.draw(context.groupId, name)
        fun getSender(): String = context.senderId
    }

}
