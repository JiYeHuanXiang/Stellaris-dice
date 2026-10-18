package stellaris.bridge;

import stellaris.dice.DiceHelper;
import stellaris.dice.DiceManager;
import stellaris.dice.DiceKernel;
import stellaris.onebot.OneBotSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;








public final class DiceBoot {


    public static final String DATA_DIR_PREFIX = "StellarisData_";

    private DiceBoot() { }









    public static DiceHelper boot(String botId, String basePath, OneBotSession session, File contentDir) {
        long selfId;
        try {
            selfId = Long.parseLong(botId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("botId 必须是 QQ 号：" + botId, e);
        }
        session.setSelfId(selfId);

        File instanceRoot = new File(basePath, DATA_DIR_PREFIX + botId);
        if (contentDir != null) {
            seedContent(instanceRoot, contentDir);
        }
        CoreModeGate.INSTANCE.init(new File(instanceRoot, "config"));

        DiceManager.install(DiceKernel.INSTANCE);
        try {
            DiceManager.setBasePath(basePath);
        } catch (IllegalStateException alreadySet) {

        }

        OneBotIntentEvent intent = new OneBotIntentEvent(session, selfId, instanceRoot);
        return DiceManager.createDice(botId, intent);
    }







    public static void seedContent(File instanceRoot, File contentDir) {
        seedDir(new File(contentDir, "draw"), new File(instanceRoot, "draw"));
        seedDir(new File(contentDir, "data"), new File(instanceRoot, "data"));
        seedDir(new File(contentDir, "find"), new File(instanceRoot, "find"));
        seedDir(new File(contentDir, "custom"), new File(instanceRoot, "custom"));
    }

    private static void seedDir(File source, File target) {
        if (!source.isDirectory()) return;
        Path src = source.toPath();
        Path dst = target.toPath();
        try (Stream<Path> walk = Files.walk(src)) {
            walk.forEach(path -> {
                Path relative = src.relativize(path);
                Path out = dst.resolve(relative);
                try {
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(out);
                    } else if (!Files.exists(out)) {
                        Files.createDirectories(out.getParent());
                        try {
                            Files.copy(path, out, StandardCopyOption.COPY_ATTRIBUTES);
                        } catch (UnsupportedOperationException e) {

                            Files.copy(path, out);
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("内容播种失败：" + path + " → " + out, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("内容目录扫描失败：" + source, e);
        }
    }
}
