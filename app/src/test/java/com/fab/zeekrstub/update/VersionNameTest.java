package com.fab.zeekrstub.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionNameTest {

    @Test
    public void numericCompareBeatsStringCompare() {
        // The bug this exists to prevent: "0.9.0" > "0.25.0" as strings.
        assertTrue(VersionName.compare("0.25.0", "0.9.0") > 0);
        assertTrue(VersionName.compare("0.9.0", "0.25.0") < 0);
        assertEquals(0, VersionName.compare("1.2", "1.2.0"));
    }

    @Test
    public void leadingVIsIgnored() {
        assertTrue(VersionName.isNewer("v1.0.1", "1.0.0"));
        assertEquals(0, VersionName.compare("v1.0.0", "1.0.0"));
    }

    @Test
    public void equalVersionIsNotNewer() {
        assertFalse(VersionName.isNewer("1.0.0", "1.0.0"));
        assertFalse(VersionName.isNewer("v1.0.0", "1.0.0"));
    }

    @Test
    public void olderVersionIsNotNewer() {
        assertFalse(VersionName.isNewer("0.9.9", "1.0.0"));
    }

    @Test
    public void unparseableNeverClaimsAnUpdate() {
        assertFalse(VersionName.isNewer("latest", "1.0.0"));
        assertFalse(VersionName.isNewer("1.0.0", "nightly"));
        assertFalse(VersionName.isNewer(null, "1.0.0"));
        assertFalse(VersionName.isNewer("1.0.0", null));
    }

    @Test
    public void prereleaseIsOlderThanTheRelease() {
        assertTrue(VersionName.compare("1.0.0", "1.0.0-beta") > 0);
        assertTrue(VersionName.compare("1.0.0-beta", "1.0.0-alpha") > 0);
    }

    @Test
    public void buildMetadataIgnored() {
        assertEquals(0, VersionName.compare("1.2.3+45", "1.2.3+99"));
    }

    @Test
    public void major() {
        assertEquals(2, VersionName.major("v2.14.0-alpha"));
        assertEquals(-1, VersionName.major("nightly"));
    }
}
