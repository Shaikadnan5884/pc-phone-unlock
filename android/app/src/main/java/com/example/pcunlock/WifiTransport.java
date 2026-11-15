package com.example.pcunlock;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

public class WifiTransport implements Transport {
    private final String url;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(10, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    private WebSocket ws;
    private Listener listener;
    private volatile boolean running;
    private volatile boolean connected;
    private volatile int generation = 0;   // identifies the newest connection attempt

    public WifiTransport(String ip, int port) {
        url = "ws://" + ip + ":" + port;
    }

    @Override
    public void start(Listener l) {
        listener = l;
        running = true;
        connect();
    }

    private synchronized void connect() {
        if (!running) return;
        final int gen = ++generation;          // older sockets become "stale"
        if (ws != null) ws.cancel();
        listener.onState("Connecting to " + url);

        ws = client.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket w, Response r) {
                if (gen != generation) return;
                connected = true;
                listener.onState("Connected to PC");
            }

            @Override
            public void onMessage(WebSocket w, String text) {
                if (gen != generation) return;
                try {
                    listener.onMessage(new JSONObject(text));
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onFailure(WebSocket w, Throwable t, Response r) {
                if (gen != generation) return;  // ignore stale sockets
                connected = false;
                listener.onState("Disconnected: " + t.getMessage());
                retry();
            }

            @Override
            public void onClosed(WebSocket w, int code, String reason) {
                if (gen != generation) return;
                connected = false;
                listener.onState("Disconnected (closed)");
                retry();
            }
        });
    }

    private void retry() {
        if (running) main.postDelayed(this::connect, 3000);
    }

    @Override
    public boolean send(JSONObject m) {
        WebSocket w = ws;
        return connected && w != null && w.send(m.toString());
    }

    @Override
    public void stop() {
        running = false;
        connected = false;
        generation++;                 // invalidate all callbacks
        main.removeCallbacksAndMessages(null);
        if (ws != null) ws.cancel();
    }
}