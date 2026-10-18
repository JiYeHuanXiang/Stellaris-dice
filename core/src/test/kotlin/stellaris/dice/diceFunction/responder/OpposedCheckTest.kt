package stellaris.dice.diceFunction.responder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test







class OpposedCheckTest {

    private fun side(level: CheckLevel, target: Int, roll: Int) = OpposedCheck.Side(level, target, roll)


    @Test
    fun `先比成功级别`() {
        assertEquals(
            OpposedCheck.Verdict.WIN,
            OpposedCheck.compare(
                side(CheckLevel.CRITICAL_SUCCESS, 50, 1),
                side(CheckLevel.EXTREME_SUCCESS, 90, 10)
            ),
            "大成功压过极难成功（哪怕对手技能值更高）"
        )
        assertEquals(
            OpposedCheck.Verdict.LOSE,
            OpposedCheck.compare(
                side(CheckLevel.HARD_SUCCESS, 90, 30),
                side(CheckLevel.EXTREME_SUCCESS, 10, 20)
            )
        )
        assertEquals(
            OpposedCheck.Verdict.WIN,
            OpposedCheck.compare(
                side(CheckLevel.FAILURE, 50, 80),
                side(CheckLevel.CRITICAL_FAILURE, 90, 100)
            ),
            "同为失败，但大失败更差"
        )
        assertEquals(
            OpposedCheck.Verdict.LOSE,
            OpposedCheck.compare(
                side(CheckLevel.CRITICAL_FAILURE, 90, 100),
                side(CheckLevel.FAILURE, 10, 99)
            )
        )
    }


    @Test
    fun `同级比技能值`() {
        assertEquals(
            OpposedCheck.Verdict.WIN,
            OpposedCheck.compare(
                side(CheckLevel.SUCCESS, 60, 40),
                side(CheckLevel.SUCCESS, 50, 41)
            ),
            "技能 60 对 50：技能高的赢，哪怕骰点低"
        )
        assertEquals(
            OpposedCheck.Verdict.LOSE,
            OpposedCheck.compare(
                side(CheckLevel.SUCCESS, 30, 29),
                side(CheckLevel.SUCCESS, 80, 20)
            )
        )
    }


    @Test
    fun `再同才比骰点`() {
        assertEquals(
            OpposedCheck.Verdict.WIN,
            OpposedCheck.compare(
                side(CheckLevel.SUCCESS, 60, 40),
                side(CheckLevel.SUCCESS, 60, 35)
            )
        )
        assertEquals(
            OpposedCheck.Verdict.LOSE,
            OpposedCheck.compare(
                side(CheckLevel.SUCCESS, 60, 20),
                side(CheckLevel.SUCCESS, 60, 25)
            )
        )
    }


    @Test
    fun `全同是平手`() {
        assertEquals(
            OpposedCheck.Verdict.DRAW,
            OpposedCheck.compare(
                side(CheckLevel.SUCCESS, 60, 40),
                side(CheckLevel.SUCCESS, 60, 40)
            )
        )
    }


    @Test
    fun `胜负文案`() {
        assertEquals("你赢了", OpposedCheck.verdictText(OpposedCheck.Verdict.WIN))
        assertEquals("你输了", OpposedCheck.verdictText(OpposedCheck.Verdict.LOSE))
        assertEquals("平手", OpposedCheck.verdictText(OpposedCheck.Verdict.DRAW))
    }
}
