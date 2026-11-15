package com.example.pcunlock;

import android.content.Context;
import android.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

public class Pairing {
    public interface Callback {
        void done(boolean ok, String message);
    }

    public static void pair(Context ctx, JSONObject qr, Callback cb) {
        new Thread(() -> {
            try {
                String t = qr.getString("transport");
                if (!t.equals("wifi") && !t.equals("hotspot") && !t.equals("firebase")) {
                    cb.done(false, "Bluetooth is not added yet");
                    return;
                }
                byte[] pub = KeyHelper.createKey();
                JSONObject req = new JSONObject()
                        .put("type", "PAIR_REQUEST")
                        .put("token", qr.getString("token"))
                        .put("phone_pubkey", Base64.encodeToString(pub, Base64.NO_WRAP));

                boolean ok = t.equals("firebase") ? pairFirebase(qr, req) : pairWifi(qr, req);

                if (ok) Prefs.save(ctx, qr);
                else KeyHelper.deleteKey();
                cb.done(ok, ok ? "Paired successfully!"
                        : "PC rejected or did not answer (token expired? wrong IP? firewall?)");
            } catch (Exception e) {
                KeyHelper.deleteKey();
                cb.done(false, "Error: " + e.getMessage());
            }
        }).start();
    }

    private static boolean pairWifi(JSONObject qr, JSONObject req) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] ok = {false};
        String url = "ws://" + qr.getString("ip") + ":" + qr.getInt("port");
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS).build();
        WebSocket ws = client.newWebSocket(new Request.Builder().url(url).build(),
                new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket w, Response r) {
                        w.send(req.toString());
                    }

                    @Override
                    public void onMessage(WebSocket w, String text) {
                        try {
                            ok[0] = new JSONObject(text).optBoolean("ok");
                        } catch (Exception ignored) {
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(WebSocket w, Throwable t, Response r) {
                        latch.countDown();
                    }
                });
        latch.await(10, TimeUnit.SECONDS);
        ws.close(1000, null);
        return ok[0];
    }

    private static boolean pairFirebase(JSONObject qr, JSONObject req) throws Exception {
        String base = FirebaseTransport.base(qr.getString("db_url"), qr.getString("pair_id"));
        FirebaseTransport.put(base, "to_pc", req);
        long end = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < end) {
            JSONObject m = FirebaseTransport.take(base, "to_phone");
            if (m != null && "PAIR_RESULT".equals(m.optString("type"))) return m.optBoolean("ok");
            Thread.sleep(1000);
        }
        return false;
    }
}