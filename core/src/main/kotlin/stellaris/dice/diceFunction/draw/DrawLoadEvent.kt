package stellaris.dice.diceFunction.draw

import java.io.File







interface DrawLoadEvent {
    fun loadSuccess(file: DrawFile)
    fun loadFailure(file: File, e: Throwable)
}
