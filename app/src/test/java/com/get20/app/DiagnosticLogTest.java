package com.get20.app;

import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import static org.junit.Assert.*;

public class DiagnosticLogTest {
    private void set(String name, Object value) throws Exception {
        Field field = DiagnosticLog.class.getDeclaredField(name);
        field.setAccessible(true); field.set(null, value);
    }
    @Before public void resetMemoryOnly() throws Exception {
        set("session", true); set("next", 0); set("count", 0); set("sequence", 0L);
        set("startNs", 1000000L); set("startWall", 0L);
        Field field = DiagnosticLog.class.getDeclaredField("events"); field.setAccessible(true);
        ((ArrayDeque<?>) field.get(null)).clear();
    }
    @Test public void frameRingKeepsNewestFramesInOrder() {
        for (int i = 1; i <= 2000; i++) DiagnosticLog.frame(1000000L + i * 1000L, 2000, 1000, .25f, 0);
        String csv = DiagnosticLog.snapshot().split("recorded_us\n")[1];
        String[] lines = csv.split("\n");
        assertEquals(1800, lines.length);
        assertTrue(lines[0].startsWith("201,201,2,1,2500,0,"));
        assertTrue(lines[1799].startsWith("2000,2000,2,1,2500,0,"));
    }
    @Test public void eventHistoryIsBoundedAndPreservesEndResult() {
        for (int i = 0; i < 300; i++) DiagnosticLog.event("event=" + i + ";");
        String log = DiagnosticLog.snapshot();
        assertFalse(log.contains("event=59;"));
        assertTrue(log.contains("event=60;"));
        assertTrue(log.contains("event=299;"));
    }
    @Test public void discardedFrameIsDistinguishableFromZeroPurple() {
        DiagnosticLog.frame(2000000L, 50000000, 0, -1f, 1);
        DiagnosticLog.frame(3000000L, 1000000, 10000, 0f, 0);
        String log = DiagnosticLog.snapshot();
        assertTrue(log.contains("1,1000,50000,0,-10000,1,"));
        assertTrue(log.contains("2,2000,1000,10,0,0,"));
    }
}
