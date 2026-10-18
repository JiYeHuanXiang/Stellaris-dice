package stellaris.dice.diceFunction.lightScript

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
}
