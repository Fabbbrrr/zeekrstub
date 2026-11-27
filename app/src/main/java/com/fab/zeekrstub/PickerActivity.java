package com.fab.zeekrstub;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The manual-install half: browse the places an APK can actually live on this
 * head unit, then hand it to the installer with the BACK-not-OPEN warning.
 *
 * <p>Roots are the app's own dirs, Downloads, and mounted removable volumes.
 * Plain APKs only. Split/bundle packages are listed so the answer is "out of
 * scope" rather than a mystery failure.</p>
 *
 * <p>Also the handler for {@code application/vnd.android.package-archive}: any
 * app (or a FileProvider from one) can hand a file over and it lands here.</p>
 */
public final class PickerActivity extends Activity {

    private static final int REQ_READ = 7;
    private static final int REQ_PICK = 11;
    /** Handover copy cap. A stranger's intent must not fill the car's storage. */
    private static final long MAX_HANDOVER_BYTES = 200L * 1024 * 1024;

    private LinearLayout list;
    private TextView crumb;
    private File current;
    private boolean consumedIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        askForReadPermission();
        if (!consumeIntent()) {
            showRoots();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumedIntent = false;
        if (!consumeIntent()) {
            showRoots();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importUri(data.getData());
            } else {
                showRoots();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (current != null) {
            showDir(current);
        }
    }

    private void buildUi() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        int pad = dp(20);
        column.setPadding(pad, dp(24), pad, dp(16));

        TextView title = new TextView(this);
        title.setText(R.string.picker_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTextColor(ContextCompat.getColor(this, R.color.text));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(title);

        TextView note = new TextView(this);
        note.setText(R.string.picker_fat32);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(ContextCompat.getColor(this, R.color.muted));
        note.setPadding(0, dp(8), 0, dp(12));
        column.addView(note);

        crumb = new TextView(this);
        crumb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        crumb.setTextColor(ContextCompat.getColor(this, R.color.muted));
        crumb.setTypeface(Typeface.MONOSPACE);
        crumb.setPadding(0, 0, 0, dp(10));
        column.addView(crumb);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(column);
        Ui.fitColumn(this, column);
    }

    private void askForReadPermission() {
        if (Build.VERSION.SDK_INT > 32) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
            } catch (Throwable ignored) {
                // the app's own dirs still work without it
            }
        }
    }

    /** ACTION_VIEW from another app: copy the stream into our cache, then offer install. */
    private boolean consumeIntent() {
        Intent intent = getIntent();
        if (intent == null || consumedIntent || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }
        Uri data = intent.getData();
        if (data == null) {
            return false;
        }
        consumedIntent = true;
        importUri(data);
        return true;
    }

    private void importUri(Uri uri) {
        Toast.makeText(this, R.string.picker_reading, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File target = new File(getCacheDir(), "handover.apk");
            String failure = null;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    throw new IllegalStateException("no stream");
                }
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_HANDOVER_BYTES) {
                        throw new IllegalStateException(getString(R.string.picker_too_big));
                    }
                    out.write(buffer, 0, n);
                }
            } catch (Throwable e) {
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            final String error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (error != null) {
                    Toast.makeText(this, getString(R.string.picker_copy_failed) + " (" + error + ")",
                            Toast.LENGTH_LONG).show();
                    showRoots();
                } else {
                    InstallDialog.confirm(this, target, "Handed over by another app");
                }
            });
        }, "handover").start();
    }

    private void showRoots() {
        current = null;
        crumb.setText("roots");
        list.removeAllViews();
        addRow(getString(R.string.picker_system_picker),
                getString(R.string.picker_system_picker_note), v -> openSystemPicker());
        for (Root root : roots()) {
            File dir = root.dir;
            addRow(root.label, dir.getAbsolutePath(), v -> showDir(dir));
        }
    }

    private void showDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            Toast.makeText(this, R.string.picker_denied, Toast.LENGTH_SHORT).show();
            return;
        }
        current = dir;
        crumb.setText(dir.getAbsolutePath());
        list.removeAllViews();

        File parent = dir.getParentFile();
        if (parent != null && parent.canRead()) {
            addRow("..", getString(R.string.picker_up), v -> showDir(parent));
        }

        File[] children;
        try {
            children = dir.listFiles();
        } catch (Throwable e) {
            children = null;
        }
        if (children == null) {
            Toast.makeText(this, R.string.picker_denied, Toast.LENGTH_SHORT).show();
            addRow(getString(R.string.picker_all_files), getString(R.string.picker_all_files_note),
                    v -> openAllFilesSettings());
            return;
        }
        List<File> dirs = new ArrayList<>();
        List<File> apks = new ArrayList<>();
        List<File> bundles = new ArrayList<>();
        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.US);
            if (child.isDirectory()) {
                dirs.add(child);
            } else if (name.endsWith(".apk")) {
                apks.add(child);
            } else if (name.endsWith(".xapk") || name.endsWith(".apkm") || name.endsWith(".apks")) {
                bundles.add(child);
            }
        }
        dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        apks.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        bundles.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File child : dirs) {
            addRow(child.getName() + "/", "folder", v -> showDir(child));
        }
        for (File apk : apks) {
            addRow(apk.getName(), mb(apk.length()), v ->
                    InstallDialog.confirm(this, apk, "Manual install"));
        }
        for (File bundle : bundles) {
            addRow(bundle.getName(), "split/bundle — out of scope", v ->
                    Toast.makeText(this, R.string.picker_split, Toast.LENGTH_LONG).show());
        }
        if (dirs.isEmpty() && apks.isEmpty() && bundles.isEmpty()) {
            addRow(getString(R.string.picker_empty), "", null);
        }
        if (!hasAllFiles() && !isOwnDir(dir)) {
            // Android 11+ hides Downloads and USB sticks from File listings until
            // all-files access is granted. Say so instead of showing an empty folder.
            addRow(getString(R.string.picker_all_files), getString(R.string.picker_all_files_note),
                    v -> openAllFilesSettings());
        }
    }

    private List<Root> roots() {
        Set<String> seen = new LinkedHashSet<>();
        List<Root> roots = new ArrayList<>();
        File external = getExternalFilesDir(null);
        if (external != null) {
            addRoot(roots, seen, getString(R.string.picker_app_files_external), external);
        }
        addRoot(roots, seen, getString(R.string.picker_app_files_internal), getFilesDir());
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null) {
            addRoot(roots, seen, getString(R.string.picker_downloads), downloads);
        }
        for (File volume : removableVolumes()) {
            addRoot(roots, seen, getString(R.string.picker_removable) + ": " + volume.getName(),
                    volume);
        }
        return roots;
    }

    private void addRoot(List<Root> roots, Set<String> seen, String label, File dir) {
        if (dir == null) {
            return;
        }
        String key = dir.getAbsolutePath();
        if (!seen.add(key)) {
            return;
        }
        roots.add(new Root(label, dir));
    }

    /** Mounted USB sticks. The car reads FAT32 only. */
    private List<File> removableVolumes() {
        List<File> volumes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            StorageManager manager = (StorageManager) getSystemService(STORAGE_SERVICE);
            if (manager != null) {
                for (StorageVolume volume : manager.getStorageVolumes()) {
                    if (!volume.isRemovable()) {
                        continue;
                    }
                    File dir = null;
                    if (Build.VERSION.SDK_INT >= 30) {
                        dir = volume.getDirectory();
                    }
                    if (dir == null && volume.getUuid() != null) {
                        dir = new File("/storage/" + volume.getUuid());
                    }
                    if (dir != null && dir.isDirectory() && seen.add(dir.getAbsolutePath())) {
                        volumes.add(dir);
                    }
                }
            }
        } catch (Throwable ignored) {
            // fall through to the directory scan
        }
        File storage = new File("/storage");
        File[] children = storage.listFiles();
        if (children != null) {
            for (File child : children) {
                String name = child.getName();
                if ("emulated".equals(name) || "self".equals(name)) {
                    continue;
                }
                if (child.isDirectory() && seen.add(child.getAbsolutePath())) {
                    volumes.add(child);
                }
            }
        }
        return volumes;
    }

    private void addRow(String title, String detail, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        int pad = dp(14);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(ContextCompat.getColor(this, R.color.text));
        name.setTypeface(Typeface.MONOSPACE);
        row.addView(name);

        if (detail != null && !detail.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(detail);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            sub.setTextColor(ContextCompat.getColor(this, R.color.muted));
            sub.setPadding(0, dp(4), 0, 0);
            row.addView(sub);
        }
        if (listener != null) {
            row.setOnClickListener(listener);
            row.setClickable(true);
            row.setFocusable(true);
        }
        row.setGravity(Gravity.START);
        list.addView(row);
    }

    private void openSystemPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/vnd.android.package-archive");
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Throwable e) {
            Toast.makeText(this, "no system file picker: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasAllFiles() {
        if (Build.VERSION.SDK_INT < 30) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean isOwnDir(File dir) {
        try {
            String path = dir.getCanonicalPath();
            File own = getExternalFilesDir(null);
            return (own != null && path.startsWith(own.getCanonicalPath()))
                    || path.startsWith(getFilesDir().getCanonicalPath());
        } catch (Throwable e) {
            return false;
        }
    }

    private void openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < 30) {
            askForReadPermission();
            return;
        }
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable e) {
            try {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {
                Toast.makeText(this, getString(R.string.picker_system_picker_note),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private static final class Root {
        final String label;
        final File dir;

        Root(String label, File dir) {
            this.label = label;
            this.dir = dir;
        }
    }
}
