package stellaris.host;

import stellaris.util.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;







public final class HostConfig {


    public static final File CONFIG_FILE = new File("config/host.json");

    public String botQq = "";
    public String apiUrl = "http://127.0.0.1:3000";
    public int eventPort = 5701;
    public String accessToken = "";
    public String dataDir = "data";
    public String contentDir = "content";






    public int webUiPort = 0;
    public String webUiToken = "";


    public static HostConfig fromEnv() {
        HostConfig config = new HostConfig();
        config.botQq = value("STELLARIS_BOT_QQ", "");
        config.apiUrl = value("STELLARIS_ONEBOT_API", config.apiUrl);
        config.eventPort = Integer.parseInt(value("STELLARIS_EVENT_PORT", "5701"));
        config.accessToken = value("STELLARIS_ACCESS_TOKEN", "");
        config.dataDir = value("STELLARIS_DATA", config.dataDir);
        config.contentDir = value("STELLARIS_CONTENT", config.contentDir);
        config.webUiPort = Integer.parseInt(value("STELLARIS_WEBUI_PORT", "0"));
        config.webUiToken = value("STELLARIS_WEBUI_TOKEN", "");
        return config;
    }


    public static HostConfig load(File file) {
        if (!file.isFile()) return new HostConfig();
        try {
            Map<String, Object> map = Json.parseObject(
                    Files.readString(file.toPath(), StandardCharsets.UTF_8));
            HostConfig config = new HostConfig();
            config.botQq = Json.getString(map, "botQq", config.botQq);
            config.apiUrl = Json.getString(map, "apiUrl", config.apiUrl);
            config.eventPort = Json.getInt(map, "eventPort", config.eventPort);
            config.accessToken = Json.getString(map, "accessToken", config.accessToken);
            config.dataDir = Json.getString(map, "dataDir", config.dataDir);
            config.contentDir = Json.getString(map, "contentDir", config.contentDir);
            config.webUiPort = Json.getInt(map, "webUiPort", config.webUiPort);
            config.webUiToken = Json.getString(map, "webUiToken", config.webUiToken);
            return config;
        } catch (Throwable t) {
            System.err.println("[群星] 配置读取失败，使用默认值：" + t);
            return new HostConfig();
        }
    }


    public HostConfig copy() {
        HostConfig copy = new HostConfig();
        copy.botQq = botQq;
        copy.apiUrl = apiUrl;
        copy.eventPort = eventPort;
        copy.accessToken = accessToken;
        copy.dataDir = dataDir;
        copy.contentDir = contentDir;
        copy.webUiPort = webUiPort;
        copy.webUiToken = webUiToken;
        return copy;
    }


    public Map<String, Object> toMap(boolean maskToken) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("botQq", botQq);
        map.put("apiUrl", apiUrl);
        map.put("eventPort", eventPort);
        map.put("accessToken", maskToken ? (accessToken == null || accessToken.isEmpty() ? "" : "***") : accessToken);
        map.put("dataDir", dataDir);
        map.put("contentDir", contentDir);
        map.put("webUiPort", webUiPort);
        map.put("webUiToken", maskToken ? (webUiToken == null || webUiToken.isEmpty() ? "" : "***") : webUiToken);
        return map;
    }


    public static HostConfig fromMap(Map<String, Object> map) {
        HostConfig config = new HostConfig();
        config.botQq = Json.getString(map, "botQq", config.botQq);
        config.apiUrl = Json.getString(map, "apiUrl", config.apiUrl);
        config.eventPort = Json.getInt(map, "eventPort", config.eventPort);
        config.accessToken = Json.getString(map, "accessToken", config.accessToken);
        config.dataDir = Json.getString(map, "dataDir", config.dataDir);
        config.contentDir = Json.getString(map, "contentDir", config.contentDir);
        config.webUiPort = Json.getInt(map, "webUiPort", config.webUiPort);
        config.webUiToken = Json.getString(map, "webUiToken", config.webUiToken);
        return config;
    }


    public void save(File file) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("botQq", botQq);
        map.put("apiUrl", apiUrl);
        map.put("eventPort", eventPort);
        map.put("accessToken", accessToken);
        map.put("dataDir", dataDir);
        map.put("contentDir", contentDir);
        map.put("webUiPort", webUiPort);
        map.put("webUiToken", webUiToken);
        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Files.writeString(file.toPath(), Json.write(map), StandardCharsets.UTF_8);
    }


    public String validate() {
        if (botQq == null || !botQq.matches("\\d{5,12}")) {
            return "骰娘 QQ 必须是 5~12 位数字";
        }
        if (apiUrl == null
                || !(apiUrl.startsWith("http://") || apiUrl.startsWith("https://"))) {
            return "OneBot API 地址必须是 http:// 或 https://";
        }
        if (eventPort < 1 || eventPort > 65535) {
            return "事件端口必须在 1~65535";
        }
        if (dataDir == null || dataDir.isBlank()) {
            return "数据根不能为空";
        }
        if (contentDir == null || contentDir.isBlank()) {
            return "内容目录不能为空";
        }
        return null;
    }

    private static String value(String key, String fallback) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isEmpty()) return fromEnv;
        String fromProperty = System.getProperty(key);
        return fromProperty == null || fromProperty.isEmpty() ? fallback : fromProperty;
    }
}
