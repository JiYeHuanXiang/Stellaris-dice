package stellaris.dice.diceFunction.ops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecoveryManagerTest {

    @TempDir
    lateinit var temp: File

    @Test
    fun `导出导入恢复全量数据且不动源包`() {
        val root = File(temp, "StellarisData_12345")
        write(root, "data/config.json", """{"global":{"MASTER_QQ":"1"}}""")
        write(
            root,
            "data/cards.json",
            """{"cards":{"1":{"甲":{"rule":"coc7"}}},"bindings":{}}"""
        )
        write(root, "data/keywords.json", """{"version":1,"entries":[{"uuid":1,"keyword":"你好","content":"回复"}]}""")
        write(root, "data/blacklist.json", """{"version":1,"entries":[{"uuid":1,"type":"USER","number":"9"}]}""")
        write(root, "draw/test.json", """{"牌堆":["一","二"]}""")
        write(root, "custom/sn.toml", """[[define]]
name = "x"
content = "y"
""")
        write(root, "log/1/a.txt", "log")

        val manager = RecoveryManager(root.path)
        val pkg = File(temp, "pkg.zip")
        assertTrue(manager.exportTo(pkg), "导出应成功")
        assertTrue(pkg.isFile)
        val before = pkg.readBytes()

        write(root, "draw/test.json", """{"牌堆":["被改"]}""")
        File(root, "custom/sn.toml").delete()

        val result = manager.import(pkg)
        assertTrue(result.success, result.message)
        assertTrue(File(root, "custom/sn.toml").isFile, "插件应恢复")
        assertTrue(File(root, "draw/test.json").readText().contains("一"), "牌堆应恢复")
        assertTrue(File(root, "backup").listFiles()!!.any { it.name.startsWith("before-import") })
        assertTrue(before.contentEquals(pkg.readBytes()), "源迁移包不应被修改")
    }

    @Test
    fun `备份列表与按天清理`() {
        val root = File(temp, "StellarisData_1")
        root.mkdirs()
        val manager = RecoveryManager(root.path)
        val one = manager.makeBackup("backup")!!
        assertTrue(one.isFile)
        assertEquals(1, manager.listBackups().size)

        one.setLastModified(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000)
        assertEquals(1, manager.deleteOlderThan(30))
        assertTrue(manager.listBackups().isEmpty())
    }

    @Test
    fun `overwrite 从同级实例目录迁移`() {
        val source = File(temp, "StellarisData_111")
        val target = File(temp, "StellarisData_222")
        write(source, "data/config.json", """{"global":{"MASTER_QQ":"111"}}""")
        write(source, "draw/a.json", """{"牌堆":["来自111"]}""")
        target.mkdirs()

        val result = RecoveryManager(target.path).overwriteFrom("111")
        assertTrue(result.success, result.message)
        assertTrue(File(target, "draw/a.json").readText().contains("来自111"))
        assertTrue(File(target, "data/config.json").readText().contains("111"))
    }

    @Test
    fun `overwrite 源实例缺失时失败且不动目标`() {
        val target = File(temp, "StellarisData_222")
        target.mkdirs()
        write(target, "draw/b.json", """{"牌堆":["保留"]}""")

        val result = RecoveryManager(target.path).overwriteFrom("999")
        assertFalse(result.success, result.message)
        assertTrue(result.message.contains("找不到实例目录"), result.message)
        assertTrue(File(target, "draw/b.json").readText().contains("保留"), "失败时目标数据不应被改动")
    }

    @Test
    fun `导入包里的路径穿越条目会被拒绝`() {
        val root = File(temp, "StellarisData_1")
        root.mkdirs()
        val evil = File(temp, "evil.txt")
        val pkg = File(temp, "evil.zip")
        ZipOutputStream(FileOutputStream(pkg)).use { zip ->
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }

        val result = RecoveryManager(root.path).import(pkg)
        assertFalse(result.success, result.message)
        assertFalse(evil.exists(), "路径穿越文件不应被写出：${evil.path}")
    }

    private fun write(root: File, relative: String, content: String) {
        val file = File(root, relative)
        file.parentFile.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }
}
