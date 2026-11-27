package com.fab.zeekrstub.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TagFilterTest {

    @Test
    public void emptyFilterAcceptsEverything() {
        assertTrue(TagFilter.matches("", "v1.0.0"));
        assertTrue(TagFilter.matches(null, "1.0.0"));
    }

    @Test
    public void prefixWildcard() {
        assertTrue(TagFilter.matches("v*", "v1.0.0"));
        assertFalse(TagFilter.matches("v*", "1.0.0"));
    }

    @Test
    public void caseInsensitive() {
        assertTrue(TagFilter.matches("V*", "v1.0.0"));
    }

    @Test
    public void exclusionWins() {
        assertTrue(TagFilter.matches("v*,!v*-alpha", "v1.0.0"));
        assertFalse(TagFilter.matches("v*,!v*-alpha", "v1.1.0-alpha"));
    }

    @Test
    public void exclusionOnlyMeansEverythingElse() {
        assertTrue(TagFilter.matches("!v*-alpha", "v1.0.0"));
        assertFalse(TagFilter.matches("!v*-alpha", "v1.0.0-alpha"));
    }

    @Test
    public void multiplePatternsKeepOrder() {
        List<String> kept = TagFilter.filter("v1.0.*,v2.*", Arrays.asList(
                "v2.0.0", "v1.0.5", "v1.1.0", "nightly"));
        assertEquals(Arrays.asList("v2.0.0", "v1.0.5"), kept);
    }

    @Test
    public void blankTagNeverMatches() {
        assertFalse(TagFilter.matches("v*", "  "));
        assertFalse(TagFilter.matches("", null));
    }
}
