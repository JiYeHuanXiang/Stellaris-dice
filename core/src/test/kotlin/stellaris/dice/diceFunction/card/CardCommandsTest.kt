package stellaris.dice.diceFunction.card

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import stellaris.dice.diceFunction.DiceConstant
import java.nio.file.Path










class CardCommandsTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var manager: PlayerCardManager

    @BeforeEach
    fun setUp() {
        manager = PlayerCardManager(tempDir.toString())
    }

    private fun commands(group: String = GROUP): CardCommands = CardCommands(manager, OWNER, group)




    @Test
    fun `建卡 解析卡型并切为当前`() {
        val outcome = commands().run("new 夜刃 dnd5e")
        assertTrue(outcome is CardCommands.Outcome.Created, "$outcome")
        val card = (outcome as CardCommands.Outcome.Created).card
        assertEquals("夜刃", card.playerName)
        assertEquals(PlayerCardData.RuleType.DND5E, card.rule)
        assertEquals("夜刃", manager.getCurrentPlayer(OWNER, GROUP).playerName, "建完就该是当前卡")

        assertEquals(
            PlayerCardData.RuleType.COC7,
            (commands().run("new 无名") as CardCommands.Outcome.Created).card.rule,
            "不写规则时缺省 COC7"
        )
    }


    @Test
    fun `九种卡型的指令词都认`() {
        listOf("coc6", "coc7", "dnd3r", "dnd5e", "dx", "fate", "dg", "wx", "cpr").forEach { word ->
            val parsed = PlayerCardData.RuleType.parse(word)
            assertTrue(parsed != null, "「$word」该被认出来")
            assertEquals(word, parsed!!.word)
        }
        assertEquals(PlayerCardData.RuleType.COC7, PlayerCardData.RuleType.parse("COC7"), "不区分大小写")
        assertNull(PlayerCardData.RuleType.parse("gurps"))
    }


    @Test
    fun `建卡参数不对时给用法`() {
        assertEquals(
            CardCommands.Reason.USAGE,
            (commands().run("new") as CardCommands.Outcome.Failed).reason
        )
        assertEquals(
            CardCommands.Reason.USAGE,
            (commands().run("new 甲 gurps") as CardCommands.Outcome.Failed).reason
        )
    }


    @Test
    fun `同名卡不覆盖`() {
        commands().run("new 甲")
        val outcome = commands().run("new 甲") as CardCommands.Outcome.Failed
        assertEquals(CardCommands.Reason.EXISTS, outcome.reason)
        assertEquals("甲", outcome.detail)
        assertEquals(1, manager.countPlayers(OWNER))
    }


    @Test
    fun `卡名过长与卡数上限`() {
        val tooLong = "一".repeat(DiceConstant.MAX_PLAYER_LENGTH + 1)
        assertEquals(
            CardCommands.Reason.NAME_TOO_LONG,
            (commands().run("new $tooLong") as CardCommands.Outcome.Failed).reason
        )
        repeat(DiceConstant.MAX_CARD_COUNT) { index -> commands().run("new 卡$index") }
        assertEquals(DiceConstant.MAX_CARD_COUNT, manager.countPlayers(OWNER))
        assertEquals(
            CardCommands.Reason.TOO_MANY_CARDS,
            (commands().run("new 再来一张") as CardCommands.Outcome.Failed).reason
        )
    }




    @Test
    fun `列卡`() {

        val empty = CardCommands(PlayerCardManager(tempDir.resolve("empty").toString()), OWNER, GROUP)
        val emptyOutcome = empty.run("list") as CardCommands.Outcome.Listed
        assertTrue(emptyOutcome.cards.isEmpty())

        commands().run("new 甲")
        commands().run("new 乙")
        commands().run("grp 甲")
        val outcome = commands().run("list") as CardCommands.Outcome.Listed
        assertEquals(listOf("甲", "乙"), outcome.cards.map { it.playerName })
        assertEquals("甲", outcome.currentName)
    }


    @Test
    fun `看卡`() {
        commands().run("new 甲")
        commands().run("new 乙")
        assertEquals(
            "乙",
            (commands().run("show") as CardCommands.Outcome.Shown).card.playerName,
            "不给名字 = 当前卡（最后建的「乙」）"
        )
        assertEquals("甲", (commands().run("show 甲") as CardCommands.Outcome.Shown).card.playerName)
        assertEquals(
            CardCommands.Reason.NO_SUCH_CARD,
            (commands().run("show 丙") as CardCommands.Outcome.Failed).reason
        )
    }




    @Test
    fun `切卡按群分开`() {
        commands().run("new 甲")
        commands().run("new 乙")
        assertEquals("乙", manager.getCurrentPlayer(OWNER, GROUP).playerName)


        CardCommands(manager, OWNER, OTHER_GROUP).run("grp 乙")

        assertEquals(
            "甲",
            (commands().run("grp 甲") as CardCommands.Outcome.Bound).card.playerName
        )
        assertEquals("甲", manager.currentPlayerName(OWNER, GROUP))
        assertEquals("乙", manager.currentPlayerName(OWNER, OTHER_GROUP), "别的群各绑各的")


        val bindings = (commands().run("grp") as CardCommands.Outcome.BindingsListed).bindings
        assertEquals("甲", bindings[GROUP])
        assertEquals("乙", bindings[OTHER_GROUP])
        assertEquals(
            CardCommands.Reason.NO_SUCH_CARD,
            (commands().run("grp 丙") as CardCommands.Outcome.Failed).reason
        )

        assertTrue(commands().run("grp clr") is CardCommands.Outcome.BindingsCleared)
        assertEquals(0, manager.getBindings(OWNER).size)
    }


    @Test
    fun `tag 绑卡与解绑`() {
        commands().run("new 甲")
        commands().run("new 乙")
        assertEquals(
            "甲",
            (commands().run("tag 甲") as CardCommands.Outcome.Bound).card.playerName
        )
        assertEquals("甲", manager.currentPlayerName(OWNER, GROUP))

        assertTrue(commands().run("tag") is CardCommands.Outcome.Unbound)
        assertEquals(null, manager.currentPlayerName(OWNER, GROUP), "解绑后回默认卡")

        assertEquals(
            CardCommands.Reason.NO_SUCH_CARD,
            (commands().run("tag 丙") as CardCommands.Outcome.Failed).reason
        )
    }


    @Test
    fun `pc nn 改名与重名`() {
        commands().run("new 甲")
        commands().run("new 乙")

        val renamed = commands().run("nn 丙") as CardCommands.Outcome.Renamed
        assertEquals("乙", renamed.from)
        assertEquals("丙", renamed.to)
        assertTrue(manager.verifyPlayerExistence(OWNER, "丙"))
        assertFalse(manager.verifyPlayerExistence(OWNER, "乙"))

        assertEquals(
            CardCommands.Reason.EXISTS,
            (commands().run("nn 甲") as CardCommands.Outcome.Failed).reason
        )
        assertEquals(
            CardCommands.Reason.USAGE,
            (commands().run("nn") as CardCommands.Outcome.Failed).reason
        )
    }


    @Test
    fun `pc clr 清空全部卡`() {
        commands().run("new 甲")
        commands().run("new 乙")
        assertTrue(commands().run("clr") is CardCommands.Outcome.AllCleared)
        assertEquals(0, manager.getPlayers(OWNER).size)
        assertEquals(0, manager.getBindings(OWNER).size)
    }


    @Test
    fun `show 阈值`() {
        commands().run("new 甲")
        val card = manager.getCurrentPlayer(OWNER, GROUP)
        card.setProperty("力量", 70)
        card.setProperty("体质", 40)
        manager.savePlayer(card)

        val outcome = commands().run("show 50") as CardCommands.Outcome.ShownAbove
        assertEquals(50, outcome.threshold)
        assertEquals("甲", outcome.card.playerName)
    }




    @Test
    fun `复制卡 带内容且相互独立`() {
        commands().run("new 源 dnd5e")
        val source = manager.getCurrentPlayer(OWNER, GROUP)
        source.setProperty("侦查", 70)
        manager.savePlayer(source)

        val outcome = commands().run("cpy 目标") as CardCommands.Outcome.Copied
        assertEquals("目标", outcome.to)
        assertEquals("源", outcome.from, "只给一个名字时源是当前卡")

        val target = manager.getPlayers(OWNER).first { it.playerName == "目标" }
        assertEquals(70, target.readProperty("侦查"), "属性要带过来")
        assertEquals(PlayerCardData.RuleType.DND5E, target.rule, "卡型要带过来")

        target.setProperty("侦查", 10)
        manager.savePlayer(target)
        assertEquals(70, source.readProperty("侦查"), "改副本不该动到源")
    }


    @Test
    fun `复制卡的边界`() {
        commands().run("new 源")
        assertEquals(
            "源",
            (commands().run("cpy 源 副本") as CardCommands.Outcome.Copied).from
        )
        assertEquals(
            CardCommands.Reason.EXISTS,
            (commands().run("cpy 源 副本") as CardCommands.Outcome.Failed).reason
        )
        assertEquals(
            CardCommands.Reason.NO_SUCH_CARD,
            (commands().run("cpy 没这个 新名") as CardCommands.Outcome.Failed).reason
        )
        assertEquals(
            CardCommands.Reason.USAGE,
            (commands().run("cpy") as CardCommands.Outcome.Failed).reason
        )
    }




    @Test
    fun `改当前卡的规则`() {
        commands().run("new 甲")
        val outcome = commands().run("rule cpr") as CardCommands.Outcome.RuleChanged
        assertEquals(PlayerCardData.RuleType.CPR, outcome.card.rule)
        assertEquals(
            PlayerCardData.RuleType.CPR,
            manager.getCurrentPlayer(OWNER, GROUP).rule,
            "要落库"
        )
        assertEquals(
            CardCommands.Reason.USAGE,
            (commands().run("rule") as CardCommands.Outcome.Failed).reason
        )
        assertEquals(
            CardCommands.Reason.USAGE,
            (commands().run("rule gurps") as CardCommands.Outcome.Failed).reason
        )
    }


    @Test
    fun `删卡`() {
        commands().run("new 甲")
        commands().run("new 乙")
        assertEquals("乙", commands().run("del") .let { (it as CardCommands.Outcome.Deleted).name })
        assertEquals(listOf("甲"), manager.getPlayers(OWNER).map { it.playerName })

        assertEquals("甲", (commands().run("del 甲") as CardCommands.Outcome.Deleted).name)
        assertEquals(
            CardCommands.Reason.NO_SUCH_CARD,
            (commands().run("del 甲") as CardCommands.Outcome.Failed).reason
        )
    }


    @Test
    fun `不识别的子指令给用法`() {
        listOf("", "   ", "stat", "3d6").forEach { param ->
            val outcome = commands().run(param)
            assertTrue(
                outcome is CardCommands.Outcome.Failed &&
                    outcome.reason == CardCommands.Reason.USAGE,
                "「$param」该给用法提示，实得 $outcome"
            )
        }
    }

    private companion object {
        const val OWNER = "10001"
        const val GROUP = "20002"
        const val OTHER_GROUP = "30003"
    }
}
