package stellaris.onebot;

import stellaris.util.Json;

import java.util.Map;







public final class MusicArk {


    private static final String MUSIC_CQ = "[CQ:music";

    private MusicArk() {}


    public static String arkJson(Map<String, Object> d) {
        String title = str(d.get("title"));

        Map<String, Object> music = Json.newObject();
        music.put("action", "");
        music.put("android_pkg_name", "");
        music.put("app_type", 1);
        music.put("appid", 100497308);
        music.put("jump_url", str(d.get("url")));
        music.put("music_type", 0);
        music.put("preview", str(d.get("image")));
        music.put("share_type", 3);
        music.put("source_icon", "");
        music.put("source_url", str(d.get("audio")));
        music.put("tag", str(d.get("content")));
        music.put("title", title);

        Map<String, Object> meta = Json.newObject();
        meta.put("music", music);

        Map<String, Object> config = Json.newObject();
        config.put("type", "normal");
        config.put("forward", true);

        Map<String, Object> ark = Json.newObject();
        ark.put("app", "com.tencent.structmsg");
        ark.put("config", config);
        ark.put("desc", "音乐");
        ark.put("meta", meta);
        ark.put("prompt", "[分享]" + title);
        ark.put("ver", "0.0.0.1");
        ark.put("view", "music");
        return Json.write(ark);
    }







    public static String customToArkJson(String text) {
        if (text == null || text.indexOf(MUSIC_CQ) < 0) return text;
        StringBuilder out = null;
        int i = 0;
        int n = text.length();
        while (i < n) {
            int start = text.indexOf(MUSIC_CQ, i);
            if (start < 0) break;
            int end = text.indexOf(']', start);
            if (end < 0) break;
            String rewritten = customCq(text.substring(start + 4, end));
            if (out == null) out = new StringBuilder(n + 512);
            out.append(text, i, start);
            if (rewritten == null) out.append(text, start, end + 1);
            else out.append(rewritten);
            i = end + 1;
        }
        if (out == null) return text;
        out.append(text, i, n);
        return out.toString();
    }


    private static String customCq(String body) {
        Map<String, Object> d = Json.newObject();
        String[] head = body.split(",", 2);
        if (head.length > 1) {
            for (String kv : head[1].split(",")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                d.put(kv.substring(0, eq).trim(), unescapeCq(kv.substring(eq + 1).trim()));
            }
        }
        if (!"custom".equalsIgnoreCase(str(d.get("type")))) return null;
        return "[CQ:json,data=" + cqEscape(arkJson(d)) + "]";
    }


    private static String cqEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("[", "&#91;")
                .replace("]", "&#93;")
                .replace(",", "&#44;");
    }

    private static String unescapeCq(String v) {
        return v.replace("&#91;", "[").replace("&#93;", "]")
                .replace("&#44;", ",").replace("&amp;", "&");
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
