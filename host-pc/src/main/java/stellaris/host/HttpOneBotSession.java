package stellaris.host;

import stellaris.onebot.OneBotSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;








public final class HttpOneBotSession extends OneBotSession {

    private final URI api;
    private final String accessToken;
    private final HttpClient client;

    public HttpOneBotSession(String apiUrl, String accessToken) {
        this.api = URI.create(apiUrl);
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    protected void sendFrame(String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(api)
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (!accessToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "OneBot HTTP " + response.statusCode() + "：" + response.body());
        }

        onMessage(response.body());
    }

    @Override
    public String label() {
        return "HTTP " + api;
    }

    @Override
    public void close() {

    }
}
