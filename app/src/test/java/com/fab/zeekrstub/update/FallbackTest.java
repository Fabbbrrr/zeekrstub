package com.fab.zeekrstub.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The fallback list is the difference between one dead repo and no updates at all. */
public class FallbackTest {

    @Test
    public void commasAndNewlinesBothSeparate() {
        List<String> entries = UpdateFlow.splitFallbacks(
                "someone/stub,\nother/stub\nhttps://example.com/app.apk");
        assertEquals(Arrays.asList("someone/stub", "other/stub",
                "https://example.com/app.apk"), entries);
    }

    @Test
    public void blanksAreIgnored() {
        assertEquals(Collections.emptyList(), UpdateFlow.splitFallbacks(""));
        assertEquals(Collections.emptyList(), UpdateFlow.splitFallbacks(null));
        assertEquals(Collections.emptyList(), UpdateFlow.splitFallbacks(" , \n ,, "));
    }

    @Test
    public void singleEntrySurvivesTrimming() {
        assertEquals(Collections.singletonList("a/b"),
                UpdateFlow.splitFallbacks("  a/b  "));
    }

    @Test
    public void directUrlEntryIsRecognisedAsAUrl() {
        List<String> entries = UpdateFlow.splitFallbacks("https://example.com/app.apk");
        assertTrue(entries.get(0).startsWith("https://"));
        assertTrue(entries.get(0).endsWith(".apk"));
    }
}
