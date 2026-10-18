package stellaris.host;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;





class WebUiApiTest {

    private static final class StubControl implements HostControl {
        final HostConfig config = new HostConfig();
        boolean running = true;
        boolean stopped;

        StubControl() {
            config.botQq = "10000";
            config.apiUrl = "http://127.0.0.1:3000";
        }

        @Override public boolean isRunning() { return running; }
        @Override public String runningBotQq() { return running ? config.botQq : ""; }
        @Override public HostConfig config() { return config.copy(); }
        @Override public void updateConfig(HostConfig next) { config.botQq = next.botQq; }
        @Override public void start(HostConfig next) { running = true; }
        @Override public void stop() { running = false; stopped = true; }
    }

    @Test
    void apiRequiresTokenAndExposesStatus() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        StubControl control = new StubControl();
        WebUiApiServer api = new WebUiApiServer(control, "secret");
        api.start(port);
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + port);

            HttpResponse<String> unauthorized = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/status")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Assertions.assertEquals(401, unauthorized.statusCode());

            HttpResponse<String> status = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/status"))
                            .header("Authorization", "Bearer secret")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Assertions.assertEquals(200, status.statusCode());
            Assertions.assertTrue(status.body().contains("\"running\":true"), status.body());
            Assertions.assertTrue(status.body().contains("10000"), status.body());

            HttpResponse<String> stop = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/stop"))
                            .header("Authorization", "Bearer secret")
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Assertions.assertEquals(200, stop.statusCode());
            Assertions.assertTrue(control.stopped, "stop 端点应调到控制面");
        } finally {
            api.stop();
        }
    }
}
