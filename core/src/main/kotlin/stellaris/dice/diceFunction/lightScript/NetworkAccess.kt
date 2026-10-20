package stellaris.dice.diceFunction.lightScript

import stellaris.dice.diceFunction.DiceLogger
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * 插件网络访问。只放行公网 http/https；环回、内网、链路本地与保留段
 * 默认拒绝（PC 端可通过配置 `ALLOW_LOCAL_NETWORK=1` 放开）。
 */
object NetworkAccess {

    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_REDIRECTS = 3
    private const val TIMEOUT_MILLIS = 8000

    fun httpGet(url: String, allowLocal: Boolean = false): String {
        val response = request("GET", url, null, emptyMap(), allowLocal) ?: return ""
        return response
    }

    fun httpPost(url: String, data: String, headers: Map<String, String>, allowLocal: Boolean = false): String? =
        request("POST", url, data, headers, allowLocal)

    private fun request(
        method: String,
        rawUrl: String,
        body: String?,
        headers: Map<String, String>,
        allowLocal: Boolean
    ): String? {
        var current = rawUrl.trim()
        repeat(MAX_REDIRECTS + 1) {
            val url = parseAndValidate(current, allowLocal) ?: return null
            val connection = (url.openConnection() as? HttpURLConnection) ?: return null
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", "StellarisDice-Plugin/1.0")
                headers.forEach { (key, value) ->
                    if (key.isNotBlank()) connection.setRequestProperty(key, value)
                }
                if (body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) return null
                    current = URI(current).resolve(location).toString()
                    return@repeat
                }
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) break
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toString(Charsets.UTF_8.name())
                }.orEmpty()
                return text
            } catch (t: Throwable) {
                DiceLogger.error("插件网络请求失败：$current（$t）")
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun parseAndValidate(rawUrl: String, allowLocal: Boolean): URL? {
        val uri = try {
            URI(rawUrl)
        } catch (t: Throwable) {
            DiceLogger.error("插件网络地址不合法：$rawUrl")
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            DiceLogger.error("插件网络只允许 http/https：$rawUrl")
            return null
        }
        if (uri.host.isNullOrBlank()) {
            DiceLogger.error("插件网络地址缺少主机名：$rawUrl")
            return null
        }
        if (!allowLocal) {
            val addresses = try {
                InetAddress.getAllByName(uri.host)
            } catch (t: Throwable) {
                DiceLogger.error("插件网络地址无法解析：$rawUrl")
                return null
            }
            if (addresses.isEmpty() || addresses.any { !isPublic(it) }) {
                DiceLogger.error("插件网络拒绝环回/内网地址：$rawUrl")
                return null
            }
        }
        return try {
            uri.toURL()
        } catch (t: Throwable) {
            null
        }
    }

    internal fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        return when (bytes.size) {
            4 -> isPublicIpv4(bytes)
            16 -> isPublicIpv6(bytes)
            else -> false
        }
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        val a = bytes[0].toInt() and 0xFF
        val b = bytes[1].toInt() and 0xFF
        val c = bytes[2].toInt() and 0xFF
        if (a == 0 || a == 10 || a == 127) return false
        if (a == 100 && b in 64..127) return false
        if (a == 169 && b == 254) return false
        if (a == 172 && b in 16..31) return false
        if (a == 192 && b == 168) return false
        if (a == 192 && b == 0) return false
        if (a == 192 && b == 88 && c == 99) return false
        if (a == 198 && (b == 18 || b == 19)) return false
        if (a == 198 && b == 51 && c == 100) return false
        if (a == 203 && b == 0 && c == 113) return false
        return a < 224
    }

    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        val third = bytes[2].toInt() and 0xFF
        val fourth = bytes[3].toInt() and 0xFF

        // 只接受 2000::/3 的全球单播地址段，直接排除 ::/8、IPv4 兼容/映射地址、
        // ULA、链路本地和组播地址。
        if ((first and 0xE0) != 0x20) return false

        // 文档、基准测试、Teredo 与 6to4 地址不应被插件当作普通公网出口。
        if (first == 0x20 && second == 0x01) {
            if (third == 0x0D && fourth == 0xB8) return false
            if (third == 0x00 && fourth == 0x00) return false
            if (third == 0x00 && fourth == 0x02 &&
                bytes[4].toInt() == 0 && bytes[5].toInt() == 0
            ) {
                return false
            }
            if (third == 0x00 && ((fourth and 0xF0) == 0x10 || (fourth and 0xF0) == 0x20)) {
                return false
            }
        }
        if (first == 0x20 && second == 0x02) return false
        if (first == 0x3F && (second and 0xF0) == 0xF0) return false
        return true
    }
}
