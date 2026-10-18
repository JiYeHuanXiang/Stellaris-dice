package stellaris.dice.diceFunction.ops

import org.json.JSONArray
import org.json.JSONObject
import stellaris.dice.diceFunction.DiceLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 全量导出/导入与备份。
 *
 * 包格式（zip）：
 * ```
 * manifest.json
 * data/...
 * draw/...
 * custom/...
 * log/...
 * find/... pictures/... voices/... files/... video/... helpDoc/... rules/...
 * ```
 * 旧目录不会被修改；导入前会先自动备份当前数据。
 */
class RecoveryManager(private val rootPath: String) {

    private val root = File(rootPath)
    private val backupDir = File(root, "backup")
    private val botId = root.name.removePrefix("StellarisData_").ifEmpty { "unknown" }

    fun listBackups(): List<File> {
        if (!backupDir.isDirectory) return emptyList()
        return backupDir.listFiles { file -> file.isFile && file.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun makeBackup(prefix: String = "backup"): File? {
        val stamp = SimpleDateFormat("MM.dd HH-mm-ss", Locale.US).format(Date())
        val target = File(backupDir, "${safeName(prefix)} $stamp.zip")
        return if (exportTo(target)) target else null
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "backup" }

    fun findBackup(fileName: String): File? =
        listBackups().firstOrNull { it.name == fileName }

    fun deleteBackup(fileName: String): Boolean {
        val file = File(backupDir, File(fileName).name)
        if (!file.isFile) return false
        return file.delete()
    }

    fun deleteOlderThan(days: Int): Int {
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        var deleted = 0
        listBackups().forEach {
            if (it.lastModified() < cutoff && it.delete()) deleted++
        }
        return deleted
    }

    fun export(fileName: String? = null): File? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = fileName ?: "recovery-$botId-$stamp.zip"
        return makeBackup(name.removeSuffix(".zip")).let { made ->
            made
        }
    }

    fun exportTo(target: File): Boolean {
        val temp = File.createTempFile("stellaris-recovery-", ".zip")
        return try {
            ZipOutputStream(FileOutputStream(temp)).use { zip ->
                val files = collectFiles()
                val manifest = manifest(files)
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                files.forEach { (name, file) ->
                    zip.putNextEntry(ZipEntry(name))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            target.parentFile?.mkdirs()
            temp.copyTo(target, overwrite = true)
            temp.delete()
            true
        } catch (t: Throwable) {
            DiceLogger.error("导出失败：$t")
            temp.delete()
            false
        }
    }

    /**
     * 导入：先解到临时目录并校验 manifest，再自动备份当前数据，最后覆盖到实例根。
     */
    fun import(source: File): ImportResult {
        if (!source.isFile) return ImportResult(false, "找不到导入包：${source.path}")
        val temp = createTempDir()
        return try {
            unzip(source, temp)
            val manifestFile = File(temp, MANIFEST)
            if (!manifestFile.isFile) {
                return ImportResult(false, "导入包缺少 manifest.json")
            }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            val version = manifest.optInt("formatVersion", 0)
            if (version <= 0 || version > FORMAT_VERSION) {
                return ImportResult(false, "不支持的迁移包版本：$version")
            }
            val current = makeBackup("before-import")
                ?: return ImportResult(false, "导入前自动备份失败，已中止")

            copyTree(temp, root, skipManifest = true)
            ImportResult(
                true,
                "导入完成；导入前备份：${current.name}\n" + countsText(manifest.optJSONObject("counts"))
            )
        } catch (t: Throwable) {
            DiceLogger.error("导入失败：$t")
            ImportResult(false, "导入失败：${t.message ?: t}")
        } finally {
            temp.deleteRecursively()
        }
    }

    /**
     * `.recovery overwrite <骰QQ>`：从同级的另一个实例目录整包搬到当前实例。
     * 先导出到临时包，再复用 import 的备份/校验流程。
     */
    fun overwriteFrom(botId: String): ImportResult {
        val otherRoot = File(root.parentFile, "StellarisData_$botId")
        if (!otherRoot.isDirectory) {
            return ImportResult(false, "找不到实例目录：${otherRoot.path}")
        }
        val temp = File.createTempFile("stellaris-overwrite-", ".zip")
        return try {
            if (!RecoveryManager(otherRoot.path).exportTo(temp)) {
                return ImportResult(false, "源实例导出失败")
            }
            import(temp)
        } finally {
            temp.delete()
        }
    }

    class ImportResult(val success: Boolean, val message: String)

    private fun manifest(files: Map<String, File>): JSONObject {
        val counts = JSONObject()
            .put("cards", countCards())
            .put("keywords", countJsonArray("data/keywords.json", "entries"))
            .put("blacklist", countJsonArray("data/blacklist.json", "entries"))
            .put("draw", countDir("draw"))
            .put("custom", countDir("custom"))
            .put("log", countFilesRecursively("log"))
        val names = JSONArray()
        files.keys.forEach { names.put(it) }
        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("botId", botId)
            .put("createdAt", System.currentTimeMillis())
            .put("counts", counts)
            .put("files", names)
    }

    private fun collectFiles(): Map<String, File> {
        val result = LinkedHashMap<String, File>()
        EXPORT_DIRS.forEach { dirName ->
            val dir = File(root, dirName)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val relative = file.relativeTo(root).path.replace('\\', '/')
                if (relative.startsWith("backup/") || relative.startsWith("cache/")) return@forEach
                result[relative] = file
            }
        }
        return result
    }

    private fun unzip(source: File, dest: File) {
        ZipInputStream(FileInputStream(source)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(dest, entry.name).canonicalFile
                if (!target.path.startsWith(dest.canonicalPath + File.separator)) {
                    throw IllegalArgumentException("非法压缩包路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun copyTree(from: File, to: File, skipManifest: Boolean) {
        val rootCanonical = to.canonicalFile
        from.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val relative = file.relativeTo(from).path.replace('\\', '/')
            if (skipManifest && relative == MANIFEST) return@forEach
            val target = File(rootCanonical, relative).canonicalFile
            if (!target.path.startsWith(rootCanonical.path + File.separator)) {
                throw IllegalArgumentException("非法导入路径：$relative")
            }
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
    }

    private fun countCards(): Int {
        val json = readJson("data/cards.json") ?: return 0
        val cards = json.optJSONObject("cards") ?: return 0
        var count = 0
        cards.keys().forEach { owner ->
            cards.optJSONObject(owner)?.let { count += it.length() }
        }
        return count
    }

    private fun countJsonArray(relative: String, field: String): Int =
        readJson(relative)?.optJSONArray(field)?.length() ?: 0

    private fun countDir(relative: String): Int =
        File(root, relative).listFiles()?.count { it.isFile } ?: 0

    private fun countFilesRecursively(relative: String): Int {
        val dir = File(root, relative)
        if (!dir.isDirectory) return 0
        return dir.walkTopDown().count { it.isFile }
    }

    private fun readJson(relative: String): JSONObject? {
        val file = File(root, relative)
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (t: Throwable) {
            null
        }
    }

    private fun countsText(counts: JSONObject?): String {
        if (counts == null) return "（包内无计数信息）"
        return "条目数：角色卡 ${counts.optInt("cards")}、关键词 ${counts.optInt("keywords")}、" +
            "黑名单 ${counts.optInt("blacklist")}、牌堆文件 ${counts.optInt("draw")}、" +
            "插件 ${counts.optInt("custom")}、日志 ${counts.optInt("log")}"
    }

    private fun createTempDir(): File =
        java.nio.file.Files.createTempDirectory("stellaris-recovery-").toFile()

    companion object {
        const val FORMAT_VERSION = 1
        private const val MANIFEST = "manifest.json"
        private val EXPORT_DIRS = listOf(
            "data", "draw", "custom", "log", "find", "pictures", "voices",
            "files", "video", "helpDoc", "rules"
        )
    }
}
