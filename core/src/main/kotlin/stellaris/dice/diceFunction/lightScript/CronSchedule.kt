package stellaris.dice.diceFunction.lightScript

import java.time.ZonedDateTime

/**
 * 六域 cron：秒 分 时 日 月 周（0/7 = 周日）。
 * 支持通配、单值、范围、步长（斜杠 n）与逗号列表。
 */
object CronSchedule {

    fun matches(expression: String, now: ZonedDateTime): Boolean {
        val fields = expression.trim().split(Regex("\\s+"))
        if (fields.size != 6) return false
        val seconds = parse(fields[0], 0, 59) ?: return false
        val minutes = parse(fields[1], 0, 59) ?: return false
        val hours = parse(fields[2], 0, 23) ?: return false
        val days = parse(fields[3], 1, 31) ?: return false
        val months = parse(fields[4], 1, 12) ?: return false
        val weekdays = parse(fields[5], 0, 7)?.map { if (it == 7) 0 else it }?.toSet()
            ?: return false
        val cronWeekday = now.dayOfWeek.value % 7
        return now.second in seconds &&
            now.minute in minutes &&
            now.hour in hours &&
            now.dayOfMonth in days &&
            now.monthValue in months &&
            cronWeekday in weekdays
    }

    private fun parse(field: String, min: Int, max: Int): Set<Int>? {
        val text = field.trim()
        if (text.isEmpty()) return null
        if (text == "*" || text == "?") return (min..max).toSet()
        val result = HashSet<Int>()
        for (part in text.split(",")) {
            val stepParts = part.split("/", limit = 2)
            val step = if (stepParts.size == 2) {
                stepParts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            } else {
                1
            }
            val range = stepParts[0].trim()
            val bounds = when {
                range == "*" || range == "?" -> min to max
                range.contains("-") -> {
                    val pieces = range.split("-", limit = 2)
                    val start = pieces[0].toIntOrNull() ?: return null
                    val end = pieces[1].toIntOrNull() ?: return null
                    start to end
                }

                else -> {
                    val value = range.toIntOrNull() ?: return null
                    if (stepParts.size == 2) value to max else value to value
                }
            }
            var value = bounds.first
            while (value <= bounds.second) {
                if (value in min..max) result.add(value)
                value += step
            }
        }
        return result
    }
}
