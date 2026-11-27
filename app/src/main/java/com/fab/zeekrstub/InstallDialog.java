package com.fab.zeekrstub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import com.fab.zeekrstub.update.InstallLauncher;

import java.io.File;
import java.util.Locale;

/**
 * The dialog in front of every install.
 *
 * <p>It carries two things the user needs before the system installer takes over
 * the screen: the BACK-not-OPEN rule, and the current install-permission state.
 * Once the installer is in front, this app cannot say anything, and tapping
 * OPEN there wedges the head unit's installer for every later install.</p>
 */
public final class InstallDialog {

    private InstallDialog() {
    }

    public static void confirm(Activity activity, File apk, String what) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        String size = apk == null ? "missing"
                : String.format(Locale.US, "%.1f MB", apk.length() / 1024f / 1024f);
        boolean installOpAllowed = InstallLauncher.canRequestPackageInstalls(activity);
        boolean silent = InstallLauncher.holdsInstallPackages(activity);

        String message = (what == null ? "" : what + "\n\n")
                + "File: " + (apk == null ? "none" : apk.getName()) + " (" + size + ")\n"
                + "Install unknown apps: " + (installOpAllowed ? "granted" : "denied")
                + "\nINSTALL_PACKAGES: " + (silent ? "held — silent install" : "not held — system UI")
                + "\n\nWhen the installer finishes, tap BACK at the top left."
                + " Do NOT tap OPEN: tapping Open wedges the head unit's installer,"
                + " and later installs and updates fail.";

        new AlertDialog.Builder(activity)
                .setTitle(R.string.install_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.install_go, (dialog, which) -> {
                    if (!installOpAllowed) {
                        // Re-checked, not remembered: this platform drops appops.
                        askPermissionThenInstall(activity, apk);
                    } else {
                        launch(activity, apk);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static void askPermissionThenInstall(Activity activity, File apk) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.install_permission_title)
                .setMessage(R.string.install_permission_body)
                .setPositiveButton(R.string.install_permission_open, (DialogInterface d, int w) ->
                        InstallLauncher.openInstallPermissionSettings(activity))
                .setNeutralButton(R.string.install_permission_anyway, (DialogInterface d, int w) ->
                        launch(activity, apk))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static void launch(Activity activity, File apk) {
        String route = InstallLauncher.install(activity, apk);
        Prefs.of(activity).recordInstall(route);
        String text = route.startsWith("failed: ") ? route : "installer: " + route
                + ". Remember: BACK, not OPEN.";
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
