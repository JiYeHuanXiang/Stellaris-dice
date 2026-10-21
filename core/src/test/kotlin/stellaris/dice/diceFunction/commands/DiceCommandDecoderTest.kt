package stellaris.dice.diceFunction.commands

import stellaris.core.defines.DiceException
import stellaris.core.defines.ResErrorException
import stellaris.core.defines.ResErrorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test















class DiceCommandDecoderTest {

    private fun route(input: String, vararg extras: String): DiceCommandDecision? =
        DiceCommandRouter.route(input, extras.toList())



    @Test
    fun `指令表 按长度最长优先`() {

        assertEquals("helpdoc", route("helpdoc")?.command)
        assertEquals("help", route("help")?.command)

        assertEquals("dxx", route("dxx")?.command)
        assertEquals("dx", route("dx")?.command)
        assertEquals("ww", route("ww")?.command)
        assertEquals("w", route("w")?.command)

        assertEquals("rab", route("rab 侦查")?.command)
        assertEquals("ra", route("ra 侦查")?.command)
        assertEquals("range", route("range 3d6")?.command, "range 不被 r 抢走")
        assertEquals("ri", route("ri")?.command)
    }

    @Test
    fun `指令名 忽略大小写`() {
        assertEquals("r", route("R 3d6")?.command)
        assertEquals("range", route("RANGE 3d6")?.command)
        assertEquals("3d6", route("R 3d6")?.param)
    }


    @Test
    fun `参数 取指令词之后剩下的原文`() {
        assertEquals("3d6+2", route("r 3d6+2")?.param)
        assertEquals("3d6+2", route("r3d6+2")?.param, "没有空格也要认")
        assertEquals("", route("r")?.param)
        assertEquals("10", route("w10")?.param)
        assertEquals("50 理由", route("r 50 理由")?.param, "理由属于参数，分发器不拆它")
        assertEquals("wx", route("wxyz")?.command, "最长前缀优先：wx 是已注册指令")
        assertEquals("yz", route("wxyz")?.param, "wx 之后剩下的才是参数")
    }

    @Test
    fun `受保护指令 只有 dismiss`() {
        assertTrue(route("dismiss")!!.isProtected)
        assertFalse(route("r 3d6")!!.isProtected)
        assertTrue(DiceCommandDecoder.isProtected("dismiss"))
        assertFalse(DiceCommandDecoder.isProtected("bot"))
    }

    @Test
    fun `认不出来的输入 返回 null`() {
        assertNull(route("随便聊聊"))
        assertNull(route(""))
        assertNull(route("不存在的指令词"))
        assertFalse(DiceCommandRouter.isCommand("随便聊聊"))
    }







    @Test
    fun `宿主侧指令 在内核里会落到同前缀指令上`() {
        assertEquals("st", route("stella list")?.command)
        assertEquals("ella list", route("stella list")?.param)
    }







    @Test
    fun `三条无处理器的伪指令已移除`() {
        val table = DiceCommandDecoder.registeredCommands()
        assertTrue(!table.contains("warning"), "G1-001 移除")
        assertTrue(!table.contains("heartbeat"), "G1-002 移除（测试性指令）")
        assertTrue(!table.contains("diff"), "G1-078 移除")
        assertTrue(!table.contains("link"), "G1-079 移除")





        assertEquals("w", route("warning")?.command)
        assertEquals("li", route("link")?.command)
        assertEquals("h", route("heartbeat")?.command)
        assertNull(route("diff"), "没有同前缀指令，认不出来")
    }






    @Test
    fun `sn 系列有内核兜底提示`() {
        val table = DiceCommandDecoder.registeredCommands()
        assertTrue(table.contains("sn"))
        assertTrue(table.contains("setasn"))
        assertTrue(table.contains("setcoc"), "setcoc 是内置的，别一起删了")
    }


    @Test
    fun `www 保留在表内`() {
        assertEquals("www", route("www")?.command)
        assertTrue(DiceCommandDecoder.registeredCommands().contains("www"))
    }



    @Test
    fun `help draw 改写为 draw help`() {
        val decision = route("helpdraw 牌堆")!!
        assertEquals("draw", decision.command)
        assertEquals("help 牌堆", decision.param)
        assertTrue(decision.rewritten, "要如实反映这句话被改写过了")
    }

    @Test
    fun `hl数字 改写为暗检定`() {
        assertEquals("rah", route("hl30")?.command)
        assertEquals("30", route("hl30")?.param)
    }











    @Test
    fun `ra rc 的参数原样交给响应层 不在这里改写`() {
        assertEquals("ra", route("ra50斗殴")?.command)
        assertEquals("50斗殴", route("ra50斗殴")?.param)
        assertEquals("rc", route("rc50斗殴")?.command)
        assertEquals("50斗殴", route("rc50斗殴")?.param)

        assertEquals("ra", route("ra 斗殴50")?.command)
        assertEquals("斗殴50", route("ra 斗殴50")?.param)
    }


    @Test
    fun `rb rp 是一等指令 不再改写成检定`() {
        assertEquals("rb", route("rb手枪50")?.command)
        assertEquals("手枪50", route("rb手枪50")?.param)
        assertEquals("rp", route("rp手枪50")?.command)

        assertEquals("rp", route("rp3#2手枪50")?.command)
        assertEquals("3#2手枪50", route("rp3#2手枪50")?.param)

        assertEquals("rp", route("rp50")?.command)
        assertEquals("50", route("rp50")?.param)
    }







    @Test
    fun `扩展指令 rb rp 同池参与匹配 不再走 legacy 改写`() {
        val decision = route("rb手枪50", "rb", "rp")!!
        assertEquals("rb", decision.command)
        assertEquals("手枪50", decision.param)
        assertFalse(decision.rewritten)
        assertEquals("rp", route("rp50", "rb", "rp")?.command)
    }

    @Test
    fun `rb10 直接作为一等指令处理`() {
        assertEquals("rb", route("rb10")?.command)
        assertEquals("10", route("rb10")?.param)
    }

    @Test
    fun `hp 的改写态是关闭的 与既有行为一致`() {

        assertEquals("hp", route("hp50")?.command)
        assertEquals("50", route("hp50")?.param)

        assertEquals("st", route("san50")?.command)
        assertEquals("san50", route("san50")?.param)
        assertEquals("st", route("san")?.command)
        assertEquals("show san", route("san")?.param, "裸 san 是 show")
        assertEquals("st", route("mp")?.command)
        assertEquals("st", route("sp50")?.command)
    }

    @Test
    fun `卡相关的旧写法`() {
        assertEquals("pc", route("nnshow")?.command)
        assertEquals("list", route("nnshow")?.param)
        assertEquals("pc", route("stlist")?.command)
        assertEquals("list", route("stlist")?.param)
        assertEquals("nn", route("strename 新名")?.command)
        assertEquals("新名", route("strename 新名")?.param)
        assertEquals("pc", route("strm 卡名")?.command)
        assertEquals("del 卡名", route("strm 卡名")?.param)
        assertEquals("pc", route("del 卡名")?.command)
        assertEquals("del 卡名", route("del 卡名")?.param)
    }

    @Test
    fun `系统与群开关的旧写法`() {
        assertEquals("system", route("drawreload")?.command)
        assertEquals("load", route("drawreload")?.param)
        assertEquals("runtime", route("systemstate")?.command)
        assertEquals("draw", route("gas")?.command)
        assertEquals("煤气灯", route("gas")?.param)

        assertEquals("bot", route("group+停用指令")?.command)
        assertEquals("off", route("group+停用指令")?.param)
        assertEquals("reply", route("group-禁用回复")?.command)
        assertEquals("on this", route("group-禁用回复")?.param)
        assertEquals("ob", route("group+禁用ob")?.command)
        assertEquals("off", route("group+禁用ob")?.param)
    }

    @Test
    fun `改写只做一层 不会无端连环`() {

        val decision = route("strename 甲")!!
        assertEquals("nn", decision.command)
        assertTrue(decision.rewritten)
    }



    @Test
    fun `回调的返回值 决定已消费与否`() {
        assertEquals(true, dispatch("r 3d6") { _, _, _ -> true })
        assertEquals(false, dispatch("r 3d6") { _, _, _ -> false })
        assertEquals(false, dispatch("认不出来的话") { _, _, _ -> true }, "没命中就没人消费")
    }

    @Test
    fun `业务异常 交给 onException`() {
        val seen = ArrayList<ResErrorType>()
        val consumed = DiceCommandDecoder.invoke(
            object : DiceCommandCallback {
                override fun onException(exception: DiceException) {
                    seen.add((exception as ResErrorException).type)
                }

                override fun onCommand(command: String, param: String, isProtected: Boolean): Boolean {
                    throw ResErrorException(ResErrorType.NODE_NONSENSE, "参数不对")
                }
            },
            "r 3d6"
        )
        assertEquals(listOf(ResErrorType.NODE_NONSENSE), seen)
        assertFalse(consumed, "抛了业务异常就不算消费")
    }


    @Test
    fun `非业务异常 被吞下并返回未消费`() {
        val consumed = DiceCommandDecoder.invoke(
            object : DiceCommandCallback {
                override fun onException(exception: DiceException) = Unit

                override fun onCommand(command: String, param: String, isProtected: Boolean): Boolean {
                    throw NullPointerException("模拟内核缺陷")
                }
            },
            "r 3d6"
        )
        assertFalse(consumed)
    }

    @Test
    fun `扩展指令 与内置表同池排序`() {
        val decision = route("setdg on", "setdg")!!
        assertEquals("setdg", decision.command)
        assertEquals("on", decision.param)
    }

    @Test
    fun `非法插件指令名被忽略且内置名优先`() {
        val seen = ArrayList<Pair<String, String>>()
        val callback = object : DiceCommandCallback {
            override fun onException(exception: DiceException) = Unit

            override fun onCommand(command: String, param: String, isProtected: Boolean): Boolean {
                seen.add(command to param)
                return true
            }

            override fun extraCommands(): ArrayList<String> =
                arrayListOf("", "bad command", "R", "plugin")
        }

        assertTrue(DiceCommandDecoder.invoke(callback, "plugin 参数"))
        assertEquals(listOf("plugin" to "参数"), seen)

        seen.clear()
        assertFalse(DiceCommandDecoder.invoke(callback, "bad command"), "含空白的指令名不应进入匹配表")
        assertFalse(DiceCommandDecoder.invoke(callback, "x"), "空指令名不应吞掉普通文本")

        seen.clear()
        assertTrue(DiceCommandDecoder.invoke(callback, "R 3d6"))
        assertEquals(listOf("r" to "3d6"), seen, "忽略大小写的重复项保留内置指令")
    }

    @Test
    fun `内置指令查询忽略大小写`() {
        assertTrue(DiceCommandDecoder.isBuiltInCommand("R"))
        assertTrue(DiceCommandDecoder.isBuiltInCommand("SeTdG"))
        assertFalse(DiceCommandDecoder.isBuiltInCommand("plugin"))
    }

    @Test
    fun `指令表快照 含关键指令且去重`() {
        val table = DiceCommandDecoder.registeredCommands()
        assertTrue(table.contains("r"))
        assertTrue(table.contains("robot"), "bot 与 robot 是两个词")
        assertTrue(table.contains("nolawnosky"))
        assertEquals(table.size, table.distinctBy { it.lowercase() }.size, "注册表按忽略大小写去重")
        assertNotNull(route("robot"))
    }

    private fun dispatch(input: String, answer: (String, String, Boolean) -> Boolean): Boolean =
        DiceCommandDecoder.invoke(
            object : DiceCommandCallback {
                override fun onException(exception: DiceException) = Unit
                override fun onCommand(command: String, param: String, isProtected: Boolean): Boolean =
                    answer(command, param, isProtected)
            },
            input
        )
}
