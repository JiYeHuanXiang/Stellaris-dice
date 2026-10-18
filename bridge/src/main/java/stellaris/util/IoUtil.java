package stellaris.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;














public final class IoUtil {

    private IoUtil() { }


    public static void writeAtomic(File dst, byte[] data) throws IOException {
        Path target = dst.toPath();
        Path dir = target.getParent();
        if (dir != null && !Files.isDirectory(dir)) Files.createDirectories(dir);
        Path tmp = (dir == null)
                ? java.nio.file.Paths.get(dst.getName() + ".tmp")
                : dir.resolve(dst.getName() + ".tmp");
        Files.write(tmp, data);
        try {
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {

            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) { }
            throw e;
        }
    }


    public static void writeAtomic(File dst, String text) throws IOException {
        writeAtomic(dst, text.getBytes(StandardCharsets.UTF_8));
    }
}
