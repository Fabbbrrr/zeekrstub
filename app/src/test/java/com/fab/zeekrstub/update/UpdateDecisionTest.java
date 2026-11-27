package com.fab.zeekrstub.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateDecisionTest {

    private static final String APP = "com.fab.zeekrstub";
    private static final String CERT = "a4d0a80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc";

    @Test
    public void newerVersionCodeAllowed() {
        UpdateDecision.Verdict verdict = UpdateDecision.check(APP, 3, CERT, APP, 4, CERT);
        assertTrue(verdict.installable);
    }

    @Test
    public void equalVersionCodeRefused() {
        UpdateDecision.Verdict verdict = UpdateDecision.check(APP, 4, CERT, APP, 4, CERT);
        assertFalse(verdict.installable);
        assertTrue(verdict.reason.contains("same versionCode"));
    }

    @Test
    public void downgradeRefused() {
        UpdateDecision.Verdict verdict = UpdateDecision.check(APP, 5, CERT, APP, 4, CERT);
        assertFalse(verdict.installable);
        assertTrue(verdict.reason.contains("older"));
    }

    @Test
    public void differentPackageRefused() {
        UpdateDecision.Verdict verdict = UpdateDecision.check(APP, 1, CERT, "com.other.app", 9, CERT);
        assertFalse(verdict.installable);
        assertTrue(verdict.reason.contains("com.other.app"));
    }

    @Test
    public void missingPackageRefused() {
        assertFalse(UpdateDecision.check(APP, 1, CERT, null, 9, CERT).installable);
        assertFalse(UpdateDecision.check(APP, 1, CERT, "  ", 9, CERT).installable);
    }

    @Test
    public void differentSigningKeyRefused() {
        UpdateDecision.Verdict verdict = UpdateDecision.check(APP, 1, CERT, APP, 2,
                "ff".repeat(32));
        assertFalse(verdict.installable);
        assertTrue(verdict.reason.contains("different key"));
    }

    @Test
    public void unknownKeyOnEitherSideDoesNotBlockVersionCheck() {
        assertTrue(UpdateDecision.check(APP, 1, null, APP, 2, CERT).installable);
        assertTrue(UpdateDecision.check(APP, 1, "", APP, 2, CERT).installable);
    }
}
