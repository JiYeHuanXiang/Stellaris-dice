package stellaris.dice

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiceKernelConcurrencyTest {

    @Test
    @Timeout(15)
    fun `并发创建同一实例只产生一个对象`() {
        val basePath = "build/tmp/kernel-concurrency-${UUID.randomUUID()}"
        try {
            DiceKernel.setBasePath(basePath)
        } catch (alreadySet: IllegalStateException) {
        }

        val botId = "concurrent-${UUID.randomUUID()}"
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..32).map {
                pool.submit(Callable {
                    start.await()
                    DiceKernel.createDice(botId, null)
                })
            }
            start.countDown()
            val created = futures.map { it.get(10, TimeUnit.SECONDS) }
            val first = created.first()
            assertTrue(created.all { it === first }, "并发创建返回了不同实例")
            assertSame(first, DiceKernel.getDice(botId))
        } finally {
            pool.shutdownNow()
        }
    }
}
