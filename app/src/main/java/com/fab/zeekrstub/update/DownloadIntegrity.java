package com.fab.zeekrstub.update;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Proof that the bytes on disk are the bytes GitHub published.
 *
 * <p>Anyone can sign an APK with the public test key, so the platform's install
 * check cannot tell a real release from a hand-made one. This class does not
 * solve trust in the publisher; it solves "what I received is what the release
 * page lists": truncated, swapped, redirected downloads all fail here.</p>
 */
public final class DownloadIntegrity {

    private static final String SHA256_PREFIX = "sha256:";

    private DownloadIntegrity() {
    }

    /** Only https, no credentials in the URL. Every redirect hop goes through this. */
    public static URL https(String value) throws IOException {
        URL url = new URL(value);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) {
            throw new IOException("Update URLs must use HTTPS without credentials");
        }
        return url;
    }

    /**
     * The hex half of GitHub's {@code digest} field, or null when the release
     * published none. A digest that is present but malformed is an error: it
     * means the response is not what it claims to be.
     */
    public static String expectedOrNull(String digest) throws IOException {
        if (digest == null || digest.trim().isEmpty() || "null".equalsIgnoreCase(digest.trim())) {
            return null;
        }
        if (!digest.trim().matches(SHA256_PREFIX + "[0-9a-fA-F]{64}")) {
            throw new IOException("Malformed GitHub SHA-256 digest");
        }
        return digest.trim().substring(SHA256_PREFIX.length()).toLowerCase(Locale.US);
    }

    /** Strict form: throws when no digest was published. */
    public static String expected(String digest) throws IOException {
        String hex = expectedOrNull(digest);
        if (hex == null) {
            throw new IOException("Missing GitHub SHA-256 digest");
        }
        return hex;
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >>> 4) & 0x0F, 16));
            out.append(Character.forDigit(b & 0x0F, 16));
        }
        return out.toString();
    }

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        return hex(sha256().digest(bytes));
    }

    /**
     * Size always has to match. The hash has to match only when one was
     * published ({@code expectedHex} null). Anything else throws, and the caller
     * deletes the file.
     */
    public static void verify(String expectedHex, byte[] digest, long expectedSize, long actualSize)
            throws IOException {
        if (expectedSize > 0 && actualSize != expectedSize) {
            throw new IOException("Downloaded APK is " + actualSize + " bytes, release says "
                    + expectedSize);
        }
        if (expectedHex == null) {
            return;
        }
        String actual = hex(digest);
        if (!actual.equalsIgnoreCase(expectedHex)) {
            throw new IOException("Downloaded APK SHA-256 does not match the release asset");
        }
    }
}
