package stellaris.host;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stellaris.bridge.DiceBoot;
import stellaris.bridge.EventTranslator;
import stellaris.dice.DiceHelper;
import stellaris.util.Json;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;







class HostSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void coreToOutboundClosedLoop() throws Exception {
        List<String> actions = Collections.synchronizedList(new ArrayList<>());
        HttpServer fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            actions.add(body);
            Map<String, Object> request = Json.parseObject(body);
            String echo = Json.getString(request, "echo");
            String response = "{\"status\":\"ok\",\"retcode\":0,\"data\":{},\"echo\":\""
                    + echo + "\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fake.start();
        try {
            int port = fake.getAddress().getPort();
            HttpOneBotSession session = new HttpOneBotSession("http://127.0.0.1:" + port, "");
            Path content = Files.isDirectory(Path.of("content"))
                    ? Path.of("content") : Path.of("..", "content");
            DiceHelper helper = DiceBoot.boot(
                    "10000", tempDir.toString(), session, content.toFile());
            EventTranslator translator = new EventTranslator(helper, session);
            session.addEventListener(translator::translate);

            Assertions.assertTrue(
                    Files.exists(tempDir.resolve("StellarisData_10000/draw/02-姓名.json")),
                    "内容应已播种到实例 draw/");

            session.onMessage(Json.write(message(".r d6")));
            session.onMessage(Json.write(message(".name 2")));

            Assertions.assertFalse(actions.isEmpty(), "应有出站动作");
            StringBuilder all = new StringBuilder();
            for (String action : actions) all.append(action).append('\n');
            Assertions.assertTrue(all.toString().contains("send_group_msg"), all.toString());
            Assertions.assertTrue(all.toString().contains("骰点结果"), all.toString());
            Assertions.assertTrue(all.toString().contains("生成了以下名字"), all.toString());
        } finally {
            fake.stop(0);
        }
    }

    private Map<String, Object> message(String text) {
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("type", "text");
        segment.put("data", Map.of("text", text));

        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("nickname", "甲");
        sender.put("card", "");
        sender.put("role", "member");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("post_type", "message");
        event.put("message_type", "group");
        event.put("self_id", 10000);
        event.put("group_id", 20002);
        event.put("user_id", 10001);
        event.put("time", (int) (System.currentTimeMillis() / 1000));
        event.put("raw_message", text);
        event.put("message", List.of(segment));
        event.put("sender", sender);
        return event;
    }
}
