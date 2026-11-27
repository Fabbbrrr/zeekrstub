package com.fab.zeekrstub;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * The signing identity, in the two shapes people ask for: lowercase hex (what a
 * sha256sum line needs) and colon upper case (what keytool and apksigner print).
 * Every probe that needs a fingerprint goes through here, so the About screen
 * and the diagnostics report can never disagree.
 */
public final class CertText {

    /** Printed when a probe cannot answer. Never replaced by a guess. */
    public static final String UNAVAILABLE = "unavailable";

    private CertText() {
    }

    /** Lowercase hex SHA-256 of a DER certificate, or {@link #UNAVAILABLE}. */
    public static String hex(byte[] der) {
        byte[] digest = sha256(der);
        if (digest == null) {
            return UNAVAILABLE;
        }
        return hexOf(digest);
    }

    /** Upper case, colon separated SHA-256 of a DER certificate, or {@link #UNAVAILABLE}. */
    public static String colon(byte[] der) {
        byte[] digest = sha256(der);
        if (digest == null) {
            return UNAVAILABLE;
        }
        StringBuilder out = new StringBuilder(95);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                out.append(':');
            }
            int value = digest[i] & 0xFF;
            out.append(Character.toUpperCase(Character.forDigit(value >>> 4, 16)));
            out.append(Character.toUpperCase(Character.forDigit(value & 0x0F, 16)));
        }
        return out.toString();
    }

    public static String hexOf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return UNAVAILABLE;
        }
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >>> 4) & 0x0F, 16));
            out.append(Character.forDigit(b & 0x0F, 16));
        }
        return out.toString();
    }

    /** SHA-256 of the certificate of this app, colon form. */
    public static String appSha256Colon(Context context) {
        return colon(appCertificateDer(context));
    }

    /** SHA-256 of the certificate of this app, plain lowercase hex. */
    public static String appSha256(Context context) {
        return hex(appCertificateDer(context));
    }

    /** DER of the first signing certificate of this app, or null. */
    public static byte[] appCertificateDer(Context context) {
        if (context == null) {
            return null;
        }
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            return derOf(info);
        } catch (Exception e) {
            return null;
        }
    }

    /** DER of the first signing certificate of any package, or null. Safe on every API we ship. */
    public static byte[] derOf(PackageInfo info) {
        if (info == null) {
            return null;
        }
        try {
            SigningInfo signing = info.signingInfo;
            if (signing != null) {
                Signature[] signatures = signing.hasMultipleSigners()
                        ? signing.getApkContentsSigners()
                        : signing.getSigningCertificateHistory();
                if (signatures != null && signatures.length > 0) {
                    return signatures[0].toByteArray();
                }
                return null;
            }
        } catch (Throwable ignored) {
            // fall through to the legacy field
        }
        try {
            Signature[] signatures = info.signatures;
            if (signatures != null && signatures.length > 0) {
                return signatures[0].toByteArray();
            }
        } catch (Throwable ignored) {
            // unavailable
        }
        return null;
    }

    /** True when this app's certificate is the recorded one. */
    public static boolean matchesRecorded(Context context) {
        String actual = appSha256(context);
        return !UNAVAILABLE.equals(actual)
                && actual.equalsIgnoreCase(BuildConfig.EXPECTED_CERT_SHA256);
    }

    private static byte[] sha256(byte[] der) {
        if (der == null || der.length == 0) {
            return null;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(der);
        } catch (Exception e) {
            return null;
        }
    }

    /** Only used by tests to keep the two shapes in sync. */
    static boolean sameBytes(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
