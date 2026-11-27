package com.fab.zeekrstub.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AssetPickerTest {

    @Test
    public void arm64WinsOverUniversal() {
        assertEquals(1, AssetPicker.bestIndex(Arrays.asList(
                "zeekrstub-1.0.0-universal.apk", "zeekrstub-1.0.0-arm64-v8a.apk")));
    }

    @Test
    public void universalWinsOverUnnamed() {
        assertEquals(1, AssetPicker.bestIndex(Arrays.asList(
                "zeekrstub.apk", "zeekrstub-universal.apk")));
    }

    @Test
    public void x86OnlyIsNotInstallable() {
        assertEquals(-1, AssetPicker.bestIndex(Collections.singletonList(
                "zeekrstub-1.0.0-x86_64.apk")));
    }

    @Test
    public void bundlesAreOutOfScope() {
        assertEquals(AssetPicker.NOT_A_CANDIDATE, AssetPicker.rank("app.xapk"));
        assertEquals(AssetPicker.NOT_A_CANDIDATE, AssetPicker.rank("app.apkm"));
        assertEquals(AssetPicker.NOT_A_CANDIDATE, AssetPicker.rank("app.apks"));
        assertEquals(AssetPicker.NOT_A_CANDIDATE, AssetPicker.rank("app.zip"));
    }

    @Test
    public void singleApkIsPicked() {
        assertEquals(0, AssetPicker.bestIndex(Collections.singletonList("zeekrstub-1.0.0.apk")));
        assertEquals(AssetPicker.UNKNOWN_ABI, AssetPicker.rank("zeekrstub-1.0.0.apk"));
    }

    @Test
    public void tieBreaksOnShortestThenAlphabetical() {
        // Same rank, same length: alphabetical. Deterministic across runs.
        assertEquals(1, AssetPicker.bestIndex(Arrays.asList(
                "b-arm64.apk", "a-arm64.apk")));
    }

    @Test
    public void armeabiIsNotArm64() {
        assertEquals(AssetPicker.WRONG_ABI, AssetPicker.rank("app-armeabi-v7a.apk"));
        assertEquals(-1, AssetPicker.bestIndex(Collections.singletonList("app-armeabi-v7a.apk")));
    }

    @Test
    public void emptyListPicksNothing() {
        assertEquals(-1, AssetPicker.bestIndex(Collections.emptyList()));
        assertEquals(-1, AssetPicker.bestIndex(null));
    }

    @Test
    public void ranksAreOrdered() {
        assertTrue(AssetPicker.ARM64 < AssetPicker.UNIVERSAL);
        assertTrue(AssetPicker.UNIVERSAL < AssetPicker.UNKNOWN_ABI);
    }
}
