package stellaris.dice.diceFunction.template


















class SingleTemplateKey(val keyword: String) {


    var addTailIfNotExist: Boolean = false


    var addHeadIfNotExist: Boolean = false


    var decodeXdX: Boolean = false


    var unescape: Boolean = false


    val escapeKey: String get() = "html_$keyword"

    override fun toString(): String = "{$keyword}"


    fun escapeMatch(keyword: String): Boolean = keyword == escapeKey


    fun decode(input: String): String {
        var text = input
        if (decodeXdX) {
            text = text.decodeXdX()
        }
        if (unescape) {
            text = text.decodeToContent()
        }
        return text
    }
}
