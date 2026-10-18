package stellaris.dice.diceFunction

import stellaris.dice.diceFunction.random.RandomService
import java.util.Random



















object DiceRandomManager {


    val randomInstance: Random get() = RandomService.businessRandom


    val isTrueRandom: Boolean get() = RandomService.isAvailable


    val chaosPoorSize: Int get() = 0


    var lastLoadingChaosTime = 0L


    fun supplementChaosPoor() {
    }


    val randomSourceBrief: String get() = RandomService.sourceBrief


    val randomStatusBrief: String get() = RandomService.statusBrief
}
