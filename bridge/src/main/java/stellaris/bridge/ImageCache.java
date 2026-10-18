package stellaris.bridge;

import stellaris.util.Json;
import stellaris.util.NetGuard;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;





























public final class ImageCache {


    public static final int MAX_FILES = 800;

    public static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    public static final String CONFIG_KEY = "IMAGE_CACHE_DIR";













    public static final int NAME_HASH_CHARS = 16;


    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 6000;

    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private static final int MAX_REDIRECTS = 3;
    private static final String UA = "Stella-ImageCache/1.0 (OneBot image cache)";

    private static final String PART_SUFFIX = ".part";

    private static final long STALE_PART_MS = 60L * 60 * 1000;

    private static final int SNIFF_BYTES = 16;
    private static final String[] URL_EXTS = {
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "ico", "svg", "avif",
    };


    private static volatile boolean enabled;





    private static final int MEMO_MAX = 256;
    private static final Map<String, String> MEMO =
            new LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MEMO_MAX;
                }
            };


    private static final AtomicBoolean SWEEPING = new AtomicBoolean();
    private static final ExecutorService SWEEP = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stellaris-image-sweep");
        t.setDaemon(true);
        return t;
    });

    private ImageCache() { }




    public static void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        stellaris.util.LogLevel.info(on
                ? "图片处理模式：缓存为本地文件（收到的图片下载到 "
                        + "骰子数据目录/pictures/imageCache/<名字>.<ext>，最多 " + MAX_FILES
                        + " 张 / " + (MAX_AGE_MS / 86400000) + " 天）"
                : "图片处理模式：默认处理（图片段不进骰核，只在收信日志里显示 [image]）");
    }

    public static boolean enabled() {
        return enabled;
    }




    public static File dir(File diceRoot) {
        return new File(new File(diceRoot, "pictures"), "imageCache");
    }


    public static File dirForBot(File home, long botId) {
        return dir(new File(new File(home, "data"), "StellarisData_" + botId));
    }


    public static String pathOf(File diceRoot) {
        return slashes(dir(diceRoot));
    }





    public static boolean publish(stellaris.dice.DiceHelper dice, File diceRoot) {
        if (dice == null) return false;
        try {
            String want = enabled ? pathOf(diceRoot) : "";
            String have = dice.readDiceConfig(CONFIG_KEY);
            if (have == null) have = "";
            if (want.equals(have)) return false;
            dice.writeDiceConfig(CONFIG_KEY, want);
            return true;
        } catch (Throwable t) {
            stellaris.util.LogLevel.alert("图片缓存目录写入骰核配置失败: " + t);
            return false;
        }
    }








    public static String resolve(File diceRoot, Map<String, Object> data) {
        if (!enabled || data == null) return null;
        final String url = pickUrl(data);
        if (url == null) return null;

        String memo = MEMO.get(url);
        if (memo != null) {
            File f = new File(memo);
            if (f.isFile()) {
                touch(f);
                return slashes(f);
            }
            MEMO.remove(url);
        }

        try {
            NetGuard.checkOutboundUrl(url);
        } catch (RuntimeException e) {
            stellaris.util.LogLevel.alert("图片缓存跳过（出站策略拒绝）: " + url + " —— " + e.getMessage());
            return null;
        }

        try {
            File d = dir(diceRoot);
            if (!d.isDirectory() && !d.mkdirs()) {
                stellaris.util.LogLevel.alert("图片缓存目录建不出来: " + d);
                return null;
            }
            File tmp = new File(d, UUID.randomUUID() + PART_SUFFIX);
            String name;
            try {
                name = download(url, tmp);
            } catch (Throwable t) {
                delete(tmp);
                stellaris.util.LogLevel.alert("图片缓存下载失败: " + url + " —— " + t);
                return null;
            }
            if (name == null) {
                delete(tmp);
                return null;
            }
            File target = new File(d, name + "." + extOf(tmp, url));
            if (target.isFile()) {
                delete(tmp);
            } else if (!move(tmp, target)) {
                delete(tmp);
                stellaris.util.LogLevel.alert("图片缓存落盘失败: " + target);
                return null;
            }
            touch(target);
            synchronized (MEMO) {
                MEMO.put(url, target.getAbsolutePath());
            }
            scheduleSweep(diceRoot);
            return slashes(target);
        } catch (Throwable t) {
            stellaris.util.LogLevel.alert("图片缓存失败: " + url + " —— " + t);
            return null;
        }
    }


    private static String pickUrl(Map<String, Object> data) {
        String url = trim(Json.getString(data, "url"));
        if (url == null) url = trim(Json.getString(data, "file"));
        if (url == null) return null;
        String low = url.toLowerCase(Locale.ROOT);
        if (!low.startsWith("http://") && !low.startsWith("https://")) return null;
        return url;
    }






    private static String download(String url, File tmp) throws Exception {
        String cur = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(cur).openConnection();
            try {
                con.setConnectTimeout(CONNECT_TIMEOUT_MS);
                con.setReadTimeout(READ_TIMEOUT_MS);
                con.setInstanceFollowRedirects(false);
                con.setUseCaches(false);
                con.setRequestProperty("User-Agent", UA);
                int code = con.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = con.getHeaderField("Location");
                    if (loc == null || loc.trim().isEmpty()) {
                        stellaris.util.LogLevel.alert("图片缓存失败：重定向没有 Location（" + code + "）" + cur);
                        return null;
                    }
                    String next = new URL(new URL(cur), loc.trim()).toString();
                    try {
                        NetGuard.checkOutboundUrl(next);
                    } catch (RuntimeException e) {
                        stellaris.util.LogLevel.alert("图片缓存跳过（重定向到受保护地址）: " + next
                                + " —— " + e.getMessage());
                        return null;
                    }
                    cur = next;
                    continue;
                }
                if (code != 200) {
                    stellaris.util.LogLevel.alert("图片缓存失败：HTTP " + code + " " + cur);
                    return null;
                }
                long len = con.getContentLengthLong();
                if (len > MAX_BYTES) {
                    stellaris.util.LogLevel.alert("图片缓存失败：体积 " + (len / 1048576) + "MB 超过上限 "
                            + (MAX_BYTES / 1048576) + "MB —— " + cur);
                    return null;
                }
                return save(con.getInputStream(), tmp);
            } finally {
                con.disconnect();
            }
        }
        stellaris.util.LogLevel.alert("图片缓存失败：重定向次数超过 " + MAX_REDIRECTS + " —— " + url);
        return null;
    }


    private static String save(InputStream in, File tmp) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new IllegalStateException("图片超过 " + (MAX_BYTES / 1048576) + "MB 上限");
                }
                sha.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        } finally {
            close(in);
        }
        if (total == 0) throw new IllegalStateException("图片内容为空");
        return hex(sha.digest()).substring(0, NAME_HASH_CHARS);
    }


    private static String extOf(File f, String url) {
        String sniffed = null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] head = new byte[SNIFF_BYTES];
            int n = in.read(head);
            if (n > 0) sniffed = sniffExt(Arrays.copyOf(head, n));
        } catch (Throwable ignore) {

        }
        if (sniffed != null) return sniffed;
        String fromUrl = extFromUrl(url);
        return fromUrl != null ? fromUrl : "jpg";
    }


    private static String extFromUrl(String url) {
        try {
            String path = new URL(url).getPath();
            int dot = path.lastIndexOf('.');
            if (dot < 0 || dot == path.length() - 1) return null;
            String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.length() > 5 || !ext.matches("[a-z0-9]+")) return null;
            for (String known : URL_EXTS) if (known.equals(ext)) return ext;
            return null;
        } catch (Throwable ignore) {
            return null;
        }
    }


    public static String sniffExt(byte[] h) {
        if (h == null || h.length < 4) return null;
        int b0 = h[0] & 0xFF, b1 = h[1] & 0xFF, b2 = h[2] & 0xFF, b3 = h[3] & 0xFF;
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "jpg";
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "png";
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return "gif";
        if (b0 == 0x42 && b1 == 0x4D) return "bmp";
        if (b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 && h.length >= 12
                && (h[8] & 0xFF) == 0x57 && (h[9] & 0xFF) == 0x45
                && (h[10] & 0xFF) == 0x42 && (h[11] & 0xFF) == 0x50) {
            return "webp";
        }
        if ((b0 == 0x49 && b1 == 0x49 && b2 == 0x2A) || (b0 == 0x4D && b1 == 0x4D && b2 == 0x2A)) {
            return "tif";
        }
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x01 && b3 == 0x00) return "ico";
        if (b0 == 0x66 && b1 == 0x74 && b2 == 0x79 && b3 == 0x70 && h.length >= 12) {

            String brand = new String(h, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.startsWith("avif") || brand.startsWith("avis")) return "avif";
            if (brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("mif1")) {
                return "heic";
            }
        }
        return null;
    }







    public static List<File> planEviction(File[] files, long now) {
        List<File> out = new ArrayList<>();
        if (files == null) return out;
        List<File> live = new ArrayList<>();
        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            if (f.getName().endsWith(PART_SUFFIX)) {
                if (now - f.lastModified() > STALE_PART_MS) out.add(f);
                continue;
            }
            live.add(f);
        }
        File[] rest = live.toArray(new File[0]);
        Arrays.sort(rest, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                int byTime = Long.compare(b.lastModified(), a.lastModified());
                return byTime != 0 ? byTime : a.getName().compareTo(b.getName());
            }
        });
        for (int i = 0; i < rest.length; i++) {
            File f = rest[i];
            if (i >= MAX_FILES || now - f.lastModified() > MAX_AGE_MS) out.add(f);
        }
        return out;
    }


    public static void sweep(File diceRoot) {
        try {
            File d = dir(diceRoot);
            if (!d.isDirectory()) return;
            for (File f : planEviction(d.listFiles(), System.currentTimeMillis())) delete(f);
        } catch (Throwable t) {
            stellaris.util.LogLevel.debug("图片缓存淘汰失败: " + t);
        }
    }


    public static void scheduleSweep(final File diceRoot) {
        if (!SWEEPING.compareAndSet(false, true)) return;
        try {
            SWEEP.execute(new Runnable() {
                @Override public void run() {
                    try {
                        sweep(diceRoot);
                    } finally {
                        SWEEPING.set(false);
                    }
                }
            });
        } catch (Throwable t) {
            SWEEPING.set(false);
        }
    }


    public static String stateFor(File diceRoot) {
        File[] fs = dir(diceRoot).listFiles();
        if (fs == null) return null;
        int n = 0;
        long bytes = 0;
        for (File f : fs) {
            if (f == null || !f.isFile() || f.getName().endsWith(PART_SUFFIX)) continue;
            n++;
            bytes += f.length();
        }
        return n + " 张 · " + human(bytes);
    }


    public static void clearMemo() {
        synchronized (MEMO) {
            MEMO.clear();
        }
    }



    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String slashes(File f) {
        return f.getAbsolutePath().replace('\\', '/');
    }

    private static void touch(File f) {
        try {
            f.setLastModified(System.currentTimeMillis());
        } catch (Throwable ignore) {

        }
    }

    private static void delete(File f) {
        try {
            if (f != null) f.delete();
        } catch (Throwable ignore) {

        }
    }


    private static boolean move(File src, File dst) {
        try {
            Files.move(src.toPath(), dst.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (Throwable t) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static void close(Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Throwable ignore) {

        }
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }
}
