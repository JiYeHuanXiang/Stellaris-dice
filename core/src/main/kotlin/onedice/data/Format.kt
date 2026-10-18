package onedice.data









object Format {









    fun build(str: String, vararg data: Any?): String {
        val rendered = Array(data.size) { index ->
            val raw = data[index]
            when {
                raw == null -> "null"
                else -> raw.toString().let { if (it.endsWith(".0")) it.substring(0, it.length - 2) else it }
            }
        }
        var cursor = 0
        var built = str
        while (built.contains("{replace}")) {
            built = built.replaceFirst("{replace}", rendered[cursor++])
        }
        return built
    }
}
