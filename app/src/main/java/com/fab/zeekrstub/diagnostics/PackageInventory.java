package com.fab.zeekrstub.diagnostics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.fab.zeekrstub.CertText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The installed package inventory, one report line per package. Every call is
 * guarded: a locked-down container may refuse any of them, and a refused answer
 * must read "unavailable" rather than stop the report.
 */
public final class PackageInventory {

    public static final String UNAVAILABLE = "unavailable";

    /** Signing certificates and meta data, both valid from API 28 up. */
    private static final int FLAGS = PackageManager.GET_SIGNING_CERTIFICATES
            | PackageManager.GET_META_DATA;

    public static final String REQUEST_INSTALL = "android.permission.REQUEST_INSTALL_PACKAGES";
    public static final String INSTALL_PACKAGES = "android.permission.INSTALL_PACKAGES";

    private PackageInventory() {
    }

    /** Sorted by package name. Empty list when the platform will not say. */
    public static List<PackageInfo> load(Context context) {
        List<PackageInfo> out = new ArrayList<>();
        if (context == null) {
            return out;
        }
        try {
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> found = pm.getInstalledPackages(FLAGS);
            if (found != null) {
                for (PackageInfo info : found) {
                    if (info != null) {
                        out.add(info);
                    }
                }
            }
        } catch (Throwable ignored) {
            out.clear();
        }
        Collections.sort(out, BY_NAME);
        return out;
    }

    /** One report line, or "unavailable" when the record cannot be read. */
    public static String line(Context context, PackageInfo info) {
        try {
            if (info == null) {
                return UNAVAILABLE;
            }
            String pkg = text(info.packageName);
            long code = info.getLongVersionCode();
            String version = text(info.versionName);
            return " " + pkg
                    + " | " + code + "/" + version
                    + " | installer=" + installer(context, pkg)
                    + " | cert_sha256=" + CertText.hex(CertText.derOf(info))
                    + " | system=" + flag(info, ApplicationInfo.FLAG_SYSTEM)
                    + " | updated_system=" + flag(info, ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                    + " | install_perm=" + permission(context, pkg, REQUEST_INSTALL)
                    + " | install_packages=" + permission(context, pkg, INSTALL_PACKAGES);
        } catch (Throwable ignored) {
            return UNAVAILABLE;
        }
    }

    /**
     * The installer name, or "unavailable". API 30+ asks the install source,
     * older platforms ask the installer directly, and either may refuse.
     */
    public static String installer(Context context, String pkg) {
        String name = null;
        if (context == null || pkg == null || UNAVAILABLE.equals(pkg)) {
            return UNAVAILABLE;
        }
        try {
            PackageManager pm = context.getPackageManager();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    InstallSourceInfo source = pm.getInstallSourceInfo(pkg);
                    if (source != null) {
                        name = source.getInstallingPackageName();
                    }
                } catch (Throwable ignored) {
                    name = null;
                }
            }
            if (name == null) {
                name = pm.getInstallerPackageName(pkg);
            }
        } catch (Throwable ignored) {
            name = null;
        }
        return installerOrUnavailable(name);
    }

    /** Pure: the installer field has four ways of saying nothing. */
    public static String installerOrUnavailable(String installer) {
        if (installer == null) {
            return UNAVAILABLE;
        }
        String trimmed = installer.trim();
        if (trimmed.isEmpty()
                || "null".equalsIgnoreCase(trimmed)
                || "unknown".equalsIgnoreCase(trimmed)) {
            return UNAVAILABLE;
        }
        return trimmed;
    }

    /** granted, denied, or unavailable when the check itself fails. */
    public static String permission(Context context, String pkg, String permission) {
        if (context == null || pkg == null || UNAVAILABLE.equals(pkg) || permission == null) {
            return UNAVAILABLE;
        }
        try {
            int state = context.getPackageManager().checkPermission(permission, pkg);
            if (state == PackageManager.PERMISSION_GRANTED) {
                return "granted";
            }
            if (state == PackageManager.PERMISSION_DENIED) {
                return "denied";
            }
            return UNAVAILABLE;
        } catch (Throwable ignored) {
            return UNAVAILABLE;
        }
    }

    /** No control characters and no space runs reach the report, so a diff stays readable. */
    public static String text(String value) {
        if (value == null) {
            return UNAVAILABLE;
        }
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.isEmpty() ? UNAVAILABLE : flat;
    }

    private static String flag(PackageInfo info, int wanted) {
        try {
            ApplicationInfo app = info.applicationInfo;
            if (app == null) {
                return "false";
            }
            return (app.flags & wanted) != 0 ? "true" : "false";
        } catch (Throwable ignored) {
            return "false";
        }
    }

    private static final Comparator<PackageInfo> BY_NAME = new Comparator<PackageInfo>() {
        @Override
        public int compare(PackageInfo left, PackageInfo right) {
            String a = left == null || left.packageName == null ? "" : left.packageName;
            String b = right == null || right.packageName == null ? "" : right.packageName;
            return a.compareTo(b);
        }
    };
}
