package com.fab.zeekrstub.diagnostics;

import android.content.Context;
import android.provider.Settings;

import java.util.Collections;
import java.util.List;

/**
 * Reads the verifier and install switches. A key that is not there prints
 * "unavailable": the report never guesses what a missing setting means.
 *
 * Global and Secure stay separate calls. The report prints both tables, so a
 * value from one table must never be shown as the value of the other.
 */
public final class SettingsProbe {

    public static final String UNAVAILABLE = "unavailable";

    private SettingsProbe() {
    }

    /**
     * The settings-name fallback rule in one place: take the first name that
     * really answers, else say so. Null, blank and whitespace all count as no
     * answer.
     */
    public static String firstNonEmpty(List<String> values) {
        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
        }
        return UNAVAILABLE;
    }

    /** Value from the global table, or "unavailable". Never throws. */
    public static String global(Context context, String key) {
        return firstNonEmpty(Collections.singletonList(read(context, key, true)));
    }

    /** Value from the secure table, or "unavailable". Never throws. */
    public static String secure(Context context, String key) {
        return firstNonEmpty(Collections.singletonList(read(context, key, false)));
    }

    private static String read(Context context, String key, boolean global) {
        if (context == null || key == null) {
            return null;
        }
        try {
            if (global) {
                return Settings.Global.getString(context.getContentResolver(), key);
            }
            return Settings.Secure.getString(context.getContentResolver(), key);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
