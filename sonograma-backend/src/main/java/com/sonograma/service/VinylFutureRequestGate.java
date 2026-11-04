package com.sonograma.service;

/** Keeps the supplier origin from being flooded by concurrent media probes/downloads. */
final class VinylFutureRequestGate {

    private static final long MIN_INTERVAL_MS = 750L;
    private static long nextRequestAt;

    private VinylFutureRequestGate() {}

    static synchronized void awaitTurn() {
        long waitMillis = nextRequestAt - System.currentTimeMillis();
        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        nextRequestAt = System.currentTimeMillis() + MIN_INTERVAL_MS;
    }
}
