package stellaris.presence
















interface PresencePort {





    fun reportingEnabled(): Boolean







    fun guardedEndpoint(): String








    fun listPage(page: Int, pageSize: Int): List<PresenceEntry>





    fun blacklistEnabled(): Boolean





    fun reportOffender(selfQQ: String, offender: Long, reason: String)


    fun isBlocked(qq: Long): Boolean





    fun blacklistCommand(param: String, selfQQ: String): String?


    fun reportRemoval(selfQQ: String, blackQQ: Long)
}









class PresenceEntry(
    val botId: Long?,
    val nick: String,
    val version: String,
    val lastHeartbeat: Long
)
