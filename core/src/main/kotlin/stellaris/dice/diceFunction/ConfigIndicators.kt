package stellaris.dice.diceFunction

import org.json.JSONArray
import org.json.JSONObject

/**
 * 宿主文本设定页的配置总表，格式为 `{ 分组: [[键, 中文名, 提示], ...] }`。
 * 只登记内核会读取的键。
 */
object ConfigIndicators {

    fun json(): String {
        val root = JSONObject()
        root.put("基础设置", group(
            item(DiceConfigStore.MASTER_QQ, "master QQ", "多个 QQ 用换行分隔"),
            item(DiceConfigStore.DEFAULT_DICE_NUMBER, "默认骰面", "1~9999；规则模式开启时裸 .r 仍用体系默认骰"),
            item(DiceConfigStore.RULE_MODE, "规则模式", "off/coc/coc6/dnd5e/dnd3r/dg/fate/dx/wx/cpr"),
            item(DiceConfigStore.COC_HOUSE_RULE, "COC 房规档位", "0~5，见 .setcoc 空参列表"),
            item(DiceConfigStore.BOT_OFF, "本群停用", "1=停用，0=启用"),
            item(DiceConfigStore.REPLY_ON, "关键词自动回复", "1=开，0=关"),
            item(DiceConfigStore.OB_ON, "旁观者开关", "true/false"),
            item(DiceConfigStore.WELCOME_ON, "入群欢迎", "1=开，0=关"),
            item(DiceConfigStore.WELCOME_TEXT, "入群欢迎文本", "可用 {user}/{player}/{group}"),
            item(DiceConfigStore.NO_LAWNOSKY, "无法无天", "1=被踢/被禁言不自动拉黑"),
            item(DiceConfigStore.DEBUG_ON, "调试开关", "1=开，0=关"),
            item(DiceConfigStore.NUDGE_QQ_SAY, "戳一戳回复", "可用 {player}/{user}"),
            item("JOIN_GROUP_SAY", "入群问候", "被邀请入群时发送"),
            item("AUTO_AGREE_FRIEND", "自动同意好友", "1=同意，0=忽略"),
            item("AUTO_AGREE_GROUP", "自动同意群邀请", "1=同意，0=忽略"),
            item("BACKUP_AUTO", "自动备份", "1=每 10 分钟一次，0=关"),
            item(DiceConfigStore.ALLOW_LOCAL_NETWORK, "插件允许内网地址", "1=允许本机/内网，0=仅公网")
        ))

        root.put("群管理", group(
            item(DiceConfigStore.DISABLE_JRRP, "禁用 jrrp", "1=本群禁用"),
            item(DiceConfigStore.DISABLE_DRAW, "禁用牌堆", "1=本群禁用 .draw/.deck"),
            item(DiceConfigStore.DISABLE_HELP, "禁用帮助", "1=本群禁用 .help"),
            item(DiceConfigStore.DISABLE_KEYWORDS, "本群屏蔽关键词", "JSON 数组；由 .group -词条 维护"),
            item(DiceConfigStore.FIND_GROUP, "find 默认分组", "词条检索的默认分组"),
            item(DiceConfigStore.DRAW_DEFAULT_DECK, "默认牌堆", "裸 .draw 使用")
        ))

        root.put("文本·骰点", group(
            item(DiceConfigStore.SENTENCE_ROLL, "普通骰", "可用 {roll} 等字段"),
            item(DiceConfigStore.SENTENCE_ROLL_WITH_REASON, "普通骰（带理由）", "可用 {reason}"),
            item(DiceConfigStore.SENTENCE_ILLEGAL, "非法指令", "中性提示，不暴露内部异常"),
            item(DiceConfigStore.SENTENCE_ILLEGAL_TOO_MUCH, "重复次数过多", "多重骰上限提示"),
            item(DiceConfigStore.SENTENCE_HIDDEN_ROLL_HINT, "暗骰提示", ".rh/.hl 的引导"),
            item(DiceConfigStore.SENTENCE_HIDDEN_ROLL, "暗骰占位", "群里只留占位，结果私聊")
        ))

        root.put("文本·检定", group(
            item(DiceConfigStore.SENTENCE_RABP, "检定", "可用 {skill}/{roll}/{success}"),
            item(DiceConfigStore.SENTENCE_RABP_WITH_REASON, "检定（带理由）", "可用 {reason}"),
            item(DiceConfigStore.SENTENCE_RABP_MUTI, "多重检定", "多次检定逐行输出"),
            item(DiceConfigStore.SENTENCE_RABP_MUTI_WITH_REASON, "多重检定（带理由）", "可用 {reason}"),
            item(DiceConfigStore.SENTENCE_GROWTH, "成长检定", ".en 的结果文本"),
            item(DiceConfigStore.SENTENCE_SAN_CHECK, "理智检定", ".sc 的结果文本")
        ))

        root.put("文本·卡与对抗", group(
            item(DiceConfigStore.SENTENCE_CARD_UPDATED, "卡更新", "属性写回成功"),
            item(DiceConfigStore.SENTENCE_CARD_SHOW, "卡展示", "属性列表"),
            item(DiceConfigStore.SENTENCE_CARD_NO_SUCH_ABILITY, "卡无此属性", "提示文本"),
            item(DiceConfigStore.SENTENCE_OPPOSED_START, "对抗开始", "先手登记"),
            item(DiceConfigStore.SENTENCE_OPPOSED_FIRST_WIN, "先手胜", "可用 {winner}"),
            item(DiceConfigStore.SENTENCE_OPPOSED_LAST_WIN, "后手胜", "可用 {winner}"),
            item(DiceConfigStore.SENTENCE_OPPOSED_EQUAL, "对抗平手", "结果文本"),
            item(DiceConfigStore.SENTENCE_OPPOSED_ALL_FAILURE, "对抗全失败", "双方都失败"),
            item(DiceConfigStore.SENTENCE_FIRST_ATTACKING, "先攻", "先攻列表输出")
        ))

        root.put("文本·其他", group(
            item(DiceConfigStore.SENTENCE_DRAW, "抽牌", "牌堆结果文本"),
            item(DiceConfigStore.SENTENCE_MADNESS, "疯狂", ".ti/.li 的结果文本")
        ))
        return root.toString()
    }

    private fun item(key: String, label: String, hint: String): JSONArray =
        JSONArray().put(key).put(label).put(hint)

    private fun group(vararg items: JSONArray): JSONArray =
        JSONArray().apply { items.forEach { put(it) } }
}
