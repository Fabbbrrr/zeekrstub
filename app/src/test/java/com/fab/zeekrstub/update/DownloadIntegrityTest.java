package com.fab.zeekrstub.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DownloadIntegrityTest {

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    public void sha256KnownVector() {
        assertEquals(ABC_SHA256, DownloadIntegrity.sha256Hex(
                "abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void httpsOnly() {
        try {
            DownloadIntegrity.https("http://example.com/app.apk");
            fail("http must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("HTTPS"));
        }
        try {
            DownloadIntegrity.https("https://user:pass@example.com/app.apk");
            fail("credentials must be refused");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void digestPrefixStrippedAndLowercased() throws Exception {
        assertEquals(ABC_SHA256,
                DownloadIntegrity.expectedOrNull("sha256:" + ABC_SHA256.toUpperCase()));
        assertNull(DownloadIntegrity.expectedOrNull(""));
        assertNull(DownloadIntegrity.expectedOrNull(null));
        assertNull(DownloadIntegrity.expectedOrNull("null"));
    }

    @Test
    public void malformedDigestIsAnErrorNotSilence() {
        try {
            DownloadIntegrity.expectedOrNull("sha256:abc");
            fail("short digest must be refused");
        } catch (IOException expected) {
            // expected
        }
        try {
            DownloadIntegrity.expected(""); // strict form: absent digest is an error
            fail("strict form must refuse a missing digest");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void sizeMismatchRefused() {
        byte[] digest = DownloadIntegrity.sha256().digest("abc".getBytes(StandardCharsets.UTF_8));
        try {
            DownloadIntegrity.verify(ABC_SHA256, digest, 999, 3);
            fail("size mismatch must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("999"));
        }
    }

    @Test
    public void hashMismatchRefused() {
        byte[] digest = DownloadIntegrity.sha256().digest("abc".getBytes(StandardCharsets.UTF_8));
        try {
            DownloadIntegrity.verify("00".repeat(32), digest, 3, 3);
            fail("hash mismatch must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("SHA-256"));
        }
    }

    @Test
    public void absentDigestStillChecksSize() throws Exception {
        DownloadIntegrity.verify(null, new byte[]{1, 2, 3}, 3, 3);
        try {
            DownloadIntegrity.verify(null, new byte[]{1, 2, 3}, 4, 3);
            fail("size mismatch must be refused even without a digest");
        } catch (IOException expected) {
            // expected
        }
    }
}
