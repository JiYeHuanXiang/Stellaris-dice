package stellaris.dice.diceFunction.responder

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.RuleMode
import stellaris.dice.diceFunction.card.CardAbilities
import stellaris.dice.diceFunction.random.RandomService
import stellaris.dice.diceFunction.template.DiceMessageTemplate
import java.util.Random

class RuleModeTest {

    private class ScriptedRandom(private val units: List<Long>) : Random() {
        private var cursor = 0
        override fun nextLong(): Long = units[cursor++ % units.size] shl 1
    }

    private val card = object : CardAbilities {
        private val values = mapOf("侦查" to 60)
        override fun readProperty(name: String): Int = values[name] ?: 0
        override fun hasProperty(name: String): Boolean = values.containsKey(name)
    }

    @AfterEach
    fun restore() {
        RandomService.installTestProvider(null)
    }

    private fun context(
        param: String,
        mode: RuleMode,
        withCard: Boolean = false
    ): RespondContext {
        val template = DiceMessageTemplate()
        RespondContext.seedTemplate(template, "甲", "10001", "20002")
        return RespondContext(
            param = param,
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = template,
            sentences = DefaultSentences,
            defaultDiceNumber = 100,
            card = if (withCard) card else CardAbilities.EMPTY,
            ruleMode = mode
        )
    }

    private fun render(context: RespondContext, rule: RollRules = RollRules.R): String =
        context.template.buildAll(
            if (rule.isCheck) DiceResponder.check(context, rule) else DiceResponder.r(context, rule)
        )

    @Test
    fun `各体系裸 r 使用各自默认骰`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertTrue(render(context("", RuleMode.DND5E)).contains("1d20="), "DND 默认 d20")
        assertTrue(render(context("", RuleMode.DND5R)).contains("1d20="), "DND 5R 默认 d20")
        assertTrue(render(context("", RuleMode.DG)).contains("1d100="), "DG 默认 d100")
        assertTrue(render(context("", RuleMode.FATE)).contains("f="), "FATE 默认 4dF")
        assertTrue(render(context("", RuleMode.DX)).contains("1c10="), "DX 默认 1c10")
        assertTrue(render(context("", RuleMode.WX)).contains("1a10="), "WX 默认 1a10")
        assertTrue(render(context("", RuleMode.CPR)).contains("1d10="), "CPR 默认 1d10")
    }

    @Test
    fun `裸 d 按体系换成对应骰子项`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertTrue(render(context("3d", RuleMode.DND5E)).contains("3d20"), "DND 3d → 3d20")
        assertTrue(render(context("3d", RuleMode.DX)).contains("3c10"), "DX 3d → 3c10")
        assertTrue(render(context("3d", RuleMode.WX)).contains("3a10"), "WX 3d → 3a10")
    }

    @Test
    fun `DND 5e 检定掷高且 20 大成功`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(19)))
        val text = render(context("15", RuleMode.DND5E), RollRules.RA)
        assertTrue(text.contains("1d20=20/15"), text)
        assertTrue(text.contains("大成功"), text)
    }

    @Test
    fun `DND 5R 检定掷高且 20 大成功`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(19)))
        val text = render(context("15", RuleMode.DND5R), RollRules.RA)
        assertTrue(text.contains("1d20=20/15"), text)
        assertTrue(text.contains("大成功"), text)
    }

    @Test
    fun `DND 5R 力竭每级减 2`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(11)))
        val exhausted = object : CardAbilities {
            override fun readProperty(name: String): Int = if (name == "力竭") 2 else 0
            override fun hasProperty(name: String): Boolean = name == "力竭"
        }
        val template = DiceMessageTemplate()
        RespondContext.seedTemplate(template, "甲", "10001", "20002")
        val context = RespondContext(
            param = "10",
            isGroupMsg = true,
            groupId = "20002",
            senderId = "10001",
            senderNick = "甲",
            template = template,
            sentences = DefaultSentences,
            defaultDiceNumber = 100,
            card = exhausted,
            ruleMode = RuleMode.DND5R
        )
        val text = render(context, RollRules.RA)
        assertTrue(text.contains("1d20=12/14"), "力竭 2 级：目标 10 → 14：$text")
        assertTrue(text.contains("失败"), text)
    }

    @Test
    fun `DND 5e 奖励骰是优势 2d20 取高`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 19)))
        val text = render(context("15", RuleMode.DND5E), RollRules.RAB)
        assertTrue(text.contains("2d20k1"), text)
        assertTrue(text.contains("成功"), text)
    }

    @Test
    fun `DG 奖励两个及以上折算目标值加 40`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val text = render(context("2 50", RuleMode.DG), RollRules.RAB)
        assertTrue(text.contains("/50+(40)"), text)
        assertTrue(text.contains("大成功"), text)
    }

    @Test
    fun `DG 惩罚一个折算目标值减 20`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        val text = render(context("1 50", RuleMode.DG), RollRules.RAP)
        assertTrue(text.contains("/50+(-20)"), text)
    }

    @Test
    fun `FATE 奖励两个把难度减 4`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(3, 3, 3, 3)))
        val text = render(context("2 2", RuleMode.FATE), RollRules.RAB)
        assertTrue(text.contains("/2+(-4)"), text)
    }

    @Test
    fun `DX 与 WX 奖励骰折算为池大小`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0, 0, 0)))
        assertTrue(
            render(context("2 2", RuleMode.DX), RollRules.RAB).contains("4c10="),
            "DX 池 2 + 2"
        )
        assertTrue(
            render(context("2 2", RuleMode.WX), RollRules.RAB).contains("4a10="),
            "WX 池 2 + 2"
        )
    }

    @Test
    fun `rb rp 在 COC 是普通奖惩骰 在 DG 给提示`() {
        RandomService.installTestProvider(ScriptedRandom(listOf(0)))
        assertTrue(render(context("b2", RuleMode.COC)).contains("奖励d100"), "COC .rb 2")
        assertTrue(
            render(context("b2", RuleMode.DG)).contains("没有奖惩骰"),
            "DG .rb 2 应提示等价写法"
        )
    }
}
