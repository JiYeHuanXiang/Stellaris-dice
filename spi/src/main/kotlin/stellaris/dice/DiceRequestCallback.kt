package stellaris.dice








interface DiceRequestCallback {


    fun agree()


    fun ignore() {}


    fun reject() {
        ignore()
    }
}
