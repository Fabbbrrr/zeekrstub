package com.fab.zeekrstub;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.fab.zeekrstub.update.InstallLauncher;
import com.fab.zeekrstub.update.UpdateFlow;
import com.fab.zeekrstub.update.UpdateSource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Identity, provable from the car: the app id, both shapes of the signing
 * fingerprint, the two install permissions, and where updates come from.
 * If someone ever asks "is this the same app?", this screen answers it without
 * a laptop.
 */
public final class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        column.setPadding(pad, dp(28), pad, dp(28));
        scroll.addView(column);
        setContentView(scroll);
        Ui.fitColumn(this, column);

        Ui.header(this, column, R.string.about_title);
        addSection(column, "Build");
        addRow(column, "applicationId", BuildConfig.APPLICATION_ID);
        addRow(column, "versionName", BuildConfig.VERSION_NAME);
        addRow(column, "versionCode", String.valueOf(BuildConfig.VERSION_CODE));
        addRow(column, "release branch", BuildConfig.RELEASE_BRANCH);
        addRow(column, "targetSdk / minSdk", "36 / 28");

        addSection(column, "Signing identity");
        addRow(column, "SHA-256 (colon)", CertText.appSha256Colon(this));
        addRow(column, "SHA-256 (hex)", CertText.appSha256(this));
        addRow(column, "recorded key", BuildConfig.EXPECTED_CERT_SHA256);
        addRow(column, "matches recorded",
                CertText.matchesRecorded(this) ? "yes" : "NO — do not ship this build");

        addSection(column, "Install permissions");
        addRow(column, "install unknown apps", InstallLauncher.permissionText(this));
        addRow(column, "INSTALL_PACKAGES", InstallLauncher.installPackagesText(this));
        addRow(column, "installer handlers",
                String.valueOf(InstallLauncher.resolveInstallerCount(this)));

        addSection(column, "Update source");
        addRow(column, "source", UpdateSource.from(Prefs.of(this)).describe());
        addRow(column, "auto-check", Prefs.of(this).autoCheck() ? "hourly (max)" : "off");
        addRow(column, "last check", lastCheck());

        addSection(column, "Provenance");
        addRow(column, "mechanism from", "dts88/zeekr-shortcut-car (GPL-3.0-only)");
        addRow(column, "key origin", "that repo's public AOSP test keystore");
        addRow(column, "licence", "GPL-3.0-only");
        addRow(column, "unofficial", "not affiliated with or endorsed by ZEEKR");
    }

    private String lastCheck() {
        Prefs prefs = Prefs.of(this);
        String text = prefs.lastCheckText();
        if (text.isEmpty()) {
            return getString(R.string.status_idle);
        }
        long at = prefs.lastCheckAt();
        return text + (at == 0 ? "" : " @ " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(at)));
    }

    private void addSection(LinearLayout column, String text) {
        TextView view = new TextView(this);
        view.setText(text.toUpperCase(Locale.US));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTextColor(ContextCompat.getColor(this, R.color.muted));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(24), 0, dp(6));
        column.addView(view);
    }

    private void addRow(LinearLayout column, String label, String value) {
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        name.setTextColor(ContextCompat.getColor(this, R.color.muted));
        name.setPadding(0, dp(8), 0, 0);
        column.addView(name);

        TextView body = new TextView(this);
        body.setText(value == null || value.isEmpty() ? CertText.UNAVAILABLE : value);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.setTextColor(ContextCompat.getColor(this, R.color.text));
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        column.addView(body);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
