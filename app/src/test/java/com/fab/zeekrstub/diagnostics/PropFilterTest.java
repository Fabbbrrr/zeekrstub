package com.fab.zeekrstub.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** The filter and the spoof marker are pure, so they get pinned here. */
public class PropFilterTest {

    @org.junit.Test
    public void virtualizedReadsTheGuestMarkers() {
        java.util.Map<String, String> gvm = new java.util.HashMap<>();
        gvm.put("ro.product.model", "msmnile_gvmq for arm64");
        gvm.put("ro.product.device", "zeekr_dhu_sa8295_ovs");
        org.junit.Assert.assertTrue(PropFilter.virtualized(gvm));

        java.util.Map<String, String> qemu = new java.util.HashMap<>();
        qemu.put("ro.kernel.qemu", "1");
        org.junit.Assert.assertTrue(PropFilter.virtualized(qemu));

        java.util.Map<String, String> real = new java.util.HashMap<>();
        real.put("ro.product.model", "Zeekr 7X");
        real.put("ro.product.device", "zeekr_dhu_sa8295");
        org.junit.Assert.assertFalse(PropFilter.virtualized(real));

        org.junit.Assert.assertFalse(PropFilter.virtualized(new java.util.HashMap<>()));
    }

    @Test
    public void noiseWordsInKeyMatch() {
        assertTrue(PropFilter.matches("ro.package_verifier_enable", "1"));
        assertTrue(PropFilter.matches("persist.sys.adb_enable", "0"));
        assertTrue(PropFilter.matches("ro.allow_mock_location", "0"));
        assertTrue(PropFilter.matches("ro.whitelist_mode", "1"));
        assertTrue(PropFilter.matches("ro.sandbox_mode", "1"));
        assertTrue(PropFilter.matches("ro.kernel.qemu.emulator", "1"));
        assertTrue(PropFilter.matches("persist.dfm.enabled", "1"));
        assertTrue(PropFilter.matches("ro.sefence.mode", "0"));
        assertTrue(PropFilter.matches("ro.test_build", "1"));
    }

    @Test
    public void noiseWordsInValueMatch() {
        assertTrue(PropFilter.matches("ro.boot.mode", "verifier_on"));
        assertTrue(PropFilter.matches("ro.unknown.key", "sandbox"));
        assertTrue(PropFilter.matches("ro.unknown.key", "DFM"));
    }

    @Test
    public void secureWithFollowingCharacterMatches() {
        // The pattern is "secure." with a dot, so one more character is needed.
        assertTrue(PropFilter.matches("ro.boot.secure_boot", "1"));
        assertTrue(PropFilter.matches("ro.build.type", "SecureMode"));
    }

    @Test
    public void productKeysAlwaysMatch() {
        assertTrue(PropFilter.matches("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a"));
        assertTrue(PropFilter.matches("ro.product.model", "sargo"));
    }

    @Test
    public void displayIdAlwaysMatches() {
        assertTrue(PropFilter.matches("ro.build.display.id", "zeekr_dhu_sa8295_ovs_1"));
    }

    @Test
    public void boringLineDoesNotMatch() {
        assertFalse(PropFilter.matches("com.android.launcher3", "vendor"));
        assertFalse(PropFilter.matches("ro.board.platform", "sa8295p"));
        assertFalse(PropFilter.matches(null, null));
    }

    @Test
    public void matchingIsCaseInsensitive() {
        assertTrue(PropFilter.matches("persist.SYS.ADB", "1"));
        assertTrue(PropFilter.matches("ro.PACKAGE_VERIFIER", "1"));
        assertTrue(PropFilter.matches("ro.unknown.key", "Whitelist"));
    }

    @Test
    public void spoofedWhenDisplaySaysZeekrAndBrandSaysPixel() {
        Map<String, String> props = new HashMap<>();
        props.put("ro.build.display.id", "zeekr_dhu_sa8295_ovs_20240101");
        props.put("ro.product.brand", "google");
        props.put("ro.product.model", "sargo");
        assertTrue(PropFilter.spoofed(props));
        assertTrue("SPOOFED".equals(PropFilter.spoofMarker(props)));
    }

    @Test
    public void cleanWhenBrandStaysZeekr() {
        Map<String, String> props = new HashMap<>();
        props.put("ro.build.display.id", "zeekr_dhu_sa8295_ovs_20240101");
        props.put("ro.product.brand", "zeekr");
        props.put("ro.product.model", "dhu");
        assertFalse(PropFilter.spoofed(props));
        assertTrue("clean".equals(PropFilter.spoofMarker(props)));
    }

    @Test
    public void cleanWhenKeysAreMissing() {
        assertFalse(PropFilter.spoofed(new HashMap<String, String>()));
        assertFalse(PropFilter.spoofed(null));
        Map<String, String> onlyBrand = new HashMap<>();
        onlyBrand.put("ro.product.brand", "google");
        assertFalse(PropFilter.spoofed(onlyBrand));
        assertTrue("clean".equals(PropFilter.spoofMarker(onlyBrand)));
    }

    @Test
    public void spoofMarkerReadsTheFingerprintToo() {
        Map<String, String> props = new HashMap<>();
        props.put("ro.build.fingerprint", "google/sargo/sargo:11/QQ3A/1234:user/release-keys_zeekr");
        props.put("ro.product.device", "bonito");
        assertTrue(PropFilter.spoofed(props));
    }
}
