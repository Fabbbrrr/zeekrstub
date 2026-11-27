package com.fab.zeekrstub.update;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Talks to the GitHub releases API.
 *
 * <p>Lists releases instead of calling /releases/latest, because that endpoint
 * only sees full releases and answers 404 on a repo that has none yet — which
 * reads as "never any update". The list is filtered here by tag filter, so the
 * owner's choice of tags is what decides, not GitHub's idea of "latest".</p>
 *
 * <p>The User-Agent header is mandatory: without it GitHub answers 403. That was
 * measured on this platform, not assumed.</p>
 */
public final class GithubReleases {

    public static final String USER_AGENT = "zeekrstub-update-check";

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final long MAX_APK_BYTES = 50L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private GithubReleases() {
    }

    /** One downloadable file on a release. */
    public static final class Asset {
        public final String name;
        public final String url;
        public final long bytes;
        /** "sha256:<hex>" when GitHub published one, else empty. */
        public final String digest;

        public Asset(String name, String url, long bytes, String digest) {
            this.name = name;
            this.url = url;
            this.bytes = bytes;
            this.digest = digest == null ? "" : digest;
        }

        @Override
        public String toString() {
            return name + " (" + bytes + " bytes)";
        }
    }

    /** One release with its assets. */
    public static final class Release {
        public final String tagName;
        public final String pageUrl;
        public final String body;
        public final List<Asset> assets;

        public Release(String tagName, String pageUrl, String body, List<Asset> assets) {
            this.tagName = tagName;
            this.pageUrl = pageUrl;
            this.body = body == null ? "" : body;
            this.assets = assets;
        }

        public List<String> assetNames() {
            List<String> names = new ArrayList<>(assets.size());
            for (Asset asset : assets) {
                names.add(asset.name);
            }
            return names;
        }
    }

    public interface ProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    /**
     * Every release the source accepts, newest tag first. Drafts are skipped.
     * A release with no assets is skipped: there would be nothing to install.
     */
    public static List<Release> fetch(UpdateSource source) throws IOException {
        if (source == null || !source.valid()) {
            throw new IOException("Update source is not a valid owner/repo");
        }
        String body = getText(source.releasesUrl(), "application/vnd.github+json");
        List<Release> releases = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(body);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null || object.optBoolean("draft", false)) {
                    continue;
                }
                String tag = object.optString("tag_name", "").trim();
                if (tag.isEmpty() || !source.matchesTag(tag)) {
                    continue;
                }
                List<Asset> assets = assetsOf(object);
                if (assets.isEmpty()) {
                    continue;
                }
                releases.add(new Release(tag, object.optString("html_url", source.pageUrl()),
                        object.optString("body", ""), assets));
            }
        } catch (org.json.JSONException e) {
            throw new IOException("Unreadable GitHub response: " + e.getMessage());
        }
        Collections.sort(releases, new Comparator<Release>() {
            @Override
            public int compare(Release a, Release b) {
                return VersionName.compare(b.tagName, a.tagName);
            }
        });
        return releases;
    }

    /** Newest release in the list, or null. */
    public static Release newest(List<Release> releases) {
        return releases == null || releases.isEmpty() ? null : releases.get(0);
    }

    /** Asset to install from a release, or null when nothing fits an arm64 device. */
    public static Asset pickAsset(Release release) {
        if (release == null || release.assets.isEmpty()) {
            return null;
        }
        int index = AssetPicker.bestIndex(release.assetNames());
        return index < 0 ? null : release.assets.get(index);
    }

    private static List<Asset> assetsOf(JSONObject release) {
        List<Asset> assets = new ArrayList<>();
        JSONArray array = release.optJSONArray("assets");
        if (array == null) {
            return assets;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject asset = array.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "").trim();
            String url = asset.optString("browser_download_url", "").trim();
            if (name.isEmpty() || url.isEmpty()) {
                continue;
            }
            if (AssetPicker.rank(name) == AssetPicker.NOT_A_CANDIDATE) {
                continue;
            }
            String digest = asset.optString("digest", "");
            if ("null".equalsIgnoreCase(digest)) {
                digest = "";
            }
            assets.add(new Asset(name, url, asset.optLong("size", 0), digest));
        }
        return assets;
    }

    /**
     * Download to {@code target}, hashing as it goes. Size must match; SHA-256
     * must match when the release published a digest. A failed download deletes
     * its half-written file: a truncated APK only produces "problem parsing the
     * package" later.
     */
    public static void download(Asset asset, File target, ProgressListener listener)
            throws IOException {
        if (asset == null) {
            throw new IOException("No asset to download");
        }
        String expectedHex = DownloadIntegrity.expectedOrNull(asset.digest);
        java.security.MessageDigest hash = DownloadIntegrity.sha256();
        HttpURLConnection connection = open(asset.url, "application/octet-stream");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download failed: HTTP " + code);
            }
            long total = connection.getContentLengthLong();
            if (total > MAX_APK_BYTES) {
                throw new IOException("Asset is " + (total / (1024 * 1024)) + " MB, over the limit");
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Cannot create " + parent);
            }
            byte[] buffer = new byte[64 * 1024];
            long done = 0;
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new FileOutputStream(target)) {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    hash.update(buffer, 0, n);
                    done += n;
                    if (done > MAX_APK_BYTES) {
                        throw new IOException("Download exceeded the size limit");
                    }
                    if (listener != null) {
                        listener.onProgress(done, total);
                    }
                }
            }
            if (total > 0 && done != total) {
                throw new IOException("Received " + done + " of " + total + " bytes");
            }
            DownloadIntegrity.verify(expectedHex, hash.digest(), asset.bytes, done);
        } catch (IOException e) {
            if (target.exists() && !target.delete()) {
                // best effort; the caller reports the download failure anyway
            }
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    private static String getText(String url, String accept) throws IOException {
        HttpURLConnection connection = open(url, accept);
        try {
            int code = connection.getResponseCode();
            if (code == 403 || code == 429) {
                throw new IOException("GitHub refused the request (HTTP " + code
                        + "): rate limit or block. Try again later, or add a fallback source.");
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub answered HTTP " + code);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                    if (buffer.size() > MAX_JSON_BYTES) {
                        throw new IOException("Response exceeded the size limit");
                    }
                }
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    /** GET with our User-Agent and manual, https-only redirect following. */
    private static HttpURLConnection open(String url, String accept) throws IOException {
        URL next = DownloadIntegrity.https(url);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) next.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", accept);
            connection.setInstanceFollowRedirects(false);
            try {
                int code = connection.getResponseCode();
                if (!isRedirect(code)) {
                    return connection;
                }
                String location = connection.getHeaderField("Location");
                if (location == null) {
                    throw new IOException("Redirect without Location");
                }
                next = DownloadIntegrity.https(new URL(next, location).toString());
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
            connection.disconnect();
        }
        throw new IOException("Too many redirects");
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /** Short label for logs and the UI. */
    public static String describe(Asset asset) {
        if (asset == null) {
            return "no asset";
        }
        return asset.name + " (" + String.format(Locale.US, "%.1f", asset.bytes / 1024f / 1024f)
                + " MB, " + AssetPicker.describeRank(AssetPicker.rank(asset.name)) + ")";
    }
}
