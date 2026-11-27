package com.fab.zeekrstub.diagnostics;

import com.fab.zeekrstub.CertText;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads system properties with /system/bin/getprop, which is a normal exec and
 * needs no root. If the exec fails, one key at a time is asked through
 * SystemProperties reflection. If that fails too, the key prints "unavailable".
 */
public final class PropProbe {

    public static final String DISPLAY_ID = "ro.build.display.id";

    /** Always printed at the top of the props section, in this order. */
    public static final String[] HEAD_KEYS = {
            DISPLAY_ID,
            "ro.product.brand",
            "ro.product.manufacturer",
            "ro.product.model",
            "ro.product.device"
    };

    private static final String GETPROP = "/system/bin/getprop";
    private static final long READ_MS = 1500L;
    private static final int MAX_LINES = 4000;

    private PropProbe() {
    }

    /** True for the keys printed in the fixed block, so the filtered list can skip them. */
    public static boolean isHeadKey(String key) {
        for (String head : HEAD_KEYS) {
            if (head.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** All properties the platform will hand over, sorted by key. Never throws. */
    public static Map<String, String> read() {
        Map<String, String> props = new TreeMap<>();
        StringBuilder sink = new StringBuilder(16384);
        if (execGetProp(sink)) {
            parse(sink.toString(), props);
        }
        return props;
    }

    /**
     * One property: the exec result first, then reflection, then "unavailable".
     * A key that exists with an empty value counts as no answer.
     */
    public static String value(Map<String, String> props, String key) {
        if (props != null && key != null) {
            String found = props.get(key);
            if (found != null && !found.trim().isEmpty()) {
                return found.trim();
            }
        }
        String reflected = reflect(key);
        return reflected == null ? CertText.UNAVAILABLE : reflected;
    }

    /**
     * Runs getprop and reads stdout on a helper thread, so a hung process still
     * gives up after {@link #READ_MS}. Whatever arrived by then is kept.
     */
    private static boolean execGetProp(StringBuilder sink) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{GETPROP});
            final Process running = process;
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    drain(running, sink);
                }
            }, "getprop-read");
            reader.setDaemon(true);
            reader.start();
            try {
                reader.join(READ_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return sink.length() > 0;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                    // the process is already gone
                }
            }
        }
    }

    private static void drain(Process process, StringBuilder sink) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            int lines = 0;
            while (lines < MAX_LINES && (line = in.readLine()) != null) {
                synchronized (sink) {
                    sink.append(line).append('\n');
                }
                lines++;
            }
        } catch (Throwable ignored) {
            // keep what arrived
        } finally {
            close(in);
        }
    }

    /**
     * Accepts both shapes: "key=value" and the bracket form getprop prints,
     * "[key]: [value]".
     */
    static void parse(String text, Map<String, String> into) {
        if (text == null || into == null) {
            return;
        }
        String[] lines = text.split("\n", -1);
        int added = 0;
        for (String raw : lines) {
            if (added >= MAX_LINES) {
                return;
            }
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            int marker = line.indexOf("]: [");
            String key;
            String value;
            if (line.startsWith("[") && marker > 1) {
                key = line.substring(1, marker).trim();
                int end = line.endsWith("]") ? line.length() - 1 : line.length();
                int from = Math.min(marker + 4, end);
                value = line.substring(from, Math.max(from, end)).trim();
            } else {
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                key = line.substring(0, equals).trim();
                value = line.substring(equals + 1).trim();
            }
            if (key.isEmpty()) {
                continue;
            }
            into.put(key, value);
            added++;
        }
    }

    private static String reflect(String key) {
        if (key == null) {
            return null;
        }
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getMethod("get", String.class);
            Object answer = get.invoke(null, key);
            if (answer instanceof String) {
                String trimmed = ((String) answer).trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        } catch (Throwable ignored) {
            // hidden API, or no such key
        }
        return null;
    }

    private static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
                // nothing left to do
            }
        }
    }
}
