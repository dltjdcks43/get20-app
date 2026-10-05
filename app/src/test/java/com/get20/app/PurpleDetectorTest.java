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
    @Test public void aSingleFrameTriggersWithoutDebounce() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertTrue(d.risingEdge(.9f));
        assertFalse(d.risingEdge(0f));
        assertTrue(d.risingEdge(.9f));
    }
    @Test public void heldPurpleNeverRepeatsAndAbsenceRearms() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertTrue(d.risingEdge(.8f));
        for (int frame = 0; frame < 600; frame++) assertFalse(d.risingEdge(.8f));
        assertFalse(d.risingEdge(0));
        assertTrue(d.risingEdge(.8f));
    }
    @Test public void noiseNearThresholdDoesNotCauseDuplicate() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertTrue(d.risingEdge(.18f));
        assertFalse(d.risingEdge(.17f));
        assertFalse(d.risingEdge(.19f));
        assertFalse(d.risingEdge(.08f));
        assertFalse(d.risingEdge(.18f));
        assertFalse(d.risingEdge(.079f));
        assertTrue(d.risingEdge(.18f));
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
        assertFalse(d.risingEdge(d.fraction(b, w, h, row, 4, 20, 20, 10)));
    }
    @Test public void whiteTextInCircleStillAllowsDetection() {
        PurpleDetector d = new PurpleDetector(260, 315);
        ByteBuffer b = ByteBuffer.allocate(64 * 64 * 4);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int at = (y * 64 + x) * 4;
            boolean white = x >= 28 && x <= 36;
            b.put(at, (byte)(white ? 255 : 151)); b.put(at+1, (byte)(white ? 255 : 54)); b.put(at+2, (byte)(white ? 255 : 220));
        }
        assertTrue(d.risingEdge(d.fraction(b, 64, 64, 256, 4, 32, 32, 24)));
    }
    @Test public void resetStartsNewSession() {
        PurpleDetector d = new PurpleDetector(260, 315);
        assertTrue(d.risingEdge(1)); d.reset(); assertTrue(d.risingEdge(1));
    }
}
