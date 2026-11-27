package com.fab.zeekrstub.update;

import java.util.List;
import java.util.Locale;

/**
 * Picks the right APK out of a release's asset list.
 *
 * <p>The head unit is arm64. A release may publish one universal APK, or one
 * per ABI. Plain APK only: xapk/apkm/apks bundles are out of scope and are
 * never chosen.</p>
 */
public final class AssetPicker {

    /** Not installable, or a bundle. */
    public static final int NOT_A_CANDIDATE = -1;
    /** Native arm64 build. Best. */
    public static final int ARM64 = 0;
    /** Declares itself universal / no native code. Fine. */
    public static final int UNIVERSAL = 1;
    /** APK with no ABI in its name. Acceptable. */
    public static final int UNKNOWN_ABI = 2;
    /** Says it is for a different CPU. Do not install. */
    public static final int WRONG_ABI = 3;

    private AssetPicker() {
    }

    /** Rank of an asset name for an arm64 device; {@link #NOT_A_CANDIDATE} when it is not an APK. */
    public static int rank(String name) {
        return rank(name, null, null);
    }

    /**
     * Rank with an identity filter. A release can carry one APK per identity
     * (main and spare), and each app must take only its own: {@code requiredToken}
     * must appear in the name, {@code excludedToken} must not.
     */
    public static int rank(String name, String requiredToken, String excludedToken) {
        if (name == null) {
            return NOT_A_CANDIDATE;
        }
        String lower = name.trim().toLowerCase(Locale.US);
        if (lower.isEmpty()) {
            return NOT_A_CANDIDATE;
        }
        if (requiredToken != null && !requiredToken.isEmpty()
                && !lower.contains(requiredToken.toLowerCase(Locale.US))) {
            return NOT_A_CANDIDATE;
        }
        if (excludedToken != null && !excludedToken.isEmpty()
                && lower.contains(excludedToken.toLowerCase(Locale.US))) {
            return NOT_A_CANDIDATE;
        }
        if (lower.endsWith(".xapk") || lower.endsWith(".apkm") || lower.endsWith(".apks")
                || lower.endsWith(".zip") || lower.endsWith(".aab")) {
            return NOT_A_CANDIDATE;
        }
        if (!lower.endsWith(".apk")) {
            return NOT_A_CANDIDATE;
        }
        boolean arm64 = lower.contains("arm64") || lower.contains("aarch64") || lower.contains("v8a");
        boolean universal = lower.contains("universal") || lower.contains("noarch")
                || lower.contains("all") || lower.contains("any");
        boolean wrong = lower.contains("x86") || lower.contains("x86_64") || lower.contains("x64")
                || lower.contains("i686") || lower.contains("armeabi") || lower.contains("armv7")
                || lower.contains("mips") || lower.contains("arm-v7");
        if (arm64) {
            return ARM64;
        }
        if (universal) {
            return UNIVERSAL;
        }
        if (wrong) {
            return WRONG_ABI;
        }
        return UNKNOWN_ABI;
    }

    /**
     * Index of the best asset to download, or -1 when the release carries no
     * installable APK for this device. Deterministic: rank first, then shortest
     * name, then alphabetical — two runs on the same release pick the same file.
     */
    public static int bestIndex(List<String> names) {
        return bestIndex(names, null, null);
    }

    /** Same as {@link #bestIndex(List)} with an identity filter. */
    public static int bestIndex(List<String> names, String requiredToken, String excludedToken) {
        if (names == null) {
            return -1;
        }
        int best = -1;
        int bestRank = WRONG_ABI;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int rank = rank(name, requiredToken, excludedToken);
            if (rank == NOT_A_CANDIDATE || rank == WRONG_ABI) {
                continue;
            }
            if (best < 0) {
                best = i;
                bestRank = rank;
                continue;
            }
            if (rank < bestRank) {
                best = i;
                bestRank = rank;
                continue;
            }
            if (rank == bestRank) {
                String current = names.get(best).toLowerCase(Locale.US);
                String candidate = name.toLowerCase(Locale.US);
                if (candidate.length() < current.length()
                        || (candidate.length() == current.length() && candidate.compareTo(current) < 0)) {
                    best = i;
                }
            }
        }
        return best;
    }

    /** Human text for a rank, used in the "why this file" status line. */
    public static String describeRank(int rank) {
        switch (rank) {
            case ARM64:
                return "arm64";
            case UNIVERSAL:
                return "universal";
            case UNKNOWN_ABI:
                return "no ABI in name";
            case WRONG_ABI:
                return "other ABI";
            default:
                return "not an APK";
        }
    }
}
