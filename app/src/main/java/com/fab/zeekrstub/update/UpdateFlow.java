package com.fab.zeekrstub.update;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.fab.zeekrstub.BuildConfig;
import com.fab.zeekrstub.CertText;
import com.fab.zeekrstub.Prefs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Locale;

/**
 * Check, download, verify, hand over. Every step reports a state; nothing here
 * is allowed to end in silence.
 *
 * <p>Network work runs on its own thread and the listener is called on the main
 * thread, so an Activity can update its text without caring where the work ran.</p>
 */
public final class UpdateFlow {

    public enum Kind {
        /** Nothing to install, with the reason. */
        NO_UPDATE,
        /** A newer release exists and a usable APK was chosen; waiting for the user. */
        FOUND,
        /** Downloaded, size and digest checked, versionCode and key accepted. */
        READY,
        /** Anything that stopped the flow, with the honest reason. */
        ERROR
    }

    public static final class Result {
        public final Kind kind;
        public final String text;
        public final GithubReleases.Release release;
        public final GithubReleases.Asset asset;
        public final File apk;
        public final ApkMeta meta;

        Result(Kind kind, String text, GithubReleases.Release release,
               GithubReleases.Asset asset, File apk, ApkMeta meta) {
            this.kind = kind;
            this.text = text;
            this.release = release;
            this.asset = asset;
            this.apk = apk;
            this.meta = meta;
        }
    }

    public interface Listener {
        void onStatus(String text);

        void onResult(Result result);
    }

    private static final String WORK_DIR = "updates";
    /** Marks the spare identity's asset, so each package takes only its own file. */
    private static final String SPARE_TOKEN = "spare";

    /** "spare" for the spare package, null for the main one. */
    static String ownAssetToken() {
        return BuildConfig.APPLICATION_ID.endsWith("." + SPARE_TOKEN) ? SPARE_TOKEN : null;
    }

    private UpdateFlow() {
    }

    public static String currentVersionName(Context context) {
        try {
            String name = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** Where downloads live: the app's own external files dir, never shared storage. */
    public static File workDir(Context context) {
        File external = context.getExternalFilesDir(null);
        File base = external != null ? external : context.getFilesDir();
        return new File(base, WORK_DIR);
    }

    /**
     * Ask the source whether there is anything newer. Downloads nothing.
     */
    public static void check(final Context context, final Listener listener) {
        check(context, listener, false);
    }

    /**
     * @param includeSameTag when true, a release whose tag equals the installed
     *        versionName is still offered. The versionCode gate after the
     *        download decides, so a rebuilt release under the same tag is not a
     *        permanent dead end.
     */
    public static void check(final Context context, final Listener listener,
                             final boolean includeSameTag) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            Result result;
            try {
                result = checkNow(app, text -> postToMain(() -> listener.onStatus(text)),
                        includeSameTag);
            } catch (Throwable e) {
                result = error("check failed: " + reason(e));
            }
            final Result finalResult = result;
            Prefs.of(app).recordCheck(finalResult.text,
                    finalResult.release == null ? "" : finalResult.release.tagName);
            postToMain(() -> listener.onResult(finalResult));
        }, "update-check").start();
    }

    /**
     * Synchronous check for the periodic worker. Returns the state text and
     * records it; never throws.
     */
    public static String checkQuietly(Context context) {
        try {
            Result result = checkNow(context, text -> { }, false);
            Prefs.of(context).recordCheck(result.text,
                    result.release == null ? "" : result.release.tagName);
            return result.text;
        } catch (Throwable e) {
            String text = "auto-check failed: " + reason(e);
            Prefs.of(context).recordCheck(text, "");
            return text;
        }
    }

    /**
     * Tries the configured repo first, then each fallback in order. A fallback is
     * either {@code owner/repo} on the same API host or a plain https URL to an
     * APK. One deleted repo, one rate limit, one blocked host must not end the
     * only update channel this app has.
     */
    private static Result checkNow(Context app, StatusSink status, boolean includeSameTag) {
        Prefs prefs = Prefs.of(app);
        UpdateSource source = UpdateSource.from(prefs);
        List<String> problems = new ArrayList<>();
        Result noUpdate = null;

        if (source.valid()) {
            Result result = checkChannel(app, source, status, includeSameTag, problems);
            if (result != null && result.kind != Kind.NO_UPDATE) {
                return result;
            }
            noUpdate = result;
        } else {
            problems.add("settings are not a valid GitHub owner/repo: " + source.describe());
        }

        for (String entry : splitFallbacks(prefs.fallbacks())) {
            if (entry.startsWith("http://") || entry.startsWith("https://")) {
                Result direct = directUrl(entry);
                if (direct != null) {
                    return direct;
                }
                problems.add(entry + ": not an https APK URL");
                continue;
            }
            String owner = entry;
            String repo = "";
            int slash = entry.indexOf('/');
            if (slash > 0) {
                owner = entry.substring(0, slash);
                repo = entry.substring(slash + 1);
            }
            UpdateSource alt = new UpdateSource(source.host, owner, repo, source.tagFilter);
            if (!alt.valid()) {
                problems.add(entry + ": not owner/repo");
                continue;
            }
            Result result = checkChannel(app, alt, status, includeSameTag, problems);
            if (result != null && result.kind != Kind.NO_UPDATE) {
                return result;
            }
            if (result != null) {
                noUpdate = result;
            }
        }

        if (noUpdate != null && problems.isEmpty()) {
            return noUpdate;
        }
        if (noUpdate != null) {
            return new Result(Kind.NO_UPDATE, noUpdate.text + " (also tried: "
                    + String.join("; ", problems) + ")", noUpdate.release, noUpdate.asset, null, null);
        }
        return error("check failed on every source: " + String.join("; ", problems));
    }

    /** One GitHub repo. Returns null when it could not answer. */
    private static Result checkChannel(Context app, UpdateSource source, StatusSink status,
                                       boolean includeSameTag, List<String> problems) {
        status.status("checking " + source.describe());
        List<GithubReleases.Release> releases;
        try {
            releases = GithubReleases.fetch(source);
        } catch (Throwable e) {
            problems.add(source.describe() + ": " + reason(e));
            return null;
        }
        if (releases.isEmpty()) {
            return new Result(Kind.NO_UPDATE,
                    "no update: no release matches " + source.describe(), null, null, null, null);
        }
        GithubReleases.Release newest = releases.get(0);
        // The release carries one APK per identity; take only this one's.
        String wanted = ownAssetToken();
        String unwanted = wanted == null ? SPARE_TOKEN : null;
        GithubReleases.Asset asset = GithubReleases.pickAsset(newest, wanted, unwanted);
        if (asset == null) {
            return new Result(Kind.NO_UPDATE, "no update: release " + newest.tagName
                    + " carries no installable APK for " + BuildConfig.APPLICATION_ID,
                    newest, null, null, null);
        }
        String installed = currentVersionName(app);
        if (!VersionName.isNewer(newest.tagName, installed) && !includeSameTag) {
            return new Result(Kind.NO_UPDATE, "same version, nothing to do: installed "
                    + installed + " (" + BuildConfig.VERSION_CODE + "), newest listed "
                    + newest.tagName + ". If that release was rebuilt, download it anyway:"
                    + " the versionCode check still decides.", newest, asset, null, null);
        }
        status.status("found " + newest.tagName + ": " + GithubReleases.describe(asset));
        return new Result(Kind.FOUND, "update " + newest.tagName + " available: "
                + GithubReleases.describe(asset), newest, asset, null, null);
    }

    /**
     * A plain APK URL. No release metadata, so no tag comparison and no digest:
     * the package id, the versionCode and the signing key are still checked after
     * the download, and the status says so instead of implying more.
     */
    private static Result directUrl(String url) {
        String name;
        try {
            java.net.URL parsed = DownloadIntegrity.https(url);
            String path = parsed.getPath();
            name = path == null || path.isEmpty()
                    ? "update.apk" : path.substring(path.lastIndexOf('/') + 1);
        } catch (Throwable e) {
            return null;
        }
        if (!name.toLowerCase(Locale.US).endsWith(".apk")) {
            return null;
        }
        GithubReleases.Asset asset = new GithubReleases.Asset(name, url, 0, "");
        GithubReleases.Release release = new GithubReleases.Release("direct", url, "",
                java.util.Collections.singletonList(asset));
        return new Result(Kind.FOUND, "direct link " + name
                + ": no release digest and no published size, so only the package, versionCode"
                + " and signing key can be checked", release, asset, null, null);
    }

    /** Fallbacks are comma or newline separated; blanks are ignored. */
    public static List<String> splitFallbacks(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String piece : raw.split("[,\n\r]+")) {
            String entry = piece.trim();
            if (!entry.isEmpty()) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Download the chosen asset, verify it, read its real package/versionCode/key,
     * and only then call it ready.
     */
    public static void downloadAndVerify(final Context context, final Result found,
                                         final Listener listener) {
        final Context app = context.getApplicationContext();
        if (found == null || found.asset == null) {
            postToMain(() -> listener.onResult(error("nothing selected to download")));
            return;
        }
        new Thread(() -> {
            Result result;
            try {
                result = downloadNow(app, found, text -> postToMain(() -> listener.onStatus(text)));
            } catch (Throwable e) {
                result = error("download failed: " + reason(e));
            }
            final Result finalResult = result;
            Prefs.of(app).recordCheck(finalResult.text,
                    finalResult.release == null ? "" : finalResult.release.tagName);
            postToMain(() -> listener.onResult(finalResult));
        }, "update-download").start();
    }

    private static Result downloadNow(Context app, Result found, StatusSink status) {
        File dir = workDir(app);
        clear(dir);
        File target = new File(dir, safeName(found.asset.name));
        boolean hasDigest;
        try {
            hasDigest = DownloadIntegrity.expectedOrNull(found.asset.digest) != null;
        } catch (Throwable e) {
            return error("release digest is malformed; refusing: " + reason(e));
        }
        status.status("downloading " + GithubReleases.describe(found.asset));
        try {
            GithubReleases.download(found.asset, target, (done, total) -> {
                if (total > 0 && done == total) {
                    status.status("downloaded, verifying");
                } else if (total > 0) {
                    status.status("downloading " + (done * 100 / total) + "% ("
                            + mb(done) + " of " + mb(total) + ")");
                } else {
                    status.status("downloading " + mb(done));
                }
            });
        } catch (Throwable e) {
            return error("download failed: " + reason(e));
        }
        String verified;
        if (hasDigest) {
            verified = "downloaded, SHA-256 verified against the release asset";
        } else if (found.asset.bytes > 0) {
            verified = "downloaded, size verified (release published no SHA-256 digest)";
        } else {
            verified = "downloaded from a direct link: no digest and no published size,"
                    + " so only the package, versionCode and key were checked";
        }
        ApkMeta meta = ApkMeta.read(app, target);
        if (meta == null) {
            return error("downloaded file is not a readable APK");
        }
        UpdateDecision.Verdict verdict = UpdateDecision.check(
                app.getPackageName(),
                BuildConfig.VERSION_CODE,
                CertText.appSha256(app),
                meta.packageName,
                meta.versionCode,
                meta.certHex);
        if (!verdict.installable) {
            return error(verified + ", refused: " + verdict.reason);
        }
        return new Result(Kind.READY, verified + "; " + verdict.reason + " — tap install",
                found.release, found.asset, target, meta);
    }

    /**
     * Turns a same-version result into a download candidate. Only the versionCode
     * read from the downloaded APK can allow the install, so this bypasses the
     * tag comparison and nothing else.
     */
    public static Result candidate(Result sameVersion) {
        if (sameVersion == null || sameVersion.asset == null) {
            return null;
        }
        return new Result(Kind.FOUND,
                "will download " + sameVersion.release.tagName + ": "
                        + GithubReleases.describe(sameVersion.asset)
                        + ". Installed versionCode " + BuildConfig.VERSION_CODE
                        + "; the APK must carry a higher one.",
                sameVersion.release, sameVersion.asset, null, null);
    }

    /** Deletes previous downloads. A stale APK in the folder has no value and can confuse a later install. */
    public static void clear(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                // best effort
            }
        }
    }

    private static String safeName(String name) {
        String clean = name == null ? "update.apk" : name.replace('/', '_').replace('\\', '_');
        return clean.isEmpty() ? "update.apk" : clean;
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f);
    }

    private static String reason(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }

    private static Result error(String text) {
        return new Result(Kind.ERROR, text, null, null, null, null);
    }

    private interface StatusSink {
        void status(String text);
    }

    private static void postToMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}
