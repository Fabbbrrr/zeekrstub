package com.fab.zeekrstub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fab.zeekrstub.update.UpdateFlow;
import com.fab.zeekrstub.update.UpdateScheduler;
import com.fab.zeekrstub.update.UpdateSource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The update source is a setting, not a constant: owner, repo, tag filter and
 * API host. A fork, a mirror, or a private release train all work without a new
 * APK.
 */
public final class SettingsActivity extends Activity implements UpdateFlow.Listener {

    private EditText owner;
    private EditText repo;
    private EditText tag;
    private EditText host;
    private CheckBox auto;
    private TextView effective;
    private TextView lastCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.fitColumn(this, findViewById(R.id.column));
        Ui.fitFields(this, findViewById(R.id.edt_owner), findViewById(R.id.edt_repo),
                findViewById(R.id.edt_tag), findViewById(R.id.edt_host));
        Ui.header(this, findViewById(R.id.column), R.string.settings_title);

        owner = findViewById(R.id.edt_owner);
        repo = findViewById(R.id.edt_repo);
        tag = findViewById(R.id.edt_tag);
        host = findViewById(R.id.edt_host);
        auto = findViewById(R.id.chk_auto);
        effective = findViewById(R.id.txt_effective);
        lastCheck = findViewById(R.id.txt_lastcheck);

        findViewById(R.id.btn_save).setOnClickListener(v -> save(true));
        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            Prefs.of(this).resetSourceToDefaults();
            load();
            save(false);
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_check).setOnClickListener(v -> {
            if (save(false)) {
                UpdateFlow.check(this, this);
            }
        });

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showLastCheck();
    }

    private void load() {
        Prefs prefs = Prefs.of(this);
        owner.setText(prefs.owner());
        repo.setText(prefs.repo());
        tag.setText(prefs.tagFilter());
        host.setText(prefs.apiHost());
        auto.setChecked(prefs.autoCheck());
        showEffective();
        showLastCheck();
    }

    private boolean save(boolean toast) {
        Prefs prefs = Prefs.of(this);
        UpdateSource candidate = new UpdateSource(host.getText().toString(),
                owner.getText().toString(), repo.getText().toString(), tag.getText().toString());
        if (!candidate.valid()) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.settings_invalid)
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
            return false;
        }
        prefs.setApiHost(candidate.host);
        prefs.setOwner(candidate.owner);
        prefs.setRepo(candidate.repo);
        prefs.setTagFilter(candidate.tagFilter);
        prefs.setAutoCheck(auto.isChecked());
        UpdateScheduler.sync(this);
        showEffective();
        if (toast) {
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void showEffective() {
        effective.setText(UpdateSource.from(Prefs.of(this)).describe()
                + "\nauto-check: " + (Prefs.of(this).autoCheck() ? "hourly (max)" : "off"));
    }

    private void showLastCheck() {
        Prefs prefs = Prefs.of(this);
        String text = prefs.lastCheckText();
        if (text.isEmpty()) {
            lastCheck.setText(R.string.status_idle);
            return;
        }
        long at = prefs.lastCheckAt();
        String when = at == 0 ? "" : "\n" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(at));
        lastCheck.setText(text + when);
    }

    @Override
    public void onStatus(String text) {
        lastCheck.setText(text);
    }

    @Override
    public void onResult(UpdateFlow.Result result) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        showLastCheck();
        if (result.kind == UpdateFlow.Kind.FOUND) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_found_title)
                    .setMessage(result.text + "\n\n" + getString(R.string.install_warning))
                    .setPositiveButton(R.string.update_download, (d, w) ->
                            UpdateFlow.downloadAndVerify(this, result, this))
                    .setNegativeButton(R.string.update_later, null)
                    .show();
        } else if (result.kind == UpdateFlow.Kind.READY) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_ready_title)
                    .setMessage(result.text + "\n\n" + getString(R.string.install_warning))
                    .setPositiveButton(R.string.update_install_now, (d, w) ->
                            InstallDialog.confirm(this, result.apk,
                                    "Self-update to " + result.meta.versionName
                                            + " (" + result.meta.versionCode + ")"))
                    .setNegativeButton(R.string.update_later, null)
                    .show();
        } else {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setTitle(result.kind == UpdateFlow.Kind.ERROR
                            ? R.string.update_error_title : R.string.update_no_update_title)
                    .setMessage(result.text)
                    .setPositiveButton(R.string.action_ok, null);
            if (result.kind == UpdateFlow.Kind.NO_UPDATE && result.release != null) {
                dialog.setNeutralButton(R.string.update_download_anyway, (d, w) ->
                        UpdateFlow.downloadAndVerify(this, UpdateFlow.candidate(result), this));
            }
            dialog.show();
        }
    }

    /** Kept so the back stack behaves from About. */
    static void open(Activity from) {
        from.startActivity(new Intent(from, SettingsActivity.class));
    }
}
