package stellaris.core.defines












class ResErrorException(
    val type: ResErrorType,
    brief: String = "",
    cause: Throwable? = null
) : DiceException(brief, cause) {

    override fun toString(): String =
        "ResErrorException{brief=$brief,type=$type,msg=${super.message},cause=$cause}"
}
