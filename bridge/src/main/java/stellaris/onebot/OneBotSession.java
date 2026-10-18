package stellaris.onebot;

import stellaris.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;






public abstract class OneBotSession {
    public static final int ONEBOT_V11 = 11;

    private final AtomicLong echoSeq = new AtomicLong(System.currentTimeMillis() % 100000);
    private final Map<String, PendingCall> pending = new ConcurrentHashMap<>();
    private final Set<Consumer<Map<String, Object>>> eventListeners =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Consumer<Throwable>> closeListeners =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final java.util.concurrent.atomic.AtomicBoolean transportClosed =
            new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long selfId = -1;
    private volatile String implName = "OneBot";

    public static final class ActionException extends RuntimeException {







        public final boolean answered;

        public ActionException(String msg) { this(msg, false, null); }
        public ActionException(String msg, Throwable cause) { this(msg, false, cause); }
        public ActionException(String msg, boolean answered, Throwable cause) {
            super(msg, cause);
            this.answered = answered;
        }
    }

    private static final class PendingCall {
        final long deadline;
        final CompletableFutureLike future = new CompletableFutureLike();
        PendingCall(long deadline) { this.deadline = deadline; }
    }


    public static final class CompletableFutureLike {
        Object value;
        Throwable error;
        boolean done;
        final Object lock = new Object();
        public boolean isDone() { synchronized (lock) { return done; } }
        public Object get(long millis) throws InterruptedException {
            long end = System.currentTimeMillis() + millis;
            synchronized (lock) {
                while (!done) {
                    long left = end - System.currentTimeMillis();
                    if (left <= 0) throw new ActionException("action timeout");
                    lock.wait(left);
                }
                if (error != null) throw (error instanceof RuntimeException) ? (RuntimeException) error : new ActionException(error.getMessage(), error);
                return value;
            }
        }
        void complete(Object v) {
            synchronized (lock) { value = v; done = true; lock.notifyAll(); }
        }
        void fail(Throwable t) {
            synchronized (lock) { error = t; done = true; lock.notifyAll(); }
        }
    }



    protected abstract void sendFrame(String json) throws Exception;


    public abstract String label();


    public abstract void close();



    public final void onMessage(String raw) {
        Map<String, Object> m;
        try {
            m = Json.parseObject(raw);
        } catch (Exception e) {
            log("dropping non-JSON frame: " + e.getMessage());
            return;
        }
        Object echo = m.get("echo");
        if (echo != null) {
            PendingCall pc = pending.remove(String.valueOf(echo));
            if (pc != null) {


                Object status = m.get("status");
                Object rc = m.get("retcode");
                boolean failed = "failed".equals(status)
                        || (rc instanceof Number && ((Number) rc).longValue() != 0);
                Object rawData = m.get("data");
                if (failed) {


                    Object wording = m.get("wording");
                    if (wording == null) wording = m.get("message");
                    if (wording == null) wording = m.get("msg");
                    pc.future.fail(new ActionException("action failed: status=" + status
                            + " retcode=" + rc
                            + (wording == null ? "" : " wording=" + wording)
                            + " echo=" + echo, true, null));
                } else {



                    Map<String, Object> out = Json.newObject();
                    if (rawData instanceof Map) {
                        out.putAll((Map<String, Object>) rawData);
                    }
                    out.put("data", rawData == null ? Json.newObject() : rawData);
                    pc.future.complete(out);
                }
                return;
            }
        }

        Object postType = m.get("post_type");
        if ("meta_event".equals(postType)) {
            Object life = m.get("meta_event_type");
            boolean heartbeat = "heartbeat".equals(life);
            if ("lifecycle".equals(life)) {
                Object sub = m.get("sub_type");
                if ("connect".equals(sub)) {
                    selfId = Json.getLong(m, "self_id", -1);
                    implName = String.valueOf(m.getOrDefault("impl", m.getOrDefault("implementation", "OneBot")));
                    onLifecycleConnect(selfId, implName);
                }
                dispatchEvent(m);
            } else if (!heartbeat) {
                dispatchEvent(m);
            }
            return;
        }
        dispatchEvent(m);
    }


    public final void onTransportClosed(Throwable cause) {
        if (!transportClosed.compareAndSet(false, true)) return;
        for (PendingCall pc : pending.values()) {
            pc.future.fail(new ActionException("transport closed", cause));
        }
        pending.clear();
        for (Consumer<Throwable> l : closeListeners) {
            try { l.accept(cause); } catch (Throwable t) { log("close listener failed: " + t); }
        }
        onClosed(cause);
    }


    public final boolean isClosed() { return transportClosed.get(); }


    public final void addCloseListener(Consumer<Throwable> l) { closeListeners.add(l); }


    protected void onLifecycleConnect(long selfId, String implName) {}
    protected void onClosed(Throwable cause) {}
    protected void log(String msg) {}

    public final void addEventListener(Consumer<Map<String, Object>> l) {
        eventListeners.add(l);
    }

    private void dispatchEvent(Map<String, Object> ev) {
        for (Consumer<Map<String, Object>> l : eventListeners) {
            try { l.accept(ev); } catch (Throwable t) { log("event listener failed: " + t); }
        }
    }


    public Map<String, Object> action(String action, Map<String, Object> params, long timeoutMillis) {
        String echo = String.valueOf(echoSeq.incrementAndGet());
        Map<String, Object> frame = Json.newObject();
        frame.put("action", action);
        if (params != null && !params.isEmpty()) frame.put("params", params);
        frame.put("echo", echo);
        PendingCall pc = new PendingCall(System.currentTimeMillis() + timeoutMillis);
        pending.put(echo, pc);
        try {
            sendFrame(Json.write(frame));
        } catch (Exception e) {
            pending.remove(echo);
            throw new ActionException("send failed on " + label() + ": " + e.getMessage(), e);
        }
        try {
            return (Map<String, Object>) pc.future.get(timeoutMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ActionException("interrupted", ie);
        }
    }

    public Map<String, Object> action(String action, Map<String, Object> params) {
        return action(action, params, 15000);
    }


    public long selfId() { return selfId; }
    public void setSelfId(long id) { this.selfId = id; }
    public String implName() { return implName; }

    public boolean sendPrivateMsg(long userId, String message, boolean autoEscape) {
        Map<String, Object> p = Json.newObject();
        p.put("user_id", userId);
        p.put("message", message);
        p.put("auto_escape", autoEscape);
        action("send_private_msg", p);
        return true;
    }

    public boolean sendGroupMsg(long groupId, String message, boolean autoEscape) {
        Map<String, Object> p = Json.newObject();
        p.put("group_id", groupId);
        p.put("message", message);
        p.put("auto_escape", autoEscape);
        action("send_group_msg", p);
        return true;
    }

    public void setGroupLeave(long groupId, isDismiss dismiss) {
        Map<String, Object> p = Json.newObject();
        p.put("group_id", groupId);
        p.put("is_dismiss", dismiss == isDismiss.YES);
        action("set_group_leave", p);
    }

    public enum isDismiss { NO, YES }

    public Map<String, Object> getGroupInfo(long groupId) {
        Map<String, Object> p = Json.newObject();
        p.put("group_id", groupId);
        return action("get_group_info", p);
    }

    public Map<String, Object> getGroupList() {
        return action("get_group_list", null);
    }

    public Map<String, Object> getFriendList() {
        return action("get_friend_list", null);
    }

    public Map<String, Object> getGroupMemberInfo(long groupId, long userId) {
        return getGroupMemberInfo(groupId, userId, false);
    }






    public Map<String, Object> getGroupMemberInfo(long groupId, long userId, boolean noCache) {
        Map<String, Object> p = Json.newObject();
        p.put("group_id", groupId);
        p.put("user_id", userId);
        p.put("no_cache", noCache);
        return action("get_group_member_info", p);
    }

    public Map<String, Object> getLoginInfo() {
        return action("get_login_info", null);
    }

    public void setGroupCard(long groupId, long userId, String card) {
        Map<String, Object> p = Json.newObject();
        p.put("group_id", groupId);
        p.put("user_id", userId);
        p.put("card", card == null ? "" : card);
        action("set_group_card", p);
    }





    public void setGroupSpecialTitle(long groupId, long userId, String title) {
        Map<String, Object> p = Json.newObject();
        p.put("group_id", groupId);
        p.put("user_id", userId);
        p.put("special_title", title == null ? "" : title);

        p.put("duration", -1);
        action("set_group_special_title", p);
    }


    public void setGroupWholeBan(long groupId, boolean enable) {
        Map<String, Object> p = Json.newObject();
        p.put("group_id", groupId);
        p.put("enable", enable);
        action("set_group_whole_ban", p);
    }







    public static String hmacSha1Hex(String secret, String body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] d = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
