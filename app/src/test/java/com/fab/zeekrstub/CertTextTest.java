package com.fab.zeekrstub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.MessageDigest;
import java.util.Locale;

public class CertTextTest {

    private static final byte[] DER = new byte[]{
            0x30, 0x1a, 0x02, 0x01, 0x00, 0x7f, (byte) 0x80, (byte) 0xff, 0x10, 0x00
    };

    @Test
    public void colonFormIsUppercaseGroupsOfTwo() {
        String colon = CertText.colon(DER);
        assertEquals(95, colon.length());           // 32 groups + 31 colons
        assertEquals(31, colon.length() - colon.replace(":", "").length());
        assertTrue(colon.matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"));
    }

    @Test
    public void hexFormIsLowercaseAndNoSeparators() {
        String hex = CertText.hex(DER);
        assertEquals(64, hex.length());
        assertEquals(hex.toLowerCase(Locale.US), hex);
        assertFalse(hex.contains(":"));
    }

    @Test
    public void bothShapesAgree() {
        String colon = CertText.colon(DER).replace(":", "").toLowerCase(Locale.US);
        assertEquals(CertText.hex(DER), colon);
    }

    @Test
    public void matchesRealSha256() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(DER);
        assertEquals(CertText.hexOf(digest), CertText.hex(DER));
    }

    @Test
    public void missingCertificateIsUnavailableNotAFakeValue() {
        assertEquals(CertText.UNAVAILABLE, CertText.hex(null));
        assertEquals(CertText.UNAVAILABLE, CertText.hex(new byte[0]));
        assertEquals(CertText.UNAVAILABLE, CertText.colon(null));
    }

    @Test
    public void recordedFingerprintHasTheRightShape() {
        String recorded = BuildConfig.EXPECTED_CERT_SHA256;
        assertEquals(64, recorded.length());
        assertTrue(recorded.matches("[0-9A-F]{64}"));
        assertNotEquals(recorded, CertText.hex(DER));
    }
}
