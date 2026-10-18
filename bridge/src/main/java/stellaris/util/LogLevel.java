package stellaris.util;

import java.io.PrintStream;
import java.util.function.Consumer;







public final class LogLevel {


    public static final String DICE_TAG = "[骰] ";

    public static final String ALERT_TAG = "[骰!] ";

    private static volatile Consumer<String> sink;
    private static volatile boolean debugEnabled = true;

    private LogLevel() { }

    public static void install(Consumer<String> newSink) {
        sink = newSink;
    }

    public static void setDebug(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void info(String message) {
        emit(DICE_TAG + message, false);
    }

    public static void debug(String message) {
        if (debugEnabled) emit(DICE_TAG + message, false);
    }

    public static void alert(String message) {
        emit(ALERT_TAG + message, true);
    }

    private static void emit(String line, boolean error) {
        Consumer<String> current = sink;
        if (current != null) {
            current.accept(line);
            return;
        }
        PrintStream out = error ? System.err : System.out;
        out.println(line);
    }
}
