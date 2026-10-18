package stellaris.host;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import stellaris.bridge.DiceBoot;
import stellaris.bridge.EventTranslator;
import stellaris.dice.DiceHelper;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;







public final class HostRuntime implements AutoCloseable, HostControl {

    private HttpServer server;
    private HttpOneBotSession session;
    private DiceHelper helper;
    private String runningBotQq = "";
    private HostConfig config = new HostConfig();

    private String basePath;

    public synchronized void start(HostConfig config) throws Exception {
        if (server != null) stop();
        String problem = config.validate();
        if (problem != null) throw new IllegalArgumentException(problem);
        if (basePath != null && !basePath.equals(config.dataDir)) {
            throw new IllegalStateException(
                    "数据根已在本进程内初始化为 " + basePath + "；修改后请重启程序");
        }
        basePath = config.dataDir;

        HttpOneBotSession newSession = new HttpOneBotSession(config.apiUrl, config.accessToken);
        DiceHelper newHelper;
        try {
            newHelper = DiceBoot.boot(
                    config.botQq, config.dataDir, newSession, new File(config.contentDir));
        } catch (Throwable t) {
            newSession.close();
            throw t;
        }
        EventTranslator translator = new EventTranslator(newHelper, newSession);
        newSession.addEventListener(translator::translate);

        HttpServer newServer = HttpServer.create(
                new InetSocketAddress(config.eventPort), 0);
        newServer.setExecutor(Executors.newFixedThreadPool(4));
        newServer.createContext("/", exchange -> handleEvent(newSession, exchange));
        newServer.start();

        session = newSession;
        helper = newHelper;
        server = newServer;
        runningBotQq = config.botQq;
        this.config = config.copy();
    }

    @Override
    public synchronized HostConfig config() {
        return config.copy();
    }

    @Override
    public synchronized void updateConfig(HostConfig newConfig) throws IOException {
        newConfig.save(HostConfig.CONFIG_FILE);
        config = newConfig.copy();
    }

    @Override
    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized String runningBotQq() {
        return runningBotQq;
    }

    public synchronized DiceHelper helper() {
        return helper;
    }

    @Override
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (session != null) {
            session.close();
            session = null;
        }
        helper = null;
        runningBotQq = "";
    }

    @Override
    public void close() {
        stop();
    }

    private static void handleEvent(HttpOneBotSession session, HttpExchange exchange)
            throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {

            session.onMessage(body);
            exchange.sendResponseHeaders(204, -1);
        } catch (Throwable t) {
            System.err.println("[群星] 事件处理失败：" + t);
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }
}
