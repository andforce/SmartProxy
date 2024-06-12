package me.smartproxy.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

import me.smartproxy.R;
import me.smartproxy.core.LocalVpnService;
import me.smartproxy.core.OnStatusChangedListener;

public class MainActivity extends AppCompatActivity {

    private static String GL_HISTORY_LOGS;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String CONFIG_URL_KEY = "CONFIG_URL_KEY";

    private static final int START_VPN_SERVICE_REQUEST_CODE = 1985;

    private SwitchCompat switchProxy;
    private TextView textViewLog;
    private ScrollView scrollViewLog;
    private TextView textViewConfigUrl;
    private Calendar mCalendar;

    private Button mExitButton;

    private final OnStatusChangedListener listener = new OnStatusChangedListener() {
        @Override
        public void onStatusChanged(String status, Boolean isRunning) {
            switchProxy.setEnabled(true);
            switchProxy.setChecked(isRunning);
            Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onLogReceived(String logString) {
            mCalendar.setTimeInMillis(System.currentTimeMillis());
            logString = String.format(Locale.ENGLISH, "[%1$02d:%2$02d:%3$02d] %4$s\n",
                    mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE),
                    mCalendar.get(Calendar.SECOND),
                    logString);

            Log.i(TAG, logString);

            if (textViewLog.getLineCount() > 200) {
                textViewLog.setText("");
            }
            textViewLog.append(logString);
            scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);
            GL_HISTORY_LOGS = textViewLog.getText() == null ? "" : textViewLog.getText().toString();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_2);

        scrollViewLog = findViewById(R.id.scrollViewLog);
        textViewLog = findViewById(R.id.textViewLog);
        findViewById(R.id.configUrlLayout).setOnClickListener(v -> {
            if (switchProxy.isChecked()) {
                return;
            }

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.config_url)
                    .setItems(new CharSequence[]{
                            getString(R.string.config_url_manual)
                    }, (dialogInterface, i) -> {
                        if (i == 1) {
                            showConfigUrlInputDialog();
                        }
                    })
                    .show();
        });

        textViewConfigUrl = findViewById(R.id.textViewConfigUrl);
        String configUrl = readConfigUrl();
        if (TextUtils.isEmpty(configUrl)) {
            textViewConfigUrl.setText(R.string.config_not_set_value);
        } else {
            textViewConfigUrl.setText(configUrl);
        }

        textViewLog.setText(GL_HISTORY_LOGS);
        scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);

        mCalendar = Calendar.getInstance();
        //LocalVpnService.addOnStatusChangedListener(listener);

        switchProxy = findViewById(R.id.proxy_switch);
        switchProxy.setChecked(LocalVpnService.IsRunning);
        switchProxy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (LocalVpnService.IsRunning != isChecked) {
                switchProxy.setEnabled(false);
                if (isChecked) {
                    Intent intent = LocalVpnService.prepare(MainActivity.this);
                    if (intent == null) {
                        startVPNService();
                    } else {
                        startActivityForResult(intent, START_VPN_SERVICE_REQUEST_CODE);
                    }
                } else {
                    LocalVpnService.IsRunning = false;
                }
            }
        });

        mExitButton = findViewById(R.id.btnConfigUrl);
        mExitButton.setOnClickListener(v -> {
            //finish();
            if (!LocalVpnService.IsRunning) {
                finish();
                return ;
            }

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.menu_item_exit)
                    .setMessage(R.string.exit_confirm_info)
                    .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                        LocalVpnService.IsRunning = false;
                        LocalVpnService.Instance.disconnectVPN();
                        stopService(new Intent(MainActivity.this, LocalVpnService.class));
                        System.runFinalization();
                        System.exit(0);
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        });
    }

    String readConfigUrl() {
        SharedPreferences preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE);
        return preferences.getString(CONFIG_URL_KEY, "");
    }

    void setConfigUrl(String configUrl) {
        SharedPreferences preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE);
        Editor editor = preferences.edit();
        editor.putString(CONFIG_URL_KEY, configUrl);
        editor.apply();
    }

    boolean isValidUrl(String url) {
        if (!LocalVpnService.IS_ENABLE_REMOTE_PROXY) {
            return true;
        }
        try {
            if (url == null || url.isEmpty())
                return false;

            if (url.startsWith("/")) {//file path
                File file = new File(url);
                if (!file.exists()) {
                    return false;
                }
                if (!file.canRead()) {
                    return false;
                }
            } else { //url
                Uri uri = Uri.parse(url);
                if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                    return false;
                }
                return uri.getHost() != null;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showConfigUrlInputDialog() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        editText.setHint(getString(R.string.config_url_hint));
        editText.setText(readConfigUrl());

        new AlertDialog.Builder(this)
                .setTitle(R.string.config_url)
                .setView(editText)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                    if (editText.getText() == null) {
                        return;
                    }

                    String configUrl = editText.getText().toString().trim();
                    if (isValidUrl(configUrl)) {
                        setConfigUrl(configUrl);
                        textViewConfigUrl.setText(configUrl);
                    } else {
                        Toast.makeText(MainActivity.this, R.string.err_invalid_url, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }



    private void startVPNService() {
        String configUrl = readConfigUrl();
        if (!isValidUrl(configUrl)) {
            Toast.makeText(this, R.string.err_invalid_url, Toast.LENGTH_SHORT).show();
            switchProxy.post(() -> {
                switchProxy.setChecked(false);
                switchProxy.setEnabled(true);
            });
            return;
        }

        textViewLog.setText("");
        GL_HISTORY_LOGS = null;
        LocalVpnService.ConfigUrl = configUrl;
        startService(new Intent(this, LocalVpnService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == START_VPN_SERVICE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startVPNService();
            } else {
                switchProxy.setChecked(false);
                switchProxy.setEnabled(true);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    protected void onDestroy() {
        //LocalVpnService.removeOnStatusChangedListener(listener);
        super.onDestroy();
    }

}
