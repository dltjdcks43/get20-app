package com.get20.app;

import java.nio.ByteBuffer;

/** Allocation-free RGBA region scan. Does not OCR or copy the full frame. */
public final class PurpleDetector {
    private boolean latched;
    private static final long MAX_CONFIRM_GAP_NS = 50_000_000L;
    private int candidateCount;
    private long candidateTimestamp, lastTimestamp = Long.MIN_VALUE;
    private final float hueMin, hueMax;

    public PurpleDetector(float hueMin, float hueMax) {
        this.hueMin = hueMin;
        this.hueMax = hueMax;
    }

    public boolean isPurple(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int delta = max - min;
        if (max < 65 || delta < max * 0.35f) return false;
        float hue;
        if (max == r) hue = 60f * (g - b) / delta;
        else if (max == g) hue = 120f + 60f * (b - r) / delta;
        else hue = 240f + 60f * (r - g) / delta;
        if (hue < 0) hue += 360f;
        return hue >= hueMin && hue <= hueMax;
    }

    public float fraction(ByteBuffer rgba, int width, int height, int rowStride,
                          int pixelStride, int cx, int cy, int radius) {
        int matched = 0, total = 0;
        // A disk includes colored pixels around white numerals at the center.
        for (int y = Math.max(0, cy - radius); y <= Math.min(height - 1, cy + radius); y += 2) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(width - 1, cx + radius); x += 2) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy > radius * radius) continue;
                int offset = y * rowStride + x * pixelStride;
                if (offset < 0 || offset + 2 >= rgba.limit()) continue;
                total++;
                if (isPurple(rgba.get(offset) & 255, rgba.get(offset + 1) & 255,
                        rgba.get(offset + 2) & 255)) matched++;
            }
        }
        return total == 0 ? 0f : (float) matched / total;
    }

    /** Two consecutive fresh purple frames confirm the existing rising edge. */
    public synchronized boolean risingEdge(float fraction, long timestamp) {
        if (timestamp <= lastTimestamp) { discardFrame(); return false; }
        lastTimestamp = timestamp;
        if (latched) {
            discardFrame();
            if (fraction < 0.08f) latched = false;
            return false;
        }
        if (fraction < 0.18f) { discardFrame(); return false; }
        if (candidateCount == 0 || timestamp - candidateTimestamp > MAX_CONFIRM_GAP_NS) {
            candidateCount = 1;
            candidateTimestamp = timestamp;
            return false;
        }
        candidateCount++;
        latched = true;
        discardFrame();
        return true;
    }
    /** A discarded frame breaks confirmation, but must not rearm an existing latch. */
    public synchronized void discardFrame() { candidateCount = 0; candidateTimestamp = 0; }
    public synchronized void reset() { latched = false; discardFrame(); lastTimestamp = Long.MIN_VALUE; }
}
