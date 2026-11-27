package com.fab.zeekrstub.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.fab.zeekrstub.Prefs;

import android.content.pm.ResolveInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Hands an APK to the platform, and says out loud which route it took.
 *
 * <p>Two routes:</p>
 * <ul>
 *   <li><b>Silent</b> — only when this app actually holds INSTALL_PACKAGES, the
 *       signature permission that a platform-keyed app would have. Then a
 *       PackageInstaller session installs without a prompt.</li>
 *   <li><b>System installer UI</b> — everything else. This is the fallback that
 *       works in the car's container even though
 *       {@code canRequestPackageInstalls()} reports false there: the reference
 *       repo measured that, and blocking on the false answer is what once
 *       dead-ended its in-app update.</li>
 * </ul>
 *
 * <p>On the installer screen the user must tap BACK, top left. Tapping OPEN
 * wedges the head unit installer and later installs fail. That warning is
 * carried by the dialog in front of every install, not by this class.</p>
 */
public final class InstallLauncher {

    public static final String ACTION_INSTALL_RESULT = "com.fab.zeekrstub.INSTALL_RESULT";
    public static final String EXTRA_SESSION = "session";
    public static final String EXTRA_APK = "apk";

    public static final String ROUTE_SILENT = "silent (INSTALL_PACKAGES held)";
    public static final String ROUTE_UI = "system installer UI";

    private InstallLauncher() {
    }

    /** True when the platform granted this app the installer permission. */
    public static boolean holdsInstallPackages(Context context) {
        return granted(context, "android.permission.INSTALL_PACKAGES");
    }

    /** The user-facing "install unknown apps" appop. Can lie on this platform; never used as a gate. */
    public static boolean canRequestPackageInstalls(Context context) {
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable e) {
            return false;
        }
    }

    public static String permissionText(Context context) {
        return (canRequestPackageInstalls(context) ? "granted" : "denied")
                + " (re-checked before every install)";
    }

    public static String installPackagesText(Context context) {
        if (holdsInstallPackages(context)) {
            return "granted";
        }
        return granted(context, "android.permission.INSTALL_PACKAGES") ? "granted" : "denied";
    }

    /** Settings page for "install unknown apps"; falls back to app details. */
    public static void openInstallPermissionSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.getPackageName()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Throwable ignored) {
                // nothing else to offer; the caller reports it
            }
        }
    }

    /**
     * Install, choosing the route from what the platform actually grants.
     *
     * @return the route taken, or a string starting with "failed: "
     */
    public static String install(Context context, File apk) {
        if (apk == null || !apk.isFile() || apk.length() == 0) {
            return "failed: file is missing or empty";
        }
        if (holdsInstallPackages(context)) {
            String failure = installSilently(context, apk);
            if (failure == null) {
                return ROUTE_SILENT;
            }
            // Fall through to the UI rather than dead-end: the session may be
            // refused for reasons the installer UI can still handle.
            String ui = installWithSystemUi(context, apk);
            if (ui != null) {
                return ui;
            }
            return "failed: silent " + failure + "; installer UI did not open";
        }
        String ui = installWithSystemUi(context, apk);
        return ui != null ? ui : "failed: no installer activity accepted the APK";
    }

    /** PackageInstaller session, no prompt. Returns null when started, else the reason. */
    public static String installSilently(Context context, File apk) {
        PackageInstaller installer;
        int sessionId;
        try {
            installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(apk.length());
            sessionId = installer.createSession(params);
        } catch (Throwable e) {
            return "session not created: " + e.getClass().getSimpleName();
        }
        // One session handle for the whole write-then-commit. Opening a second
        // handle just to commit is what makes sessions fail in the field.
        PackageInstaller.Session session = null;
        try {
            session = installer.openSession(sessionId);
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                session.fsync(out);
            }
            Intent callback = new Intent(context, InstallResultReceiver.class)
                    .setAction(ACTION_INSTALL_RESULT)
                    .putExtra(EXTRA_SESSION, sessionId)
                    .putExtra(EXTRA_APK, apk.getName());
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            session.commit(pending.getIntentSender());
            return null;
        } catch (Throwable e) {
            abandon(installer, sessionId);
            return "session failed: " + e.getClass().getSimpleName();
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Throwable ignored) {
                    // the commit is already handed to the platform
                }
            }
        }
    }

    /** ACTION_VIEW through our FileProvider. Returns null when the intent was accepted. */
    public static String installWithSystemUi(Context context, File apk) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apk);
        } catch (Throwable e) {
            return "FileProvider refused the path: " + e.getClass().getSimpleName();
        }
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        // This app also registers for the package-archive type (so another app can
        // hand it a file). Without this, the system shows an "Open with" chooser
        // that includes Zeekrstub itself, and picking it loops straight back here.
        targetRealInstaller(context, intent);
        try {
            context.startActivity(intent);
            return null;
        } catch (Throwable e) {
            return "no installer accepted it: " + e.getClass().getSimpleName();
        }
    }

    /**
     * Points the install intent at the real installer, never at this app.
     * Known installer packages win, then any other system app, then any other
     * app that claims the type.
     */
    static void targetRealInstaller(Context context, Intent intent) {
        try {
            List<ResolveInfo> handlers = context.getPackageManager()
                    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            String self = context.getPackageName();
            ResolveInfo known = null;
            ResolveInfo system = null;
            ResolveInfo other = null;
            for (ResolveInfo info : handlers) {
                if (info.activityInfo == null || info.activityInfo.packageName == null) {
                    continue;
                }
                String pkg = info.activityInfo.packageName;
                if (self.equals(pkg)) {
                    continue;
                }
                if ("com.android.packageinstaller".equals(pkg)
                        || "com.google.android.packageinstaller".equals(pkg)
                        || "com.android.packageinstaller.huawei".equals(pkg)) {
                    known = info;
                    break;
                }
                boolean isSystem = info.activityInfo.applicationInfo != null
                        && (info.activityInfo.applicationInfo.flags
                        & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                if (isSystem && system == null) {
                    system = info;
                } else if (!isSystem && other == null) {
                    other = info;
                }
            }
            ResolveInfo chosen = known != null ? known : (system != null ? system : other);
            if (chosen != null) {
                intent.setClassName(chosen.activityInfo.packageName, chosen.activityInfo.name);
            }
        } catch (Throwable ignored) {
            // Leave the intent generic; the platform chooser is the fallback.
        }
    }

    /** How many activities claim the package-archive type. Read-only. */
    public static int resolveInstallerCount(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setType("application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return context.getPackageManager()
                    .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static void abandon(PackageInstaller installer, int sessionId) {
        try {
            installer.abandonSession(sessionId);
        } catch (Throwable ignored) {
            // nothing useful to do
        }
    }

    private static boolean granted(Context context, String permission) {
        try {
            return context.getPackageManager()
                    .checkPermission(permission, context.getPackageName())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Records the last install attempt so the UI can report it later. */
    static void remember(Context context, String text) {
        Prefs.of(context).recordInstall(text);
    }
}
