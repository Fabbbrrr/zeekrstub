package com.fab.zeekrstub.diagnostics;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which property lines earn a place in the report, and whether this container
 * is dressing up as a Pixel. Both answers are pure functions so a unit test can
 * pin them without a device.
 */
public final class PropFilter {

    /**
     * Noise words seen on the head unit: verifier gates, allow lists, the
     * sandbox, the emulator, DFM, Sefence, adb, test builds and secure flags.
     */
    private static final Pattern NOISE = Pattern.compile(
            "(?i)verif|allow|whitelist|sandbox|emulator|dfm|sefence|adb|test|secure.");

    /** Keys the spoof marker reads. */
    private static final String[] PRODUCT_KEYS = {
            "ro.product.brand",
            "ro.product.manufacturer",
            "ro.product.model",
            "ro.product.device"
    };

    /** Words that belong to a Pixel 3a, not to a Zeekr head unit. */
    private static final String[] SPOOF_WORDS = {"google", "pixel", "sargo", "bonito", "aosp"};

    private PropFilter() {
    }

    /**
     * True when the key or the value carries a noise word. Key rules first:
     * every ro.product.* key and the display id always match.
     */
    public static boolean matches(String key, String value) {
        if (key != null) {
            if (key.startsWith("ro.product.")) {
                return true;
            }
            if ("ro.build.display.id".equals(key)) {
                return true;
            }
            if (NOISE.matcher(key).find()) {
                return true;
            }
        }
        return value != null && NOISE.matcher(value).find();
    }

    /**
     * True when the real display id says Zeekr while a product key says Pixel.
     * A missing key never counts, so no answer is invented.
     */
    public static boolean spoofed(Map<String, String> props) {
        if (props == null) {
            return false;
        }
        boolean zeekr = mentions(props.get("ro.build.display.id"), "zeekr")
                || mentions(props.get("ro.build.fingerprint"), "zeekr");
        if (!zeekr) {
            return false;
        }
        for (String key : PRODUCT_KEYS) {
            String value = props.get(key);
            for (String word : SPOOF_WORDS) {
                if (mentions(value, word)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "SPOOFED" or "clean", the one shape the report prints. */
    public static String spoofMarker(Map<String, String> props) {
        return spoofed(props) ? "SPOOFED" : "clean";
    }

    private static boolean mentions(String value, String word) {
        return value != null && value.toLowerCase(Locale.US).contains(word);
    }
}
