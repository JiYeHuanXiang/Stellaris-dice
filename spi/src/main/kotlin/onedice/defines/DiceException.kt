package onedice.defines













open class DiceException(val brief: String, cause: Throwable? = null) :
    IllegalStateException(brief, cause) {

    override fun toString(): String = "DiceException{msg=${super.message},cause=$cause}"
}
