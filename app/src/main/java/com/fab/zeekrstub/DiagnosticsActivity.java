package com.fab.zeekrstub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.fab.zeekrstub.diagnostics.DiagnosticsCollector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Sideload-survival report. Plain text, sorted, one item per line, exported
 * through the app's own files dir and the share sheet, so a report taken before
 * the firmware patch and one taken after can be diffed without cleanup.
 */
public final class DiagnosticsActivity extends Activity {

    private TextView output;
    private String report = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        int pad = dp(20);
        column.setPadding(pad, dp(24), pad, dp(16));

        Ui.header(this, column, R.string.diagnostics_title);

        TextView note = new TextView(this);
        note.setText(R.string.diagnostics_note);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(ContextCompat.getColor(this, R.color.muted));
        note.setPadding(0, dp(8), 0, dp(12));
        column.addView(note);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button collect = button(R.string.diagnostics_collect);
        Button save = button(R.string.diagnostics_save);
        Button share = button(R.string.diagnostics_share);
        LinearLayout.LayoutParams weight = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        buttons.addView(collect, weight);
        buttons.addView(save, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(share, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        output.setTextColor(ContextCompat.getColor(this, R.color.text));
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(0, dp(14), 0, dp(14));
        scroll.addView(output);
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(column);
        Ui.fitColumn(this, column);

        collect.setOnClickListener(v -> collect());
        save.setOnClickListener(v -> save());
        share.setOnClickListener(v -> share());
        collect();
    }

    private void collect() {
        output.setText(R.string.diagnostics_collect);
        new Thread(() -> {
            String text;
            try {
                text = DiagnosticsCollector.collect(getApplicationContext());
            } catch (Throwable e) {
                text = "zeekrstub diagnostics\ncollect failed: " + e.getClass().getSimpleName()
                        + ": " + e.getMessage() + "\n";
            }
            final String result = text;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                report = result;
                output.setText(result);
            });
        }, "diagnostics").start();
    }

    private void save() {
        if (report.isEmpty()) {
            collect();
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(null) != null
                    ? getExternalFilesDir(null) : getFilesDir(), "diagnostics");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("cannot create " + dir);
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File target = new File(dir, "zeekrstub-diagnostics-" + stamp + ".txt");
            try (OutputStream out = new FileOutputStream(target)) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, getString(R.string.diagnostics_saved, target.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            Toast.makeText(this, "save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void share() {
        if (report.isEmpty()) {
            collect();
            return;
        }
        try {
            File dir = new File(getExternalFilesDir(null) != null
                    ? getExternalFilesDir(null) : getFilesDir(), "diagnostics");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("cannot create " + dir);
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File target = new File(dir, "zeekrstub-diagnostics-" + stamp + ".txt");
            try (OutputStream out = new FileOutputStream(target)) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", target);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, "zeekrstub diagnostics")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.diagnostics_share_title)));
        } catch (Throwable e) {
            Toast.makeText(this, "share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Button button(int textRes) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.bg_button_secondary);
        button.setTextColor(ContextCompat.getColor(this, R.color.text));
        return button;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
