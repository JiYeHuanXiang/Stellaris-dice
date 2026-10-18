package stellaris.dice.diceFunction.template

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.random.RandomService
import java.util.Random








class DiceMessageTemplateTest {


    private class ScriptedRandom(private val units: List<Long>) : Random() {
        private var cursor = 0
        override fun nextLong(): Long {
            val unit = units[cursor % units.size]
            cursor++
            return unit shl 1
        }
    }

    @AfterEach
    fun restore() {
        RandomService.installTestProvider(null)
    }



    @Test
    fun `替换已登记的键`() {
        val template = DiceMessageTemplate()
        template.add("player", "甲")
        template.add("num", "7")
        assertEquals("甲 掷出 7", template.buildAll("{player} 掷出 {num}"))
    }


    @Test
    fun `未登记的键原样保留`() {
        val template = DiceMessageTemplate()
        assertEquals("{nope}", template.buildAll("{nope}"))
        assertEquals("甲{nope}", DiceMessageTemplate().apply { add("player", "甲") }.buildAll("{player}{nope}"))
    }


    @Test
    fun `嵌套键`() {
        val template = DiceMessageTemplate()
        template.add("a", "x{b}y")
        template.add("b", "Z")
        assertEquals("xZy", template.buildAll("{a}"))
    }






    @Test
    fun `互指的键不会死循环`() {
        val template = DiceMessageTemplate()
        template.add("a", "{b}")
        template.add("b", "{a}")
        assertEquals("{a}", template.buildAll("{a}"))
    }




    @Test
    fun `转义变体键 html_`() {
        val template = DiceMessageTemplate()
        template.add("roll", "<甲> & [乙]")
        assertEquals("<甲> & [乙]", template.buildAll("{roll}"))
        assertEquals("&#60;甲&#62; &#38; &#91;乙&#93;", template.buildAll("{html_roll}"))
    }


    @Test
    fun `还原反斜杠转义`() {
        val literal = DiceMessageTemplate().apply { add("reason", "第一行\\n第二行") }
        assertEquals("第一行\\n第二行", literal.buildAll("{reason}"), "没声明 unescape 就原样")

        val unescaped = DiceMessageTemplate().apply {
            add("reason", "第一行\\n第二行").unescape = true
        }
        assertEquals("第一行\n第二行", unescaped.buildAll("{reason}"))
    }


    @Test
    fun `就地掷骰 decodeXdX`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(2)))
        val template = DiceMessageTemplate().apply {
            add("detail", "点数 [1d6] 点").decodeXdX = true
        }
        assertEquals("点数 3 点", template.buildAll("{detail}"))
    }


    @Test
    fun `方括号里不是骰式则原样`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val template = DiceMessageTemplate().apply {
            add("detail", "【无关】与 [不是骰式]").decodeXdX = true
        }
        assertEquals("【无关】与 [不是骰式]", template.buildAll("{detail}"))
    }




    @Test
    fun `强制补尾与补头`() {
        val tail = DiceMessageTemplate().apply {
            add("roll", "结果").addTailIfNotExist = true
        }
        assertEquals("开场结果 ", tail.buildAll("开场"))

        val head = DiceMessageTemplate().apply {
            add("prefix", "[前缀]").addHeadIfNotExist = true
        }
        assertEquals("[前缀]正文 ", head.buildAll("正文"))
    }


    @Test
    fun `引用过就不重复补`() {
        val template = DiceMessageTemplate().apply {
            add("roll", "结果").addTailIfNotExist = true
        }
        assertEquals("结果", template.buildAll("{roll}"))
    }



    @Test
    fun `内联调用 用井号加花括号的写法`() {
        val template = DiceMessageTemplate()
        template.diceMessageTemplateInvoke = object : DiceMessageTemplateInvoke {
            override fun invoke(template: DiceMessageTemplate, command: String, param: String): String? =
                if (command == "hello") "HI $param" else null
        }
        assertEquals("HI 世界", template.buildAll("#{hello-世界}"))

        assertEquals("#{unknown-x}", template.buildAll("#{unknown-x}"))
    }


    @Test






    fun `内联调用的参数与结果都会渲染`() {
        val template = DiceMessageTemplate()
        template.add("who", "甲")
        template.diceMessageTemplateInvoke = object : DiceMessageTemplateInvoke {
            override fun invoke(template: DiceMessageTemplate, command: String, param: String): String? =
                "$command($param){who}"
        }
        assertEquals("say(世界)甲", template.buildAll("#{say-世界}"))
    }


    @Test
    fun `未注入内联口则原样`() {
        assertEquals("#{x-y}", DiceMessageTemplate().buildAll("#{x-y}"))
    }



    @Test
    fun `增删查`() {
        val template = DiceMessageTemplate()
        val key = template.add("a", "1")
        assertEquals("a", key.keyword)
        assertEquals("{a}", key.toString())
        assertEquals("1", template.get("a"))
        assertNotNull(template.find("a"))
        assertNotNull(template.find("html_a"), "转义键名也能找到同一个键")
        assertTrue(template.remove("a"))
        assertFalse(template.remove("a"), "已经摘掉了")
        assertNull(template.find("a"))
        assertEquals("", template.get("a"))
    }


    @Test
    fun `空值退回默认值`() {
        val template = DiceMessageTemplate()
        template.add("a", "", "兜底")
        assertEquals("兜底", template.get("a"))

        template.add("b", null, null)
        assertNull(template.find("b"))
        assertEquals("{b}", template.buildAll("{b}"))
    }

    @Test
    fun `清空`() {
        val template = DiceMessageTemplate().apply { add("a", "1") }
        template.removeAll()
        assertEquals("{a}", template.buildAll("{a}"))
    }



    @Test
    fun `htmlEscape 的字符集`() {
        assertEquals("&#60;&#62;&#34;&#39;&#38;&#91;&#93;&#58;&#44;&#92;&#10;&#35;&#42;",
            MessageCodec.htmlEscape("< >\"'&[]:,\\\n#*".replace(" ", "")))

        assertEquals("&#10;", MessageCodec.htmlEscape(DiceConstant.USER_SPLIT_STRING))
        assertEquals("&#10;", MessageCodec.htmlEscape(DiceConstant.SYSTEM_SPLIT_STRING))
        assertEquals("普通文本", MessageCodec.htmlEscape("普通文本"))
    }

    @Test
    fun `decodeToContent 还原反斜杠转义`() {
        assertEquals("第一行\n第二行", "第一行\\n第二行".decodeToContent())
        assertEquals("甲t乙", "甲\\t乙".decodeToContent(), "只有 \\n 与 \\r 是特殊转义，其余一律吃掉反斜杠")
        assertEquals("回车\r", "回车\\r".decodeToContent())
        assertEquals("结尾反斜杠", "结尾反斜杠\\".decodeToContent(), "末尾孤反斜杠丢弃")
        assertEquals("", "".decodeToContent())
    }
}
