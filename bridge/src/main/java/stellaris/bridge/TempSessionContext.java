package stellaris.bridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


















final class TempSessionContext {


    private static final long LLOB_SENTINEL_GROUP = 284840486L;
    private static final long TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_ENTRIES = 512;

    private static final Map<Long, long[]> BY_USER = new ConcurrentHashMap<>();

    private TempSessionContext() {}


    static void remember(long userId, long groupId) {
        if (userId <= 0 || groupId <= 0 || groupId == LLOB_SENTINEL_GROUP) return;
        if (BY_USER.size() >= MAX_ENTRIES && !BY_USER.containsKey(userId)) {

            long now = System.currentTimeMillis();
            BY_USER.entrySet().removeIf(e -> e.getValue()[1] <= now);
            if (BY_USER.size() >= MAX_ENTRIES) BY_USER.clear();
        }
        BY_USER.put(userId, new long[]{groupId, System.currentTimeMillis() + TTL_MS});
    }


    static long lookup(long userId) {
        long[] hit = BY_USER.get(userId);
        if (hit == null) return 0L;
        if (hit[1] <= System.currentTimeMillis()) {
            BY_USER.remove(userId);
            return 0L;
        }
        return hit[0];
    }








    static long sourceGroupOf(Map<String, Object> ev) {
        long v = rawGroup(ev.get("temp_source"));
        if (v > 0) return v;
        Map<?, ?> sender = ev.get("sender") instanceof Map ? (Map<?, ?>) ev.get("sender") : null;
        if (sender != null) {
            v = rawGroup(sender.get("group_id"));
            if (v > 0) return v;
        }
        return rawGroup(ev.get("group_id"));
    }

    private static long rawGroup(Object o) {
        if (o instanceof Number) {
            long v = ((Number) o).longValue();
            return (v > 0 && v != LLOB_SENTINEL_GROUP) ? v : 0L;
        }
        if (o instanceof String) {
            try {
                long v = Long.parseLong(((String) o).trim());
                return (v > 0 && v != LLOB_SENTINEL_GROUP) ? v : 0L;
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
