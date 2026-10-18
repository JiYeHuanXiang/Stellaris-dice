package stellaris.dice.diceFunction.observer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID






class ObserverStoreTest {

    private fun withStore(test: (ObserverStore, String) -> Unit) {
        val root = "build/tmp/observer-store-${UUID.randomUUID()}"
        try {
            test(ObserverStore(root), root)
        } finally {
            File(root).deleteRecursively()
        }
    }

    @Test
    fun `加入 退出 重复加入`() = withStore { store, _ ->
        assertEquals(ObserverStore.JoinResult.JOINED, store.join("20002", "10001", "甲"))
        assertEquals(ObserverStore.JoinResult.ALREADY, store.join("20002", "10001", "甲"))

        assertEquals(listOf("10001"), store.list("20002").map { it.id })
        assertTrue(store.isObserver("20002", "10001"))
        assertTrue(store.leave("20002", "10001"))
        assertFalse(store.isObserver("20002", "10001"))
        assertTrue(store.list("20002").isEmpty())
    }

    @Test
    fun `一个群最多十人`() = withStore { store, _ ->
        repeat(ObserverStore.MAX_OBSERVERS) { index ->
            assertEquals(
                ObserverStore.JoinResult.JOINED,
                store.join("20002", "1000$index", "观察者$index")
            )
        }
        assertEquals(
            ObserverStore.JoinResult.FULL,
            store.join("20002", "10010", "第十一个"),
            "第 11 个要拒绝"
        )
        assertEquals(ObserverStore.MAX_OBSERVERS, store.list("20002").size)
    }

    @Test
    fun `按群分开 与 跨群清理`() = withStore { store, _ ->
        store.join("20002", "10001", "甲")
        store.join("30003", "10001", "甲")
        store.join("30003", "10002", "乙")

        assertEquals(2, store.removeEverywhere("10001"))
        assertFalse(store.isObserver("20002", "10001"))
        assertFalse(store.isObserver("30003", "10001"))
        assertTrue(store.isObserver("30003", "10002"), "别人的 OB 位不该被清")
        assertEquals(0, store.removeEverywhere("10001"), "已经不在任何群")
    }

    @Test
    fun `重启后名单还在`() = withStore { store, root ->
        store.join("20002", "10001", "甲")
        store.join("20002", "10002", "乙")

        val reloaded = ObserverStore(root)
        assertEquals(listOf("10001", "10002"), reloaded.list("20002").map { it.id })
        assertEquals("乙", reloaded.list("20002")[1].name)
    }

    @Test
    fun `清空后不落空名单`() = withStore { store, root ->
        store.join("20002", "10001", "甲")
        store.clear("20002")
        assertTrue(ObserverStore(root).list("20002").isEmpty())
    }

    @Test
    fun `坏文件按空库继续`() = withStore { store, root ->
        store.join("20002", "10001", "甲")
        File(root, "data/observers.json").writeText("{ 不是 JSON", Charsets.UTF_8)
        assertTrue(ObserverStore(root).list("20002").isEmpty())
        assertNull(ObserverStore(root).list("30003").firstOrNull())
    }
}
