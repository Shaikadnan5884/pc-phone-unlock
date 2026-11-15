package com.example.pcunlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import org.json.JSONObject;

public class UnlockService extends Service {
    static final String CH_SVC = "svc", CH_ASK = "ask";

    private static volatile Transport transport;
    public static volatile String state = "Service not running";

    /** Called by ApproveActivity. Returns true only if the message was really sent. */
    public static boolean sendResponse(JSONObject m) {
        Transport t = transport;
        return t != null && t.send(m);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_SVC, "Connection",
                NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_ASK, "Unlock requests",
                NotificationManager.IMPORTANCE_HIGH));

        Notification n = new NotificationCompat.Builder(this, CH_SVC)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("PC Unlock is active")
                .setContentText("Waiting for unlock requests")
                .build();
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);

        try {
            JSONObject cfg = Prefs.load(this);
            if (cfg == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (transport != null) transport.stop();
            String t = cfg.getString("transport");
            if (t.equals("firebase"))
                transport = new FirebaseTransport(cfg.getString("db_url"), cfg.getString("pair_id"));
            else
                transport = new WifiTransport(cfg.getString("ip"), cfg.getInt("port"));

            transport.start(new Transport.Listener() {
                @Override
                public void onMessage(JSONObject msg) {
                    String type = msg.optString("type");
                    if ("CHALLENGE".equals(type)) UnlockService.this.onChallenge(msg);
                    else if ("RESULT".equals(type))
                        StageTracker.onResult(msg.optString("id"), msg.optString("stage"));
                }

                @Override
                public void onState(String s) {
                    state = s;
                    StageTracker.setConnection(s);
                }
            });
        } catch (Exception e) {
            state = "Service error: " + e.getMessage();
            StageTracker.setConnection(state);
            stopSelf();
        }
        return START_STICKY;
    }

    private void onChallenge(JSONObject m) {
        String id = m.optString("id");
        if (!StageTracker.startRequest(id)) return;   // already handling this request

        Intent i = new Intent(this, ApproveActivity.class)
                .putExtra("id", id)
                .putExtra("nonce", m.optString("nonce"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CH_ASK)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Unlock your PC?")
                .setContentText("Tap and confirm with your fingerprint")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(45000)
                .build();
        getSystemService(NotificationManager.class).notify(2, n);
    }

    @Override
    public void onDestroy() {
        if (transport != null) {
            transport.stop();
            transport = null;
        }
        state = "Service not running";
        StageTracker.setConnection(state);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}