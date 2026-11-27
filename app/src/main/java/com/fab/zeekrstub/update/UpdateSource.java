package com.fab.zeekrstub.update;

import com.fab.zeekrstub.BuildConfig;
import com.fab.zeekrstub.Prefs;

import java.util.Locale;

/**
 * Where updates come from. Every part is a setting, with the build's defaults
 * underneath: API host, owner, repo, tag filter.
 *
 * <p>Sanitising lives here so the network layer never has to trust a typo: a
 * pasted "https://github.com/" or a trailing slash is cleaned, github.com is
 * mapped to api.github.com, and a name with a slash or a space is rejected
 * instead of being pasted into a URL.</p>
 */
public final class UpdateSource {

    private static final String NAME_PATTERN = "[A-Za-z0-9._-]+";
    private static final String HOST_PATTERN = "[A-Za-z0-9._-]+(:[0-9]{1,5})?";

    public final String host;
    public final String owner;
    public final String repo;
    public final String tagFilter;

    public UpdateSource(String host, String owner, String repo, String tagFilter) {
        this.host = cleanHost(host);
        this.owner = clean(owner, BuildConfig.DEFAULT_OWNER);
        this.repo = clean(repo, BuildConfig.DEFAULT_REPO);
        this.tagFilter = tagFilter == null ? BuildConfig.DEFAULT_TAG_FILTER : tagFilter.trim();
    }

    public static UpdateSource from(Prefs prefs) {
        return new UpdateSource(prefs.apiHost(), prefs.owner(), prefs.repo(), prefs.tagFilter());
    }

    public static UpdateSource defaults() {
        return new UpdateSource(BuildConfig.DEFAULT_API_HOST, BuildConfig.DEFAULT_OWNER,
                BuildConfig.DEFAULT_REPO, BuildConfig.DEFAULT_TAG_FILTER);
    }

    public String releasesUrl() {
        return "https://" + host + "/repos/" + owner + "/" + repo + "/releases?per_page=30";
    }

    /** The page a human opens; the API host is not browsable. */
    public String pageUrl() {
        String webHost = "api.github.com".equalsIgnoreCase(host) ? "github.com" : host;
        return "https://" + webHost + "/" + owner + "/" + repo;
    }

    public String tagPageUrl(String tag) {
        return tag == null || tag.isEmpty() ? pageUrl() : pageUrl() + "/releases/tag/" + tag;
    }

    public boolean valid() {
        return host.matches(HOST_PATTERN)
                && owner.matches(NAME_PATTERN)
                && repo.matches(NAME_PATTERN)
                && !owner.startsWith(".")
                && !repo.startsWith(".");
    }

    public boolean matchesTag(String tag) {
        return TagFilter.matches(tagFilter, tag);
    }

    public String describe() {
        return "https://" + host + "/" + owner + "/" + repo
                + (tagFilter.isEmpty() ? " (all tags)" : " (tags: " + tagFilter + ")");
    }

    /** "https://github.com/", "github.com/" and " GitHub.com " all become "github.com". */
    static String cleanHost(String value) {
        String host = value == null ? "" : value.trim().toLowerCase(Locale.US);
        while (host.startsWith("https://") || host.startsWith("http://")) {
            host = host.substring(host.indexOf("://") + 3);
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        if (host.isEmpty()) {
            return BuildConfig.DEFAULT_API_HOST;
        }
        if ("github.com".equals(host) || "www.github.com".equals(host)) {
            return "api.github.com";
        }
        return host;
    }

    /**
     * Trim and drop trailing slashes only. An inner slash is left in place so
     * {@link #valid()} refuses it: silently rewriting "a/b" to "b" would point
     * the app at a repo the owner did not type.
     */
    private static String clean(String value, String fallback) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? fallback : out;
    }
}
