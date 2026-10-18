package stellaris.dice.diceFunction

import stellaris.dice.BuildConfig
import java.util.UUID






















object DiceConstant {




    const val REBOOT_EXIT_CODE: Int = 16991


    const val MAX_PLAYER_LENGTH = 20


    const val MAX_SKILL_LENGTH = 8


    const val MAX_DRAW_LIST_COUNT = 15


    const val MAX_HELPDOC_KEYWORD_LENGTH = 10


    const val MIN_PASTE_LENGTH = 1000


    const val ERROR_MSG_DUE_TO_INPUT_LONG_NAME = "拒绝你使用本功能，你录入的人物卡人物名字太长。"


    const val DEFAULT_QQ = "10000"


    const val DICE_SINGLE_ROLL_TIME_OUT = 3000L


    const val DICE_MUTILATION_ROLL_TIME_OUT = 5000L


    const val DICE_MAX_ROLL_STRING_LENGTH = 10000


    const val MAX_FIRSTLY_ATTACKING_VALUE_COUNT = 20



    const val MAX_PB_COUNT = 9


    const val MAX_LOG_COUNT = 5


    const val MAX_CARD_COUNT = 15









    const val USER_SPLIT_STRING = "#{SPLIT}"







    val SYSTEM_SPLIT_STRING = UUID.randomUUID().toString()









    const val EXPRESSION_CHARS = "0-9)(*/\\sacdkqbpef\\?:;m!^\\|&=><$+\\-"


    val REGEX_EXPRESSION_CHARS = "^((?<!\\))[$EXPRESSION_CHARS]+(?<!\\())".toRegex()


    val REGEX_PURE_EXPRESSION_CHARS = "^((?<!\\))[$EXPRESSION_CHARS]+(?<!\\())$".toRegex()


    val REGEX_PURE_NUMBERS = "^(\\d+)$".toRegex()












    const val DATA_DIR_PREFIX = "StellarisData_"


    const val SAVE_PATH_LOG = "log"
    const val SAVE_PATH_DRAW = "draw"
    const val SAVE_PATH_DATA = "data"
    const val SAVE_PATH_CACHE = "cache"
    const val SAVE_PATH_PICTURES = "pictures"
    const val SAVE_PATH_VOICES = "voices"
    const val SAVE_PATH_CUSTOM = "custom"
    const val SAVE_PATH_BACKUP = "backup"
    const val SAVE_PATH_HELP_DOC = "helpDoc"


    const val SAVE_PATH_FIND = "find"
    const val SAVE_PATH_RULES = "rules"
    const val SAVE_PATH_VIDEOS = "video"
    const val SAVE_PATH_FILES = "files"




    const val ENV_DEFAULT_PLAYER_NAME = "{player}"


    const val ENV_DEFAULT_SHOW_PLAYER_NAME = "默认人物卡"


    const val ENV_DEFAULT_SHOW_SKILL_NAME = "数值"




    const val KernelVersion = "No${BuildConfig.BUILD_TIMES}"


    const val KernelID = BuildConfig.BUILD_ID


    const val ModifiedDate = BuildConfig.BUILD_DATE







    val OS: String = "JVM " + System.getProperty("java.version", "unknown")
}
