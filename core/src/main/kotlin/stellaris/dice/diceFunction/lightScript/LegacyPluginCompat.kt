package stellaris.dice.diceFunction.lightScript

import stellaris.dice.DiceIntentEvent
import stellaris.dice.diceFunction.DiceLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File





private val LEGACY_APP_ROOTS = listOf(
    "/storage/emulated/0/AstralDice",
    "/sdcard/AstralDice",
    "/mnt/sdcard/AstralDice",
    "/storage/sdcard0/AstralDice",
    "/storage/emulated/legacy/AstralDice"
)


internal const val MIRAI_CACHE = ".MiraiCache"


private val LEGACY_HOME_SUBDIRS = setOf(MIRAI_CACHE)












internal object LegacyPluginPaths {





    fun map(path: String, home: File, basePath: File): String? {
        val p = path.trim().trim('"', '\'').replace('\\', '/')
        if (p.isEmpty()) return null
        val root = LEGACY_APP_ROOTS.firstOrNull { p == it || p.startsWith("$it/") } ?: return null
        val rest = p.substring(root.length).trim('/')
        if (rest.isEmpty()) return home.path
        if (rest.contains("..")) return null
        val first = rest.substringBefore('/')
        return when {






            first.startsWith("AstralData_") -> File(basePath, rest).path

            first in LEGACY_HOME_SUBDIRS -> File(home, rest).path
            else -> null
        }
    }
}


















internal object LegacyMiraiCache {


    private const val FRESH_MS = 5 * 60 * 1000L


    private const val RETRY_MS = 30 * 1000L


    private const val CONTACTS = "contacts/friends.json"





    private val DEVICE_KINDS = listOf("ANDROID_PHONE", "ANDROID_PAD", "ANDROID_WATCH")


    private val lastFetch = HashMap<String, Pair<Long, Int>>()
    private val fetchLock = Any()


    enum class Outcome { WROTE, FRESH, UNAVAILABLE }


    fun botDir(cacheRoot: File, botId: String): File = File(cacheRoot, botId)






    fun ensure(
        cacheRoot: File,
        botId: String,
        fetch: () -> ArrayList<DiceIntentEvent.FriendsInfo>?
    ): Outcome {
        val now = System.currentTimeMillis()
        val dir = botDir(cacheRoot, botId)
        val files = DEVICE_KINDS.map { File(File(dir, it), CONTACTS) }
        val id = key(cacheRoot, botId)
        val last = synchronized(fetchLock) { lastFetch[id] }

        if (files.any { it.isFile && it.lastModified() > (last?.first ?: 0L) && now - it.lastModified() < FRESH_MS }) {
            return Outcome.FRESH
        }
        if (last != null) {
            val window = if (last.second > 0) FRESH_MS else RETRY_MS
            if (now - last.first < window) {
                return if (last.second > 0) Outcome.FRESH else Outcome.UNAVAILABLE
            }
        }
        val friends = try {
            fetch()
        } catch (e: Throwable) {
            DiceLogger.error("[mirai-cache] 取好友列表失败: $e")
            null
        }

        val list = friends?.filter { it.id.trim() != botId.trim() } ?: emptyList()


        val body = if (list.isEmpty()) null else encode(list, now)
        var written = false
        if (body != null) {
            for (f in files) {
                try {
                    f.parentFile?.mkdirs()
                    f.writeText(body, Charsets.UTF_8)
                    written = true
                } catch (e: Throwable) {
                    DiceLogger.error("[mirai-cache] 写入失败 ${f.path}: $e")
                }
            }
        }

        synchronized(fetchLock) { lastFetch[id] = now to (if (written) list.size else 0) }
        return if (written) Outcome.WROTE else Outcome.UNAVAILABLE
    }

    private fun key(cacheRoot: File, botId: String): String = cacheRoot.path + "\u0000" + botId


    private fun encode(friends: List<DiceIntentEvent.FriendsInfo>, now: Long): String {
        val list = JSONArray()
        for (f in friends) {
            val uin = f.id.trim().toLongOrNull() ?: continue
            list.put(
                JSONObject()
                    .put("uin", uin)
                    .put("nick", f.nick)


                    .put("remark", f.remark)
                    .put("friendGroupId", 0)
            )
        }
        return JSONObject()
            .put("updateKey", 0)
            .put("updateTime", now / 1000)
            .put("friendList", list)
            .put("friendGroupList", JSONArray())
            .toString()
    }
}
