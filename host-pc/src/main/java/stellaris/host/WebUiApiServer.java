package stellaris.host;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import stellaris.util.Json;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;


















public final class WebUiApiServer implements AutoCloseable {

    private final HostControl control;
    private final String token;
    private HttpServer server;

    public WebUiApiServer(HostControl control, String token) {
        this.control = control;
        this.token = token == null ? "" : token.trim();
    }

    public synchronized void start(int port) throws IOException {
        if (server != null) stop();
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("WebUI 接口端口必须在 1~65535");
        }
        HttpServer newServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        newServer.setExecutor(Executors.newFixedThreadPool(2));
        newServer.createContext("/", this::handle);
        newServer.start();
        server = newServer;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                write(exchange, 204, null);
                return;
            }
            if (!authorized(exchange)) {
                write(exchange, 401, Map.of("error", "unauthorized"));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            switch (path) {
                case "/":
                    write(exchange, 200, index());
                    return;
                case "/api/health":
                    write(exchange, 200, Map.of("ok", true));
                    return;
                case "/api/status":
                    write(exchange, 200, status());
                    return;
                case "/api/config":
                    if ("GET".equals(method)) {
                        write(exchange, 200, control.config().toMap(true));
                    } else if ("POST".equals(method)) {
                        updateConfig(exchange);
                    } else {
                        write(exchange, 405, Map.of("error", "method not allowed"));
                    }
                    return;
                case "/api/start":
                    if (!"POST".equals(method)) {
                        write(exchange, 405, Map.of("error", "method not allowed"));
                        return;
                    }
                    control.start(control.config());
                    write(exchange, 200, status());
                    return;
                case "/api/stop":
                    if (!"POST".equals(method)) {
                        write(exchange, 405, Map.of("error", "method not allowed"));
                        return;
                    }
                    control.stop();
                    write(exchange, 200, status());
                    return;
                default:
                    write(exchange, 404, Map.of("error", "not found"));
            }
        } catch (Throwable t) {
            write(exchange, 500, Map.of("error", String.valueOf(t.getMessage())));
        }
    }

    private void updateConfig(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        HostConfig config = HostConfig.fromMap(Json.parseObject(body));
        String problem = config.validate();
        if (problem != null) {
            write(exchange, 400, Map.of("error", problem));
            return;
        }
        control.updateConfig(config);
        write(exchange, 200, control.config().toMap(true));
    }

    private Map<String, Object> index() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("service", "stellaris-dice");
        map.put("note", "本仓库不内置 WebUI 页面；社区可基于这些端点自建网页端。");
        map.put("endpoints", java.util.List.of(
                "GET /api/health", "GET /api/status", "GET /api/config",
                "POST /api/config", "POST /api/start", "POST /api/stop"
        ));
        return map;
    }

    private Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("running", control.isRunning());
        map.put("botQq", control.runningBotQq());
        HostConfig config = control.config();
        map.put("eventPort", config.eventPort);
        map.put("apiUrl", config.apiUrl);
        map.put("webUiPort", config.webUiPort);
        return map;
    }

    private boolean authorized(HttpExchange exchange) {
        if (token.isEmpty()) return true;
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.equals("Bearer " + token)) return true;
        String query = exchange.getRequestURI().getQuery();
        return query != null && query.contains("token=" + token);
    }

    private void write(HttpExchange exchange, int code, Map<String, Object> body) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        if (body == null) {
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
            return;
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
