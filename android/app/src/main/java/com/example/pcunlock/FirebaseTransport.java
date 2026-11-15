package com.example.pcunlock;

import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

public class FirebaseTransport implements Transport {
    static final MediaType JSON = MediaType.get("application/json");
    static final OkHttpClient http = new OkHttpClient();

    public static String base(String dbUrl, String pairId) {
        return dbUrl.replaceAll("/+$", "") + "/requests/" + pairId;
    }

    public static void put(String base, String slot, JSONObject m) throws IOException {
        Request r = new Request.Builder().url(base + "/" + slot + ".json")
                .put(RequestBody.create(m.toString(), JSON)).build();
        try (Response resp = http.newCall(r).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
        }
    }

    /** Read and delete a mailbox slot. Returns null if empty. */
    public static JSONObject take(String base, String slot) throws Exception {
        String url = base + "/" + slot + ".json";
        String body;
        try (Response r = http.newCall(new Request.Builder().url(url).build()).execute()) {
            if (!r.isSuccessful()) throw new IOException("HTTP " + r.code());
            body = r.body().string();
        }
        if (body == null || body.isEmpty() || body.equals("null")) return null;
        http.newCall(new Request.Builder().url(url).delete().build()).execute().close();
        return new JSONObject(body);
    }

    private final String base;
    private volatile boolean running;

    public FirebaseTransport(String dbUrl, String pairId) {
        base = base(dbUrl, pairId);
    }

    @Override
    public void start(Listener l) {
        running = true;
        new Thread(() -> {
            l.onState("Polling Firebase");
            while (running) {
                try {
                    JSONObject m = take(base, "to_phone");
                    if (m != null) l.onMessage(m);
                } catch (Exception e) {
                    l.onState("Firebase error: " + e.getMessage());
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }).start();
    }

    @Override
    public boolean send(JSONObject m) {
        // Network calls cannot run on the main thread, so send in the background.
        new Thread(() -> {
            try {
                put(base, "to_pc", m);
            } catch (Exception ignored) {
            }
        }).start();
        return true;
    }

    @Override
    public void stop() {
        running = false;
    }
}