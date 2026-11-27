package com.fab.zeekrstub.diagnostics;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Can adb reach this container? One TCP probe on the local port and one mDNS
 * browse per service type. Both run on the calling thread because a report run
 * is already off the UI thread, and both give up on a fixed timeout.
 */
public final class ReachabilityProbe {

    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final long BROWSE_MS = 3000L;

    private ReachabilityProbe() {
    }

    /**
     * The one place that decides what a connect result is called. A refused or
     * timed out connect is "closed", not a fault. Anything that is not an I/O
     * answer, such as a security refusal, is reported by class name.
     */
    public static String stateText(boolean connected, Throwable failure) {
        if (connected) {
            return "open";
        }
        if (failure == null || failure instanceof IOException) {
            return "closed";
        }
        return "unavailable: " + failure.getClass().getSimpleName();
    }

    /** One line: tcp.host:port=state (elapsed ms). Never throws. */
    public static String tcp(String host, int port) {
        long start = System.currentTimeMillis();
        boolean connected = false;
        Throwable failure = null;
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            connected = true;
        } catch (Throwable problem) {
            failure = problem;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                    // already closed
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        return "tcp." + host + ":" + port + "=" + stateText(connected, failure)
                + " (" + elapsed + " ms)";
    }

    /**
     * Browses one service type and returns its mdns line first, then one
     * "service=type:name" line per discovery, sorted. Never throws.
     */
    public static List<String> mdns(Context context, String serviceType) {
        List<String> out = new ArrayList<>();
        Set<String> services = new TreeSet<>();
        String state;
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> startFailure = new AtomicReference<>();
        NsdManager.DiscoveryListener listener = listenerFor(services, latch, startFailure);
        NsdManager manager = null;
        try {
            manager = manager(context);
            if (manager == null) {
                state = "unavailable: no NsdManager";
            } else {
                manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
                latch.await(BROWSE_MS, TimeUnit.MILLISECONDS);
                String refused = startFailure.get();
                if (refused != null) {
                    state = "unavailable: start failed, code=" + refused;
                } else if (services.isEmpty()) {
                    state = "not-found";
                } else {
                    state = "found:" + services.size();
                }
            }
        } catch (Throwable problem) {
            state = "unavailable: " + problem.getClass().getSimpleName();
        } finally {
            if (manager != null) {
                try {
                    manager.stopServiceDiscovery(listener);
                } catch (Throwable ignored) {
                    // discovery was never running
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        out.add("mdns." + serviceType + "=" + state + " (" + elapsed + " ms)");
        for (String name : services) {
            out.add("service=" + serviceType + ":" + name);
        }
        return out;
    }

    /**
     * A plain listener object. It does not extend Handler: on some platforms
     * that only moves the callback thread, and the latch already handles timing.
     */
    private static NsdManager.DiscoveryListener listenerFor(final Set<String> services,
                                                            final CountDownLatch latch,
                                                            final AtomicReference<String> startFailure) {
        return new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                startFailure.set(String.valueOf(errorCode));
                latch.countDown();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                // stopping late is harmless
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                // the browse keeps the whole window open
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                // end of the window
            }

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                if (info == null) {
                    return;
                }
                try {
                    String name = info.getServiceName();
                    if (name != null && !name.trim().isEmpty()) {
                        services.add(name.trim());
                    }
                } catch (Throwable ignored) {
                    // a malformed record is not a discovery
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
                // names found stay in the report
            }
        };
    }

    private static NsdManager manager(Context context) {
        if (context == null) {
            return null;
        }
        Object service = context.getSystemService(Context.NSD_SERVICE);
        return service instanceof NsdManager ? (NsdManager) service : null;
    }
}
