package com.fab.zeekrstub.diagnostics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/** The fallback rule lives in one place, so it gets pinned here. */
public class SettingsProbeTest {

    @Test
    public void firstRealValueWins() {
        assertEquals("1", SettingsProbe.firstNonEmpty(Arrays.asList(null, "", "  ", "1", "0")));
        assertEquals("0", SettingsProbe.firstNonEmpty(Arrays.asList("0", "1")));
    }

    @Test
    public void noAnswerGivesUnavailable() {
        assertEquals(SettingsProbe.UNAVAILABLE, SettingsProbe.firstNonEmpty(null));
        assertEquals(SettingsProbe.UNAVAILABLE, SettingsProbe.firstNonEmpty(new ArrayList<String>()));
        assertEquals(SettingsProbe.UNAVAILABLE,
                SettingsProbe.firstNonEmpty(Arrays.asList(null, "", "   ")));
    }

    @Test
    public void literalUnavailableIsARealAnswer() {
        // It is not blank, so the rule keeps it: first non-empty wins.
        assertEquals(SettingsProbe.UNAVAILABLE,
                SettingsProbe.firstNonEmpty(Collections.singletonList("unavailable")));
    }

    @Test
    public void valueIsTrimmed() {
        assertEquals("1", SettingsProbe.firstNonEmpty(Collections.singletonList("  1 ")));
    }
}
