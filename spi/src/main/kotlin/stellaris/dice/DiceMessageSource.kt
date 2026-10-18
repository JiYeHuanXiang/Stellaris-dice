package stellaris.dice








class DiceMessageSource(val type: Type, val id: String) {

    val numberId: Long = try {
        id.toLong()
    } catch (e: Throwable) {
        0
    }

    fun isGroup(): Boolean = type == Type.GROUP

    fun isPerson(): Boolean = type == Type.PERSON

    enum class Type {
        GROUP, PERSON, UNKNOWN
    }

    override fun equals(other: Any?): Boolean {
        if (other is DiceMessageSource) {
            if (other.id == id && other.type == type) {
                return true
            }
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + numberId.hashCode()
        return result
    }
}
