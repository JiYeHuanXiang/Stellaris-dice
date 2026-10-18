package stellaris.dice.diceFunction.card

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.calculation.DiceCalculation
import java.io.File








class PlayerCardTest {

    private fun tempRoot(name: String): String {
        val dir = File("build/tmp/card-test/$name")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir.absolutePath
    }




    @Test
    fun `属性按名唯一 同名即替换`() {
        val card = PlayerCardData.create("10001")
        assertTrue(card.setProperty("力量", 50))
        assertTrue(card.setProperty("力量", 80))
        assertEquals(1, card.propertyCount, "同名只该有一条")
        assertEquals(80, card.readProperty("力量"))
        assertEquals(listOf("力量"), card.propertyNames, "覆盖不改顺序")
    }





    @Test
    fun `超长技能名被拒但只记日志`() {
        val card = PlayerCardData.create("10001")
        val tooLong = "技能".repeat(DiceConstant.MAX_SKILL_LENGTH)
        assertFalse(card.setProperty(tooLong, 50))
        assertFalse(card.hasProperty(tooLong), "没写进去")
        assertEquals(0, card.propertyCount)

        val boundary = "技".repeat(DiceConstant.MAX_SKILL_LENGTH)
        assertTrue(card.setProperty(boundary, 50), "刚好到上限该写入")
    }


    @Test
    fun `读不到的属性回退 1`() {
        val card = PlayerCardData.create("10001")
        assertEquals(1, card.readProperty("察言观色"))
        assertEquals(7, card.readProperty("察言观色", 7), "显式缺省优先")
        assertEquals(1, card.readProperty(null), "null 名字也走缺省")

        card.setProperty("体型", 0)
        assertEquals(0, card.readProperty("体型"))
        assertTrue(card.hasProperty("体型"), "登记过 0 的项，hasProperty 为真")
    }

    @Test
    fun `自动名片按群开合 空模板保留原格式`() {
        val manager = PlayerCardManager(tempRoot("auto-name"))
        val card = manager.createPlayer("10001", "甲")!!
        manager.setCurrentPlayer("10001", "20002", card)
        manager.setCurrentPlayer("10001", "30003", card)

        assertNotNull(manager.configureAutoName("10001", "20002", "{pc_name}", true))
        assertNotNull(manager.configureAutoName("10001", "30003", "", true))
        assertEquals("甲", manager.renderAutoName("10001", "20002"))
        assertEquals("甲", manager.renderAutoName("10001", "30003"), "空模板应保留原格式")

        assertNotNull(manager.configureAutoName("10001", "20002", "", false))
        assertNull(manager.renderAutoName("10001", "20002"), "本群已除名")
        assertEquals("甲", manager.renderAutoName("10001", "30003"), "别的群不受影响")
    }

    @Test
    fun `未命名的卡显示默认人物卡`() {
        val card = PlayerCardData.create("10001")
        assertEquals("默认人物卡", card.readFriendlyName())
        card.playerName = "甲"
        assertEquals("甲", card.readFriendlyName())
        assertEquals("甲", card.toString())
    }

    @Test
    fun `merge 把另一张卡的属性并进来`() {
        val target = PlayerCardData.create("10001")
        target.setProperty("力量", 50)
        val source = PlayerCardData.create("10001")
        source.setProperty("力量", 90)
        source.setProperty("侦查", 25)
        target.merge(source)
        assertEquals(90, target.readProperty("力量"), "同名以来源为准")
        assertEquals(25, target.readProperty("侦查"))
        assertEquals(2, target.propertyCount)
    }



    @Test
    fun `建卡与取名不覆盖`() {
        val manager = PlayerCardManager(tempRoot("cards"))
        assertNotNull(manager.createPlayer("10001", "甲"))
        assertNull(manager.createPlayer("10001", "甲"), "同名卡不覆盖")
        assertTrue(manager.verifyPlayerExistence("10001", "甲"))
        assertFalse(manager.verifyPlayerExistence("10001", "乙"))
        assertEquals(1, manager.getPlayers("10001").size)
    }


    @Test
    fun `当前卡永远有结果`() {
        val manager = PlayerCardManager(tempRoot("current"))
        val card = manager.getCurrentPlayer("10001", "20002")
        assertEquals(DiceConstant.ENV_DEFAULT_PLAYER_NAME, card.playerName)
        assertEquals("默认人物卡", card.readFriendlyName())
    }


    @Test
    fun `绑定按群分开`() {
        val manager = PlayerCardManager(tempRoot("binding"))
        val first = manager.createPlayer("10001", "甲")
        val second = manager.createPlayer("10001", "乙")
        assertNotNull(first)
        assertNotNull(second)

        manager.setCurrentPlayer("10001", "20002", first)
        manager.setCurrentPlayer("10001", "30003", second)
        assertEquals("甲", manager.getCurrentPlayer("10001", "20002").playerName)
        assertEquals("乙", manager.getCurrentPlayer("10001", "30003").playerName)
        assertEquals("默认人物卡", manager.getCurrentPlayer("10001", "99999").readFriendlyName(), "没绑的群回默认卡")

        manager.setCurrentPlayer("10001", "20002", null)
        assertEquals("默认人物卡", manager.getCurrentPlayer("10001", "20002").readFriendlyName(), "取消绑定后回默认卡")
    }


    @Test
    fun `删卡会清掉指向它的绑定`() {
        val manager = PlayerCardManager(tempRoot("delete"))
        val card = manager.createPlayer("10001", "甲")!!
        manager.setCurrentPlayer("10001", "20002", card)
        assertTrue(manager.deleteCertainPlayer(card))
        assertFalse(manager.verifyPlayerExistence("10001", "甲"))
        assertEquals("默认人物卡", manager.getCurrentPlayer("10001", "20002").readFriendlyName())
    }






    @Test
    fun `卡与绑定能落盘往返`() {
        val root = tempRoot("round-trip")
        val first = PlayerCardManager(root)
        val card = first.createPlayer("10001", "甲")!!
        card.setProperty("力量", 90)
        card.setProperty("克苏鲁神话", 30)
        card.rule = PlayerCardData.RuleType.COC7
        card.brief = "第一个角色"
        first.savePlayer(card)
        first.setCurrentPlayer("10001", "20002", card)

        val reloaded = PlayerCardManager(root)
        val loaded = reloaded.getCurrentPlayer("10001", "20002")
        assertEquals("甲", loaded.playerName)
        assertEquals(90, loaded.readProperty("力量"))
        assertEquals(30, loaded.readProperty("克苏鲁神话"))
        assertEquals(PlayerCardData.RuleType.COC7, loaded.rule)
        assertEquals("第一个角色", loaded.brief)
        assertEquals(69, DiceCalculation.abilitiesGetMaxSan(loaded.asAbilities()), "99-30")
    }


    @Test
    fun `坏文件不致命`() {
        val root = tempRoot("broken")
        File(root, DiceConstant.SAVE_PATH_DATA).mkdirs()
        File(root, "${DiceConstant.SAVE_PATH_DATA}/cards.json").writeText("{ 这不是 JSON", Charsets.UTF_8)
        val manager = PlayerCardManager(root)
        assertEquals("默认人物卡", manager.getCurrentPlayer("10001", "20002").readFriendlyName())
    }
}
