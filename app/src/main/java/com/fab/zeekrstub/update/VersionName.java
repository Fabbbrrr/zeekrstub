package com.fab.zeekrstub.update;

import java.util.Locale;

/**
 * Version comparison for release tags and versionName strings.
 *
 * <p>String compare would answer "0.9.0" &gt; "0.25.0", which is the wrong way
 * round and results in "no update" when there is one. So compare number by
 * number, then treat a pre-release suffix as older than the plain version.</p>
 */
public final class VersionName {

    private VersionName() {
    }

    /** Positive when {@code a} is newer than {@code b}, negative when older, 0 when equal. */
    public static int compare(String a, String b) {
        String[] coreA = splitCore(a);
        String[] coreB = splitCore(b);
        int[] numsA = numbers(coreA[0]);
        int[] numsB = numbers(coreB[0]);
        int len = Math.max(numsA.length, numsB.length);
        for (int i = 0; i < len; i++) {
            int x = i < numsA.length ? numsA[i] : 0;
            int y = i < numsB.length ? numsB[i] : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        boolean preA = !coreA[1].isEmpty();
        boolean preB = !coreB[1].isEmpty();
        if (preA != preB) {
            return preA ? -1 : 1;
        }
        return coreA[1].compareTo(coreB[1]);
    }

    /** True only when the remote version is provably newer. Unparseable means "no". */
    public static boolean isNewer(String remote, String local) {
        if (remote == null || local == null) {
            return false;
        }
        if (numbers(splitCore(remote)[0]).length == 0
                || numbers(splitCore(local)[0]).length == 0) {
            return false;
        }
        return compare(remote, local) > 0;
    }

    public static boolean isSame(String a, String b) {
        return compare(a, b) == 0;
    }

    /** First numeric segment, or -1 when the string carries no version. */
    public static int major(String version) {
        int[] nums = numbers(splitCore(version)[0]);
        return nums.length == 0 ? -1 : nums[0];
    }

    /** Strips a leading v, build metadata after '+', then splits number part from suffix. */
    private static String[] splitCore(String version) {
        String s = version == null ? "" : version.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        int plus = s.indexOf('+');
        if (plus >= 0) {
            s = s.substring(0, plus);
        }
        int dash = s.indexOf('-');
        if (dash < 0) {
            return new String[]{s, ""};
        }
        return new String[]{s.substring(0, dash), s.substring(dash + 1).toLowerCase(Locale.US)};
    }

    private static int[] numbers(String core) {
        if (core.isEmpty()) {
            return new int[0];
        }
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return new int[0];
            }
        }
        return out;
    }
}
