package com.fab.zeekrstub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Settings are the update channel's control surface, so their defaults matter. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class PrefsTest {

    @Test
    public void defaultsComeFromBuildConfig() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("zeekrstub", Context.MODE_PRIVATE).edit().clear().commit();
        Prefs prefs = Prefs.of(context);
        assertEquals(BuildConfig.DEFAULT_OWNER, prefs.owner());
        assertEquals(BuildConfig.DEFAULT_REPO, prefs.repo());
        assertEquals(BuildConfig.DEFAULT_TAG_FILTER, prefs.tagFilter());
        assertEquals(BuildConfig.DEFAULT_API_HOST, prefs.apiHost());
        assertFalse(prefs.autoCheck());
        assertFalse(prefs.disclaimerAccepted());
    }

    @Test
    public void settersPersistAndBlankFallsBack() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = Prefs.of(context);
        prefs.setOwner("someone");
        prefs.setRepo("their-stub");
        prefs.setTagFilter("");
        prefs.setAutoCheck(true);
        prefs.acceptDisclaimer();

        Prefs reread = Prefs.of(context);
        assertEquals("someone", reread.owner());
        assertEquals("their-stub", reread.repo());
        assertEquals(BuildConfig.DEFAULT_TAG_FILTER, reread.tagFilter());
        assertTrue(reread.autoCheck());
        assertTrue(reread.disclaimerAccepted());
    }

    @Test
    public void resetRestoresBuildDefaults() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = Prefs.of(context);
        prefs.setOwner("x");
        prefs.setRepo("y");
        prefs.resetSourceToDefaults();
        assertEquals(BuildConfig.DEFAULT_OWNER, prefs.owner());
        assertEquals(BuildConfig.DEFAULT_REPO, prefs.repo());
    }

    @Test
    public void checkAndInstallResultsAreRecorded() {
        Context context = RuntimeEnvironment.getApplication();
        Prefs prefs = Prefs.of(context);
        prefs.recordCheck("same version, nothing to do", "v1.0.0");
        assertEquals("same version, nothing to do", prefs.lastCheckText());
        assertEquals("v1.0.0", prefs.lastRemoteTag());
        assertTrue(prefs.lastCheckAt() > 0);
        prefs.recordInstall("system installer UI");
        assertEquals("system installer UI", prefs.lastInstallText());
    }
}
