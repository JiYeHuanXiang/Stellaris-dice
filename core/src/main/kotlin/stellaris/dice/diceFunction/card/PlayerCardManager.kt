package stellaris.dice.diceFunction.card

import org.json.JSONObject
import stellaris.dice.diceFunction.DiceConstant
import stellaris.dice.diceFunction.DiceLogger
import java.io.File























class PlayerCardManager(private val rootPath: String) {


    private val cards = LinkedHashMap<String, LinkedHashMap<String, PlayerCardData>>()


    private val bindings = LinkedHashMap<String, LinkedHashMap<String, String>>()


    private val contentTables = ContentTables(rootPath)

    private val storeFile: File
        get() = File(rootPath, "${DiceConstant.SAVE_PATH_DATA}/cards.json")

    init {
        load()
    }




    fun getPlayers(owner: String): List<PlayerCardData> = cards[owner]?.values?.toList() ?: emptyList()


    fun verifyPlayerExistence(owner: String, name: String): Boolean = cards[owner]?.containsKey(name) == true







    fun getCertainPlayer(
        owner: String,
        name: String = DiceConstant.ENV_DEFAULT_PLAYER_NAME,
        autoCreate: Boolean = true
    ): PlayerCardData {
        cards[owner]?.get(name)?.let { return it }
        if (!autoCreate) {
            return decorate(PlayerCardData.create(owner, name))
        }
        return createPlayer(owner, name) ?: decorate(PlayerCardData.create(owner, name))
    }


    fun getCurrentPlayer(owner: String, group: String): PlayerCardData {
        val boundName = bindings[owner]?.get(bindingKey(group))
        if (boundName != null) {
            cards[owner]?.get(boundName)?.let { return it }
        }
        return getCertainPlayer(owner, DiceConstant.ENV_DEFAULT_PLAYER_NAME)
    }








    fun currentPlayerName(owner: String, group: String): String? =
        bindings[owner]?.get(bindingKey(group))







    fun findCurrentPlayer(owner: String, group: String): PlayerCardData? {
        val boundName = bindings[owner]?.get(bindingKey(group))
        if (boundName != null) {
            cards[owner]?.get(boundName)?.let { return it }
        }
        return cards[owner]?.get(DiceConstant.ENV_DEFAULT_PLAYER_NAME)
    }








    fun createPlayer(owner: String, name: String = DiceConstant.ENV_DEFAULT_PLAYER_NAME): PlayerCardData? {
        if (verifyPlayerExistence(owner, name)) {
            return null
        }
        val card = decorate(PlayerCardData.create(owner, name))
        cards.getOrPut(owner) { LinkedHashMap() }[name] = card
        flush()
        return card
    }


    fun savePlayer(card: PlayerCardData): Boolean {
        val owned = cards.getOrPut(card.owner) { LinkedHashMap() }
        val previousName = owned.entries.firstOrNull { it.value === card }?.key
        if (previousName != null && previousName != card.playerName) {
            owned.remove(previousName)
        }
        owned[card.playerName] = card
        flush()
        return true
    }






    /**
     * `.sn` 插件入口：给当前卡设置自动名片格式与开关。
     *
     * 模板传空串 = 保留现有模板；开关 false = 只把 [group] 从自动名片名单里除名。
     */
    fun configureAutoName(
        owner: String,
        group: String,
        format: String,
        enabled: Boolean
    ): PlayerCardData? {
        val card = findCurrentPlayer(owner, group)
            ?: getCertainPlayer(owner, DiceConstant.ENV_DEFAULT_PLAYER_NAME, autoCreate = true)
        if (format.isNotEmpty()) {
            card.autoNameFormat = format.take(200)
        }
        val scope = if (group.isEmpty()) PRIVATE_CHAT_BINDING_KEY else group
        if (enabled) {
            card.autoNameGroups.add(scope)
        } else {
            card.autoNameGroups.remove(scope)
        }
        card.autoNameEnabled = enabled || card.autoNameGroups.isNotEmpty()
        return if (savePlayer(card)) card else null
    }

    fun renderAutoName(owner: String, group: String): String? {
        val card = findCurrentPlayer(owner, group) ?: return null
        val scope = if (group.isEmpty()) PRIVATE_CHAT_BINDING_KEY else group
        val enabled = card.autoNameGroups.contains(scope) ||
            (card.autoNameGroups.isEmpty() && card.autoNameEnabled)
        if (!enabled || card.autoNameFormat.isEmpty()) return null
        return CardAutoName.render(card, card.autoNameFormat)
    }

    fun setCurrentPlayer(owner: String, group: String, player: PlayerCardData? = null) {
        val owned = bindings.getOrPut(owner) { LinkedHashMap() }
        val key = bindingKey(group)
        if (player == null) {
            owned.remove(key)
        } else {
            owned[key] = player.playerName
        }
        flush()
    }


    fun deleteCertainPlayer(card: PlayerCardData): Boolean {
        val owned = cards[card.owner] ?: return false
        owned.remove(card.playerName) ?: return false
        bindings[card.owner]?.entries?.removeIf { it.value == card.playerName }
        flush()
        return true
    }






    fun getBindings(owner: String): Map<String, String> =
        bindings[owner]?.toMap() ?: emptyMap()


    fun clearBindings(owner: String) {
        if (bindings.remove(owner) != null) {
            flush()
        }
    }


    fun removeAll(owner: String) {
        cards.remove(owner)
        bindings.remove(owner)
        flush()
    }


    fun countPlayers(owner: String): Int = cards[owner]?.size ?: 0








    fun copyPlayer(owner: String, from: String, to: String): PlayerCardData? {
        val source = cards[owner]?.get(from) ?: return null
        val created = createPlayer(owner, to) ?: return null
        created.rule = source.rule
        created.brief = source.brief
        created.merge(source)
        flush()
        return created
    }




    private fun bindingKey(group: String): String = if (group.isEmpty()) PRIVATE_CHAT_BINDING_KEY else group


    private fun decorate(card: PlayerCardData): PlayerCardData {
        card.aliases = contentTables.aliases
        card.defaultSkillValues = contentTables.skills
        return card
    }

    private fun flush() {
        try {
            val root = JSONObject()
            val cardsJson = JSONObject()
            cards.forEach { (owner, owned) ->
                val ownerJson = JSONObject()
                owned.forEach { (name, card) ->
                    ownerJson.put(
                        name,
                        JSONObject().apply {
                            put("rule", card.rule.name)
                            put("brief", card.brief)
                            put("autoNameFormat", card.autoNameFormat)
                            put("autoNameEnabled", card.autoNameEnabled)
                            put("autoNameGroups", org.json.JSONArray().apply {
                                card.autoNameGroups.forEach { put(it) }
                            })
                            put("properties", JSONObject().apply {
                                card.propertyNames.forEach { propertyName ->
                                    put(propertyName, card.readProperty(propertyName))
                                }
                            })
                        }
                    )
                }
                cardsJson.put(owner, ownerJson)
            }
            root.put("cards", cardsJson)

            val bindingsJson = JSONObject()
            bindings.forEach { (owner, owned) ->
                bindingsJson.put(owner, JSONObject(owned as Map<*, *>))
            }
            root.put("bindings", bindingsJson)

            storeFile.parentFile?.mkdirs()
            storeFile.writeText(root.toString(), Charsets.UTF_8)
        } catch (t: Throwable) {

            DiceLogger.error("人物卡落盘失败（本次运行的内存数据不受影响）：$t", LOG_TAG)
        }
    }

    private fun load() {
        if (!storeFile.isFile) {
            return
        }
        try {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            root.optJSONObject("cards")?.let { cardsJson ->
                cardsJson.keys().forEach { owner ->
                    val ownerJson = cardsJson.optJSONObject(owner) ?: return@forEach
                    val owned = LinkedHashMap<String, PlayerCardData>()
                    ownerJson.keys().forEach { name ->
                        val cardJson = ownerJson.optJSONObject(name) ?: return@forEach
                        val card = decorate(PlayerCardData.create(owner, name))
                        runCatching { card.rule = PlayerCardData.RuleType.valueOf(cardJson.optString("rule")) }
                        card.brief = cardJson.optString("brief")
                        card.autoNameFormat = cardJson.optString("autoNameFormat")
                        card.autoNameEnabled = cardJson.optBoolean("autoNameEnabled", false)
                        cardJson.optJSONArray("autoNameGroups")?.let { groups ->
                            for (index in 0 until groups.length()) {
                                groups.optString(index).takeIf { it.isNotEmpty() }
                                    ?.let { card.autoNameGroups.add(it) }
                            }
                        }
                        cardJson.optJSONObject("properties")?.let { properties ->
                            properties.keys().forEach { propertyName ->
                                card.setProperty(propertyName, properties.optInt(propertyName))
                            }
                        }
                        owned[name] = card
                    }
                    cards[owner] = owned
                }
            }
            root.optJSONObject("bindings")?.let { bindingsJson ->
                bindingsJson.keys().forEach { owner ->
                    val ownerJson = bindingsJson.optJSONObject(owner) ?: return@forEach
                    val owned = LinkedHashMap<String, String>()
                    ownerJson.keys().forEach { key -> owned[key] = ownerJson.optString(key) }
                    bindings[owner] = owned
                }
            }
        } catch (t: Throwable) {

            DiceLogger.error("人物卡读取失败，按空库继续：$t", LOG_TAG)
        }
    }

    private companion object {
        const val LOG_TAG = "PlayerCardStore"


        const val PRIVATE_CHAT_BINDING_KEY = "@direct-message@"
    }
}
