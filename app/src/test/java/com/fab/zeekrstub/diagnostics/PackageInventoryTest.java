package com.fab.zeekrstub.diagnostics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The four ways an installer name can say nothing, pinned in one test. */
public class PackageInventoryTest {

    @Test
    public void nothingIsUnavailable() {
        assertEquals(PackageInventory.UNAVAILABLE,
                PackageInventory.installerOrUnavailable(null));
        assertEquals(PackageInventory.UNAVAILABLE,
                PackageInventory.installerOrUnavailable(""));
        assertEquals(PackageInventory.UNAVAILABLE,
                PackageInventory.installerOrUnavailable("   "));
        assertEquals(PackageInventory.UNAVAILABLE,
                PackageInventory.installerOrUnavailable("null"));
        assertEquals(PackageInventory.UNAVAILABLE,
                PackageInventory.installerOrUnavailable("unknown"));
        assertEquals(PackageInventory.UNAVAILABLE,
                PackageInventory.installerOrUnavailable("NULL"));
    }

    @Test
    public void aRealInstallerIsKept() {
        assertEquals("com.android.vending",
                PackageInventory.installerOrUnavailable("com.android.vending"));
        assertEquals("com.android.vending",
                PackageInventory.installerOrUnavailable("  com.android.vending \n"));
    }

    @Test
    public void textFlattensControlCharacters() {
        assertEquals("a b", PackageInventory.text("a\r\nb"));
        assertEquals(PackageInventory.UNAVAILABLE, PackageInventory.text("  "));
        assertEquals(PackageInventory.UNAVAILABLE, PackageInventory.text(null));
    }
}
