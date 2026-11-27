package com.fab.zeekrstub.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import com.fab.zeekrstub.Prefs;

/**
 * Result of a silent install session.
 *
 * <p>A successful self-update kills this process, so the success case is often
 * never seen here; the next launch is the real confirmation. What this receiver
 * does see is the honest failure text, which it stores and shows.</p>
 */
public final class InstallResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !InstallLauncher.ACTION_INSTALL_RESULT.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String apk = intent.getStringExtra(InstallLauncher.EXTRA_APK);
        String text;
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            text = "installer wants confirmation for " + apk;
            // The platform hands back the intent that shows its own prompt.
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                } catch (Throwable e) {
                    text = text + " (could not open: " + e.getClass().getSimpleName() + ")";
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            text = "installed " + apk;
        } else {
            text = "install failed (" + status + ")"
                    + (message == null || message.isEmpty() ? "" : ": " + message);
        }
        Prefs.of(context).recordInstall(text);
        try {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            // no window to attach to; the stored text is enough
        }
    }
}
