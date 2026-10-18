










package stellaris.dice.diceFunction.lightScript

import stellaris.dice.diceFunction.DiceLogger
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ScriptableObject
import java.io.Reader
import javax.script.AbstractScriptEngine
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptEngineFactory
import javax.script.ScriptException
import javax.script.SimpleBindings

internal object DiceJsSandbox {


    private val JAVA_GLOBALS = arrayOf(
        "Packages", "java", "javax", "org", "com", "edu", "net", "MWArray",
        "importClass", "importPackage", "JavaAdapter"
    )






    private val LANG_DENY = arrayOf(
        "java.lang.system", "java.lang.runtime", "java.lang.process", "java.lang.processbuilder",
        "java.lang.classloader", "java.lang.thread", "java.lang.threadgroup", "java.lang.compiler",
        "java.lang.securitymanager", "java.lang.stackwalker", "java.lang.reflect.", "java.lang.invoke."
    )


    private fun blockedClass(fullName: String): Boolean {
        // 插件 SDK 暴露 String/Int/Boolean 等值类型方法。
        if (fullName == "stellaris.dice.diceFunction.lightScript.JsPluginRuntime\$Lib" ||
            fullName == "stellaris.dice.diceFunction.lightScript.JsPluginRuntime\$Sdk"
        ) {
            return false
        }
        val n = fullName.lowercase()
        if (n.startsWith("java.lang.")) return LANG_DENY.any { n == it || n.startsWith(it) }
        return n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jdk.") ||
                n.startsWith("sun.") || n.startsWith("com.sun.") ||
                n.startsWith("stellaris.") || n.startsWith("presence.")
    }

    private object SandboxContextFactory : ContextFactory() {
        override fun makeContext(): Context {
            val cx = super.makeContext()

            try {
                cx.languageVersion = 200
            } catch (t: Throwable) {
            }
            cx.optimizationLevel = -1
            cx.setClassShutter(ClassShutter { !blockedClass(it) })
            return cx
        }

        override fun hasFeature(cx: Context, featureIndex: Int): Boolean =
            if (featureIndex == 18) true
            else super.hasFeature(cx, featureIndex)
    }

    private val FACTORY: ScriptEngineFactory = object : ScriptEngineFactory {
        override fun getEngineName(): String = "stellaris-plugin-js"
        override fun getEngineVersion(): String = "rhino-1.7.14+sandbox"
        override fun getLanguageName(): String = "ECMAScript"
        override fun getLanguageVersion(): String = "200"
        override fun getNames(): MutableList<String> = mutableListOf("stellaris-plugin-js")
        override fun getMimeTypes(): MutableList<String> =
            mutableListOf("text/javascript", "application/javascript")
        override fun getExtensions(): MutableList<String> = mutableListOf("js")
        override fun getParameter(key: String): Any? = null
        override fun getOutputStatement(toDisplay: String): String = "print($toDisplay)"
        override fun getMethodCallSyntax(obj: String, m: String, args: Array<out String>): String =
            obj + "." + m + "(" + args.joinToString(",") + ")"
        override fun getProgram(vararg statements: String): String = statements.joinToString(";\n")
        override fun getScriptEngine(): ScriptEngine = create()
    }

    fun create(): ScriptEngine = SandboxEngine()

    private class SandboxEngine : AbstractScriptEngine() {

        private val scope: ScriptableObject? = newScope()

        private fun newScope(): ScriptableObject? {
            return try {
                val cx = SandboxContextFactory.enterContext()
                try {
                    val s = cx.initStandardObjects()
                    for (n in JAVA_GLOBALS) {
                        try {
                            ScriptableObject.deleteProperty(s, n)
                        } catch (t: Throwable) {
                        }
                        try {
                            ScriptableObject.putProperty(s, n, Context.getUndefinedValue())
                        } catch (t: Throwable) {
                        }
                    }

                    val probe = cx.evaluateString(s, "typeof Packages + '/' + typeof java", "<sandbox-check>", 1, null)
                    if (probe?.toString() != "undefined/undefined") {
                        DiceLogger.error("插件 JS 沙箱未生效：Java 包入口仍在（$probe）")
                    }
                    s
                } finally {
                    Context.exit()
                }
            } catch (t: Throwable) {
                DiceLogger.error("插件 JS 沙箱初始化失败：$t")
                null
            }
        }

        override fun eval(script: String, context: ScriptContext): Any? {
            val s = scope ?: throw ScriptException("JS 沙箱不可用")
            val cx = SandboxContextFactory.enterContext()
            try {
                val bindings = context.getBindings(ScriptContext.ENGINE_SCOPE)
                if (bindings != null) {
                    for ((k, v) in bindings) {
                        try {
                            ScriptableObject.putProperty(s, k, v)
                        } catch (t: Throwable) {
                        }
                    }
                }
                val result = cx.evaluateString(s, script, "<plugin>", 1, null)
                return Context.jsToJava(result, Any::class.java)
            } catch (se: ScriptException) {
                throw se
            } catch (t: Throwable) {

                throw if (t is Exception) ScriptException(t) else ScriptException(t.toString())
            } finally {
                Context.exit()
            }
        }

        override fun eval(reader: Reader, context: ScriptContext): Any? = eval(reader.readText(), context)

        override fun createBindings(): Bindings = SimpleBindings()

        override fun getFactory(): ScriptEngineFactory = FACTORY
    }
}
