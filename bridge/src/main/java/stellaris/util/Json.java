package stellaris.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;







public final class Json {

    private Json() {}

    public static Map<String, Object> parseObject(String text) {
        return toMap(new JSONObject(text));
    }

    public static Map<String, Object> newObject() {
        return new LinkedHashMap<>();
    }

    public static List<Object> newArray() {
        return new ArrayList<>();
    }

    public static String write(Object value) {
        return toJson(value).toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Map) return (Map<String, Object>) value;
        if (value instanceof JSONObject) return toMap((JSONObject) value);
        return null;
    }

    public static List<Object> getArray(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            return list;
        }
        if (value instanceof JSONArray) return toList((JSONArray) value);
        return null;
    }

    public static String getString(Map<String, Object> map, String key) {
        return getString(map, key, "");
    }

    public static String getString(Map<String, Object> map, String key, String fallback) {
        Object value = map == null ? null : map.get(key);
        if (value == null || value == JSONObject.NULL) return fallback;
        return value instanceof String ? (String) value : String.valueOf(value);
    }

    public static long getLong(Map<String, Object> map, String key, long fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public static int getInt(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public static boolean getBool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase();
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
            if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        }
        return fallback;
    }

    private static Map<String, Object> toMap(JSONObject object) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : object.keySet()) {
            out.put(key, fromJson(object.opt(key)));
        }
        return out;
    }

    private static List<Object> toList(JSONArray array) {
        List<Object> out = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            out.add(fromJson(array.opt(i)));
        }
        return out;
    }

    private static Object fromJson(Object value) {
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof JSONObject) return toMap((JSONObject) value);
        if (value instanceof JSONArray) return toList((JSONArray) value);
        return value;
    }

    private static Object toJson(Object value) {
        if (value instanceof Map) {
            JSONObject out = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(entry.getKey()), toJson(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List) {
            JSONArray out = new JSONArray();
            for (Object item : (List<?>) value) out.put(toJson(item));
            return out;
        }
        return value;
    }
}
