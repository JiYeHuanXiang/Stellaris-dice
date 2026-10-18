package stellaris.bridge;

import java.util.ArrayList;
import java.util.List;





























public final class MiraiCodes {

    private static final String PREFIX = "[mirai:";

    private MiraiCodes() {}


    public static String translate(String in) {
        if (in == null || in.isEmpty()) return in;
        StringBuilder out = null;
        int i = 0;
        int n = in.length();
        while (i < n) {
            int start = in.indexOf(PREFIX, i);
            if (start < 0) break;
            int close = findClose(in, start + PREFIX.length());
            if (close < 0) break;
            String cq = toCq(in.substring(start + PREFIX.length(), close));
            if (out == null) out = new StringBuilder(n);
            if (cq == null) out.append(in, i, close + 1);
            else out.append(in, i, start).append(cq);
            i = close + 1;
        }
        if (out == null) return in;
        out.append(in, i, n);
        return out.toString();
    }


    private static int findClose(String s, int from) {
        int i = from;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) { i += 2; continue; }
            if (c == ']') return i;
            i++;
        }
        return -1;
    }


    private static String toCq(String body) {
        String type = body;
        String rest = "";
        int colon = indexOfUnescaped(body, ':');
        if (colon >= 0) {
            type = body.substring(0, colon);
            rest = body.substring(colon + 1);
        }
        switch (type) {
            case "at": {
                String id = unescape(rest).trim();
                if ("all".equalsIgnoreCase(id)) return "[CQ:at,qq=all]";
                if (!id.matches("\\d+")) return null;
                return "[CQ:at,qq=" + id + "]";
            }
            case "atall":
                return "[CQ:at,qq=all]";
            case "face": {
                String id = unescape(rest).trim();
                if (!id.matches("\\d+")) return null;
                return "[CQ:face,id=" + id + "]";
            }
            case "image": {
                String f = unescape(rest).trim();
                if (f.isEmpty()) return null;
                return "[CQ:image,file=" + cqEscape(f) + "]";
            }
            case "flashimage": {
                String f = unescape(rest).trim();
                if (f.isEmpty()) return null;
                return "[CQ:image,type=flash,file=" + cqEscape(f) + "]";
            }
            case "voice": {
                String f = unescape(rest).trim();
                if (f.isEmpty()) return null;
                return "[CQ:record,file=" + cqEscape(f) + "]";
            }
            case "musicshare": {
                List<String> p = splitUnescaped(rest, ',');
                if (p.size() < 6) return null;
                String title = unescape(p.get(1));
                String summary = unescape(p.get(2));
                String jump = unescape(p.get(3));
                String pic = unescape(p.get(4));
                String audio = unescape(p.get(5));
                StringBuilder sb = new StringBuilder("[CQ:music,type=custom");
                if (!jump.isEmpty()) sb.append(",url=").append(cqEscape(jump));
                if (!audio.isEmpty()) sb.append(",audio=").append(cqEscape(audio));
                if (!title.isEmpty()) sb.append(",title=").append(cqEscape(title));
                if (!summary.isEmpty()) sb.append(",content=").append(cqEscape(summary));
                if (!pic.isEmpty()) sb.append(",image=").append(cqEscape(pic));
                return sb.append(']').toString();
            }
            case "app":
            case "json": {
                String data = unescape(rest);
                if (data.isEmpty()) return null;
                return "[CQ:json,data=" + cqEscape(data) + "]";
            }
            case "xml": {
                String data = unescape(rest);
                if (data.isEmpty()) return null;
                return "[CQ:xml,data=" + cqEscape(data) + "]";
            }
            default:
                return null;
        }
    }


    private static int indexOfUnescaped(String s, char ch) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) { i += 2; continue; }
            if (c == ch) return i;
            i++;
        }
        return -1;
    }


    private static List<String> splitUnescaped(String s, char sep) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) { i += 2; continue; }
            if (c == sep) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        out.add(s.substring(start));
        return out;
    }


    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char nx = s.charAt(i + 1);
                if (nx == '[' || nx == ']' || nx == ':' || nx == ',' || nx == '\\' || nx == '#') {
                    b.append(nx);
                    i += 2;
                    continue;
                }
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }


    static String cqEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("[", "&#91;")
                .replace("]", "&#93;")
                .replace(",", "&#44;");
    }
}
