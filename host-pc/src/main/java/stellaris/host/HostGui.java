package stellaris.host;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;







public final class HostGui {

    private static final Color BG = new Color(0x0f141a);
    private static final Color CARD = new Color(0x171d26);
    private static final Color BORDER = new Color(0x2a3342);
    private static final Color TEXT = new Color(0xe8ecf2);
    private static final Color SUB = new Color(0x9aa8ba);
    private static final Color ACCENT = new Color(0x3fa7d6);
    private static final Color ERR = new Color(0xe08f5f);

    private final HostRuntime runtime = new HostRuntime();
    private WebUiApiServer webUi;

    private JFrame frame;
    private JTextField qqField;
    private JTextField apiField;
    private JTextField eventPortField;
    private JPasswordField accessTokenField;
    private JTextField dataDirField;
    private JTextField contentDirField;
    private JTextField webUiPortField;
    private JPasswordField webUiTokenField;
    private JButton startButton;
    private JButton stopButton;
    private JButton saveButton;
    private JLabel statusLabel;
    private JTextArea logArea;

    private PrintStream originalOut;
    private PrintStream originalErr;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HostGui().show());
    }

    private void show() {
        HostConfig config = HostConfig.load(HostConfig.CONFIG_FILE);
        buildUi(config);
        installLogCapture();
        frame.setVisible(true);
        log("群星 PC 宿主已启动界面；配置保存在 " + HostConfig.CONFIG_FILE.getAbsolutePath());
    }

    private void buildUi(HostConfig config) {
        frame = new JFrame("群星 · Stellaris Dice");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(860, 600));
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout(0, 0));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG);
        form.setBorder(BorderFactory.createEmptyBorder(14, 18, 10, 18));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addField(form, gbc, row, "骰娘 QQ", qqField = textField(config.botQq));
        row = addField(form, gbc, row, "OneBot API", apiField = textField(config.apiUrl));
        row = addField(form, gbc, row, "事件端口", eventPortField = textField(String.valueOf(config.eventPort)));
        row = addField(form, gbc, row, "Access Token", accessTokenField = passwordField(config.accessToken));
        row = addField(form, gbc, row, "数据根", dataDirField = textField(config.dataDir));
        row = addField(form, gbc, row, "内容目录", contentDirField = textField(config.contentDir));
        row = addField(form, gbc, row, "WebUI 接口端口", webUiPortField = textField(String.valueOf(config.webUiPort)));
        row = addField(form, gbc, row, "WebUI Token", webUiTokenField = passwordField(config.webUiToken));

        JLabel hint = new JLabel("WebUI 仅提供 JSON 接口（默认关闭），不内置页面；社区可自建网页端。");
        hint.setForeground(SUB);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 12f));
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        form.add(hint, gbc);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        startButton = button("启动", ACCENT);
        stopButton = button("停止", new Color(0x4f9cf0));
        saveButton = button("保存配置", new Color(0x59b57a));
        statusLabel = new JLabel("未启动");
        statusLabel.setForeground(SUB);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        startButton.addActionListener(e -> start());
        stopButton.addActionListener(e -> stop());
        saveButton.addActionListener(e -> saveOnly());
        stopButton.setEnabled(false);
        buttons.add(startButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(stopButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(saveButton);
        buttons.add(statusLabel);
        buttons.add(Box.createHorizontalGlue());
        gbc.gridx = 0;
        gbc.gridy = ++row;
        gbc.gridwidth = 3;
        form.add(buttons, gbc);
        frame.add(form, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(CARD);
        logArea.setForeground(TEXT);
        logArea.setCaretColor(ACCENT);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JTextArea help = new JTextArea(
                "接入步骤\n\n"
                        + "1. OneBot 平台选择 HTTP 事件上报，地址填 http://127.0.0.1:<事件端口>/\n"
                        + "2. 动作接口填 OneBot API 地址（默认 http://127.0.0.1:3000）\n"
                        + "3. 在平台登录骰娘账号，然后点「启动」\n"
                        + "4. 群里发 .r d6 验证\n\n"
                        + "关于 WebUI\n\n"
                        + "本程序不内置 WebUI 页面。把「WebUI 接口端口」设为非 0 后，会只绑定\n"
                        + "127.0.0.1 并开放 JSON 接口：/api/status、/api/config、/api/start、/api/stop。\n"
                        + "社区可以基于这些接口自建网页端。");
        help.setEditable(false);
        help.setBackground(CARD);
        help.setForeground(SUB);
        help.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        help.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG);
        tabs.setForeground(TEXT);
        tabs.addTab("运行日志", new JScrollPane(logArea));
        tabs.addTab("接入说明", new JScrollPane(help));
        frame.add(tabs, BorderLayout.CENTER);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stop();
                restoreLogCapture();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setSize(900, 620);
    }

    private void start() {
        HostConfig config;
        try {
            config = readConfig();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "配置有误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String problem = config.validate();
        if (problem != null) {
            JOptionPane.showMessageDialog(frame, problem, "配置有误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            config.save(HostConfig.CONFIG_FILE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "配置保存失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        setBusy(true);
        statusLabel.setText("启动中…");
        log("启动骰娘 " + config.botQq + " …");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                runtime.start(config);
                if (config.webUiPort > 0) {
                    WebUiApiServer api = new WebUiApiServer(runtime, config.webUiToken);
                    api.start(config.webUiPort);
                    webUi = api;
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("运行中 · QQ " + config.botQq);
                    statusLabel.setForeground(ACCENT);
                    log("已启动：事件端口 " + config.eventPort + "，动作端点 " + config.apiUrl);
                    if (config.webUiPort > 0) {
                        log("WebUI JSON 接口：http://127.0.0.1:" + config.webUiPort + "/（无内置页面）");
                    }
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    setFieldsEnabled(false);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    statusLabel.setText("启动失败");
                    statusLabel.setForeground(ERR);
                    log("启动失败：" + cause);
                    JOptionPane.showMessageDialog(frame, String.valueOf(cause.getMessage()),
                            "启动失败", JOptionPane.ERROR_MESSAGE);
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    setFieldsEnabled(true);
                }
            }
        }.execute();
    }

    private void stop() {
        if (webUi != null) {
            webUi.stop();
            webUi = null;
        }
        runtime.stop();
        statusLabel.setText("已停止");
        statusLabel.setForeground(SUB);
        log("已停止");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        setFieldsEnabled(true);
    }

    private void saveOnly() {
        try {
            HostConfig config = readConfig();
            String problem = config.validate();
            if (problem != null) {
                JOptionPane.showMessageDialog(frame, problem, "配置有误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            config.save(HostConfig.CONFIG_FILE);
            log("配置已保存：" + HostConfig.CONFIG_FILE.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "保存失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setBusy(boolean busy) {
        startButton.setEnabled(!busy);
        stopButton.setEnabled(!busy && runtime.isRunning());
        saveButton.setEnabled(!busy);
    }

    private void setFieldsEnabled(boolean enabled) {
        qqField.setEnabled(enabled);
        apiField.setEnabled(enabled);
        eventPortField.setEnabled(enabled);
        accessTokenField.setEnabled(enabled);
        dataDirField.setEnabled(enabled);
        contentDirField.setEnabled(enabled);
        webUiPortField.setEnabled(enabled);
        webUiTokenField.setEnabled(enabled);
        saveButton.setEnabled(enabled);
    }

    private HostConfig readConfig() {
        HostConfig config = new HostConfig();
        config.botQq = qqField.getText().trim();
        config.apiUrl = apiField.getText().trim();
        config.eventPort = parseInt(eventPortField.getText(), "事件端口");
        config.accessToken = new String(accessTokenField.getPassword());
        config.dataDir = dataDirField.getText().trim();
        config.contentDir = contentDirField.getText().trim();
        config.webUiPort = parseInt(webUiPortField.getText(), "WebUI 接口端口");
        config.webUiToken = new String(webUiTokenField.getPassword());
        return config;
    }

    private int parseInt(String text, String label) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
    }

    private int addField(JPanel panel, GridBagConstraints gbc, int row, String label, javax.swing.JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        JLabel name = new JLabel(label);
        name.setForeground(SUB);
        panel.add(name, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        return row + 1;
    }

    private JTextField textField(String value) {
        JTextField field = new JTextField(value == null ? "" : value);
        field.setBackground(CARD);
        field.setForeground(TEXT);
        field.setCaretColor(ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return field;
    }

    private JPasswordField passwordField(String value) {
        JPasswordField field = new JPasswordField(value == null ? "" : value);
        field.setBackground(CARD);
        field.setForeground(TEXT);
        field.setCaretColor(ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return field;
    }

    private JButton button(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return button;
    }

    private void log(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void installLogCapture() {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(new TeeOutputStream(logArea, originalOut), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new TeeOutputStream(logArea, originalErr), true, StandardCharsets.UTF_8));
    }

    private void restoreLogCapture() {
        if (originalOut != null) System.setOut(originalOut);
        if (originalErr != null) System.setErr(originalErr);
    }


    private static final class TeeOutputStream extends OutputStream {
        private final JTextArea area;
        private final PrintStream original;
        private final StringBuilder pending = new StringBuilder();

        TeeOutputStream(JTextArea area, PrintStream original) {
            this.area = area;
            this.original = original;
        }

        @Override
        public void write(int b) {
            original.write(b);
            process(b);
        }

        @Override
        public void write(byte[] data, int off, int len) {
            original.write(data, off, len);
            for (int i = off; i < off + len; i++) process(data[i] & 0xff);
        }

        private void process(int b) {
            synchronized (pending) {
                if (b == '\n') {
                    String line = pending.toString();
                    pending.setLength(0);
                    SwingUtilities.invokeLater(() -> {
                        area.append(line + "\n");
                        area.setCaretPosition(area.getDocument().getLength());
                    });
                } else if (b != '\r') {
                    pending.append((char) b);
                }
            }
        }
    }
}
