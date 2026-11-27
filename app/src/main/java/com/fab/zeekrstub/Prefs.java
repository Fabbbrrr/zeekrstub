package com.fab.zeekrstub;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Every setting the app has, in one place, with build-time defaults from
 * gradle.properties. Settings survive updates because the app id never changes.
 *
 * Defaults are deliberately all overridable: owner, repo, tag filter, API host.
 */
public final class Prefs {

    private static final String FILE = "zeekrstub";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_REPO = "repo";
    private static final String KEY_TAG_FILTER = "tag_filter";
    private static final String KEY_API_HOST = "api_host";
    private static final String KEY_AUTO_CHECK = "auto_check";
    private static final String KEY_DISCLAIMER = "disclaimer_accepted";
    private static final String KEY_LAST_CHECK = "last_check_text";
    private static final String KEY_LAST_CHECK_AT = "last_check_at";
    private static final String KEY_LAST_REMOTE = "last_remote_tag";
    private static final String KEY_LAST_INSTALL = "last_install_text";

    private final SharedPreferences prefs;

    private Prefs(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static Prefs of(Context context) {
        return new Prefs(context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE));
    }

    public String owner() {
        return value(KEY_OWNER, BuildConfig.DEFAULT_OWNER);
    }

    public void setOwner(String value) {
        put(KEY_OWNER, value);
    }

    public String repo() {
        return value(KEY_REPO, BuildConfig.DEFAULT_REPO);
    }

    public void setRepo(String value) {
        put(KEY_REPO, value);
    }

    public String tagFilter() {
        return value(KEY_TAG_FILTER, BuildConfig.DEFAULT_TAG_FILTER);
    }

    public void setTagFilter(String value) {
        put(KEY_TAG_FILTER, value);
    }

    public String apiHost() {
        return value(KEY_API_HOST, BuildConfig.DEFAULT_API_HOST);
    }

    public void setApiHost(String value) {
        put(KEY_API_HOST, value);
    }

    /** Off by default: nothing polls GitHub until the owner asks for it. */
    public boolean autoCheck() {
        return prefs.getBoolean(KEY_AUTO_CHECK, false);
    }

    public void setAutoCheck(boolean value) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply();
    }

    public boolean disclaimerAccepted() {
        return prefs.getBoolean(KEY_DISCLAIMER, false);
    }

    public void acceptDisclaimer() {
        prefs.edit().putBoolean(KEY_DISCLAIMER, true).apply();
    }

    public String lastCheckText() {
        return value(KEY_LAST_CHECK, "");
    }

    public long lastCheckAt() {
        return prefs.getLong(KEY_LAST_CHECK_AT, 0L);
    }

    public String lastRemoteTag() {
        return value(KEY_LAST_REMOTE, "");
    }

    /** Records the outcome of a check so the UI can never show silence as success. */
    public void recordCheck(String text, String remoteTag) {
        prefs.edit()
                .putString(KEY_LAST_CHECK, text == null ? "" : text)
                .putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
                .putString(KEY_LAST_REMOTE, remoteTag == null ? "" : remoteTag)
                .apply();
    }

    public String lastInstallText() {
        return value(KEY_LAST_INSTALL, "");
    }

    /** Result of the last install attempt, including the silent-session failures. */
    public void recordInstall(String text) {
        prefs.edit().putString(KEY_LAST_INSTALL, text == null ? "" : text).apply();
    }

    public void resetSourceToDefaults() {
        prefs.edit()
                .remove(KEY_OWNER)
                .remove(KEY_REPO)
                .remove(KEY_TAG_FILTER)
                .remove(KEY_API_HOST)
                .apply();
    }

    private String value(String key, String fallback) {
        String stored = prefs.getString(key, null);
        return stored == null || stored.trim().isEmpty() ? fallback : stored.trim();
    }

    private void put(String key, String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putString(key, clean).apply();
        }
    }
}
