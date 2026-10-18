package stellaris.host.android;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import stellaris.onebot.OneBotSession;

import java.util.concurrent.TimeUnit;








public final class AndroidOneBotSession extends OneBotSession {

    private final OkHttpClient client;
    private final Request request;
    private volatile WebSocket socket;

    public AndroidOneBotSession(String url, String accessToken) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + accessToken.trim());
        }
        this.request = requestBuilder.build();
        this.client = builder.build();
    }

    public void connect() {
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log("WS 已连接：" + request.url());
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                AndroidOneBotSession.this.onMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log("WS 失败：" + t);
                onTransportClosed(t);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                onTransportClosed(null);
            }
        });
    }

    @Override
    protected void sendFrame(String json) {
        WebSocket current = socket;
        if (current == null) {
            throw new IllegalStateException("WebSocket 尚未连接");
        }
        if (!current.send(json)) {
            throw new IllegalStateException("WebSocket 发送失败");
        }
    }

    @Override
    public String label() {
        return "WS " + request.url();
    }

    @Override
    public void close() {
        WebSocket current = socket;
        if (current != null) {
            current.close(1000, "bye");
            socket = null;
        }
        client.dispatcher().executorService().shutdown();
    }
}
