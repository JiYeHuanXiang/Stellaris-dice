package stellaris.bridge;

import stellaris.dice.DiceHelper;
import stellaris.dice.DiceIntentEvent;
import stellaris.dice.DiceManager;
import stellaris.dice.DiceMessageSource;
import stellaris.dice.receive.MessageReceived;

import stellaris.onebot.OneBotSession;
import stellaris.util.Json;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;



























public final class Playground {

    private Playground() { }






    public interface Host {

        File diceBasePath();

        List<Long> onlineBots();

        String fallbackBid();
    }

    private static volatile Host host;


    public static void registerHost(Host h) { host = h; }

    private static File hostBase() {
        Host h = host;
        if (h != null) {
            try {
                File f = h.diceBasePath();
                if (f != null) return f;
            } catch (Throwable ignore) { }
        }
        return new File("data");
    }

    private static List<Long> hostBots() {
        Host h = host;
        if (h != null) {
            try {
                List<Long> bots = h.onlineBots();
                if (bots != null) return bots;
            } catch (Throwable ignore) { }
        }
        return java.util.Collections.emptyList();
    }

    private static String hostFallbackBid() {
        Host h = host;
        if (h != null) {
            try {
                String b = h.fallbackBid();
                if (b != null && !b.trim().isEmpty()) return b.trim();
            } catch (Throwable ignore) { }
        }
        return "30001";
    }


    public static final long PG_GROUP = 987654321L;

    public static final long DEFAULT_USER = 123456789L;

    public static final String MARK = "__stella_playground";

    private static final String BOT_ROLE = "member";

    private static final int BUFFER_MAX = 400;



    static final class Identity {
        final String nick;
        final String card;
        final String role;
        Identity(String nick, String card, String role) {
            this.nick = nick; this.card = card; this.role = role;
        }
    }

    static final class State {
        final long botId;
        final VirtualSession session;
        final OneBotIntentEvent virtualIntent;
        final ConcurrentLinkedDeque<Map<String, Object>> buffer = new ConcurrentLinkedDeque<>();
        final java.util.Set<Long> pgUsers = ConcurrentHashMap.newKeySet();
        final Map<Long, Identity> identities = new ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong();

        State(long botId, File dataRoot) {
            this.botId = botId;
            this.session = new VirtualSession(this);
            dataRoot.mkdirs();
            this.virtualIntent = new OneBotIntentEvent(session, botId, dataRoot, null);
        }


        void add(String side, String text) {
            addLine(this, side, "", text);
        }
    }

    private static final ConcurrentHashMap<Long, State> STATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Intent> WRAPPERS = new ConcurrentHashMap<>();


    private static State state(long botId, File dataRoot) {
        State s = STATES.get(botId);
        if (s != null) return s;
        synchronized (STATES) {
            State exist = STATES.get(botId);
            if (exist != null) return exist;
            State created = new State(botId, dataRoot);
            STATES.put(botId, created);
            return created;
        }
    }


    private static State peek(long botId) {
        return STATES.get(botId);
    }






    public static Intent wrap(long botId, DiceIntentEvent real) {
        Intent w = WRAPPERS.computeIfAbsent(botId, Intent::new);
        w.setDelegate(real);
        return w;
    }



    public static final class Intent implements DiceIntentEvent {
        private final long botId;
        private volatile DiceIntentEvent delegate;

        Intent(long botId) { this.botId = botId; }
        void setDelegate(DiceIntentEvent d) { this.delegate = d; }

        private State st() { return peek(botId); }


        private boolean mine(MessageReceived context, DiceMessageSource target) {
            if (context != null) {
                Object c = context.getContext();
                if (c instanceof Map && ((Map<?, ?>) c).get(MARK) != null) return true;
            }
            if (target == null) return false;
            State s = st();
            if (s == null) return false;
            if (target.isGroup() && target.getNumberId() == PG_GROUP) return true;
            return target.isPerson() && s.pgUsers.contains(target.getNumberId());
        }


        private boolean mineGroup(String groupId) {
            if (st() == null) return false;
            try { return groupId != null && Long.parseLong(groupId.trim()) == PG_GROUP; }
            catch (NumberFormatException e) { return false; }
        }

        @Override
        public void sendMessage(String sendContent, MessageReceived context, DiceMessageSource target) {
            if (sendContent == null || target == null) return;
            if (mine(context, target)) { st().virtualIntent.sendMessage(sendContent, context, target); return; }
            DiceIntentEvent d = delegate;
            if (d != null) d.sendMessage(sendContent, context, target);
        }

        @Override
        public boolean sendGroupFile(String filePath, String fileName, String groupId) {

            if (mineGroup(groupId)) return false;
            DiceIntentEvent d = delegate;
            return d != null && d.sendGroupFile(filePath, fileName, groupId);
        }

        @Override
        public boolean setGroupNumberName(String s, String id, String groupId) {
            if (mineGroup(groupId)) return st().virtualIntent.setGroupNumberName(s, id, groupId);
            DiceIntentEvent d = delegate;
            return d != null && d.setGroupNumberName(s, id, groupId);
        }

        @Override
        public boolean setGroupTitle(String s, String id, String groupId) {
            if (mineGroup(groupId)) return st().virtualIntent.setGroupTitle(s, id, groupId);
            DiceIntentEvent d = delegate;
            return d != null && d.setGroupTitle(s, id, groupId);
        }

        @Override
        public boolean setGroupWholeBan(boolean ban, String groupId) {
            if (mineGroup(groupId)) return st().virtualIntent.setGroupWholeBan(ban, groupId);
            DiceIntentEvent d = delegate;
            return d != null && d.setGroupWholeBan(ban, groupId);
        }

        @Override
        public void setDismiss(String groupId,
                               kotlin.jvm.functions.Function1<? super Boolean, kotlin.Unit> callback) {
            if (mineGroup(groupId)) { st().virtualIntent.setDismiss(groupId, callback); return; }
            DiceIntentEvent d = delegate;
            if (d != null) { d.setDismiss(groupId, callback); return; }
            if (callback != null) callback.invoke(false);
        }

        @Override
        public boolean isSelfGroupOperator(String groupId) {

            if (mineGroup(groupId)) return false;
            DiceIntentEvent d = delegate;
            return d != null && d.isSelfGroupOperator(groupId);
        }



        @Override public String botFrame() {
            DiceIntentEvent d = delegate;
            return d != null ? d.botFrame() : "Stella/Playground";
        }
        @Override public boolean hasInitialed() { return true; }
        @Override public void log(String str) {
            DiceIntentEvent d = delegate;
            if (d != null) d.log(str);


            else System.out.println("[playground] " + str);
        }
        @Override public ArrayList<GroupInfo> getGroupInfo() {
            DiceIntentEvent d = delegate;
            return d == null ? null : d.getGroupInfo();
        }
        @Override public ArrayList<FriendsInfo> getFriendsInfo() {
            DiceIntentEvent d = delegate;
            return d == null ? null : d.getFriendsInfo();
        }



































    }



    static final class VirtualSession extends OneBotSession {
        private final State st;

        VirtualSession(State st) {
            this.st = st;
            setSelfId(st.botId);
        }

        @Override public String label() { return "本地体验(虚拟会话)"; }
        @Override public void close() { }
        @Override protected void sendFrame(String json) {

            throw new UnsupportedOperationException("playground session has no transport");
        }


        @Override
        public Map<String, Object> action(String action, Map<String, Object> params, long timeoutMillis) {
            Map<String, Object> data = handle(action, params == null ? Json.newObject() : params);
            Map<String, Object> out = Json.newObject();
            if (data != null) out.putAll(data);
            out.put("data", data == null ? Json.newObject() : data);
            return out;
        }

        private Map<String, Object> handle(String action, Map<String, Object> p) {
            switch (action) {
                case "send_group_msg": {
                    long gid = Json.getLong(p, "group_id", -1);
                    if (gid == PG_GROUP) st.add("dice", prettify(Json.getString(p, "message")));
                    return Json.newObject();
                }
                case "send_private_msg": {
                    long uid = Json.getLong(p, "user_id", -1);
                    if (st.pgUsers.contains(uid)) st.add("dice", prettify(Json.getString(p, "message")));
                    return Json.newObject();
                }
                case "send_group_forward_msg":
                case "send_private_forward_msg":
                case "send_forward_msg": {
                    StringBuilder sb = new StringBuilder();
                    Object msgs = p.get("messages");
                    if (msgs instanceof List) {
                        for (Object o : (List<?>) msgs) {
                            if (!(o instanceof Map)) continue;
                            Object node = Json.getObject((Map<String, Object>) o, "data");
                            if (node == null) continue;
                            if (sb.length() > 0) sb.append("\n────────\n");
                            sb.append(prettify(Json.getString((Map<String, Object>) node, "content")));
                        }
                    }
                    if (sb.length() > 0) st.add("dice", "[合并转发卡]\n" + sb);
                    return Json.newObject();
                }
                case "get_login_info": {
                    Map<String, Object> d = Json.newObject();
                    d.put("user_id", st.botId);
                    d.put("nickname", diceName());
                    return d;
                }
                case "get_group_member_info": {
                    long uid = Json.getLong(p, "user_id", -1);
                    Map<String, Object> d = Json.newObject();
                    d.put("group_id", PG_GROUP);
                    d.put("user_id", uid);
                    if (uid == st.botId) {
                        d.put("nickname", diceName());
                        d.put("card", "");
                        d.put("role", BOT_ROLE);
                    } else {
                        Identity id = st.identities.get(uid);
                        d.put("nickname", id != null ? id.nick : "user-" + uid);
                        d.put("card", id != null ? id.card : "");
                        d.put("role", id != null ? id.role : "member");
                    }
                    return d;
                }
                case "get_group_info": {
                    Map<String, Object> d = Json.newObject();
                    d.put("group_id", PG_GROUP);
                    d.put("group_name", "本地体验群");
                    d.put("member_count", 2);
                    d.put("max_member_count", 500);
                    return d;
                }
                case "get_group_list": {
                    List<Object> arr = Json.newArray();
                    Map<String, Object> g = Json.newObject();
                    g.put("group_id", PG_GROUP);
                    g.put("group_name", "本地体验群");
                    arr.add(g);
                    return memberList(arr);
                }
                case "get_friend_list": {
                    List<Object> arr = Json.newArray();
                    Map<String, Object> self = Json.newObject();
                    self.put("user_id", st.botId);
                    self.put("nickname", diceName());
                    self.put("remark", "");
                    arr.add(self);
                    for (Long uid : st.pgUsers) {
                        Identity id = st.identities.get(uid);
                        Map<String, Object> f = Json.newObject();
                        f.put("user_id", uid);
                        f.put("nickname", id != null ? id.nick : "user-" + uid);
                        f.put("remark", "");
                        arr.add(f);
                    }
                    return memberList(arr);
                }
                case "set_group_card": {
                    long uid = Json.getLong(p, "user_id", -1);
                    String card = Json.getString(p, "card");


                    if (uid != st.botId) {
                        throw new OneBotSession.ActionException(
                                "action failed: retcode=500 wording=没有修改群名片的权限（骰娘在体验群是普通成员）", true, null);
                    }
                    if (card == null || card.isEmpty()) {
                        throw new OneBotSession.ActionException(
                                "action failed: retcode=500 wording=名片不能写成空（QQ 服务端固定规则）", true, null);
                    }
                    st.add("sys", "骰娘把「" + memberName(uid) + "」的群名片改为「" + card + "」（已受理）");
                    return Json.newObject();
                }
                case "set_group_leave": {
                    st.add("sys", "骰娘退出了本地体验群（模拟动作，页面不受影响）");
                    return Json.newObject();
                }
                case "set_group_special_title": {
                    st.add("sys", "骰娘给「" + memberName(Json.getLong(p, "user_id", -1))
                            + "」设置头衔「" + Json.getString(p, "special_title") + "」（已受理）");
                    return Json.newObject();
                }
                case "set_group_whole_ban": {
                    st.add("sys", "骰娘" + (Boolean.TRUE.equals(p.get("enable")) ? "开启了" : "解除了")
                            + "全体禁言（已受理，仅体验面板可见）");
                    return Json.newObject();
                }
                default:

                    return Json.newObject();
            }
        }

        private Map<String, Object> memberList(List<Object> arr) {

            Map<String, Object> d = Json.newObject();
            d.put("data", arr);
            return d;
        }

        private String memberName(long uid) {
            if (uid == st.botId) return diceName();
            Identity id = st.identities.get(uid);
            String card = id != null ? id.card : "";
            return card.isEmpty() ? (id != null ? id.nick : "user-" + uid) : card;
        }

        private String diceName() {
            try {
                DiceHelper dice = DiceManager.getDice(st.botId);
                if (dice != null) {
                    String dn = dice.readDiceConfig("DICE_NAME");
                    if (dn != null && !dn.trim().isEmpty() && !dn.trim().equals(String.valueOf(st.botId))) {
                        return dn.trim();
                    }
                }
            } catch (Throwable ignore) { }
            return "Stella";
        }
    }



    private static final java.util.regex.Pattern CQ_OUT =
            java.util.regex.Pattern.compile("\\[CQ:(\\w+)[^\\]]*]");


    static String prettify(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        java.util.regex.Matcher m = CQ_OUT.matcher(s);
        while (m.find()) {
            String type = m.group(1);
            String zh;
            if ("image".equals(type)) zh = "[图片]";
            else if ("record".equals(type)) zh = "[语音]";
            else if ("video".equals(type)) zh = "[视频]";
            else if ("at".equals(type)) {
                String qq = paramOf(m.group(), "qq");
                zh = "all".equalsIgnoreCase(qq) ? "[@全体成员]" : "[@QQ" + qq + "]";
            }
            else if ("file".equals(type)) zh = "[文件]";
            else if ("json".equals(type)) zh = "[卡片]";
            else zh = "[" + type + "]";
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(zh));
        }
        m.appendTail(sb);
        return sb.toString();
    }


    private static String paramOf(String cq, String key) {
        for (String part : cq.replaceAll("^\\[CQ:\\w+,?|]$", "").split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0 && key.equals(part.substring(0, eq))) return part.substring(eq + 1);
        }
        return "?";
    }


    private static void addLine(State st, String side, String who, String text) {
        Map<String, Object> e = Json.newObject();
        e.put("seq", st.seq.incrementAndGet());
        e.put("ts", System.currentTimeMillis());
        e.put("side", side);
        e.put("who", who == null ? "" : who);
        e.put("text", text);
        st.buffer.addLast(e);
        while (st.buffer.size() > BUFFER_MAX) st.buffer.pollFirst();
    }




    public static long resolveBid(String bidParam) {
        if (bidParam != null) {
            String s = bidParam.trim();
            if (!s.isEmpty()) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignore) { }
            }
        }
        List<Long> bots = hostBots();
        if (!bots.isEmpty()) return bots.get(0);
        try {
            return Long.parseLong(hostFallbackBid());
        } catch (Throwable t) {
            return 30001L;
        }
    }


    public static File dataRootFor(long botId) {
        return new File(hostBase(), "StellarisData_" + botId);
    }

    private static boolean online(long botId) {
        for (long id : hostBots()) if (id == botId) return true;
        return false;
    }


    public static String metaJson(String bidParam) {
        long bid = resolveBid(bidParam);
        Map<String, Object> out = Json.newObject();
        out.put("code", 0);
        out.put("bid", bid);
        out.put("online", online(bid));
        out.put("group", PG_GROUP);
        out.put("user", DEFAULT_USER);
        List<Object> bots = Json.newArray();
        for (long id : hostBots()) bots.add(id);
        out.put("bots", bots);
        return Json.write(out);
    }





    public static String pollJson(String bidParam, long afterSeq) {
        Map<String, Object> out = Json.newObject();
        out.put("code", 0);
        out.put("lines", Json.newArray());
        out.put("lastSeq", 0L);
        State st = peek(resolveBid(bidParam));
        if (st == null) return Json.write(out);
        List<Object> lines = Json.newArray();
        long last = 0;
        for (Map<String, Object> e : st.buffer) {
            long s = Json.getLong(e, "seq", 0);
            if (s > last) last = s;
            if (s > afterSeq) lines.add(e);
        }
        out.put("lines", lines);
        out.put("lastSeq", last);
        return Json.write(out);
    }


    public static String clearJson(String bidParam) {
        State st = peek(resolveBid(bidParam));
        if (st != null) st.buffer.clear();
        Map<String, Object> out = Json.newObject();
        out.put("code", 0);
        return Json.write(out);
    }















    public static void send(long botId, boolean group, long userId, String nick, String card,
                            String role, String text, boolean atMe, File dataRoot) {
        String content = text == null ? "" : text.trim();
        if (content.isEmpty()) throw new IllegalArgumentException("消息不能为空");
        if (content.length() > 8000) content = content.substring(0, 8000);
        if (nick == null || nick.trim().isEmpty()) nick = "user-" + userId;
        if (role == null || role.isEmpty()) role = "member";
        if (!group) { role = "member"; card = ""; }

        Intent wrapper = WRAPPERS.get(botId);
        if (wrapper == null) wrapper = wrap(botId, null);


        DiceHelper dice = DiceManager.createDice(botId, wrapper);
        if (dice == null) throw new IllegalStateException("骰实例创建失败（详见控制台日志）");

        State st = state(botId, dataRoot);
        st.pgUsers.add(userId);
        st.identities.put(userId, new Identity(nick.trim(),
                card == null ? "" : card.trim(), role));


        String who = card != null && !card.trim().isEmpty() ? card.trim() : nick.trim();
        addLine(st, "user", who, content);

        Map<String, Object> ev = Json.newObject();
        ev.put("post_type", "message");
        ev.put("message_type", group ? "group" : "private");
        ev.put("time", (int) (System.currentTimeMillis() / 1000));
        ev.put("self_id", botId);
        ev.put("user_id", userId);
        if (group) ev.put("group_id", PG_GROUP);
        ev.put(MARK, Boolean.TRUE);
        Map<String, Object> sender = Json.newObject();
        sender.put("user_id", userId);
        sender.put("nickname", nick.trim());
        if (group) {
            sender.put("card", card == null ? "" : card.trim());
            sender.put("role", role);
        }
        ev.put("sender", sender);
        List<Object> segs = Json.newArray();
        if (group && atMe) {
            Map<String, Object> at = Json.newObject();
            at.put("type", "at");
            Map<String, Object> atd = Json.newObject();
            atd.put("qq", String.valueOf(botId));
            at.put("data", atd);
            segs.add(at);
        }
        Map<String, Object> tx = Json.newObject();
        tx.put("type", "text");
        Map<String, Object> txd = Json.newObject();
        txd.put("text", content);
        tx.put("data", txd);
        segs.add(tx);
        ev.put("message", segs);
        ev.put("raw_message", (group && atMe ? "[CQ:at,qq=" + botId + "]" : "") + content);

        System.out.println("[stellaris] 本地体验收到消息: "
                + (group ? "群" + PG_GROUP : "私聊") + " <" + who + "> "
                + (content.length() > 80 ? content.substring(0, 80) + "…" : content));
        try {
            new EventTranslator(dice, st.session).translate(ev);
        } catch (Throwable t) {
            addLine(st, "sys", "", "处理失败：" + t);
            System.out.println("[stellaris] 本地体验事件处理失败: " + t);
        }
    }
}
