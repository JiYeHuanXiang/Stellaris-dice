package stellaris.bridge;

import stellaris.util.Json;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;




























































public final class CoreModeGate {



    private static final String DEFAULT_ON = "本群核心模式已开启，将禁用投掷之外的指令和拓展";
    private static final String DEFAULT_OFF = "本群核心模式已关闭，恢复全部指令";
    private static final String DEFAULT_DENIAL = "权限不足，仅群管理员或 master 可切换核心模式";
    private static final String PRIVATE_UNAVAILABLE = "核心模式在私聊中不可使用";
    private static final List<String> DEFAULT_WHITELIST = new ArrayList<>(java.util.Arrays.asList(
            "r", "rd", "ra", "st", "sc", "li", "ti", "rh", "rah", "rch",
            "rab", "rap", "rb", "rp",


            "coc", "coc6", "dnd", "setdnd",
            "dg", "setdg", "fate", "setfate",
            "dx", "dxx", "ww", "w", "wx", "setwx", "cpr", "setcpr"));

    private static final String PURE_ON = "本群纯净模式已开启：投掷与鉴定只回复结果和成功级别";
    private static final String PURE_OFF = "本群纯净模式已关闭";
    private static final String PURE_CORE_ONLY = "纯净模式仅在核心模式下允许启用";
    private static final String PURE_DENIAL = "权限不足，仅群管理员或 master 可切换纯净模式";
    private static final String PURE_AUTO_DENIAL = "权限不足，仅 master 可切换纯净模式联动";
    private static final String PURE_PRIVATE_UNAVAILABLE = "纯净模式在私聊中不可使用";


    private static final Pattern CQ_AT = Pattern.compile("\\[CQ:at,qq=(\\d+)]");

    public static final CoreModeGate INSTANCE = new CoreModeGate();

    private File configFile;


    private final Set<Long> groups = new LinkedHashSet<>();
    private String onMessage = DEFAULT_ON;
    private String offMessage = DEFAULT_OFF;
    private String denialMessage = DEFAULT_DENIAL;
    private String blockedNotice = "";
    private String prefix = ".";
    private int uiPort = 7788;
    private List<String> whitelist = new ArrayList<>(DEFAULT_WHITELIST);

    private final Set<Long> pureGroups = new LinkedHashSet<>();

    private boolean pureAuto = false;

    private CoreModeGate() { }

    public synchronized void init(File configDir) {
        this.configFile = new File(configDir, "core-mode.json");
        load();
    }



    public synchronized boolean isEnabledFor(long groupId) { return groups.contains(groupId); }
    public synchronized List<Long> enabledGroups() { return new ArrayList<>(groups); }
    public synchronized int enabledGroupCount() { return groups.size(); }
    public synchronized String getOnMessage() { return onMessage; }
    public synchronized String getOffMessage() { return offMessage; }
    public synchronized String getDenialMessage() { return denialMessage; }
    public synchronized String getBlockedNotice() { return blockedNotice; }
    public synchronized String getPrefix() { return prefix; }
    public synchronized int getUiPort() { return uiPort; }
    public synchronized String getWhitelistCsv() { return String.join(",", whitelist); }



    public synchronized boolean isPureFor(long groupId) { return pureGroups.contains(groupId); }
    public synchronized List<Long> pureGroups() { return new ArrayList<>(pureGroups); }
    public synchronized int pureGroupCount() { return pureGroups.size(); }
    public synchronized boolean isPureAuto() { return pureAuto; }



    public synchronized String setPureModeFor(long groupId, boolean on) {
        String note = "";
        if (on) {
            if (!isEnabledFor(groupId)) {

                groups.add(groupId);
                pureGroups.add(groupId);
                note = "（已同时开启核心模式）";
            } else {
                pureGroups.add(groupId);
            }
        } else {
            pureGroups.remove(groupId);
            if (pureAuto && isEnabledFor(groupId)) {
                groups.remove(groupId);
                note = "（核心模式已随之关闭）";
            }
        }
        save();
        return note;
    }





    public synchronized int setPureAuto(boolean on) {
        pureAuto = on;
        int synced = 0;
        if (on) for (Long g : groups) if (pureGroups.add(g)) synced++;
        save();
        return synced;
    }




    public synchronized void setEnabledFor(long groupId, boolean on) {
        if (on) {
            groups.add(groupId);
            if (pureAuto) pureGroups.add(groupId);
        } else {
            groups.remove(groupId);
            pureGroups.remove(groupId);
        }
        save();
    }





    public synchronized String setUiPort(int port) {
        if (port < 1024 || port > 65535) return "端口需在 1024-65535 之间";
        uiPort = port;
        save();
        return null;
    }








    public synchronized void apply(String onMsg, String offMsg, String denialMsg,
                                   String notice, String whitelistCsv) {
        if (onMsg != null && !onMsg.trim().isEmpty()) onMessage = onMsg.trim();
        if (offMsg != null && !offMsg.trim().isEmpty()) offMessage = offMsg.trim();
        if (denialMsg != null) denialMessage = denialMsg.trim();
        if (notice != null) blockedNotice = notice.trim();
        if (whitelistCsv != null) whitelist = parseWhitelist(whitelistCsv);
        save();
    }






    synchronized List<String> parseWhitelist(String csv) {
        String pfx = prefix == null ? "" : prefix;
        List<String> list = new ArrayList<>();
        for (String w : csv.split("[,，、\\s]+")) {
            String s = w.trim();
            if (s.isEmpty()) continue;
            if (!pfx.isEmpty() && s.startsWith(pfx)) s = s.substring(pfx.length());

            s = s.replaceAll("^[.。!！/\\\\]+", "").trim();
            if (s.isEmpty()) continue;
            s = s.toLowerCase();
            if (!list.contains(s)) list.add(s);
        }
        return list;
    }









    public boolean intercept(Map<String, Object> ev, stellaris.onebot.OneBotSession session,
                             stellaris.dice.DiceHelper dice) {
        String msgType = String.valueOf(ev.getOrDefault("message_type", ""));
        boolean isGroup = "group".equals(msgType);
        if (!isGroup && !"private".equals(msgType)) return false;
        long userId = Json.getLong(ev, "user_id", -1);
        long groupId = isGroup ? Json.getLong(ev, "group_id", -1) : -1;





        if (atSomeoneElse(ev)) return isGroup && isEnabledFor(groupId);

        String text = plainText(ev);
        String pfx = getPrefix();




        String norm = normalizeWidth(text);
        String normPfx = normalizeWidth(pfx);
        boolean isCommand = !norm.isEmpty() && norm.startsWith(normPfx);
        String rest = isCommand ? norm.substring(normPfx.length()).trim() : "";
        String token = rest.isEmpty() ? "" : rest.split("\\s+", 2)[0].toLowerCase();



        String restRaw = (isCommand && !rest.isEmpty())
                ? text.substring(text.length() - rest.length()) : "";
        String param = rest.contains(" ") ? rest.substring(rest.indexOf(' ') + 1).trim() : "";


        if (isCommand && "core".equals(token)) {
            if (!isGroup) {

                reply(session, msgType, groupId, userId, PRIVATE_UNAVAILABLE);
                return true;
            }
            boolean toggle = param.equals("on") || param.equals("off");
            if (toggle) {
                if (!isAdmin(ev, userId, dice)) {
                    reply(session, msgType, groupId, userId, getDenialMessage());
                    return true;
                }
                boolean on = param.equals("on");
                boolean pureWas = isPureFor(groupId);
                setEnabledFor(groupId, on);
                String extra;
                if (on) {

                    extra = (pureAuto && !pureWas && isEnabledFor(groupId)) ? "（纯净模式已联动开启）" : "";
                } else {

                    extra = pureWas ? "（纯净模式已随之关闭）" : "";
                }
                reply(session, msgType, groupId, userId, (on ? getOnMessage() : getOffMessage()) + extra);
                return true;
            }
            if (param.isEmpty()) {
                reply(session, msgType, groupId, userId,
                        "本群核心模式：" + (isEnabledFor(groupId) ? "开" : "关"));
                return true;
            }
            reply(session, msgType, groupId, userId, "用法：" + pfx + "core on / " + pfx + "core off");
            return true;
        }



        if (isCommand && "pure".equals(token)) {
            if (!isGroup) {
                reply(session, msgType, groupId, userId, PURE_PRIVATE_UNAVAILABLE);
                return true;
            }
            String sub = param.isEmpty() ? "" : param.split("\\s+", 2)[0].toLowerCase();
            String arg = param.contains(" ") ? param.substring(param.indexOf(' ') + 1).trim().toLowerCase() : "";
            if (sub.isEmpty() || (sub.equals("mode") && arg.isEmpty()) || (sub.equals("auto") && arg.isEmpty())) {
                StringBuilder sb = new StringBuilder("本群纯净模式：").append(isPureFor(groupId) ? "开" : "关");
                if (sub.isEmpty()) sb.append("\n纯净模式自动联动：").append(isPureAuto() ? "开" : "关");
                reply(session, msgType, groupId, userId, sb.toString());
                return true;
            }
            boolean mode = "mode".equals(sub);
            boolean auto = "auto".equals(sub);
            boolean toggle = arg.equals("on") || arg.equals("off");
            if ((!mode && !auto) || !toggle) {
                reply(session, msgType, groupId, userId, pureUsage());
                return true;
            }
            if (mode) {
                if (!isAdmin(ev, userId, dice)) {
                    reply(session, msgType, groupId, userId, PURE_DENIAL);
                    return true;
                }
                boolean on = arg.equals("on");

                if (on && !isEnabledFor(groupId) && !isPureAuto()) {
                    reply(session, msgType, groupId, userId, PURE_CORE_ONLY);
                    return true;
                }
                String note = setPureModeFor(groupId, on);
                reply(session, msgType, groupId, userId, (on ? PURE_ON : PURE_OFF) + note);
                return true;
            }


            if (!isDiceMaster(dice, userId)) {
                reply(session, msgType, groupId, userId, PURE_AUTO_DENIAL);
                return true;
            }
            boolean on = arg.equals("on");
            int synced = setPureAuto(on);
            String autoText = on
                    ? "纯净模式自动联动已开启：核心模式开关将同步带动纯净模式"
                      + (synced > 0 ? "（已同步开启 " + synced + " 个核心模式群的纯净模式）" : "")
                    : "纯净模式自动联动已关闭：纯净模式不再随核心模式自动开关";
            reply(session, msgType, groupId, userId, autoText);
            return true;
        }


        if (!isGroup) return false;

        if (!isEnabledFor(groupId)) return false;

        if (isCommand && isAllowedByWhitelist(dispatchCommand(dice, restRaw))) return false;
        String notice = getBlockedNotice();
        if (!notice.isEmpty()) reply(session, msgType, groupId, userId, notice);
        return true;
    }

    synchronized List<String> getWhitelist() { return whitelist; }
















    boolean isAllowedByWhitelist(String commandWord) {
        if (commandWord == null || commandWord.isEmpty()) return false;
        List<String> wl = getWhitelist();
        if (wl.contains(commandWord)) return true;


        return "r".equals(commandWord) && wl.contains("rd");
    }










    static String dispatchCommand(stellaris.dice.DiceHelper dice, String restRaw) {
        final String in = restRaw == null ? "" : restRaw.trim();
        if (in.isEmpty()) return "";
        final List<String> hit = new ArrayList<>(1);
        try {
            stellaris.dice.diceFunction.commands.DiceCommandDecoder.INSTANCE.invoke(
                    new stellaris.dice.diceFunction.commands.DiceCommandCallback() {
                        @Override public ArrayList<String> extraCommands() {
                            ArrayList<String> extra = new ArrayList<>();
                            if (dice != null) {
                                try {
                                    extra.addAll(dice.getDiceFunction()
                                            .getDiceCustomFileLoader().getCustomCommands());
                                    extra.add("blackcloud");


                                    extra.add("find");
                                    extra.add("查询");
                                    extra.add("查找");


                                    extra.add("rb");
                                    extra.add("rp");



                                    extra.add("dg");
                                    extra.add("fate");
                                    extra.add("wx");
                                    extra.add("cpr");
                                    extra.add("setdg");
                                    extra.add("setfate");
                                    extra.add("setdx");
                                    extra.add("setwx");
                                    extra.add("setcpr");
                                } catch (Throwable ignore) {

                                }
                            }
                            return extra;
                        }
                        @Override public boolean onCommand(String command, String param, boolean isProtected) {
                            hit.add(command == null ? "" : command.toLowerCase());
                            return true;
                        }
                        @Override public void onException(stellaris.core.defines.DiceException e) { }
                    }, in);
        } catch (Throwable t) {
            return "";
        }
        return hit.isEmpty() ? "" : hit.get(0);
    }



    static String normalizeWidth(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uFF01' && c <= '\uFF5E') sb.append((char) (c - 0xFEE0));
            else if (c == '\u3000') sb.append(' ');
            else if (c == '\u3002') sb.append('.');
            else sb.append(c);
        }
        return sb.toString();
    }

    private String pureUsage() {
        String pfx = getPrefix();
        return "用法：" + pfx + "pure mode on / " + pfx + "pure mode off（管理员）；"
                + pfx + "pure auto on / " + pfx + "pure auto off（仅 master）";
    }




    static String plainText(Map<String, Object> ev) {
        Object raw = ev.get("message");
        if (raw instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object o : Json.getArray(ev, "message")) {
                if (o instanceof Map) {
                    Map<String, Object> seg = (Map<String, Object>) o;
                    if ("text".equals(String.valueOf(seg.get("type")))) {
                        Map<String, Object> d = Json.getObject(seg, "data");
                        if (d != null && d.get("text") != null) sb.append(d.get("text"));
                    }
                }
            }
            return sb.toString().trim();
        }
        String s = String.valueOf(ev.getOrDefault("raw_message", "")).trim();
        return s.replaceAll("\\[CQ:[^\\]]*\\]", "").trim();
    }


















    public static boolean atSomeoneElse(Map<String, Object> ev) {
        long selfId = Json.getLong(ev, "self_id", -1);
        if (selfId <= 0) return false;
        String self = String.valueOf(selfId);
        boolean atMe = false, atOther = false;
        Object raw = ev.get("message");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> seg = (Map<String, Object>) o;
                if (!"at".equals(String.valueOf(seg.get("type")))) continue;
                Map<String, Object> d = Json.getObject(seg, "data");
                if (d == null || d.get("qq") == null) continue;
                String qq = String.valueOf(d.get("qq"));
                if (qq.equals(self)) atMe = true;
                else if (qq.matches("\\d+")) atOther = true;
            }
        } else {
            Matcher at = CQ_AT.matcher(String.valueOf(ev.getOrDefault("raw_message", "")));
            while (at.find()) {
                if (at.group(1).equals(self)) atMe = true;
                else atOther = true;
            }
        }
        return atOther && !atMe;
    }


    private boolean isAdmin(Map<String, Object> ev, long userId, stellaris.dice.DiceHelper dice) {
        Map<String, Object> sender = Json.getObject(ev, "sender");
        if (sender != null) {
            String role = String.valueOf(sender.getOrDefault("role", "")).toLowerCase();
            if ("owner".equals(role) || "admin".equals(role) || "administrator".equals(role)) return true;
        }
        return isDiceMaster(dice, userId);
    }




    private static boolean isDiceMaster(stellaris.dice.DiceHelper dice, long userId) {
        if (dice != null) {
            try {
                return dice.getDiceFunction().getDiceConfig().isMaster(String.valueOf(userId));
            } catch (Throwable t) {

            }
        }
        return isDiceMasterByScan(userId);
    }



    private static volatile long masterFailUntil;

    private static boolean isDiceMasterByScan(long userId) {
        if (System.currentTimeMillis() < masterFailUntil) return false;
        try {
            for (stellaris.dice.DiceBrief brief : stellaris.dice.DiceManager.getDiceList()) {
                stellaris.dice.DiceHelper helper =
                        stellaris.dice.DiceManager.getDice(brief.getBotId());
                if (helper == null) continue;
                String masters = helper.readDiceConfig("MASTER_QQ");
                if (masters == null) continue;
                for (String line : masters.split("\r?\n")) {
                    if (String.valueOf(userId).equals(line.trim())) return true;
                }
            }
        } catch (Throwable t) {
            masterFailUntil = System.currentTimeMillis() + 60000;
            System.err.println("[core-gate] master lookup unavailable (retry in 60s): " + t);
        }
        return false;
    }

    private static void reply(stellaris.onebot.OneBotSession session, String msgType, long groupId, long userId, String text) {
        try {


            stellaris.util.LogLevel.debug("[gate] 核心模式答复 "
                    + ("group".equals(msgType) ? "群" + groupId : "私聊" + userId) + "："
                    + (text == null ? "" : text.length() > 60 ? text.substring(0, 60) + "…" : text));
            if ("group".equals(msgType)) {
                session.sendGroupMsg(groupId, text, false);
            } else {
                session.sendPrivateMsg(userId, text, false);
            }
        } catch (Throwable t) {
            System.err.println("[core-gate] reply failed: " + t);
        }
    }



    private void load() {
        if (configFile == null || !configFile.isFile()) return;
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(configFile.toPath(), StandardCharsets.UTF_8));
            groups.clear();


            List<Object> gs = Json.getArray(m, "groups");
            if (gs != null) for (Object o : gs) {
                try {
                    groups.add(Long.parseLong(String.valueOf(o).trim()));
                } catch (NumberFormatException ignore) {

                }
            }
            onMessage = opt(m, "onMessage", DEFAULT_ON);
            offMessage = opt(m, "offMessage", DEFAULT_OFF);
            denialMessage = opt(m, "denialMessage", DEFAULT_DENIAL);
            blockedNotice = opt(m, "blockedNotice", "");
            prefix = opt(m, "prefix", ".");
            uiPort = Json.getInt(m, "uiPort", 7788);
            List<Object> wl = Json.getArray(m, "whitelist");
            if (wl != null) {
                List<String> list = new ArrayList<>();
                for (Object o : wl) {
                    String s = String.valueOf(o).trim().toLowerCase();
                    if (!s.isEmpty() && !list.contains(s)) list.add(s);
                }
                whitelist = list;
            }

            List<Object> pgs = Json.getArray(m, "pureGroups");
            if (pgs != null) for (Object o : pgs) {
                try {
                    pureGroups.add(Long.parseLong(String.valueOf(o).trim()));
                } catch (NumberFormatException ignore) {
                }
            }
            pureAuto = Json.getBool(m, "pureAuto", false);
            if (Json.getBool(m, "enabled", false)) {


                System.out.println("[stellaris] 核心模式已改为按群设置：旧配置里的全局开关 enabled=true 已忽略，"
                        + "请在各群用 " + prefix + "core on 开启");
            }
        } catch (Exception e) {
            System.err.println("[core-gate] config load failed, using defaults: " + e);
        }
    }

    private static String opt(Map<String, Object> m, String key, String def) {
        String v = Json.getString(m, key);
        return v == null ? def : v;
    }

    private void save() {
        if (configFile == null) return;
        try {
            Map<String, Object> m = Json.newObject();
            List<Object> gs = Json.newArray();
            for (Long g : groups) gs.add(g);
            m.put("groups", gs);
            m.put("onMessage", onMessage);
            m.put("offMessage", offMessage);
            m.put("denialMessage", denialMessage);
            m.put("blockedNotice", blockedNotice);
            m.put("prefix", prefix);
            m.put("uiPort", uiPort);
            m.put("whitelist", whitelist);
            List<Object> pgs = Json.newArray();
            for (Long g : pureGroups) pgs.add(g);
            m.put("pureGroups", pgs);
            m.put("pureAuto", pureAuto);
            if (configFile.getParentFile() != null) configFile.getParentFile().mkdirs();
            stellaris.util.IoUtil.writeAtomic(configFile, Json.write(m));
        } catch (Exception e) {
            System.err.println("[core-gate] config save failed: " + e);
        }
    }
}
