package net.local.picocapture;

import java.util.function.LongSupplier;

/** Bounded, monotonic two-second window. Counts payload written, not acknowledged traffic. */
final class ConnectionMetrics {
    private static final int BUCKETS = 8;
    private static final long BUCKET_MS = 250;
    private final long[] ticks = new long[BUCKETS], encoded = new long[BUCKETS], sent = new long[BUCKETS];
    private final LongSupplier clock;
    private final long started;
    private long resets, discarded;

    ConnectionMetrics() { this(() -> System.nanoTime() / 1000000); }
    ConnectionMetrics(LongSupplier clock) {
        this.clock = clock;
        started = clock.getAsLong();
        java.util.Arrays.fill(ticks, Long.MIN_VALUE);
    }
    synchronized void encoded(int bytes) { add(bytes, false); }
    synchronized void sent(int bytes) { add(bytes, true); }
    private void add(int bytes, boolean network) {
        if (bytes <= 0) return;
        long tick = clock.getAsLong() / BUCKET_MS;
        int slot = (int) (tick % BUCKETS);
        if (ticks[slot] != tick) { ticks[slot] = tick; encoded[slot] = 0; sent[slot] = 0; }
        if (network) sent[slot] += bytes; else encoded[slot] += bytes;
    }
    synchronized void queueReset(int chunks) { resets++; discarded += Math.max(0, chunks); }
    synchronized Snapshot snapshot() {
        long now = clock.getAsLong(), tick = now / BUCKET_MS, video = 0, network = 0;
        for (int i = 0; i < BUCKETS; i++) if (ticks[i] <= tick && ticks[i] > tick - BUCKETS) {
            video += encoded[i]; network += sent[i];
        }
        double elapsed = Math.max(1, Math.min(BUCKETS * BUCKET_MS, now - started));
        return new Snapshot(video * 8 / (elapsed * 1000), network * 8 / (elapsed * 1000), resets, discarded);
    }
    static final class Snapshot {
        final double videoMbps, sentMbps;
        final long resets, discarded;
        Snapshot(double videoMbps, double sentMbps, long resets, long discarded) {
            this.videoMbps = videoMbps; this.sentMbps = sentMbps; this.resets = resets; this.discarded = discarded;
        }
    }
}
