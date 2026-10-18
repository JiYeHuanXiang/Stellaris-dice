package stellaris.bridge;











public final class HostSettings {

    public interface Provider {
        boolean mergeForward();
        boolean detectSelfMessage();
    }

    private static volatile Provider provider = new Provider() {
        @Override public boolean mergeForward() { return false; }
        @Override public boolean detectSelfMessage() { return true; }
    };

    private HostSettings() { }

    public static void install(Provider newProvider) {
        provider = newProvider == null
                ? new Provider() {
                    @Override public boolean mergeForward() { return false; }
                    @Override public boolean detectSelfMessage() { return true; }
                }
                : newProvider;
    }

    public static boolean mergeForward() {
        return provider.mergeForward();
    }

    public static boolean detectSelfMessage() {
        return provider.detectSelfMessage();
    }
}
