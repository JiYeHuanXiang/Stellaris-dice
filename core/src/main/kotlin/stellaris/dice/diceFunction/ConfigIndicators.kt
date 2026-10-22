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
            item(DiceConfigStore.RULE_MODE, "规则模式", "off/coc/coc6/dnd5e/dnd5r/dnd3r/dg/fate/dx/wx/cpr"),
            item(DiceConfigStore.COC_HOUSE_RULE, "COC 房规", "0~5 或 rules/ 里的自定义规则名，见 .setcoc 空参列表"),
            item(DiceConfigStore.PREFIX, "指令触发符", "默认 .。!！；每个字符都算触发符"),
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
            item(DiceConfigStore.DRAW_DEFAULT_DECK, "默认牌堆", "裸 .draw 使用"),
            item(DiceConfigStore.AVOID_CLEARING_GROUP_LIST, "免清群名单", "每行一个群号；.master groupclr 跳过"),
            item(DiceConfigStore.GROUP_CLEANED_SORRY_REPLY, "清理群告别语", "可用 {days}"),
            item(DiceConfigStore.SYSTEM_SPLIT, "长消息分段", "1=拆多条，0=并入换行"),
            item(DiceConfigStore.SYSTEM_CENSORSHIP, "敏感词审查", "1=开启，0=关闭"),
            item(DiceConfigStore.FORBIDDEN_WORDS, "敏感词表", "每行一个；命中替换为等长 *"),
            item(DiceConfigStore.REPLACE_FORBIDDEN_WORDS, "敏感词替换规则", "每行 词=替换；替换写 - 表示整条拦截")
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
            item(DiceConfigStore.SENTENCE_SAN_CHECK, "理智检定", ".sc 的结果文本"),
            item(DiceConfigStore.SENTENCE_BIG_SUCCESS, "大成功", "六档成功级别写法"),
            item(DiceConfigStore.SENTENCE_VERY_HARD_SUCCESS, "极难成功", "六档成功级别写法"),
            item(DiceConfigStore.SENTENCE_HARD_SUCCESS, "困难成功", "六档成功级别写法"),
            item(DiceConfigStore.SENTENCE_SUCCESS, "成功", "六档成功级别写法"),
            item(DiceConfigStore.SENTENCE_FAILURE, "失败", "六档成功级别写法"),
            item(DiceConfigStore.SENTENCE_BIG_FAILURE, "大失败", "六档成功级别写法")
        ))

        root.put("文本·理智", group(
            item(DiceConfigStore.SC_PRE_SENTENCE, "sc 外层模板", "为空时用「理智检定」；可用 {result}"),
            item(DiceConfigStore.SC_SUCCESS, "sc 成功", "成功分支"),
            item(DiceConfigStore.SC_FAILURE, "sc 失败", "失败分支"),
            item(DiceConfigStore.SC_BIG_SUCCESS, "sc 大成功", "大成功分支"),
            item(DiceConfigStore.SC_BIG_FAILURE, "sc 大失败", "大失败分支"),
            item(DiceConfigStore.SC_INNOCENT_CRAZY, "sc 灵感失败", "掉 5 点但灵感失败"),
            item(DiceConfigStore.SC_TEMPORARY_CRAZY, "sc 临时疯狂", "掉 5 点且灵感成功"),
            item(DiceConfigStore.SC_RANDOM_CRAZY, "sc 不定时疯狂", "掉落达到原理智 1/5"),
            item(DiceConfigStore.SC_PERMANENT_CRAZY, "sc 永久疯狂", "理智归零")
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
            item(DiceConfigStore.SENTENCE_MADNESS, "疯狂", ".ti/.li 的结果文本"),
            item(DiceConfigStore.SENTENCE_DICE_OPEN, "骰子开启", ".bot on 成功"),
            item(DiceConfigStore.SENTENCE_DICE_CLOSE, "骰子关闭", ".bot off 成功"),
            item(DiceConfigStore.SENTENCE_DICE_DENIED, "骰子权限拒绝", ".bot 无权限时"),
            item(DiceConfigStore.SENTENCE_DICE_HELP_TEXT, "骰子帮助文本", ".bot 不带参数的用法行"),
            item(DiceConfigStore.SENTENCE_DICE_ROBOT_TEXT, "骰子标题", ".bot 不带参数的标题"),
            item(DiceConfigStore.SENTENCE_OPEN_ALREADY, "骰子已开启", ".bot on 但本就开着"),
            item(DiceConfigStore.SENTENCE_CLOSE_ALREADY, "骰子已关闭", ".bot off 但本就关着"),
            item(DiceConfigStore.SENTENCE_LOG_OPEN, "日志开始", "可用 {name}"),
            item(DiceConfigStore.SENTENCE_LOG_CLOSE, "日志暂停", "可用 {name}"),
            item(DiceConfigStore.SENTENCE_LOG_END, "日志结束", "可用 {name}"),
            item(DiceConfigStore.SENTENCE_LOG_RESUME, "日志继续", "可用 {name}"),
            item(DiceConfigStore.SENTENCE_LOG_ALREADY, "日志已存在", "已有日志在记录"),
            item(DiceConfigStore.SENTENCE_LOG_NOT_OPEN, "日志未开启", "没有正在记录的日志"),
            item(DiceConfigStore.SENTENCE_LOG_NOT_FOUND, "日志不存在", "可用 {name}"),
            item(DiceConfigStore.SENTENCE_LOG_DENIED, "日志权限拒绝", ".log 无权限时"),
            item(DiceConfigStore.SENTENCE_OB_JOIN, "旁观加入", "可用 {player}"),
            item(DiceConfigStore.SENTENCE_OB_JOIN_FAILURE, "旁观加入失败", "已在 OB 位"),
            item(DiceConfigStore.SENTENCE_OB_EXIT, "旁观退出", "退出成功"),
            item(DiceConfigStore.SENTENCE_OB_EXIT_FAILURE, "旁观退出失败", "本来就不在 OB 位"),
            item(DiceConfigStore.SENTENCE_NAME, "随机名字标题", "可用 {player}"),
            item(DiceConfigStore.SENTENCE_JRRP, "今日人品", "可用 {player}/{value}"),
            item(DiceConfigStore.SENTENCE_CHANGE_CARD, "切换人物卡", "可用 {name}"),
            item(DiceConfigStore.SENTENCE_CHANGE_NAME, "人物卡改名", "可用 {from}/{to}"),
            item(DiceConfigStore.SENTENCE_SETCOC_DENIED, "房规权限拒绝", ".setcoc 无权限时"),
            item(DiceConfigStore.SENTENCE_RULE_QUERY_FAILURE, "规则查询失败", "可用 {result}/{query}"),
            item(DiceConfigStore.SENTENCE_DRAW_SUCCESS, "抽牌成功提示", "可用 {result}"),
            item(DiceConfigStore.SENTENCE_DRAW_FAILURE, "抽牌失败提示", "可用 {result}")
        ))
        return root.toString()
    }

    private fun item(key: String, label: String, hint: String): JSONArray =
        JSONArray().put(key).put(label).put(hint)

    private fun group(vararg items: JSONArray): JSONArray =
        JSONArray().apply { items.forEach { put(it) } }
}
