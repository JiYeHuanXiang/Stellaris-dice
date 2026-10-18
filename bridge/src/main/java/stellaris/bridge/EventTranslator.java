package stellaris.bridge;

import stellaris.dice.DiceHelper;
import stellaris.dice.DiceRequestCallback;
import stellaris.dice.receive.MessageReceived;
import stellaris.onebot.OneBotSession;
import stellaris.util.Json;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;















public final class EventTranslator {
    private static final Pattern CQ_AT = Pattern.compile("\\[CQ:at,qq=(\\d+)]");
    private static final Pattern CQ_ANY = Pattern.compile("\\[CQ:[^\\]]*]");

    private final DiceHelper dice;
    private final OneBotSession session;
    private final OneBotIntentEvent.ChannelResolver resolver;
    private final long selfId;
    private final String selfIdStr;








    private final Map<Long, String> latestFriendFlag = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Long, String> latestGroupFlag = new java.util.concurrent.ConcurrentHashMap<>();

    public EventTranslator(DiceHelper dice, OneBotSession session) {
        this(dice, session, null);
    }

    public EventTranslator(DiceHelper dice, OneBotSession session, OneBotIntentEvent.ChannelResolver resolver) {
        this.dice = dice;
        this.session = session;
        this.resolver = resolver;
        this.selfId = session.selfId();
        this.selfIdStr = String.valueOf(selfId);
    }


    private OneBotSession chan() {
        if (resolver != null) {
            try {
                OneBotSession s = resolver.resolve(selfId, session);
                if (s != null) return s;
            } catch (Throwable ignore) {

            }
        }
        return session;
    }


    public void translate(Map<String, Object> ev) {
        String postType = String.valueOf(ev.getOrDefault("post_type", ""));
        switch (postType) {
            case "message": translateMessage(ev); break;





            case "message_sent": translateMessage(ev); break;
            case "notice": translateNotice(ev); break;
            case "request": translateRequest(ev); break;
            case "meta_event": break;
            default: break;
        }
    }



    private void translateMessage(Map<String, Object> ev) {
        String msgType = String.valueOf(ev.getOrDefault("message_type", ""));
        if (!"group".equals(msgType) && !"private".equals(msgType)) return;







        if (HostSettings.detectSelfMessage() && Json.getLong(ev, "user_id", -1) == selfId) {
            System.out.println("[stellaris] 已忽略自身消息 (user_id==self_id==" + selfId + ")，防回环");
            return;
        }






        OneBotSession outbound = chan();
        if (CoreModeGate.INSTANCE.intercept(ev, outbound, dice)) return;



        boolean isGroup = "group".equals(msgType);
        long userId = Json.getLong(ev, "user_id", -1);
        long groupId = isGroup ? Json.getLong(ev, "group_id", -1) : -1;



        if (!isGroup) {
            long tempGroup = TempSessionContext.sourceGroupOf(ev);
            if (tempGroup > 0) TempSessionContext.remember(userId, tempGroup);
        }
        int time = Json.getInt(ev, "time", (int) (System.currentTimeMillis() / 1000));



        System.out.println("[stellaris] 收到消息: "
                + (isGroup ? ("群" + groupId) : "私聊") + " <" + nickOf(ev) + "> "
                + preview(buildPreviewText(ev)));

        String nick = "user-" + userId;
        boolean isAdmin = false, isOwner = false;
        Map<String, Object> sender = Json.getObject(ev, "sender");
        if (sender != null) {
            String n = Json.getString(sender, "card");
            if (n == null || n.isEmpty()) n = Json.getString(sender, "nickname");
            if (n != null && !n.isEmpty()) nick = n;
            String role = String.valueOf(sender.getOrDefault("role", "")).toLowerCase();




            isOwner = "owner".equals(role);
            isAdmin = isOwner || "admin".equals(role) || "administrator".equals(role);
        }

        Content content = buildContent(ev);
        MessageReceived msg = MessageReceived.Companion.buildMessageEvent(
                dice.getDiceFunction(),
                content.text,
                isGroup ? String.valueOf(groupId) : null,
                String.valueOf(userId),
                nick,
                nick,
                time,
                isAdmin,
                isOwner,
                isGroup,
                content.isAtMe,

                isGroup && CoreModeGate.INSTANCE.isPureFor(groupId),
                ev,

                content.atTargets);
        try {
            dice.eventMessageReceive(msg);
        } catch (Throwable t) {
            System.out.println("[stellaris] 骰核处理事件失败: " + t);
            t.printStackTrace();
        }
    }

    static final class Content {
        final String text;
        final boolean isAtMe;







        final List<String> atTargets;

        Content(String text, boolean isAtMe, List<String> atTargets) {
            this.text = text;
            this.isAtMe = isAtMe;
            this.atTargets = atTargets;
        }
    }


    private volatile File imageRoot;
    private boolean imageRootResolved;

    private File imageRoot() {
        if (!imageRootResolved) {
            try {
                imageRoot = new File(dice.getDiceFunction().root());
            } catch (Throwable t) {
                imageRoot = null;
                return null;
            }
            imageRootResolved = true;
        }
        return imageRoot;
    }






    public String textForProbe(Map<String, Object> ev) {
        return buildContent(ev).text;
    }



    static String nickOf(Map<String, Object> ev) {
        Map<String, Object> sender = Json.getObject(ev, "sender");
        if (sender != null) {
            String n = Json.getString(sender, "card");
            if (n == null || n.isEmpty()) n = Json.getString(sender, "nickname");
            if (n != null && !n.isEmpty()) return n;
        }
        return "user-" + Json.getLong(ev, "user_id", -1);
    }


    static String buildPreviewText(Map<String, Object> ev) {
        Object raw = ev.get("message");
        StringBuilder sb = new StringBuilder();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> seg = (Map<String, Object>) o;
                String type = String.valueOf(seg.get("type"));
                if ("text".equals(type)) {
                    Map<String, Object> d = castData(seg);
                    Object t = d == null ? null : d.get("text");
                    if (t != null) sb.append(t);
                } else if ("at".equals(type)) {
                    Map<String, Object> d = castData(seg);
                    sb.append('@').append(d == null ? "?" : d.get("qq"));
                } else if (sb.length() == 0 || sb.charAt(sb.length() - 1) != ' ') {
                    sb.append('[').append(type).append(']');
                }
            }
        } else {
            String s = String.valueOf(ev.getOrDefault("raw_message", ""));
            sb.append(CQ_ANY.matcher(s).replaceAll(""));
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castData(Map<String, Object> seg) {
        Object d = seg.get("data");
        return d instanceof Map ? (Map<String, Object>) d : null;
    }

    static String preview(String text) {
        String t = text.replaceAll("\\s+", " ");
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }











    Content buildContent(Map<String, Object> ev) {
        StringBuilder sb = new StringBuilder();
        boolean isAtMe = false;
        List<String> atTargets = new ArrayList<>();
        Object raw = ev.get("message");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> seg = (Map<String, Object>) o;
                String type = String.valueOf(seg.get("type"));
                Map<String, Object> d = Json.getObject(seg, "data");
                if ("text".equals(type) && d != null && d.get("text") != null) {
                    sb.append(d.get("text"));
                } else if ("at".equals(type) && d != null && d.get("qq") != null) {
                    String qq = String.valueOf(d.get("qq"));
                    if (qq.equals(selfIdStr)) isAtMe = true;
                    if (qq.matches("\\d+")) {
                        sb.append(qq);
                        if (!qq.equals(selfIdStr) && !atTargets.contains(qq)) atTargets.add(qq);
                    }
                } else if ("image".equals(type) && d != null) {




                    String cached = ImageCache.enabled() ? ImageCache.resolve(imageRoot(), d) : null;
                    if (cached != null) {
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
                        sb.append(cached);
                    }
                }

            }
        } else {
            String s = String.valueOf(ev.getOrDefault("raw_message", ""));
            Matcher at = CQ_AT.matcher(s);
            StringBuffer out = new StringBuffer();
            while (at.find()) {
                String qq = at.group(1);
                if (qq.equals(selfIdStr)) {
                    isAtMe = true;
                } else if (!atTargets.contains(qq)) {
                    atTargets.add(qq);
                }
                at.appendReplacement(out, Matcher.quoteReplacement(qq));
            }
            at.appendTail(out);
            sb.append(CQ_ANY.matcher(out.toString()).replaceAll(""));
        }
        String text = sb.toString().trim();
        if (text.startsWith(selfIdStr)) {
            text = text.substring(selfIdStr.length()).trim();
            isAtMe = true;
        }
        return new Content(text, isAtMe, atTargets);
    }



    private void translateNotice(Map<String, Object> ev) {
        String noticeType = String.valueOf(ev.getOrDefault("notice_type", ""));
        long userId = Json.getLong(ev, "user_id", -1);
        long groupId = Json.getLong(ev, "group_id", -1);

        switch (noticeType) {
            case "group_increase": {
                long operator = Json.getLong(ev, "operator_id", -1);
                if (userId == selfId) {
                    dice.eventJoinNewGroup(groupId, operator > 0 ? operator : 0);
                } else {
                    dispatchMemberJoin(groupId, userId, ev);
                }
                break;
            }
            case "group_decrease": {
                String sub = String.valueOf(ev.getOrDefault("sub_type", ""));
                if ("kick_me".equals(sub)) {
                    long operator = Json.getLong(ev, "operator_id", -1);
                    dice.eventKick(operator, groupId);
                }
                break;
            }
            case "group_ban": {
                String sub = String.valueOf(ev.getOrDefault("sub_type", ""));
                if ("ban".equals(sub) && userId == selfId) {
                    long operator = Json.getLong(ev, "operator_id", -1);
                    dice.eventMute(operator, groupId);
                }
                break;
            }
            case "notify": {
                if ("poke".equals(String.valueOf(ev.getOrDefault("sub_type", "")))) {
                    translatePoke(ev, userId, groupId);
                }
                break;
            }
            default: break;
        }
    }














    private void dispatchMemberJoin(final long groupId, final long userId, final Map<String, Object> ev) {
        Thread t = new Thread(() -> {
            String nick = null;
            String groupNick = null;

            Map<String, Object> sender = Json.getObject(ev, "sender");
            if (sender != null) {
                nick = Json.getString(sender, "nickname");
                groupNick = Json.getString(sender, "card");
                if (groupNick == null || groupNick.isEmpty()) groupNick = nick;
            }
            if (nick == null || nick.isEmpty()) {
                try {
                    Map<String, Object> info = chan().getGroupMemberInfo(groupId, userId, true);
                    String n = Json.getString(info, "nickname");
                    String card = Json.getString(info, "card");
                    if (n != null && !n.isEmpty()) nick = n;
                    if (card != null && !card.isEmpty()) groupNick = card;
                    else if (groupNick == null || groupNick.isEmpty()) groupNick = nick;
                } catch (Throwable e) {
                    System.out.println("[stellaris] 入群成员信息查询失败（群" + groupId + " 用户" + userId
                            + "）：" + e.getMessage() + " —— 欢迎语里的 {user} 会是 undefined");
                }
            }
            dice.eventNewMemberJoin(MessageReceived.Companion.buildMessageEvent(
                    dice.getDiceFunction(), null, String.valueOf(groupId),
                    String.valueOf(userId), nick, groupNick, 0,

                    false, false, false, false, false, ev, java.util.Collections.emptyList()));
        }, "stellaris-member-join");
        t.setDaemon(true);
        t.start();
    }















    private void translatePoke(Map<String, Object> ev, final long fromUser, final long groupId) {
        final long targetId = Json.getLong(ev, "target_id", -1);
        final boolean isGroup = groupId > 0;



        System.out.println("[stellaris] 收到戳一戳: " + (isGroup ? ("群" + groupId) : "私聊")
                + " <用户" + fromUser + "> → " + targetId
                + (targetId == selfId ? "（戳的是骰娘）" : "（不是骰娘，忽略）"));
        if (targetId != selfId) return;


        if (isGroup && CoreModeGate.INSTANCE.isEnabledFor(groupId)) {
            System.out.println("[stellaris] 戳一戳被吞：本群开着核心模式（戳一戳属被禁用的「拓展」）");
            return;
        }
        if (nudgeSayEmpty()) {
            System.out.println("[stellaris] 戳一戳被吞：文本设定「（×）戳一戳回复」为空（默认就是空，"
                    + "配了才会回）");
            return;
        }
        final Map<String, Object> sender = Json.getObject(ev, "sender");

        Thread t = new Thread(() -> {
            String nick = null;
            String card = null;

            if (sender != null) {
                nick = Json.getString(sender, "nickname");
                card = Json.getString(sender, "card");
            }
            if (nick == null || nick.isEmpty()) {
                try {
                    if (isGroup) {
                        Map<String, Object> info = chan().getGroupMemberInfo(groupId, fromUser);
                        if (info != null) {
                            nick = Json.getString(info, "nickname");
                            card = Json.getString(info, "card");
                        }
                    } else {
                        Map<String, Object> p = Json.newObject();
                        p.put("user_id", fromUser);
                        Map<String, Object> info = chan().action("get_stranger_info", p);
                        if (info != null) nick = Json.getString(info, "nickname");
                    }
                } catch (Throwable e) {
                    System.out.println("[stellaris] 戳一戳昵称查询失败（用户" + fromUser + "）：" + e.getMessage()
                            + " —— 回复里的 {user} 会是 undefined");
                }
            }

            String groupNick = (card == null || card.isEmpty()) ? nick : card;
            try {
                dice.eventNudge(String.valueOf(targetId), MessageReceived.Companion.buildMessageEvent(
                        dice.getDiceFunction(), null,
                        isGroup ? String.valueOf(groupId) : null,
                        String.valueOf(fromUser), nick, groupNick, 0,
                        false, false, isGroup, false, false, ev, java.util.Collections.emptyList()));
            } catch (Throwable e) {
                System.out.println("[stellaris] 骰核处理戳一戳失败: " + e);
                e.printStackTrace();
            }
        }, "stellaris-poke");
        t.setDaemon(true);
        t.start();
    }





    private boolean nudgeSayEmpty() {
        try {
            String say = dice.getDiceFunction().getDiceConfig().getDICE_NUDGE_QQ_SAY();
            return say == null || say.trim().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }




    private static String newestFlag(Map<Long, String> seen, long key, String fallback) {
        String f = seen.get(key);
        return f == null ? fallback : f;
    }

    private void translateRequest(Map<String, Object> ev) {
        String reqType = String.valueOf(ev.getOrDefault("request_type", ""));
        long userId = Json.getLong(ev, "user_id", -1);
        final String flag = String.valueOf(ev.getOrDefault("flag", ""));

        System.out.println("[stellaris] 收到请求: " + reqType + " 用户" + userId
                + (reqType.equals("group") ? " 群" + Json.getLong(ev, "group_id", -1) : "")
                + "（flag=" + flag + "）");

        switch (reqType) {
            case "friend": {
                final long uid = userId;
                latestFriendFlag.put(uid, flag);
                dice.eventAddFriend(userId, new DiceRequestCallback() {
                    @Override public void agree() { answerFriendRequest(newestFlag(latestFriendFlag, uid, flag), true); }
                    @Override public void ignore() { answerFriendRequest(newestFlag(latestFriendFlag, uid, flag), false); }
                    @Override public void reject() { answerFriendRequest(newestFlag(latestFriendFlag, uid, flag), false); }
                });
                break;
            }
            case "group": {
                if ("invite".equals(String.valueOf(ev.getOrDefault("sub_type", "")))) {
                    long groupId = Json.getLong(ev, "group_id", -1);
                    final long gid = groupId;
                    latestGroupFlag.put(gid, flag);
                    dice.eventInviteInGroup(userId, groupId, new DiceRequestCallback() {
                        @Override public void agree() { answerGroupInvite(newestFlag(latestGroupFlag, gid, flag), true); }
                        @Override public void ignore() { answerGroupInvite(newestFlag(latestGroupFlag, gid, flag), false); }
                        @Override public void reject() { answerGroupInvite(newestFlag(latestGroupFlag, gid, flag), false); }
                    });
                }
                break;
            }
            default: break;
        }
    }

    private void answerFriendRequest(String flag, boolean approve) {
        try {
            Map<String, Object> p = Json.newObject();
            p.put("flag", flag);
            p.put("approve", approve);


            chan().action("set_friend_add_request", p);
            System.out.println("[stellaris] 好友请求已" + (approve ? "同意" : "拒绝")
                    + "（flag=" + flag + "，平台已受理）");
        } catch (Throwable t) {
            System.out.println("[stellaris] 好友请求处理失败（flag=" + flag + "，approve=" + approve + "）: " + t);
        }
    }

    private void answerGroupInvite(String flag, boolean approve) {
        try {
            Map<String, Object> p = Json.newObject();
            p.put("flag", flag);
            p.put("sub_type", "invite");
            p.put("approve", approve);
            chan().action("set_group_add_request", p);
        } catch (Throwable t) {
            System.out.println("[stellaris] 群邀请处理失败: " + t);
        }
    }
}
