package com.get20.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.WindowManager;

final class Config {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("target", Context.MODE_PRIVATE); }
    static Rect screen(Context c) { return c.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds(); }
    static boolean valid(Context c) {
        Rect r = screen(c);
        SharedPreferences p = prefs(c);
        int x = p.getInt("x", -1), y = p.getInt("y", -1);
        return x >= 0 && y >= 0 && x < r.width() && y < r.height()
                && p.getInt("width", 0) == r.width() && p.getInt("height", 0) == r.height()
                && p.getInt("rotation", -1) == rotation(c);
    }
    @SuppressWarnings("deprecation")
    static int rotation(Context c) { return c.getSystemService(WindowManager.class).getDefaultDisplay().getRotation(); }
}
