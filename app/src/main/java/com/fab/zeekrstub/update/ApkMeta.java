package com.fab.zeekrstub.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.fab.zeekrstub.CertText;

import java.io.File;

/**
 * What is actually inside a downloaded APK, read from the file without
 * installing it. This is where the versionCode decision gets its evidence.
 */
public final class ApkMeta {

    public final String packageName;
    public final int versionCode;
    public final String versionName;
    public final String certHex;

    private ApkMeta(String packageName, int versionCode, String versionName, String certHex) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.certHex = certHex;
    }

    /** Null when the file is not a readable APK. */
    public static ApkMeta read(Context context, File apk) {
        if (context == null || apk == null || !apk.isFile()) {
            return null;
        }
        try {
            PackageManager pm = context.getPackageManager();
            @SuppressWarnings("deprecation")
            PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (info == null || info.packageName == null) {
                return null;
            }
            return new ApkMeta(info.packageName, (int) info.getLongVersionCode(),
                    info.versionName == null ? "" : info.versionName,
                    CertText.hex(CertText.derOf(info)));
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return packageName + " " + versionName + " (" + versionCode + ")";
    }
}
