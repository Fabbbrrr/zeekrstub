package com.fab.zeekrstub.update;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * One release carries one APK per identity. Each app must take only its own file,
 * or the spare downloads the main APK, refuses it as a foreign package, and the
 * spare door quietly stops working.
 */
public class IdentityAssetTest {

    private static final String MAIN = "zeekrstub-1.0.3.apk";
    private static final String SPARE = "zeekrstub-spare-1.0.3.apk";

    @Test
    public void mainIdentityIgnoresTheSpareAsset() {
        assertEquals(0, AssetPicker.bestIndex(Arrays.asList(MAIN, SPARE), null, "spare"));
        // The spare is at index 0 here and is refused, so the main asset at index 1 wins.
        assertEquals(1, AssetPicker.bestIndex(Arrays.asList(SPARE, MAIN), null, "spare"));
    }

    @Test
    public void spareIdentityTakesOnlyItsOwnAsset() {
        assertEquals(1, AssetPicker.bestIndex(Arrays.asList(MAIN, SPARE), "spare", null));
        assertEquals(0, AssetPicker.bestIndex(Arrays.asList(SPARE, MAIN), "spare", null));
    }

    @Test
    public void spareIdentityWithoutItsAssetFindsNothing() {
        assertEquals(-1, AssetPicker.bestIndex(Collections.singletonList(MAIN), "spare", null));
    }

    @Test
    public void mainIdentityWithoutItsAssetFindsNothing() {
        assertEquals(-1, AssetPicker.bestIndex(Collections.singletonList(SPARE), null, "spare"));
    }

    @Test
    public void aReleaseWithOnlyOneApkStillWorksForTheMatchingIdentity() {
        assertEquals(0, AssetPicker.bestIndex(Collections.singletonList(MAIN), null, "spare"));
        assertEquals(0, AssetPicker.bestIndex(Collections.singletonList(SPARE), "spare", null));
    }

    @Test
    public void tokenMatchIgnoresCase() {
        assertEquals(0, AssetPicker.bestIndex(
                Collections.singletonList("Zeekrstub-SPARE-1.0.3.apk"), "spare", null));
    }
}
