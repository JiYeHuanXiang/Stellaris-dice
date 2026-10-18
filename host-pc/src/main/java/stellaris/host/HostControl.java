package stellaris.host;

import java.io.IOException;







public interface HostControl {

    boolean isRunning();

    String runningBotQq();


    HostConfig config();


    void updateConfig(HostConfig config) throws IOException;

    void start(HostConfig config) throws Exception;

    void stop();
}
