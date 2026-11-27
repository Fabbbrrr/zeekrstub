package com.fab.zeekrstub.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which release tags the app accepts. Empty filter means every non-draft
 * release; otherwise a comma separated list of patterns where '*' stands for
 * "anything". Matching ignores case, so {@code v*} also accepts {@code V1.2.0}.
 *
 * <p>A leading '!' excludes: {@code v*,!v*-alpha} takes stable tags and drops
 * alphas. Exclusion wins, because the safer reading is the one that shows the
 * owner fewer unverified builds.</p>
 */
public final class TagFilter {

    private TagFilter() {
    }

    public static boolean matches(String filter, String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        String patternList = filter == null ? "" : filter.trim();
        if (patternList.isEmpty()) {
            return true;
        }
        String value = tag.trim().toLowerCase(Locale.US);
        boolean sawInclude = false;
        boolean included = false;
        for (String raw : patternList.split(",")) {
            String pattern = raw.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.startsWith("!")) {
                if (glob(pattern.substring(1), value)) {
                    return false;
                }
            } else {
                sawInclude = true;
                if (glob(pattern, value)) {
                    included = true;
                }
            }
        }
        return !sawInclude || included;
    }

    /** Keeps the list order, drops tags the filter refuses. */
    public static List<String> filter(String filter, List<String> tags) {
        List<String> out = new ArrayList<>();
        if (tags == null) {
            return out;
        }
        for (String tag : tags) {
            if (matches(filter, tag)) {
                out.add(tag);
            }
        }
        return out;
    }

    /** Case-insensitive glob with '*' only. Anchored at both ends. */
    static boolean glob(String pattern, String value) {
        String p = pattern.toLowerCase(Locale.US);
        if (!p.contains("*")) {
            return p.equals(value);
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-^$?|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }
}
