package stellaris.dice.diceFunction.log

import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File
import java.io.FileOutputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter






















class LogManager(private val rootPath: String) {


    class LogFile(val name: String, val file: File, val active: Boolean)


    enum class StartResult { STARTED, RESUMED, ALREADY, TOO_MANY, INVALID_NAME }

    private class ActiveLog(val name: String, val file: File)


    private val active = HashMap<String, ActiveLog>()

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")








    @Synchronized
    fun start(group: String, rawName: String): StartResult {
        if (active.containsKey(group)) {
            return StartResult.ALREADY
        }
        val name = normalizeName(rawName)
            ?: return StartResult.INVALID_NAME
        val effectiveName = name.ifEmpty {
            "log-" + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        }
        val file = fileFor(group, effectiveName)
        if (file.exists()) {

            active[group] = ActiveLog(effectiveName, file)
            return StartResult.RESUMED
        }
        val dir = groupDirectory(group)
        val existingCount = dir.listFiles()
            ?.count { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            ?: 0
        if (existingCount >= DiceConstant.MAX_LOG_COUNT) {
            return StartResult.TOO_MANY
        }
        try {
            dir.mkdirs()
            FileOutputStream(file).use { output ->
                output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            }
            file.appendText(
                "【日志】群 $group 开始于 ${timeNow()}\n",
                Charsets.UTF_8
            )
        } catch (t: Throwable) {
            DiceLogger.error("日志文件创建失败：$t", LOG_TAG)
            return StartResult.INVALID_NAME
        }
        active[group] = ActiveLog(effectiveName, file)
        return StartResult.STARTED
    }


    @Synchronized
    fun appendUser(group: String, name: String, id: String, content: String) {
        append(group, "$name($id) ${timeNow()}\n$content\n")
    }


    @Synchronized
    fun appendBot(group: String, botId: String, content: String) {
        append(group, "骰娘($botId) ${timeNow()}\n$content\n")
    }

    private fun append(group: String, text: String) {
        val log = active[group] ?: return
        val content = text.trim()
        if (content.isEmpty()) {
            return
        }
        try {
            log.file.appendText(content + "\n", Charsets.UTF_8)
        } catch (t: Throwable) {
            DiceLogger.error("日志写入失败：$t", LOG_TAG)
        }
    }


    @Synchronized
    fun stop(group: String): String? = active.remove(group)?.name


    @Synchronized
    fun activeName(group: String): String? = active[group]?.name


    @Synchronized
    fun activeFile(group: String): File? = active[group]?.file






    @Synchronized
    fun stopIfActive(group: String, rawName: String): Boolean {
        val name = normalizeName(rawName) ?: return false
        val current = active[group] ?: return false
        return if (current.name == name) {
            active.remove(group)
            true
        } else {
            false
        }
    }


    @Synchronized
    fun list(group: String): List<LogFile> {
        val dir = groupDirectory(group)
        val current = active[group]?.name
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.map { file ->
                val name = file.nameWithoutExtension
                LogFile(name, file, name == current)
            }
            ?.toList()
            ?: emptyList()
    }


    @Synchronized
    fun find(group: String, rawName: String): File? {
        val name = normalizeName(rawName) ?: return null
        if (name.isEmpty()) {
            return null
        }
        val file = fileFor(group, name)
        return if (file.isFile) file else null
    }


    @Synchronized
    fun remove(group: String, rawName: String): Boolean {
        val name = normalizeName(rawName) ?: return false
        if (name.isEmpty()) {
            return false
        }
        if (active[group]?.name == name) {
            active.remove(group)
        }
        return fileFor(group, name).delete()
    }


    @Synchronized
    fun clear(group: String): Int {
        active.remove(group)
        val dir = groupDirectory(group)
        var count = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension.equals("txt", ignoreCase = true)) {
                if (file.delete()) {
                    count++
                }
            }
        }
        return count
    }



    private fun groupDirectory(group: String): File =
        File(rootPath, "${DiceConstant.SAVE_PATH_LOG}/$group")

    private fun fileFor(group: String, name: String): File =
        File(groupDirectory(group), "$name.txt")




    private fun normalizeName(rawName: String): String? {
        val stripped = rawName.trim()
            .removeSuffix(".txt")
            .removeSuffix(".TXT")
            .trim()

        if (stripped == "." || stripped == "..") {
            return null
        }
        val sanitized = stripped
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim()
            .trimEnd('.', ' ')
            .take(MAX_LOG_NAME_LENGTH)
        if (sanitized == "." || sanitized == "..") {
            return null
        }
        return sanitized
    }

    private fun timeNow(): String = LocalTime.now().format(timeFormat)

    private companion object {
        const val LOG_TAG = "LogManager"


        const val MAX_LOG_NAME_LENGTH = 30
    }
}
