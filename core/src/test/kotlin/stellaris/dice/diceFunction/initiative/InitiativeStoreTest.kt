package stellaris.dice.diceFunction.initiative

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID







class InitiativeStoreTest {

    private fun tempRoot(): String = "build/tmp/initiative-store-${UUID.randomUUID()}"

    private fun withStore(test: (InitiativeStore, String) -> Unit) {
        val root = tempRoot()
        try {
            test(InitiativeStore(root), root)
        } finally {
            File(root).deleteRecursively()
        }
    }

    @Test
    fun `按值降序 同名替换保留血量与状态`() = withStore { store, _ ->
        val list = store.get("20002")
        list.put("甲", "d20", 10, "10001")
        list.put("乙", "d20", 20, "10001")
        assertEquals(listOf("乙", "甲"), list.entries.map { it.name }, "先攻值降序")

        val first = list.findExact("甲")!!
        first.setHp(12, 12)
        first.applyBuff(InitiativeEntry.BuffMode.ADD, "中毒")

        list.put("甲", "d20+3", 30, "10001")
        val replaced = list.findExact("甲")!!
        assertEquals(30, replaced.value)
        assertEquals("d20+3", replaced.expression)
        assertEquals(12, replaced.hp, "重掷不该清掉血量")
        assertEquals(listOf("中毒"), replaced.buffs, "重掷不该清掉状态")
        assertEquals(listOf("甲", "乙"), list.entries.map { it.name })
    }

    @Test
    fun `回合推进与轮次`() = withStore { store, _ ->
        val list = store.get("20002")
        list.put("甲", "d20", 20, "10001")
        list.put("乙", "d20", 10, "10001")
        assertEquals("甲", list.current()?.name)

        assertEquals("乙", list.next()?.name)
        assertEquals(1, list.rounds, "还没走回头部")
        assertEquals("甲", list.next()?.name)
        assertEquals(2, list.rounds, "走回头部轮次 +1")
    }

    @Test
    fun `删条目后指针不越界`() = withStore { store, _ ->
        val list = store.get("20002")
        list.put("甲", "d20", 20, "10001")
        list.put("乙", "d20", 10, "10001")
        list.put("丙", "d20", 5, "10001")

        list.next()
        list.next()
        assertEquals("丙", list.current()?.name)


        list.remove(list.findExact("甲")!!)
        assertEquals("丙", list.current()?.name)


        list.remove(list.findExact("丙")!!)
        assertEquals("乙", list.current()?.name)
    }

    @Test
    fun `容量上限`() = withStore { store, _ ->
        val list = store.get("20002")
        repeat(20) { index ->
            assertTrue(list.put("哥布林$index", "d20", index, "10001"))
        }
        assertFalse(list.put("第二十一条", "d20", 1, "10001"), "超过 20 条要拒绝")
        assertEquals(20, list.size())
    }

    @Test
    fun `重启后列表与指针还在`() = withStore { store, root ->
        val list = store.get("20002")
        list.put("甲", "d20", 20, "10001")
        list.put("乙", "d20", 10, "10001")
        list.next()
        list.findExact("甲")!!.setHp(8, 12)
        list.findExact("甲")!!.applyBuff(InitiativeEntry.BuffMode.ADD, "中毒")
        store.save("20002")

        val reloaded = InitiativeStore(root).get("20002")
        assertEquals(listOf("甲", "乙"), reloaded.entries.map { it.name })
        assertEquals("乙", reloaded.current()?.name)
        assertEquals(1, reloaded.rounds)
        assertEquals(8, reloaded.findExact("甲")!!.hp)
        assertEquals(12, reloaded.findExact("甲")!!.maxHp)
        assertEquals(listOf("中毒"), reloaded.findExact("甲")!!.buffs)
    }

    @Test
    fun `清空后不落空列表`() = withStore { store, root ->
        val list = store.get("20002")
        list.put("甲", "d20", 20, "10001")
        store.save("20002")
        list.clear()
        store.save("20002")
        assertNull(InitiativeStore(root).peek("20002"))
    }

    @Test
    fun `没设血量时累计扣血`() = withStore { store, _ ->
        val list = store.get("20002")
        list.put("哥布林", "d20", 5, "10001")
        val entry = list.findExact("哥布林")!!
        entry.addHp(-7)
        assertEquals(7, entry.damage)
        assertTrue(entry.statText().contains("扣血:7"))
        entry.addHp(3)
        assertEquals(4, entry.damage, "回血会冲抵扣血")
    }

    @Test
    fun `设血量后归零即死亡`() = withStore { store, _ ->
        val list = store.get("20002")
        list.put("哥布林", "d20", 5, "10001")
        val entry = list.findExact("哥布林")!!
        entry.setHp(12, 12)
        assertTrue(entry.addHp(-20))
        assertEquals(-8, entry.hp)
        assertTrue(entry.statText().contains("血量:-8/12"))
    }
}
