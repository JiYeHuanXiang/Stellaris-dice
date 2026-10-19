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

    private fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF
            if (a == 0 || a == 10 || a == 127) return false
            if (a == 169 && b == 254) return false
            if (a == 172 && b in 16..31) return false
            if (a == 192 && b == 168) return false
            if (a == 100 && b in 64..127) return false
            if (a == 192 && b == 0) return false
            if (a == 198 && (b == 18 || b == 19)) return false
            if (a >= 224) return false
        } else if (bytes.size == 16) {
            if (bytes[10].toInt() == 0xFF && bytes[11].toInt() == 0xFF) {
                return isPublic(java.net.InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            }
            if (bytes.all { it.toInt() == 0 }) return false
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if ((first and 0xFE) == 0xFC) return false
            if (first == 0xFE && (second and 0xC0) == 0x80) return false
            if (first == 0xFE && (second and 0xC0) == 0xC0) return false
            if (first == 0xFF) return false
        }
        return true
    }
}
