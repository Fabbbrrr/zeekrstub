package com.fab.zeekrstub.diagnostics;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.fab.zeekrstub.BuildConfig;
import com.fab.zeekrstub.CertText;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the whole diagnostics report as one string.
 *
 * <p>The layout is fixed: same headings, same key order, one item per line,
 * sorted where a list can vary. Two runs on an unchanged car differ only in
 * values that really changed.</p>
 *
 * <p>Read only. It boots nothing, starts no activity, takes no wake lock, and
 * the only process it starts is /system/bin/getprop. Every probe is caught on
 * its own, so a refusal shows as "unavailable" and the rest of the report
 * still arrives.</p>
 */
public final class DiagnosticsCollector {

    public static final String SCHEMA = "1";
    public static final String TITLE = "zeekrstub diagnostics";

    /** Ports a container-side adb daemon listens on. */
    private static final String LOOPBACK = "127.0.0.1";
    private static final int ADB_PORT = 5555;

    /** Names people ask about after a failed install. */
    private static final String[] HIGHLIGHT = {
            "com.anyapp.zee.store",
            "com.puneetlj.box",
            "com.vlite.sdk",
            "com.ecarx.xsfinstallverifier",
            "com.zsupport",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.brave.browser",
            "com.google.android.inputmethod.latin",
            "com.fab.zeekrstub"
    };

    /** Substrings that mark a store or browser build that has no fixed name. */
    private static final String[] HIGHLIGHT_WORDS = {"sandboxcar", "store"};

    /** Fixed order, fixed names: a diff must line up key for key. */
    private static final String[][] SETTINGS = {
            {"global", "package_verifier_enable"},
            {"global", "verifier_verify_adb_installs"},
            {"global", "install_non_market_apps"},
            {"secure", "install_non_market_apps"},
            {"secure", "development_settings_enabled"},
            {"secure", "adb_enabled"}
    };

    private static final String[] NSD_TYPES = {
            "_adb-tls-pairing._tcp",
            "_adb-tls-connect._tcp"
    };

    private static final String APK_MIME = "application/vnd.android.package-archive";

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .withZone(ZoneOffset.UTC);

    private DiagnosticsCollector() {
    }

    /** The full report. Safe on any thread that is not the UI thread. */
    public static String collect(Context context) {
        StringBuilder out = new StringBuilder(8192);
        try {
            out.append(TITLE).append('\n');
            out.append("schema=").append(SCHEMA).append('\n');
            out.append("generated_utc=").append(utcNow()).append('\n');
            out.append("app_version=").append(BuildConfig.VERSION_NAME).append('\n');
            out.append("app_version_code=").append(BuildConfig.VERSION_CODE).append('\n');
            out.append("note=one item per line, sorted, unavailable is a real answer\n");

            List<PackageInfo> packages = PackageInventory.load(context);
            appendPackages(out, context, packages);
            appendHighlight(out, context, packages);
            appendSettings(out, context);
            appendProps(out);
            appendReachability(out, context);
            appendThisApp(out, context);
        } catch (Throwable last) {
            out.append("\n[9 unexpected]\n");
            out.append("collect=unavailable: ").append(last.getClass().getSimpleName()).append('\n');
        }
        return out.toString();
    }

    private static void appendPackages(StringBuilder out, Context context, List<PackageInfo> packages) {
        out.append("\n[1 packages]\n");
        out.append("count=").append(packages.isEmpty()
                ? PackageInventory.UNAVAILABLE
                : String.valueOf(packages.size())).append('\n');
        for (PackageInfo info : packages) {
            out.append(PackageInventory.line(context, info)).append('\n');
        }
    }

    private static void appendHighlight(StringBuilder out, Context context, List<PackageInfo> packages) {
        Map<String, PackageInfo> byName = new TreeMap<>();
        for (PackageInfo info : packages) {
            if (info != null && info.packageName != null) {
                byName.put(info.packageName, info);
            }
        }
        TreeSet<String> wanted = new TreeSet<>();
        Collections.addAll(wanted, HIGHLIGHT);
        for (String name : byName.keySet()) {
            if (storeLike(name)) {
                wanted.add(name);
            }
        }
        out.append("\n[1 highlight]\n");
        for (String name : wanted) {
            PackageInfo info = byName.get(name);
            out.append(info == null ? "absent=" + name : PackageInventory.line(context, info));
            out.append('\n');
        }
    }

    /**
     * The sandboxcar store and browser builds carry no fixed package name, so
     * the name itself is the test. This is a substring test, exactly as asked.
     */
    private static boolean storeLike(String packageName) {
        if (packageName == null) {
            return false;
        }
        String lower = packageName.toLowerCase(Locale.US);
        for (String word : HIGHLIGHT_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static void appendSettings(StringBuilder out, Context context) {
        out.append("\n[2 settings]\n");
        for (String[] entry : SETTINGS) {
            boolean secure = "secure".equals(entry[0]);
            String value = secure
                    ? SettingsProbe.secure(context, entry[1])
                    : SettingsProbe.global(context, entry[1]);
            out.append(entry[0]).append('.').append(entry[1]).append('=').append(value).append('\n');
        }
    }

    private static void appendProps(StringBuilder out) {
        Map<String, String> props = PropProbe.read();
        out.append("\n[3 props]\n");
        for (String key : PropProbe.HEAD_KEYS) {
            out.append(key).append('=').append(PropProbe.value(props, key)).append('\n');
        }
        out.append("spoof=").append(PropFilter.spoofMarker(props)).append('\n');
        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (PropProbe.isHeadKey(entry.getKey())) {
                continue;   // already printed above; a duplicate line breaks diffing
            }
            if (PropFilter.matches(entry.getKey(), entry.getValue())) {
                out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
        }
    }

    private static void appendReachability(StringBuilder out, Context context) {
        out.append("\n[4 reachability]\n");
        out.append(ReachabilityProbe.tcp(LOOPBACK, ADB_PORT)).append('\n');
        List<String> services = new ArrayList<>();
        for (String type : NSD_TYPES) {
            List<String> lines = ReachabilityProbe.mdns(context, type);
            if (!lines.isEmpty()) {
                out.append(lines.get(0)).append('\n');
                for (int i = 1; i < lines.size(); i++) {
                    services.add(lines.get(i));
                }
            }
        }
        Collections.sort(services);
        for (String line : services) {
            out.append(line).append('\n');
        }
    }

    private static void appendThisApp(StringBuilder out, Context context) {
        out.append("\n[5 this app]\n");
        String pkg = BuildConfig.APPLICATION_ID;
        out.append("cert_sha256=").append(CertText.appSha256(context)).append('\n');
        out.append("cert_sha256_colon=").append(CertText.appSha256Colon(context)).append('\n');
        out.append("cert_matches_recorded=").append(CertText.matchesRecorded(context)).append('\n');
        out.append("version_code=").append(BuildConfig.VERSION_CODE).append('\n');
        out.append("version_name=").append(BuildConfig.VERSION_NAME).append('\n');
        out.append("install_permission=")
                .append(PackageInventory.permission(context, pkg, PackageInventory.REQUEST_INSTALL))
                .append('\n');
        out.append("install_packages_held=")
                .append(PackageInventory.permission(context, pkg, PackageInventory.INSTALL_PACKAGES))
                .append('\n');
        out.append("installer_handler=").append(installerHandler(context)).append('\n');
        out.append("note=installer_handler is resolve-only; a diagnostic run must not launch an installer\n");
    }

    /** Count of activities that claim APK installs. Resolve only, never start. */
    private static String installerHandler(Context context) {
        if (context == null) {
            return CertText.UNAVAILABLE;
        }
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setType(APK_MIME);
            List<ResolveInfo> matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            int count = matches == null ? 0 : matches.size();
            return count == 0 ? "none" : "resolvable:" + count;
        } catch (Throwable ignored) {
            return CertText.UNAVAILABLE;
        }
    }

    private static String utcNow() {
        try {
            return UTC.format(Instant.now());
        } catch (Throwable ignored) {
            return CertText.UNAVAILABLE;
        }
    }
}
