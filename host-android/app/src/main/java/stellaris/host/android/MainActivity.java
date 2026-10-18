package stellaris.host.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import stellaris.bridge.DiceBoot;
import stellaris.bridge.EventTranslator;
import stellaris.dice.DiceHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;







public final class MainActivity extends Activity {

    private static final String PREFS = "stellaris_host";
    private static final String KEY_QQ = "botQq";
    private static final String KEY_WS = "wsUrl";
    private static final String KEY_TOKEN = "token";

    private static final int BG = Color.rgb(0x0f, 0x14, 0x1a);
    private static final int CARD = Color.rgb(0x17, 0x1d, 0x26);
    private static final int TEXT = Color.rgb(0xe8, 0xec, 0xf2);
    private static final int SUB = Color.rgb(0x9a, 0xa8, 0xba);
    private static final int ACCENT = Color.rgb(0x3f, 0xa7, 0xd6);

    private EditText qqField;
    private EditText wsField;
    private EditText tokenField;
    private Button startButton;
    private Button stopButton;
    private TextView statusLabel;
    private TextView logView;

    private AndroidOneBotSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("群星 · Stellaris Dice");
        title.setTextColor(TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("OneBot 正向 WebSocket 接入；内容随包播种。");
        hint.setTextColor(SUB);
        hint.setTextSize(13);
        hint.setPadding(0, dp(4), 0, dp(12));
        root.addView(hint);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        qqField = field(root, "骰娘 QQ", prefs.getString(KEY_QQ, ""), InputType.TYPE_CLASS_NUMBER);
        wsField = field(root, "OneBot WS 地址", prefs.getString(KEY_WS, "ws://127.0.0.1:3001/"),
                InputType.TYPE_CLASS_TEXT);
        tokenField = field(root, "Access Token（可空）", prefs.getString(KEY_TOKEN, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(10), 0, dp(10));
        startButton = button("启动", ACCENT);
        stopButton = button("停止", Color.rgb(0x4f, 0x9c, 0xf0));
        stopButton.setEnabled(false);
        startButton.setOnClickListener(v -> start());
        stopButton.setOnClickListener(v -> stop());
        buttons.addView(startButton);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopParams.leftMargin = dp(8);
        buttons.addView(stopButton, stopParams);
        root.addView(buttons);

        statusLabel = new TextView(this);
        statusLabel.setText("未启动");
        statusLabel.setTextColor(SUB);
        statusLabel.setTextSize(13);
        statusLabel.setPadding(0, 0, 0, dp(10));
        root.addView(statusLabel);

        logView = new TextView(this);
        logView.setTextColor(TEXT);
        logView.setTextSize(12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setBackgroundColor(CARD);
        logView.setPadding(dp(10), dp(10), dp(10), dp(10));
        logView.setTextIsSelectable(true);
        root.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));

        TextView note = new TextView(this);
        note.setText("提示：模拟器访问电脑上的 OneBot 用 ws://10.0.2.2:<端口>/；同机平台用 127.0.0.1。");
        note.setTextColor(SUB);
        note.setTextSize(12);
        note.setPadding(0, dp(10), 0, 0);
        root.addView(note);
        return scroll;
    }

    private EditText field(LinearLayout root, String label, String value, int inputType) {
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(SUB);
        name.setTextSize(13);
        name.setPadding(0, dp(8), 0, dp(2));
        root.addView(name);

        EditText edit = new EditText(this);
        edit.setText(value == null ? "" : value);
        edit.setInputType(inputType);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(SUB);
        edit.setBackgroundColor(CARD);
        edit.setSingleLine(true);
        root.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    private Button button(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(color);
        return button;
    }

    private void start() {
        String qq = qqField.getText().toString().trim();
        String ws = wsField.getText().toString().trim();
        String token = tokenField.getText().toString();
        if (!qq.matches("\\d{5,12}")) {
            appendLog("骰娘 QQ 必须是 5~12 位数字");
            return;
        }
        if (!(ws.startsWith("ws://") || ws.startsWith("wss://"))) {
            appendLog("WS 地址必须以 ws:// 或 wss:// 开头");
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_QQ, qq)
                .putString(KEY_WS, ws)
                .putString(KEY_TOKEN, token)
                .apply();

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("启动中…");
        statusLabel.setTextColor(ACCENT);
        appendLog("启动骰娘 " + qq + " …");

        new Thread(() -> {
            try {
                File content = new File(getFilesDir(), "content");
                seedAssets(content);
                session = new AndroidOneBotSession(ws, token);
                DiceHelper helper = DiceBoot.boot(
                        qq,
                        new File(getFilesDir(), "dataRoot").getAbsolutePath(),
                        session,
                        content);
                EventTranslator translator = new EventTranslator(helper, session);
                session.addEventListener(translator::translate);
                session.connect();
                runOnUiThread(() -> {
                    statusLabel.setText("运行中 · QQ " + qq);
                    statusLabel.setTextColor(ACCENT);
                    appendLog("已连接：" + ws);
                });
            } catch (Throwable t) {
                Log.e("stellaris", "start failed", t);
                runOnUiThread(() -> {
                    statusLabel.setText("启动失败");
                    statusLabel.setTextColor(Color.rgb(0xe0, 0x8f, 0x5f));
                    appendLog("启动失败：" + t);
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                });
            }
        }, "stellaris-boot").start();
    }

    private void stop() {
        if (session != null) {
            session.close();
            session = null;
        }
        statusLabel.setText("已停止");
        statusLabel.setTextColor(SUB);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        appendLog("已停止");
    }


    private void seedAssets(File contentRoot) throws IOException {
        for (String dir : new String[]{"draw", "data", "find", "custom"}) {
            copyAssetDir(dir, new File(contentRoot, dir));
        }
    }

    private void copyAssetDir(String assetPath, File target) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            if (!target.exists()) {
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                try (InputStream in = getAssets().open(assetPath);
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                }
            }
            return;
        }
        target.mkdirs();
        for (String child : children) {
            copyAssetDir(assetPath + "/" + child, new File(target, child));
        }
    }

    private void appendLog(String line) {
        runOnUiThread(() -> logView.append(line + "\n"));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    @Override
    protected void onDestroy() {
        stop();
        super.onDestroy();
    }
}
