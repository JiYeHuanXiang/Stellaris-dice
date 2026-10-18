package stellaris.host;







public final class HostMain {

    private HostMain() { }

    public static void main(String[] args) throws Exception {
        HostConfig config = HostConfig.fromEnv();
        HostRuntime runtime = new HostRuntime();
        runtime.start(config);

        WebUiApiServer webUi = null;
        if (config.webUiPort > 0) {
            webUi = new WebUiApiServer(runtime, config.webUiToken);
            webUi.start(config.webUiPort);
            System.out.println("[群星] WebUI JSON 接口：http://127.0.0.1:" + config.webUiPort
                    + "/（不内置页面，社区可自建）");
        }

        WebUiApiServer finalWebUi = webUi;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (finalWebUi != null) finalWebUi.stop();
            runtime.stop();
        }));

        System.out.println("[群星] 骰娘 " + config.botQq + " 已启动");
        System.out.println("[群星] 事件监听：http://0.0.0.0:" + config.eventPort + "/");
        System.out.println("[群星] 动作端点：" + config.apiUrl);
        System.out.println("[群星] 数据根：" + config.dataDir + "；内容：" + config.contentDir);
        Thread.currentThread().join();
    }
}
