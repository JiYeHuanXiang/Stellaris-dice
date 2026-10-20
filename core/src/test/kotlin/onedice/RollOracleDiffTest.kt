package onedice

import onedice.data.DiceConfigs
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader
import java.util.Random




































class RollOracleDiffTest {

    companion object {







        private val KNOWN_DIFFERENCES: Map<String, String> = linkedMapOf(

            " 3D6 + 2 " to "空白：参考实现当非法字符；这里容忍（调用方不必先自己剥）",
            "1 d 6" to "空白：同上",

            "1?2:3+4" to "三元式分组：参考实现是 1 ? (2+4) : 3；这里是 1 ? 2 : (3+4)，且只求值选中分支",
            "0?2:3+4" to "三元式分组：同上，且只求值选中分支",
            "1?2:3" to "三元式改为惰性求值，明细只包含实际选中的分支",
            "0?2:3" to "三元式改为惰性求值，明细只包含实际选中的分支",
            "0&2" to "逻辑与短路后不再求值右分支，明细标记短路",
            "0&(1/0)" to "逻辑与短路：参考实现会求值除零右分支，这里直接得到 0",
            "1|(1/0)" to "逻辑或短路：参考实现会求值除零右分支，这里直接得到 1",
            "1?1:$9" to "三元惰性求值：参考实现会读取未赋值临时变量，这里只执行选中分支",

            "5!" to "后缀阶乘：参考实现要求右操作数，末尾写 `5!` 直接报错；这里按位置区分前后缀",
            "0!" to "后缀阶乘：同上",
            "3!" to "后缀阶乘：同上",
            "20!" to "后缀阶乘：同上",
            "99999!" to "后缀阶乘：同上",

            "(1+2)*3" to "'+' 子树多包一层括号：参考实现是 ((1+2))*3，这里是一层",
            "\$0e5;\$0+1" to "临时变量的取值区间：参考实现漏出未知哨兵导致区间倒置，这里按子表达式的真实区间算",

            "" to "空表达式：参考实现 UNKNOWN_GENERATE_FATAL（NPE），这里 INPUT_RAW_INVALID",
            "\$3" to "读未赋值的临时变量：参考实现 UNKNOWN_GENERATE_FATAL（NPE），这里 NODE_SUB_VAL_INVALID"
        )
    }


    private class ZeroRandom : Random() {
        override fun nextInt(): Int = 0
        override fun nextInt(bound: Int): Int = 0
        override fun nextLong(): Long = 0
    }


    private val corpus: List<String> = listOf(

        "7", "1+2*3", "(1+2)*3", "2-(3-1)", "2+3+4", "7/2", "2^10", "2^3^2", "99^99",
        "-5", "-2^2", "-2^3", "-2*3", "-1d6",

        "5!", "0!", "20!", "!0", "!5",

        "3=3", "3=4", "3<4", "4>3", "1&2", "0&2", "0|2",

        "1?2:3", "0?2:3", "1?2:3+4", "0?2:3+4", "1?1:$9",
        "0&(1/0)", "1|(1/0)",

        "1d6", "3d6+2", "d", "d6", "20d6", "5d6k3", "5d6q2", "5d6a5", "1d6k3",

        "5a5k5m6", "1a10", "7a5k7", "10c10m10", "4f3", "f", "b2", "p1", "b+5",

        "10d10-7a5k7*10+6", "1d(7a5k7)a(1d4)", "(1d100)^(7a5k7)", "1d6+1d6", "3d6*2+1",

        "\$0e5;\$0+1", "1d6;2+2",

        "0d6", "10000d6", "1d0", "1d6k3q2", "5f4", "b10", "b0", "1a1k1m6", "1a10k20m10",
        "1+", "(1+2", "1x2", "", "\$3", "3!", "99999!",

        " 3D6 + 2 ", "1 d 6"
    )

    @Test
    fun `与参考实现的差异全集就是基线`() {
        val oracleDir = System.getenv("ONEDICE_ORACLE_DIR")
        assumeTrue(
            oracleDir != null && File(oracleDir, "onedice").isDirectory,
            "未设 ONEDICE_ORACLE_DIR（或其中没有 onedice/），跳过差分比对"
        )



        val stdlib = File(kotlin.Unit::class.java.protectionDomain.codeSource.location!!.toURI())
        val loader = URLClassLoader(
            arrayOf(File(oracleDir!!).toURI().toURL(), stdlib.toURI().toURL()),
            ClassLoader.getPlatformClassLoader()
        )
        val rollClass = loader.loadClass("onedice.Roll")
        val configsClass = loader.loadClass("onedice.data.DiceConfigs")
        val configs = configsClass
            .getConstructor(Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Random::class.java)
            .newInstance(1_000L, true, ZeroRandom())
        val rollConstructor = rollClass.getConstructor(String::class.java, configsClass)
        val startMethod = rollClass.getMethod("start")

        val same = ArrayList<String>()
        val unexpected = ArrayList<String>()
        val baselineButSame = ArrayList<String>()

        corpus.forEach { expression ->
            val mine = describeMine(expression)
            val theirs = describeOracle(expression, rollConstructor, startMethod, configs)
            val known = KNOWN_DIFFERENCES.containsKey(expression)
            when {
                mine == theirs && known -> baselineButSame.add(expression)
                mine == theirs -> same.add(expression)
                known -> same.add(expression)
                else -> unexpected.add(
                    "表达式的字面写法：${expression.ifEmpty { "(空)" }}\n    本实现：$mine\n    参考实现：$theirs"
                )
            }
        }

        val report = File("build/oracle-diff.txt")
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("骰点表达式差分比对：共 ${corpus.size} 条")
                appendLine("  一致（含基线内的差异）：${same.size} 条")
                appendLine("  基线外差异：${unexpected.size} 条")
                appendLine("  基线里已不再差异：${baselineButSame.size} 条")
                appendLine("  基准：$oracleDir")
                appendLine()
                if (unexpected.isNotEmpty()) {
                    appendLine("── 基线外的差异（每条都要么修掉、要么写进基线并说明理由）──")
                    unexpected.forEach { appendLine(it) }
                    appendLine()
                }
                if (baselineButSame.isNotEmpty()) {
                    appendLine("── 基线里已不再差异（可从基线删掉）──")
                    baselineButSame.forEach { appendLine("  ${it.ifEmpty { "(空)" }}") }
                    appendLine()
                }
                appendLine("── 基线（已知且接受的差异）──")
                KNOWN_DIFFERENCES.forEach { (expression, why) ->
                    appendLine("  ${expression.ifEmpty { "(空)" }}  ←  $why")
                }
            },
            Charsets.UTF_8
        )
        println("差分报告：${report.absolutePath}（基线外差异 ${unexpected.size} 条）")

        assertTrue(same.isNotEmpty(), "一条都没比对上，基准多半没加载成功")
        assertTrue(
            unexpected.isEmpty(),
            "出现基线外的差异，逐条看 build/oracle-diff.txt：\n" + unexpected.joinToString("\n")
        )
    }


    private fun describeMine(expression: String): String = try {
        val result = Roll(expression, DiceConfigs(1_000, true, ZeroRandom())).start()
        "OK  值=${result.resInt} 区间=[${result.resIntMin},${result.resIntMax}] 明细=${result.resDetail}"
    } catch (e: Throwable) {
        "ERR ${(e as? onedice.defines.ResErrorException)?.type ?: e::class.simpleName}"
    }


    private fun describeOracle(
        expression: String,
        rollConstructor: java.lang.reflect.Constructor<*>,
        startMethod: java.lang.reflect.Method,
        configs: Any
    ): String = try {
        val roll = rollConstructor.newInstance(expression, configs)
        val result = startMethod.invoke(roll)!!
        "OK  值=${result.read("getResInt")} 区间=[${result.read("getResIntMin")},${result.read("getResIntMax")}]" +
            " 明细=${result.read("getResDetail")}"
    } catch (e: Throwable) {
        val cause = e.cause ?: e
        val type = try {
            cause.javaClass.getMethod("getType").invoke(cause)?.toString() ?: "?"
        } catch (t: Throwable) {
            cause.javaClass.simpleName
        }
        "ERR $type"
    }

    private fun Any.read(getter: String): Any? = javaClass.getMethod(getter).invoke(this)

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }
}
