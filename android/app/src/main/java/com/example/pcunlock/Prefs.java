package com.example.pcunlock;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

public class Prefs {
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("pcunlock", Context.MODE_PRIVATE);
    }

    public static void save(Context c, JSONObject cfg) {
        sp(c).edit().putString("cfg", cfg.toString()).apply();
    }

    public static JSONObject load(Context c) {
        try {
            String s = sp(c).getString("cfg", null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear(Context c) {
        sp(c).edit().clear().apply();
    }
}