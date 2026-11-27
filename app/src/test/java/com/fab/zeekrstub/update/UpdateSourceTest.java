package com.fab.zeekrstub.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fab.zeekrstub.BuildConfig;

import org.junit.Test;

public class UpdateSourceTest {

    @Test
    public void pastedWebUrlBecomesApiHost() {
        assertEquals("api.github.com", UpdateSource.cleanHost("https://github.com/"));
        assertEquals("api.github.com", UpdateSource.cleanHost("github.com"));
        assertEquals("api.github.com", UpdateSource.cleanHost(" GitHub.com "));
    }

    @Test
    public void enterpriseHostKeptWithPort() {
        assertEquals("ghe.example.com:8443",
                UpdateSource.cleanHost("https://ghe.example.com:8443/api/v3"));
    }

    @Test
    public void blankHostFallsBackToBuildDefault() {
        assertEquals(BuildConfig.DEFAULT_API_HOST, UpdateSource.cleanHost(""));
        assertEquals(BuildConfig.DEFAULT_API_HOST, UpdateSource.cleanHost(null));
    }

    @Test
    public void ownerAndRepoAreSanitised() {
        UpdateSource source = new UpdateSource("api.github.com", " Fabbbrrr ", "zeekrstub/", "v*");
        assertEquals("Fabbbrrr", source.owner);
        assertEquals("zeekrstub", source.repo);
        assertTrue(source.valid());
    }

    @Test
    public void namesWithSlashAreRejectedNotPasted() {
        assertFalse(new UpdateSource("api.github.com", "a/b", "repo", "").valid());
        assertFalse(new UpdateSource("api.github.com", "owner", "re po", "").valid());
        assertFalse(new UpdateSource("api.github.com", "owner", "..", "").valid());
    }

    @Test
    public void urls() {
        UpdateSource source = new UpdateSource("api.github.com", "Fabbbrrr", "zeekrstub", "v*");
        assertEquals("https://api.github.com/repos/Fabbbrrr/zeekrstub/releases?per_page=30",
                source.releasesUrl());
        assertEquals("https://github.com/Fabbbrrr/zeekrstub", source.pageUrl());
        assertEquals("https://github.com/Fabbbrrr/zeekrstub/releases/tag/v1.0.0",
                source.tagPageUrl("v1.0.0"));
    }

    @Test
    public void tagFilterTravelsWithTheSource() {
        UpdateSource source = new UpdateSource("api.github.com", "o", "r", "v*,!v*-alpha");
        assertTrue(source.matchesTag("v1.0.0"));
        assertFalse(source.matchesTag("v1.0.0-alpha"));
    }

    @Test
    public void defaultsMatchBuildConfig() {
        UpdateSource source = UpdateSource.defaults();
        assertEquals(BuildConfig.DEFAULT_OWNER, source.owner);
        assertEquals(BuildConfig.DEFAULT_REPO, source.repo);
        assertEquals(BuildConfig.DEFAULT_TAG_FILTER, source.tagFilter);
        assertEquals(BuildConfig.DEFAULT_API_HOST, source.host);
    }
}
