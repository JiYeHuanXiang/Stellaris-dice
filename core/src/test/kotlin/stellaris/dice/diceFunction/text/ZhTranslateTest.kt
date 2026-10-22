package stellaris.dice.diceFunction.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZhTranslateTest {

    @Test
    fun `繁体转简体`() {
        assertEquals("侦查", ZhTranslate.hk2zh("偵查"))
        assertEquals("们来并丢干", ZhTranslate.hk2zh("們來並丟乾"))
    }

    @Test
    fun `未收录字符原样透传`() {
        assertEquals("abc123😀", ZhTranslate.hk2zh("abc123😀"))
    }

    @Test
    fun `比较与包含按归一后判断`() {
        assertTrue(ZhTranslate.equal("們", "们"))
        assertTrue(ZhTranslate.startsWith("來測試", "来测"))
        assertTrue(ZhTranslate.contained("這是測試", "测试"))
        assertFalse(ZhTranslate.contained("這是測試", "不存在"))
    }

    @Test
    fun `空输入返回空串`() {
        assertEquals("", ZhTranslate.hk2zh(null))
        assertEquals("", ZhTranslate.hk2zh(""))
    }
}
