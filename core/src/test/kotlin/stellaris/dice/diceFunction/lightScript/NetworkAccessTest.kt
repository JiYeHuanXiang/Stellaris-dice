package stellaris.dice.diceFunction.lightScript

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class NetworkAccessTest {

    private lateinit var server: HttpServer
    private var port = 0

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/hello") { exchange ->
            val body = "你好".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/post") { exchange ->
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val header = exchange.requestHeaders.getFirst("X-Test").orEmpty()
            val reply = "$body/$header".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        server.start()
        port = server.address.port
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `默认拒绝本机地址`() {
        assertEquals("", NetworkAccess.httpGet("http://127.0.0.1:$port/hello"))
        assertTrue(NetworkAccess.httpGet("http://localhost:$port/hello").isEmpty())
    }

    @Test
    fun `显式允许时可访问本机`() {
        assertEquals("你好", NetworkAccess.httpGet("http://127.0.0.1:$port/hello", allowLocal = true))
        assertEquals(
            "数据/abc",
            NetworkAccess.httpPost(
                "http://127.0.0.1:$port/post",
                "数据",
                mapOf("X-Test" to "abc"),
                allowLocal = true
            )
        )
    }

    @Test
    fun `只允许 http 与 https`() {
        assertEquals("", NetworkAccess.httpGet("file:///etc/passwd", allowLocal = true))
        assertEquals("", NetworkAccess.httpGet("ftp://127.0.0.1/file", allowLocal = true))
    }

    @Test
    fun `本机地址的数字与缩写形式被拒绝`() {
        for (host in listOf(
            "127.1", "2130706433", "0x7f.0.0.1", "0177.0.0.1",
            "[::1]", "[::ffff:127.0.0.1]", "[::ffff:7f00:1]"
        )) {
            assertEquals("", NetworkAccess.httpGet("http://$host:$port/hello"), host)
        }
    }

    @Test
    fun `内网与保留段被拒绝`() {
        for (host in listOf(
            "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.1.1", "100.64.0.1",
            "224.0.0.1", "[fc00::1]", "[fe80::1]", "[::ffff:192.168.1.1]"
        )) {
            assertEquals("", NetworkAccess.httpGet("http://$host/hello"), host)
        }
    }

    @Test
    fun `IPv6 特殊地址与 IPv4 文档段不会被当成公网`() {
        for (host in listOf(
            "::127.0.0.1", "0:0:0:0:0:0:7f00:1", "::ffff:127.0.0.1",
            "2001:db8::1", "2002:c0a8:0101::1", "2001:0::1", "3fff::1", "100::1",
            "192.0.2.1", "198.51.100.1", "203.0.113.1"
        )) {
            assertFalse(NetworkAccess.isPublic(InetAddress.getByName(host)), host)
        }
        assertTrue(NetworkAccess.isPublic(InetAddress.getByName("8.8.8.8")))
        assertTrue(NetworkAccess.isPublic(InetAddress.getByName("2606:4700:4700::1111")))
    }

    private fun ipv6(vararg words: Int): InetAddress {
        val bytes = ByteArray(16)
        words.forEachIndexed { index, word ->
            bytes[index * 2] = (word ushr 8).toByte()
            bytes[index * 2 + 1] = word.toByte()
        }
        return Inet6Address.getByAddress(null, bytes, 0)
    }

    @Test
    fun `IPv6 判定直接覆盖 16 字节分支`() {
        // getByName 与 InetAddress.getByAddress 都会把 IPv4 映射地址归一化成 Inet4Address，
        // 这里用 Inet6Address.getByAddress 强制保留 16 字节，确保判定走 isPublicIpv6。
        val rejected = listOf(
            intArrayOf(0, 0, 0, 0, 0, 0xffff, 0x7f00, 0x0001),
            intArrayOf(0, 0, 0, 0, 0, 0xffff, 0xc0a8, 0x0101),
            intArrayOf(0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(0xfc00, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(0xfe80, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(0xff02, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(0x2001, 0x0db8, 0, 0, 0, 0, 0, 1),
            intArrayOf(0x2002, 0xc0a8, 0x0101, 0, 0, 0, 0, 1),
            intArrayOf(0x2001, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(0x3fff, 0, 0, 0, 0, 0, 0, 1)
        )
        for (words in rejected) {
            val address = ipv6(*words)
            assertEquals(16, address.address.size, "必须走 IPv6 分支")
            assertFalse(NetworkAccess.isPublic(address), address.hostAddress)
        }

        assertTrue(NetworkAccess.isPublic(ipv6(0x2606, 0x4700, 0x4700, 0, 0, 0, 0, 0x1111)))
        assertTrue(NetworkAccess.isPublic(ipv6(0x2001, 0x4860, 0x4860, 0, 0, 0, 0, 0x8888)))
    }

    @Test
    fun `重定向继续走同一校验`() {
        server.createContext("/hop") { exchange ->
            exchange.responseHeaders.add("Location", "/hello")
            exchange.sendResponseHeaders(302, -1)
        }
        assertEquals("你好", NetworkAccess.httpGet("http://127.0.0.1:$port/hop", allowLocal = true))
        assertEquals("", NetworkAccess.httpGet("http://127.0.0.1:$port/hop"))
    }
}
