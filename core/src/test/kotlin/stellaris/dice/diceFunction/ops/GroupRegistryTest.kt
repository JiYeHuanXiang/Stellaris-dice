package stellaris.dice.diceFunction.ops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GroupRegistryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `记录群并统计久未使用`() {
        val registry = GroupRegistry(tempDir.toString())
        registry.touch("100")
        registry.touch("200")

        assertEquals(2, registry.size())
        assertTrue(registry.unused(30).isEmpty(), "刚用过的群不该被算作久未使用")
    }

    @Test
    fun `落盘后新实例能读回`() {
        val registry = GroupRegistry(tempDir.toString())
        registry.touch("100", "测试群")

        val restarted = GroupRegistry(tempDir.toString())
        assertEquals(1, restarted.size())
        assertTrue(restarted.unused(30).isEmpty())
    }

    @Test
    fun `seed 不覆盖已有最后使用时间`() {
        val registry = GroupRegistry(tempDir.toString())
        registry.seed(listOf("300" to "甲群"))
        assertEquals(1, registry.size())

        registry.seed(listOf("300" to "乙群"))
        assertEquals(1, registry.size())
        assertTrue(registry.unused(30).isEmpty())
    }

    @Test
    fun `按天数过滤久未使用的群`() {
        val registry = GroupRegistry(tempDir.toString())
        registry.touch("100")
        registry.touch("200")

        val later = System.currentTimeMillis() + 31L * 24 * 60 * 60 * 1000
        assertEquals(2, registry.unused(30, later).size)
        assertEquals(0, registry.unused(30).size)
    }

    @Test
    fun `天数为零不返回结果`() {
        val registry = GroupRegistry(tempDir.toString())
        registry.touch("100")
        assertTrue(registry.unused(0).isEmpty())
    }
}
