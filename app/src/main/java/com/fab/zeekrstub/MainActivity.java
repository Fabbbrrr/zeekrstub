package com.fab.zeekrstub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.fab.zeekrstub.update.InstallLauncher;
import com.fab.zeekrstub.update.UpdateFlow;
import com.fab.zeekrstub.update.UpdateScheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The screen the owner sees every time: the rule, the current identity, and the
 * three things that keep the app alive — check, install, diagnose.
 */
public final class MainActivity extends Activity implements UpdateFlow.Listener {

    private TextView versionText;
    private TextView permText;
    private TextView certText;
    private TextView statusLine;
    private UpdateFlow.Result found;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Ui.fitColumn(this, findViewById(R.id.column));

        versionText = findViewById(R.id.txt_version);
        permText = findViewById(R.id.txt_perms);
        certText = findViewById(R.id.txt_cert);
        statusLine = findViewById(R.id.txt_lastcheck);

        findViewById(R.id.btn_check).setOnClickListener(v -> checkForUpdates());
        findViewById(R.id.btn_install).setOnClickListener(v ->
                startActivity(new Intent(this, PickerActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btn_diagnostics).setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));
        findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        if (!Prefs.of(this).disclaimerAccepted()) {
            showDisclaimer();
        }
        UpdateScheduler.sync(this);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void showDisclaimer() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.disclaimer_title)
                .setMessage(R.string.disclaimer_body)
                .setCancelable(false)
                .setPositiveButton(R.string.disclaimer_accept, (dialog, which) -> {
                    Prefs.of(this).acceptDisclaimer();
                    UpdateScheduler.sync(this);
                })
                .show();
    }

    private void refresh() {
        String versionName = BuildConfig.VERSION_NAME;
        int versionCode = BuildConfig.VERSION_CODE;
        versionText.setText("Zeekrstub " + versionName + " · versionCode " + versionCode
                + "\n" + BuildConfig.APPLICATION_ID);

        boolean installOp = InstallLauncher.canRequestPackageInstalls(this);
        boolean installPackages = InstallLauncher.holdsInstallPackages(this);
        permText.setText("Install unknown apps: " + (installOp ? "granted" : "denied")
                + "  ·  INSTALL_PACKAGES: " + (installPackages ? "held" : "not held")
                + "\nRoute: " + (installPackages ? "silent session" : "system installer UI")
                + "  ·  re-checked before every install");

        String colon = CertText.appSha256Colon(this);
        certText.setText("cert " + shorten(colon)
                + (CertText.matchesRecorded(this) ? "  (recorded key)" : "  (NOT the recorded key)"));

        Prefs prefs = Prefs.of(this);
        String last = prefs.lastCheckText();
        long at = prefs.lastCheckAt();
        if (last.isEmpty()) {
            statusLine.setText(R.string.status_idle);
        } else {
            statusLine.setText(last + "\n" + (at == 0 ? "" : "at " + clock(at)));
        }
    }

    private void checkForUpdates() {
        statusLine.setText(R.string.status_checking);
        UpdateFlow.check(this, this);
    }

    @Override
    public void onStatus(String text) {
        statusLine.setText(text);
    }

    @Override
    public void onResult(UpdateFlow.Result result) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        statusLine.setText(result.text);
        switch (result.kind) {
            case NO_UPDATE:
                AlertDialog.Builder noUpdate = new AlertDialog.Builder(this)
                        .setTitle(R.string.update_no_update_title)
                        .setMessage(result.text)
                        .setPositiveButton(R.string.action_ok, null);
                if (result.release != null) {
                    // Same tag, maybe a rebuilt release. Only the versionCode in
                    // the downloaded APK can allow it, so offering the download
                    // cannot install anything wrong.
                    noUpdate.setNeutralButton(R.string.update_download_anyway, (d, w) ->
                            UpdateFlow.downloadAndVerify(this, UpdateFlow.candidate(result), this));
                }
                noUpdate.show();
                break;
            case FOUND:
                found = result;
                new AlertDialog.Builder(this)
                        .setTitle(R.string.update_found_title)
                        .setMessage(result.text + "\n\n" + getString(R.string.install_warning))
                        .setPositiveButton(R.string.update_download, (d, w) ->
                                UpdateFlow.downloadAndVerify(this, found, this))
                        .setNegativeButton(R.string.update_later, null)
                        .show();
                break;
            case READY:
                found = null;
                new AlertDialog.Builder(this)
                        .setTitle(R.string.update_ready_title)
                        .setMessage(result.text + "\n\n" + getString(R.string.install_warning))
                        .setPositiveButton(R.string.update_install_now, (d, w) ->
                                InstallDialog.confirm(this, result.apk,
                                        "Self-update to " + result.meta.versionName
                                                + " (" + result.meta.versionCode + ")"))
                        .setNegativeButton(R.string.update_later, null)
                        .show();
                break;
            case ERROR:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.update_error_title)
                        .setMessage(result.text)
                        .setPositiveButton(R.string.action_ok, null)
                        .show();
                break;
            default:
                break;
        }
    }

    private static String shorten(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 24) {
            return fingerprint;
        }
        return fingerprint.substring(0, 14) + "…" + fingerprint.substring(fingerprint.length() - 11);
    }

    private static String clock(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }
}
