package stellaris.dice.diceFunction.responder




















object OpposedCheck {


    class Side(val level: CheckLevel, val target: Int, val roll: Int)


    enum class Verdict {

        WIN,


        LOSE,


        DRAW
    }


    fun compare(self: Side, opponent: Side): Verdict {

        if (self.level.ordinal != opponent.level.ordinal) {
            return if (self.level.ordinal < opponent.level.ordinal) Verdict.WIN else Verdict.LOSE
        }

        if (self.target != opponent.target) {
            return if (self.target > opponent.target) Verdict.WIN else Verdict.LOSE
        }

        if (self.roll != opponent.roll) {
            return if (self.roll > opponent.roll) Verdict.WIN else Verdict.LOSE
        }
        return Verdict.DRAW
    }


    fun verdictText(verdict: Verdict): String = when (verdict) {
        Verdict.WIN -> "你赢了"
        Verdict.LOSE -> "你输了"
        Verdict.DRAW -> "平手"
    }
}
