















package stellaris.dice.diceFunction.random

import stellaris.dice.diceFunction.DiceLogger
import java.security.SecureRandom
import java.util.Random

object RandomService {
    private const val tag = "RandomService"


    private val localSource: SecureRandom? = try {
        SecureRandom().also { it.nextBytes(ByteArray(1)) }
    } catch (t: Throwable) {
        DiceLogger.error("本机系统熵源初始化失败，已回退降级源：$t", tag)
        null
    }


    private val degradedFallback: Random = Random(System.nanoTime())

    @Volatile
    private var testProvider: Random? = null


    val businessRandom: Random get() = testProvider ?: localSource ?: degradedFallback


    val isAvailable: Boolean get() = testProvider != null || localSource != null


    val sourceBrief: String get() = when {
        testProvider != null -> "测试注入的确定性随机源"
        localSource != null -> "本机系统熵源（${localSource.algorithm}）"
        else -> "降级兜底源"
    }


    val statusBrief: String get() = when {
        testProvider != null -> "测试"
        localSource != null -> "正常"
        else -> "降级（本机系统熵源初始化失败，已回退）"
    }


    fun installTestProvider(provider: Random?) {
        testProvider = provider
    }
}
