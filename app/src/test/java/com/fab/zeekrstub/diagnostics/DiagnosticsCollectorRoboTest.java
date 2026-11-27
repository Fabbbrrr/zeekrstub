package com.fab.zeekrstub.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Smoke test: the report builds, keeps its shape, and survives a platform that
 * refuses every probe it is asked about. Robolectric is that platform.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class DiagnosticsCollectorRoboTest {

    @Test
    public void reportHasFixedShapeAndStableSections() {
        Context context = RuntimeEnvironment.getApplication();

        String report = DiagnosticsCollector.collect(context);
        String again = DiagnosticsCollector.collect(context);

        assertTrue(report.startsWith("zeekrstub diagnostics"));
        assertTrue(report.contains("\nschema=1\n"));
        assertTrue(report.contains("\n[1 packages]\n"));
        assertTrue(report.contains("\n[1 highlight]\n"));
        assertTrue(report.contains("\n[2 settings]\n"));
        assertTrue(report.contains("\n[3 props]\n"));
        assertTrue(report.contains("\n[4 reachability]\n"));
        assertTrue(report.contains("\n[5 this app]\n"));

        assertEquals(count(report, "[1 packages]"), count(again, "[1 packages]"));

        assertFalse(report.contains("\r"));
        assertFalse(again.contains("\r"));
    }

    @Test
    public void headerAndSectionOrderAreFixed() {
        String report = DiagnosticsCollector.collect(RuntimeEnvironment.getApplication());

        assertTrue(report.indexOf("generated_utc=") < report.indexOf("app_version="));
        assertTrue(report.indexOf("app_version=") < report.indexOf("app_version_code="));
        assertTrue(report.indexOf("app_version_code=") < report.indexOf("[1 packages]"));
        assertTrue(report.indexOf("[1 packages]") < report.indexOf("[2 settings]"));
        assertTrue(report.indexOf("[2 settings]") < report.indexOf("[3 props]"));
        assertTrue(report.indexOf("[3 props]") < report.indexOf("[4 reachability]"));
        assertTrue(report.indexOf("[4 reachability]") < report.indexOf("[5 this app]"));
    }

    @Test
    public void unreachableProbesStillReportEveryLine() {
        String report = DiagnosticsCollector.collect(RuntimeEnvironment.getApplication());

        assertTrue(report.contains("\ntcp.127.0.0.1:5555="));
        assertTrue(report.contains("\nmdns._adb-tls-pairing._tcp="));
        assertTrue(report.contains("\nmdns._adb-tls-connect._tcp="));
        assertTrue(report.contains("\nspoof="));
        assertTrue(report.contains("\ninstaller_handler="));
        assertTrue(report.contains("\nglobal.package_verifier_enable="));
        assertTrue(report.contains("\nsecure.adb_enabled="));
        assertTrue(report.contains("\ncert_sha256="));
    }

    private static int count(String text, String needle) {
        int hits = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            hits++;
            at = text.indexOf(needle, at + needle.length());
        }
        return hits;
    }
}
