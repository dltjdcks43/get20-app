package com.get20.app;

import org.junit.Test;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class PurpleDetectorTest {
    @Test public void detectsPurpleButNotOtherNumberColors() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertTrue(d.isPurple(151, 54, 220));
        assertTrue(d.isPurple(116, 55, 201));
        for (int[] c : new int[][]{{255,255,255},{0,0,0},{255,0,0},{0,255,0},{0,0,255},{255,255,0},{23,135,124},{55,45,60},{180,170,180}})
            assertFalse(d.isPurple(c[0], c[1], c[2]));
    }
    @Test public void nonPurpleFramesNeverTrigger() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(0f, 1));
        assertFalse(d.risingEdge(.17f, 16_000_001));
    }
    @Test public void singlePurpleFrameDoesNotTrigger() {
        assertFalse(new PurpleDetector(260, 315).risingEdge(.9f, 1));
    }
    @Test public void twoFreshPurpleFramesTrigger() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(.18f, 1));
        assertTrue(d.risingEdge(.18f, 50_000_001));
    }
    @Test public void nonPurpleBreaksConsecutiveConfirmation() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(.9f, 1));
        assertFalse(d.risingEdge(.17f, 16_000_001));
        assertFalse(d.risingEdge(.9f, 32_000_001));
    }
    @Test public void longGapStartsNewCandidate() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(.9f, 1));
        assertFalse(d.risingEdge(.9f, 50_000_002));
        assertTrue(d.risingEdge(.9f, 66_000_002));
    }
    @Test public void heldPurpleNeverRepeats() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(.8f, 1));
        assertTrue(d.risingEdge(.8f, 16_000_001));
        assertFalse(d.risingEdge(.8f, 32_000_001));
        assertFalse(d.risingEdge(.8f, 48_000_001));
    }
    @Test public void hysteresisAndRearmStillNeedTwoNewFrames() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(.18f, 1));
        assertTrue(d.risingEdge(.18f, 16_000_001));
        assertFalse(d.risingEdge(.17f, 32_000_001));
        assertFalse(d.risingEdge(.08f, 48_000_001));
        assertFalse(d.risingEdge(.18f, 64_000_001));
        assertFalse(d.risingEdge(.079f, 80_000_001));
        assertFalse(d.risingEdge(.18f, 96_000_001));
        assertTrue(d.risingEdge(.18f, 112_000_001));
    }
    @Test public void staleFrameClearsCandidateWithoutRearmingLatch() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(1f, 1));d.discardFrame();
        assertFalse(d.risingEdge(1f, 16_000_001));
        assertTrue(d.risingEdge(1f, 32_000_001));d.discardFrame();
        assertFalse(d.risingEdge(1f, 48_000_001));
    }
    @Test public void duplicateAndBackwardTimestampsClearCandidate() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(1f, 10));
        assertFalse(d.risingEdge(1f, 10));
        assertFalse(d.risingEdge(1f, 11));
        assertFalse(d.risingEdge(1f, 9));
        assertFalse(d.risingEdge(1f, 12));
        assertTrue(d.risingEdge(1f, 13));
    }
    @Test public void rowPaddingAndPixelStrideAreRespected() {
        PurpleDetector d = new PurpleDetector(260, 315);
        int w = 40, h = 40, pixel = 8, row = w * pixel + 64;
        ByteBuffer buffer = ByteBuffer.allocate(row * h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int at = y * row + x * pixel;
            buffer.put(at, (byte)151); buffer.put(at+1, (byte)54); buffer.put(at+2, (byte)220);
        }
        assertEquals(1f, d.fraction(buffer, w, h, row, pixel, 20, 20, 10), 0f);
        assertEquals(1f, d.fraction(buffer, w, h, row, pixel, 0, 0, 10), 0f);
    }
    @Test public void purpleOutsideFixedRegionDoesNotTrigger() {
        PurpleDetector d = new PurpleDetector(260, 315);
        int w = 100, h = 100, row = w * 4;
        ByteBuffer b = ByteBuffer.allocate(row * h);
        for (int y = 70; y < h; y++) for (int x = 70; x < w; x++) {
            int at = y * row + x * 4;
            b.put(at, (byte)151); b.put(at+1, (byte)54); b.put(at+2, (byte)220);
        }
        assertEquals(0f, d.fraction(b, w, h, row, 4, 20, 20, 10), 0f);
        assertFalse(d.risingEdge(d.fraction(b, w, h, row, 4, 20, 20, 10), 1));
    }
    @Test public void whiteTextInCircleStillAllowsDetection() {
        PurpleDetector d = new PurpleDetector(260, 315);
        ByteBuffer b = ByteBuffer.allocate(64 * 64 * 4);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int at = (y * 64 + x) * 4;
            boolean white = x >= 28 && x <= 36;
            b.put(at, (byte)(white ? 255 : 151)); b.put(at+1, (byte)(white ? 255 : 54)); b.put(at+2, (byte)(white ? 255 : 220));
        }
        float fraction = d.fraction(b, 64, 64, 256, 4, 32, 32, 24);
        assertFalse(d.risingEdge(fraction, 1));
        assertTrue(d.risingEdge(fraction, 16_000_001));
    }
    @Test public void resetStartsNewSession() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertFalse(d.risingEdge(1, 1)); d.reset();
        assertFalse(d.risingEdge(1, 2));
        assertTrue(d.risingEdge(1, 3)); d.reset();
        assertFalse(d.risingEdge(1, 4));
        assertTrue(d.risingEdge(1, 5));
    }
}
