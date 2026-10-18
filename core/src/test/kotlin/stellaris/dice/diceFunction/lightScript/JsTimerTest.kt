package stellaris.dice.diceFunction.lightScript

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZonedDateTime

class JsTimerTest {

    @TempDir
    lateinit var root: File

    private class FakeHost : PluginHost {
        val sent = ArrayList<String>()
        override fun setGroupCard(user: String, text: String, group: String): Boolean = true
        override fun configureAutoName(
            user: String,
            group: String,
            format: String,
            enabled: Boolean
        ): AutoNameReceipt = AutoNameReceipt("卡", enabled, format)

        override fun syncAutoName(user: String, group: String): Boolean = true
        override fun sendGroupMessage(text: String, group: String) {
            sent.add("$group:$text")
        }

        override fun sendPrivateMessage(text: String, userId: String) {
            sent.add("$userId:$text")
        }

        override fun roll(expression: String): Int = 42
        override fun draw(group: String, name: String): String? = "draw"
        override fun setGroupTitle(title: String, userId: String, group: String): Boolean = true
        override fun setGroupWholeBan(ban: Boolean, group: String): Boolean = true
        override fun reloadPlugins() {}
    }

    private fun write(
        toml: String
    ) {
        val dir = File(root, "custom")
        dir.mkdirs()
        File(dir, "js.toml").writeText(toml.trimIndent(), Charsets.UTF_8)
    }

    @Test
    fun `program 里的执行脚本能调用 Lib`() {
        write(
            """
            [config]
            mood = "开心"

            [[define]]
            name = "常量值"
            content = "常量"

            [[auto]]
            keywordFull = "脚本"
            program = "【执行脚本 Lib.reply('脚本回复')】"

            [[auto]]
            keywordFull = "取值"
            program = "【执行脚本 Lib.getConstant('常量值') + '/' + Lib.getConfig('mood') + '/' + Lib.getSender()】"
            """.trimIndent()
        )
        val host = FakeHost()
        val loader = PluginLoader(root.path).apply {
            this.host = host
            reload()
        }
        assertEquals(null, loader.matchAuto("脚本", "10001", "20002", true))
        assertTrue(host.sent.contains("20002:脚本回复"), host.sent.toString())

        val value = loader.matchAuto("取值", "10001", "20002", true)
        assertEquals("常量/开心/10001", value?.content)
    }

    @Test
    fun `六域 cron 匹配与定时任务执行`() {
        write(
            """
            [[timer]]
            name = "每分钟"
            expression = "* * * * * *"
            js = "Lib.sendGroupMessage('tick','123')"
            """.trimIndent()
        )
        val host = FakeHost()
        val loader = PluginLoader(root.path).apply {
            this.host = host
            reload()
        }

        loader.tickTimers(ZonedDateTime.of(2026, 10, 19, 3, 0, 0, 0, java.time.ZoneId.systemDefault()))
        assertTrue(host.sent.contains("123:tick"), host.sent.toString())

        host.sent.clear()
        loader.tickTimers(ZonedDateTime.of(2026, 10, 19, 3, 0, 0, 0, java.time.ZoneId.systemDefault()))
        assertTrue(host.sent.isEmpty(), "同一秒不应重复触发：${host.sent}")
    }

    @Test
    fun `cron 支持范围 列表 与步长`() {
        val now = ZonedDateTime.of(2026, 10, 19, 3, 30, 0, 0, java.time.ZoneId.systemDefault())
        assertTrue(CronSchedule.matches("0 30 3 * * *", now))
        assertTrue(CronSchedule.matches("0 0/10 3-4 * * *", now))
        assertTrue(CronSchedule.matches("0 30 3 19 10 1", now), "2026-10-19 是周一")
        assertTrue(!CronSchedule.matches("1 30 3 * * *", now))
        assertTrue(!CronSchedule.matches("0 30 3 * * 2", now))

        assertTrue(CronSchedule.matches("0 30 3 * * 1-5", now), "星期范围")
        assertTrue(CronSchedule.matches("0 30 3 * * 1/2", now), "星期步长从周一算起")
        assertTrue(CronSchedule.matches("0 30 3 1-31/2 * *", now), "日期范围加步长")
        assertTrue(!CronSchedule.matches("0 30 3 * * 0", now), "周日不是周一")

        val sunday = ZonedDateTime.of(2026, 10, 18, 3, 30, 0, 0, java.time.ZoneId.systemDefault())
        assertTrue(CronSchedule.matches("0 30 3 * * 7", sunday), "7 等价周日")
        assertTrue(!CronSchedule.matches("0 30 3 * * 6", sunday))
    }
}
