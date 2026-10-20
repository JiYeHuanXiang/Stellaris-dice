package stellaris.dice.diceFunction.draw

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import stellaris.dice.diceFunction.random.RandomService
import java.util.Random

class DrawDataTest {

    private class FixedRandom(private val value: Long) : Random() {
        override fun nextLong(): Long = value
    }

    @AfterEach
    fun restoreRandom() {
        RandomService.installTestProvider(null)
    }

    @Test
    fun `非法静态权重按默认值处理且小数权重保留比例`() {
        val deck = DrawData()
        deck.appendData("负权重", "-2")
        deck.appendData("零权重", "0")
        deck.appendData("半权重", ".5")
        deck.freshPriorities()

        assertEquals(25, deck.remain(), "小数精度统一放大后，三条权重为 10:10:5")

        RandomService.installTestProvider(FixedRandom(40L))
        assertEquals("半权重", deck.draw(putBack = true))
    }

    @Test
    fun `超大权重不会撑爆区间`() {
        val deck = DrawData()
        deck.appendData("大权重", "1e30")
        deck.freshPriorities()

        assertEquals(1_000_000_000, deck.remain())
        RandomService.installTestProvider(FixedRandom(0L))
        assertEquals("大权重", deck.draw(putBack = false))
        assertEquals(999_999_999, deck.remain())
    }
}
