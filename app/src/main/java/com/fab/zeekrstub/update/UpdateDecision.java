package com.fab.zeekrstub.update;

/**
 * The one place that decides whether a downloaded APK may replace this app.
 *
 * <p>Every rule here exists because breaking it is unrecoverable on a car whose
 * install gate has closed: wrong package id installs a stranger, wrong key
 * silently forks the signing identity, and an equal versionCode is refused by
 * Android itself, which reads as "the update did nothing".</p>
 */
public final class UpdateDecision {

    public static final class Verdict {
        public final boolean installable;
        public final String reason;

        private Verdict(boolean installable, String reason) {
            this.installable = installable;
            this.reason = reason;
        }

        public static Verdict allow(String reason) {
            return new Verdict(true, reason);
        }

        public static Verdict refuse(String reason) {
            return new Verdict(false, reason);
        }
    }

    private UpdateDecision() {
    }

    /**
     * @param expectedPackage applicationId this app must keep forever
     * @param localVersionCode versionCode currently installed
     * @param localCertHex     signing cert of the installed app, may be null
     * @param apkPackage       applicationId inside the downloaded APK
     * @param apkVersionCode   versionCode inside the downloaded APK
     * @param apkCertHex       signing cert inside the downloaded APK, may be null
     */
    public static Verdict check(String expectedPackage, int localVersionCode, String localCertHex,
                                String apkPackage, int apkVersionCode, String apkCertHex) {
        if (apkPackage == null || apkPackage.trim().isEmpty()) {
            return Verdict.refuse("APK carries no package name; refusing");
        }
        if (!apkPackage.equals(expectedPackage)) {
            return Verdict.refuse("APK is " + apkPackage + ", app is " + expectedPackage
                    + "; a different id is a fresh install, which the gate may refuse");
        }
        if (localCertHex != null && apkCertHex != null
                && !localCertHex.isEmpty() && !apkCertHex.isEmpty()
                && !localCertHex.equalsIgnoreCase(apkCertHex)) {
            return Verdict.refuse("APK is signed with a different key; installing it would"
                    + " break every later update");
        }
        if (apkVersionCode == localVersionCode) {
            return Verdict.refuse("same versionCode (" + apkVersionCode
                    + "); Android refuses an equal version, nothing to do");
        }
        if (apkVersionCode < localVersionCode) {
            return Verdict.refuse("APK versionCode " + apkVersionCode + " is older than installed "
                    + localVersionCode + "; refusing to downgrade");
        }
        return Verdict.allow("versionCode " + apkVersionCode + " > " + localVersionCode);
    }
}
