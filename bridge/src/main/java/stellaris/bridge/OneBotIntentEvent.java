package stellaris.bridge;

import stellaris.dice.DiceIntentEvent;
import stellaris.dice.DiceMessageSource;
import stellaris.dice.receive.MessageReceived;
import stellaris.onebot.MusicArk;
import stellaris.onebot.OneBotSession;


import stellaris.util.Json;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;















public final class OneBotIntentEvent implements DiceIntentEvent {

    private static final Pattern MARKER = Pattern.compile("#\\{([A-Z]+)-([^}]*)\\}");







    public interface ChannelResolver {
        OneBotSession resolve(long selfId, OneBotSession fallback);
    }

    private final OneBotSession session;
    private final long selfId;
    private final ChannelResolver resolver;

    private final File dataRoot;










    private static final long SELF_OPERATOR_TTL_MS = 60_000L;
    private final Map<Long, Long> selfOperatorUntil = new ConcurrentHashMap<>();

    public OneBotIntentEvent(OneBotSession session, long selfId, File dataRoot) {
        this(session, selfId, dataRoot, null);
    }

    public OneBotIntentEvent(OneBotSession session, long selfId, File dataRoot, ChannelResolver resolver) {
        this.session = session;
        this.selfId = selfId;
        this.dataRoot = dataRoot;
        this.resolver = resolver;
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














    private static final String SPLIT_MARKER = stellaris.dice.diceFunction.DiceConstant.USER_SPLIT_STRING;

    @Override
    public void sendMessage(String sendContent, MessageReceived context, DiceMessageSource target) {
        if (sendContent == null || target == null) return;
        String text = resolveMarkers(sendContent, target);
        text = MiraiCodes.translate(text);



        text = MusicArk.customToArkJson(text);
        if (text.isEmpty()) return;




        if (target.isPerson()) text = stripAtForPrivate(text);
        try {
            List<String> parts = new ArrayList<>();
            for (String part : text.split(java.util.regex.Pattern.quote(SPLIT_MARKER), -1)) {
                if (!part.trim().isEmpty()) parts.add(part);
            }
            if (parts.isEmpty()) return;
            if (parts.size() > 1 && HostSettings.mergeForward()) {
                Deliver d = sendForward(parts, context, target);
                if (d == Deliver.SENT) {
                    log("sendMessage：按分段符合并为 1 条转发（" + parts.size() + " 个节点）");
                    return;
                }
                if (d == Deliver.UNKNOWN) {


                    logAlert("[消息合并] 合并转发的结果未知（平台未答复：超时/连接断），"
                            + "**不再逐条补发**以免重复投递——这条回复可能没送到，用户重发指令即可");
                    return;
                }
            }
            for (String part : parts) sendOne(part, context, target);
            if (parts.size() > 1) log("sendMessage：按分段符切成 " + parts.size() + " 条发送");
        } catch (Throwable t) {
            logAlert("sendMessage failed (" + target.getType() + " " + target.getId() + "): " + t);
        }
    }

    @Override
    public boolean sendGroupFile(String filePath, String fileName, String groupId) {
        File file = resolveLocalOrDataFile(filePath, "files");
        if (file == null || !file.isFile()) return false;
        try {
            Map<String, Object> p = Json.newObject();
            p.put("group_id", Long.parseLong(groupId == null ? "" : groupId.trim()));
            p.put("file", file.getAbsolutePath());
            p.put("name", fileName == null || fileName.isEmpty() ? file.getName() : fileName);
            chan().action("upload_group_file", p, 60000);
            log("sendGroupFile：群 " + groupId + " 上传 " + fileName + " (" + file.getName() + ")");
            return true;
        } catch (Throwable t) {

            logAlert("sendGroupFile failed: " + t);
            return false;
        }
    }









    private enum Deliver { SENT, REJECTED, UNKNOWN }


    static boolean outcomeUnknown(Throwable t) {
        return !(t instanceof stellaris.onebot.OneBotSession.ActionException)
                || !((stellaris.onebot.OneBotSession.ActionException) t).answered;
    }















    private Deliver sendForward(List<String> parts, MessageReceived context, DiceMessageSource target) {
        if (System.currentTimeMillis() < forwardMutedUntil()) {
            log("sendMessage：合并转发在静默窗口内（上次：" + forwardFailReason + "），本次逐条发送");
            return Deliver.REJECTED;
        }
        String name = selfNickname();
        List<Object> nodes = new ArrayList<>();
        for (String part : parts) {
            Map<String, Object> data = Json.newObject();
            data.put("uin", selfId);
            data.put("name", name);
            data.put("user_id", selfId);
            data.put("nickname", name);
            data.put("content", part);
            Map<String, Object> node = Json.newObject();
            node.put("type", "node");
            node.put("data", data);
            nodes.add(node);
        }

        if (target.isGroup()) {
            return forwardVia(attempts(true, 0), true, target.getNumberId(), 0, nodes, parts.size(),
                    "群" + target.getNumberId());
        }
        if (target.isPerson()) {
            long userId = target.getNumberId();
            long ctxGroup = privateGroupHint(target, context);
            return forwardVia(attempts(false, ctxGroup), false, userId, ctxGroup, nodes, parts.size(),
                    "私聊" + userId);
        }
        logAlert("unsupported DiceMessageSource type: " + target.getType() + "（无法合并转发）");
        return Deliver.REJECTED;
    }










    private Deliver forwardVia(List<String[]> attempts, boolean isGroup, long id,
                               long ctxGroup, List<Object> nodes, int nodeCount, String where) {
        String lastError = null;
        String lastAction = null;
        for (String[] att : attempts) {
            String action = att[0];
            boolean useHint = "1".equals(att[1]);
            if (action.equals(lastAction) && looksUnsupported(lastError)) continue;
            Map<String, Object> p = forwardParams(action, isGroup, id, ctxGroup, useHint, nodes);
            Attempt a = tryForward(action, p, where, nodeCount);
            if (a.ok()) {
                forwardPreferredAction = action;
                forwardPreferredSession = chan();
                return Deliver.SENT;
            }
            if (a.unknown) {
                noteForwardUnknown(a.err);
                return Deliver.UNKNOWN;
            }
            lastError = a.err;
            lastAction = action;
        }
        noteForwardFailure(lastError == null ? "没有可用的转发动作" : lastError);
        return Deliver.REJECTED;
    }


    private static final class Attempt {
        final String err;
        final boolean unknown;
        Attempt(String err, boolean unknown) { this.err = err; this.unknown = unknown; }
        boolean ok() { return err == null; }
    }






    private List<String[]> attempts(boolean isGroup, long ctxGroup) {
        String typed = isGroup ? "send_group_forward_msg" : "send_private_forward_msg";
        boolean hintOk = !isGroup && ctxGroup > 0;
        List<String[]> out = new ArrayList<>();
        String preferred = (forwardPreferredSession == chan()) ? forwardPreferredAction : null;
        if (preferred != null) out.add(new String[]{preferred, hintOk && typed.equals(preferred) ? "1" : "0"});
        if (hintOk) out.add(new String[]{typed, "1"});
        out.add(new String[]{typed, "0"});
        out.add(new String[]{"send_forward_msg", "0"});

        List<String[]> dedup = new ArrayList<>();
        for (String[] a : out) {
            boolean dup = false;
            for (String[] b : dedup) {
                if (b[0].equals(a[0]) && b[1].equals(a[1])) { dup = true; break; }
            }
            if (!dup) dedup.add(a);
        }
        return dedup;
    }

    private Map<String, Object> forwardParams(String action, boolean isGroup, long id,
                                              long ctxGroup, boolean useHint, List<Object> nodes) {
        Map<String, Object> p = Json.newObject();
        if ("send_forward_msg".equals(action)) p.put("message_type", isGroup ? "group" : "private");
        if (isGroup) p.put("group_id", id);
        else p.put("user_id", id);
        if (useHint && ctxGroup > 0) p.put("group_id", ctxGroup);
        p.put("messages", nodes);
        return p;
    }


    private Attempt tryForward(String action, Map<String, Object> params, String where, int nodeCount) {
        try {
            chan().action(action, params);
            sentLog(where, "[合并转发 " + nodeCount + " 条]");
            return new Attempt(null, false);
        } catch (Throwable t) {
            return new Attempt(action + " 失败（" + where + "）：" + t.getMessage(), outcomeUnknown(t));
        }
    }





    private volatile long forwardMutedUntil;
    private volatile Object forwardMutedSession;
    private volatile String forwardFailReason = "";

    private volatile String forwardPreferredAction;
    private volatile Object forwardPreferredSession;

    private static final long FORWARD_UNSUPPORTED_TTL_MS = 30 * 60_000L;

    private static final long FORWARD_TRANSIENT_TTL_MS = 2 * 60_000L;

    private long forwardMutedUntil() {
        if (forwardMutedSession != chan()) return 0L;
        return forwardMutedUntil;
    }



    static boolean looksUnsupported(String err) {
        if (err == null) return false;
        String s = err.toLowerCase();
        return s.contains("retcode=1404") || s.contains("1404")
                || s.contains("不支持") || s.contains("unknown action") || s.contains("unknownaction")
                || s.contains("not support") || s.contains("unsupported") || s.contains("未实现")
                || s.contains("no such action") || s.contains("not implemented");
    }

    private void noteForwardFailure(String err) {
        boolean unsupported = looksUnsupported(err);
        forwardFailReason = err;
        forwardMutedSession = chan();
        forwardMutedUntil = System.currentTimeMillis()
                + (unsupported ? FORWARD_UNSUPPORTED_TTL_MS : FORWARD_TRANSIENT_TTL_MS);
        logAlert("[消息合并] 合并转发没发出去，已退回逐条发送（本次起 "
                + (unsupported ? "30 分钟" : "2 分钟") + "内不再尝试）——" + err);
        if (unsupported) {
            logAlert("[消息合并] 这个平台看起来不支持合并转发动作（send_group_forward_msg / "
                    + "send_private_forward_msg / send_forward_msg 都不认）：想让日志干净就把设置页"
                    + "「消息合并」关掉，多段回复会一直逐条发送。");
        }
    }





    private void noteForwardUnknown(String err) {
        forwardFailReason = err;
        forwardMutedSession = chan();
        forwardMutedUntil = System.currentTimeMillis() + FORWARD_TRANSIENT_TTL_MS;
    }


    private void sendOne(String text, MessageReceived context, DiceMessageSource target) {
        if (target.isGroup()) {
            Map<String, Object> p = Json.newObject();
            p.put("group_id", target.getNumberId());
            p.put("message", text);
            p.put("auto_escape", false);
            chan().action("send_group_msg", p);
            sentLog("群" + target.getNumberId(), text);
        } else if (target.isPerson()) {
            sendPrivateResilient(target, text, context);
        } else {
            logAlert("unsupported DiceMessageSource type: " + target.getType());
        }
    }


    private static void sentLog(String where, String text) {
        System.out.println("[stellaris] 发送消息: " + where + " -> " + outPreview(text));
    }






    private static long privateGroupHint(DiceMessageSource target, MessageReceived context) {
        String g = context != null ? context.getGroup() : "";
        if (g != null && !g.isEmpty()) {
            try {
                long v = Long.parseLong(g.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {

            }
        }
        long remembered = target == null ? 0L : TempSessionContext.lookup(target.getNumberId());
        if (remembered > 0) {
            System.out.println("[stellaris] 私聊群提示：" + target.getNumberId()
                    + " 的临时会话来源群=" + remembered + "（入站记忆）");
        }
        return remembered;
    }














    private void sendPrivateResilient(DiceMessageSource target, String text, MessageReceived context) {
        long userId = target.getNumberId();
        long ctxGroup = privateGroupHint(target, context);
        String lastError = null;
        if (ctxGroup > 0) {
            Map<String, Object> withHint = Json.newObject();
            withHint.put("user_id", userId);
            withHint.put("message", text);
            withHint.put("auto_escape", false);
            withHint.put("group_id", ctxGroup);
            try {
                chan().action("send_private_msg", withHint);
                sentLog("私聊" + userId, text);
                log("私聊投递 " + userId + "：带群号提示（群" + ctxGroup + "）已受理——非好友按群临时会话投递");
                return;
            } catch (Throwable t) {
                if (outcomeUnknown(t)) {
                    logAlert("[私聊] 结果未知（平台未答复：超时/连接断），**不再换规范形重发**以免重复投递"
                            + "（用户 " + userId + "）：" + t.getMessage());
                    return;
                }
                lastError = String.valueOf(t.getMessage());
                log("私聊投递 " + userId + "：带群号提示被拒（" + lastError + "），退回规范形");
            }
        } else {


            log("私聊投递 " + userId + "：**无群上下文**（内核事件无群号、临时会话记忆未命中）"
                    + "⇒ 不带群号提示，对方不是好友时平台投不出去");
        }
        Map<String, Object> plain = Json.newObject();
        plain.put("user_id", userId);
        plain.put("message", text);
        plain.put("auto_escape", false);
        try {
            chan().action("send_private_msg", plain);
            sentLog("私聊" + userId, text);
            log("私聊投递 " + userId + "：规范形已受理（不带群号）");
            return;
        } catch (Throwable t) {
            if (outcomeUnknown(t)) {
                logAlert("[私聊] 结果未知（平台未答复：超时/连接断），消息可能已送达——不再重发："
                        + t.getMessage());
                return;
            }
            lastError = String.valueOf(t.getMessage());
        }
        logAlert("私聊发送失败（用户 " + userId + "）：" + lastError
                + (ctxGroup > 0
                    ? " —— 已带群号提示（群" + ctxGroup + "）仍失败：对方不是好友、且平台没按群临时会话投递"
                      + "（平台不支持该扩展 / 群不允许临时会话）"
                    : " —— 消息没有群上下文，无法用群临时会话投递（对方不是好友时必然收不到）")
                + "；群里那条提示已发出");
    }

    private static final Pattern CQ_OUT = Pattern.compile("\\[CQ:(\\w+)[^\\]]*\\]");


    private static final Pattern CQ_AT_OUT = Pattern.compile("\\[CQ:at,[^\\]]*qq=(all|\\d+)[^\\]]*\\]");

    private static final Pattern CQ_AT_ANY = Pattern.compile("\\[CQ:at,[^\\]]*\\]");





    static String stripAtForPrivate(String text) {
        if (text == null || text.isEmpty() || !text.contains("[CQ:at,")) return text;
        Matcher m = CQ_AT_OUT.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String rep = "all".equalsIgnoreCase(m.group(1)) ? "@全体成员" : "@" + m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        String out = sb.toString();
        if (out.contains("[CQ:at,")) {
            out = CQ_AT_ANY.matcher(out).replaceAll("");
        }
        return out;
    }


    static String outPreview(String s) {
        StringBuffer sb = new StringBuffer();
        Matcher m = CQ_OUT.matcher(s);
        while (m.find()) {
            String type = m.group(1);
            String zh;
            if ("image".equals(type)) zh = "[图片]";
            else if ("record".equals(type)) zh = "[语音]";
            else if ("video".equals(type)) zh = "[视频]";
            else if ("at".equals(type)) zh = "[@]";
            else if ("file".equals(type)) zh = "[文件]";
            else if ("json".equals(type)) zh = "[卡片]";
            else zh = "[" + type + "]";
            m.appendReplacement(sb, Matcher.quoteReplacement(zh));
        }
        m.appendTail(sb);
        String out = sb.toString().replaceAll("\\s+", " ");
        return out.length() > 100 ? out.substring(0, 100) + "…" : out;
    }







    private String resolveMarkers(String input, DiceMessageSource target) {
        Matcher m = MARKER.matcher(input);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String cmd = m.group(1);
            String param = m.group(2).trim();
            String replacement = null;
            switch (cmd) {
                case "PICTURE": replacement = fileMarkerToCq(param, "image", "pictures"); break;
                case "VOICE": replacement = fileMarkerToCq(param, "record", "voices"); break;
                case "VIDEO": replacement = fileMarkerToCq(firstToken(param), "video", "video"); break;
                case "AT": {
                    String qq = param;
                    int bar = qq.indexOf('|');
                    if (bar > 0) qq = qq.substring(0, bar).trim();
                    if (qq.equalsIgnoreCase("ALL")) replacement = "[CQ:at,qq=all]";
                    else if (qq.matches("\\d+")) replacement = "[CQ:at,qq=" + qq + "]";
                    break;
                }
                case "FILE": {



                    String local = param, name = null;
                    int star = param.indexOf("***");
                    int bar = param.indexOf('|');
                    if (star >= 0) {
                        local = param.substring(0, star).trim();
                        name = param.substring(star + 3).trim();
                    } else if (bar > 0) {
                        local = param.substring(0, bar).trim();
                        name = param.substring(bar + 1).trim();
                    }
                    File f = resolveLocalOrDataFile(local, "files");
                    if (f != null && target.isGroup()) {
                        try {

                            String uploadName = name != null && !name.isEmpty() ? name : f.getName();
                            Map<String, Object> p = Json.newObject();
                            p.put("group_id", target.getNumberId());
                            p.put("file", f.getAbsolutePath());
                            p.put("name", uploadName);
                            chan().action("upload_group_file", p, 60000);
                            System.out.println("[stellaris] 发送群文件: 群" + target.getNumberId()
                                    + " -> " + uploadName + " (" + f.getName() + ")");
                            replacement = "";
                        } catch (Throwable t) {
                            logAlert("upload_group_file failed: " + t);
                        }
                    }
                    break;
                }
                default: break;
            }
            sb.append(input, last, m.start());
            if (replacement != null) sb.append(replacement);
            else sb.append(m.group());
            last = m.end();
        }
        sb.append(input.substring(last));
        return sb.toString();
    }

    private String fileMarkerToCq(String param, String cqType, String subDir) {
        File f = resolveDataFile(param, subDir);
        if (f == null) {


            String low = param.toLowerCase();
            if (low.startsWith("http://") || low.startsWith("https://")
                    || low.startsWith("base64://")) {
                return "[CQ:" + cqType + ",file=" + MiraiCodes.cqEscape(param) + "]";
            }
            logAlert("marker file not found: " + subDir + "/" + param);
            return "<" + cqType + ":" + param + ">";
        }



        String abs = f.getAbsolutePath().replace('\\', '/');
        return "[CQ:" + cqType + ",file=file://" + (abs.startsWith("/") ? abs : "/" + abs) + "]";
    }


    private File resolveDataFile(String rel, String subDir) {
        String clean = rel.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        File a = new File(dataRoot, subDir + "/" + clean);
        if (a.isFile()) return a;
        File b = new File(dataRoot, clean);
        if (b.isFile()) return b;
        return null;
    }






    private File resolveLocalOrDataFile(String local, String subDir) {
        if (local.contains(":") || local.startsWith("/") || local.startsWith("\\")) {
            File abs = new File(local);
            if (abs.isFile()) return abs;
        }
        return resolveDataFile(local, subDir);
    }

    private static String firstToken(String s) {
        int bar = s.indexOf('|');
        String t = bar > 0 ? s.substring(0, bar) : s;
        int comma = t.indexOf(',');
        return (comma > 0 ? t.substring(0, comma) : t).trim();
    }
























    @Override
    public boolean setGroupNumberName(String s, String id, String groupId) {
        long gid;
        try {
            gid = Long.parseLong(groupId == null ? "" : groupId.trim());
        } catch (RuntimeException e) {

            logAlert("setGroupNumberName 失败：群号无效（'" + groupId + "'）——私聊会话里没有群可改名片");
            return false;
        }
        long uid;
        try {
            uid = Long.parseLong(id == null ? "" : id.trim());
        } catch (RuntimeException e) {
            logAlert("setGroupNumberName 失败：成员号无效（'" + id + "'）");
            return false;
        }
        String card = s == null ? "" : s;


        String roleText = null;
        if (card.isEmpty()) {
            long now = System.currentTimeMillis();
            Long until = selfOperatorUntil.get(gid);
            if (until != null && until > now) {
                log("setGroupNumberName：空名片=内核的权限探针（.sn 家族），身份记忆还新鲜（"
                        + ((until - now + 999) / 1000) + " 秒内查到过管理员/群主），不发写请求");
                return true;
            }
            roleText = querySelfRole(gid);
            if (isOperatorRole(roleText)) {
                selfOperatorUntil.put(gid, now + SELF_OPERATOR_TTL_MS);
                log("setGroupNumberName：空名片=内核的权限探针（.sn 家族），骰娘在该群是管理员/群主，"
                        + "不发写请求（QQ 会拒「名片写成空」：OIDB 0x8fc_3 error 1007）");
                return true;
            }
            if (roleText == null) {







                log("setGroupNumberName：空名片=内核的权限探针（.sn 家族），但身份查询失败——"
                        + "不发注定被真机拒的写请求，保守按「是管理员/群主」作答");
                return true;
            }
        }
        try {
            chan().setGroupCard(gid, uid, card);


            if (uid != selfId) {
                selfOperatorUntil.put(gid, System.currentTimeMillis() + SELF_OPERATOR_TTL_MS);
            }
            return true;
        } catch (Throwable t) {

            selfOperatorUntil.remove(gid);
            logAlert("setGroupNumberName 失败：改群 " + gid + " 成员 " + uid + " 的名片被平台拒绝（card='"
                    + card + "'）：" + t.getMessage()
                    + "；骰娘在该群身份="
                    + (roleText != null ? describeRole(roleText) : selfRoleForLog(gid)));
            return false;
        }
    }





    private String selfRoleForLog(long gid) {
        try {
            return describeRole(roleOf(chan().getGroupMemberInfo(gid, selfId, true)));
        } catch (Throwable t) {

            return "查询失败（" + t.getMessage() + "）";
        }
    }


    private String querySelfRole(long gid) {
        try {
            return roleOf(chan().getGroupMemberInfo(gid, selfId, true));
        } catch (Throwable t) {
            return null;
        }
    }


    private static String roleOf(Map<String, Object> info) {
        Object raw = Json.getString(info, "role");
        if (raw == null) return null;
        String role = String.valueOf(raw).trim();
        return (role.isEmpty() || "null".equalsIgnoreCase(role)) ? null : role;
    }

    private static boolean isOperatorRole(String role) {
        if (role == null) return false;
        String low = role.toLowerCase();
        return "owner".equals(low) || "admin".equals(low) || "administrator".equals(low);
    }


    private static String describeRole(String role) {
        if (role == null) return "平台未提供 role（可能不支持 get_group_member_info）";
        if (isOperatorRole(role)) return role + "（骰娘是管理员/群主，本次被拒与骰娘权限无关）";
        return role + "（骰娘是普通成员，改他人群名片需要管理员/群主）";
    }








    @Override
    public boolean setGroupTitle(String s, String id, String groupId) {
        long gid;
        long uid;
        try {
            gid = Long.parseLong(groupId == null ? "" : groupId.trim());
            uid = Long.parseLong(id == null ? "" : id.trim());
        } catch (RuntimeException e) {
            logAlert("setGroupTitle 失败：群号/成员号无效（group='" + groupId + "' id='" + id + "'）");
            return false;
        }
        String title = s == null ? "" : s;
        try {
            chan().setGroupSpecialTitle(gid, uid, title);

            if (uid != selfId) {
                selfOperatorUntil.put(gid, System.currentTimeMillis() + SELF_OPERATOR_TTL_MS);
            }
            log("setGroupTitle（插件调用）：群 " + gid + " 成员 " + uid + " 头衔='" + title + "' 已提交");
            return true;
        } catch (Throwable t) {
            selfOperatorUntil.remove(gid);
            logAlert("setGroupTitle 失败（插件调用）：群 " + gid + " 成员 " + uid + " 头衔='" + title
                    + "' 被平台拒绝：" + t.getMessage() + "；骰娘在该群身份=" + selfRoleForLog(gid));
            return false;
        }
    }





    @Override
    public boolean setGroupWholeBan(boolean ban, String groupId) {
        long gid;
        try {
            gid = Long.parseLong(groupId == null ? "" : groupId.trim());
        } catch (RuntimeException e) {
            logAlert("setGroupWholeBan 失败：群号无效（'" + groupId + "'）");
            return false;
        }
        try {
            chan().setGroupWholeBan(gid, ban);
            selfOperatorUntil.put(gid, System.currentTimeMillis() + SELF_OPERATOR_TTL_MS);
            log("setGroupWholeBan（插件调用）：群 " + gid + " " + (ban ? "全体禁言" : "解除禁言") + " 已提交");
            return true;
        } catch (Throwable t) {
            selfOperatorUntil.remove(gid);
            logAlert("setGroupWholeBan 失败（插件调用）：群 " + gid + " " + (ban ? "全体禁言" : "解除禁言")
                    + " 被平台拒绝：" + t.getMessage() + "；骰娘在该群身份=" + selfRoleForLog(gid));
            return false;
        }
    }

    @Override
    public void setDismiss(String groupId, final kotlin.jvm.functions.Function1<? super Boolean, kotlin.Unit> callback) {
        boolean ok;
        try {
            long gid = Long.parseLong(groupId);






            String role = querySelfRole(gid);
            boolean asOwner = "owner".equalsIgnoreCase(role);
            log("setDismiss：群 " + gid + " 骰娘 role=" + (role == null ? "(查询失败)" : role)
                    + " → is_dismiss=" + asOwner);
            chan().setGroupLeave(gid, asOwner ? OneBotSession.isDismiss.YES : OneBotSession.isDismiss.NO);
            ok = true;
        } catch (Throwable t) {
            logAlert("setDismiss failed: " + t);
            ok = false;
        }
        if (callback != null) callback.invoke(ok);
    }









    @Override
    public boolean isSelfGroupOperator(String groupId) {
        long gid;
        try {
            gid = Long.parseLong(groupId == null ? "" : groupId.trim());
        } catch (RuntimeException e) {

            return false;
        }
        String role = querySelfRole(gid);
        if (role == null) {
            log("isSelfGroupOperator：群 " + gid + " 身份查询失败，保守按「是管理员/群主」处理"
                    + "（内核若传的是用户号而不是群号，这里就会一路失败——见 DiceHelper 的调用点）");
            return true;
        }
        return isOperatorRole(role);
    }



    @Override
    public String botFrame() {
        return "Stella/" + chan().implName();
    }


























































































    private static final int GROUP_LIST_ATTEMPTS = 4;
    private static final long GROUP_LIST_RETRY_MS = 15_000L;

    @Override
    public ArrayList<GroupInfo> getGroupInfo() {




        Throwable last = null;
        for (int attempt = 1; attempt <= GROUP_LIST_ATTEMPTS; attempt++) {
            try {
                return fetchGroupInfo();
            } catch (Throwable t) {
                last = t;
                log("getGroupInfo：第 " + attempt + "/" + GROUP_LIST_ATTEMPTS + " 次失败（" + t.getMessage() + "）");
                if (attempt < GROUP_LIST_ATTEMPTS) {
                    try {
                        Thread.sleep(GROUP_LIST_RETRY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        logAlert("getGroupInfo 失败（已重试 " + GROUP_LIST_ATTEMPTS + " 次）：" + last
                + "；依赖群列表的功能（批量清群/白名单）本次启动可能不可用——内核不会再拉一次");
        return null;
    }


    private ArrayList<GroupInfo> fetchGroupInfo() {
        Map<String, Object> resp = chan().getGroupList();
        ArrayList<GroupInfo> out = new ArrayList<>();
        Object data = resp.get("data");
        for (Object o : (Iterable<?>) data) {
            if (o instanceof Map) {
                Map<?, ?> g = (Map<?, ?>) o;
                long id = Json.getLong((Map<String, Object>) g, "group_id", -1);
                String name = String.valueOf(Json.getString((Map<String, Object>) g, "group_name"));
                if (id > 0) out.add(new GroupInfo(id, name, 0));
            }
        }
        return out;
    }

    @Override
    public ArrayList<FriendsInfo> getFriendsInfo() {
        ArrayList<FriendsInfo> out = new ArrayList<>();




        out.add(new FriendsInfo(String.valueOf(selfId), selfNickname()));
        try {
            Map<String, Object> resp = chan().getFriendList();
            Object data = resp.get("data");
            for (Object o : (Iterable<?>) data) {
                if (o instanceof Map) {
                    Map<?, ?> f = (Map<?, ?>) o;
                    String id = String.valueOf(Json.getLong((Map<String, Object>) f, "user_id", -1));
                    String nick = String.valueOf(Json.getString((Map<String, Object>) f, "nickname"));


                    String remark = Json.getString((Map<String, Object>) f, "remark");
                    out.add(new FriendsInfo(id, nick, remark == null ? "" : remark));
                }
            }
        } catch (Throwable t) {




            log("getFriendsInfo：平台没给出好友列表，本次只带上骰娘自己那条");
        }
        return out;
    }


    private volatile String selfNickCache;
    private volatile long selfNickAt;

    private String selfNickname() {
        String cached = selfNickCache;
        if (cached != null && System.currentTimeMillis() - selfNickAt < 600_000L) return cached;
        String nick = null;
        try {
            Map<String, Object> resp = chan().getLoginInfo();
            Object data = resp.get("data");
            if (data instanceof Map) {
                Object raw = Json.getString((Map<String, Object>) data, "nickname");
                if (raw != null) {
                    String s = String.valueOf(raw).trim();
                    if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) nick = s;
                }
            }
        } catch (Throwable t) {

            log("getFriendsInfo：平台没答出骰娘昵称，昵称显示退回 QQ 号");
        }
        if (nick == null) nick = String.valueOf(selfId);
        selfNickCache = nick;
        selfNickAt = System.currentTimeMillis();
        return nick;
    }

    @Override
    public boolean hasInitialed() {
        return true;
    }






    @Override
    public void log(String str) {
        stellaris.util.LogLevel.debug(str);
    }


    public void logAlert(String str) {
        stellaris.util.LogLevel.alert(str);
    }
}
