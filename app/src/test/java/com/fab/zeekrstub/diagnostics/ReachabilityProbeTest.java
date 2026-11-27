package com.fab.zeekrstub.diagnostics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;

/** One place decides what a connect result is called, so pin it. */
public class ReachabilityProbeTest {

    @Test
    public void connectedIsOpen() {
        assertEquals("open", ReachabilityProbe.stateText(true, null));
        // A failure that arrives after a connect does not undo the connect.
        assertEquals("open", ReachabilityProbe.stateText(true, new SecurityException()));
    }

    @Test
    public void refusedOrTimedOutIsClosed() {
        assertEquals("closed", ReachabilityProbe.stateText(false, null));
        assertEquals("closed", ReachabilityProbe.stateText(false, new ConnectException("refused")));
        assertEquals("closed", ReachabilityProbe.stateText(false, new SocketTimeoutException("timeout")));
        assertEquals("closed", ReachabilityProbe.stateText(false, new ClosedByInterruptException()));
    }

    @Test
    public void otherFailuresNameTheirClass() {
        assertEquals("unavailable: SecurityException",
                ReachabilityProbe.stateText(false, new SecurityException()));
        assertEquals("unavailable: IllegalStateException",
                ReachabilityProbe.stateText(false, new IllegalStateException("no")));
    }
}
