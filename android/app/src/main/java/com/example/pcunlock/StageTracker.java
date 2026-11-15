package com.example.pcunlock;

import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single source of truth for the unlock stages. Updated only by real events. */
public class StageTracker {
    public static final int PENDING = 0, ACTIVE = 1, DONE = 2, FAILED = 3;
    public static final int CONNECTED = 0, SIGNAL = 1, AUTH = 2, SENT = 3, VERIFIED = 4, UNLOCKED = 5;
    public static final String[] TITLES = {
            "Connected to PC", "Unlock signal received", "Fingerprint authenticated",
            "Response sent to PC", "PC verified signature", "Unlock released"};

    public interface Listener { void onChanged(); }

    private static final int[] state = new int[6];
    private static final String[] detail = {"Not started", "Waiting for a request",
            "", "", "", ""};
    private static final long[] time = new long[6];
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String currentId = "";
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private static final Runnable timeout = () -> {
        synchronized (StageTracker.class) {
            if (state[VERIFIED] == ACTIVE) setLocked(VERIFIED, FAILED, "No reply from PC (timed out)");
            else if (state[UNLOCKED] == ACTIVE) setLocked(UNLOCKED, FAILED, "PC did not confirm release");
        }
        fire();
    };

    public static void addListener(Listener l) { listeners.addIfAbsent(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    public static synchronized int state(int i) { return state[i]; }
    public static synchronized String detail(int i) { return detail[i]; }
    public static synchronized String timeText(int i) {
        return time[i] == 0 ? "" : fmt.format(new Date(time[i]));
    }

    private static void setLocked(int stage, int st, String d) {
        state[stage] = st;
        detail[stage] = d;
        time[stage] = (st == PENDING) ? 0 : System.currentTimeMillis();
    }

    private static void fire() {
        main.post(() -> { for (Listener l : listeners) l.onChanged(); });
    }

    public static synchronized void set(int stage, int st, String d) {
        setLocked(stage, st, d);
        fire();
    }

    /** Stage 1: driven by the real transport state text. */
    public static void setConnection(String s) {
        if (s.startsWith("Connected") || s.startsWith("Polling")) set(CONNECTED, DONE, s);
        else if (s.startsWith("Connecting")) set(CONNECTED, ACTIVE, s);
        else if (s.startsWith("Service not")) set(CONNECTED, PENDING, "Service not running");
        else set(CONNECTED, FAILED, s + " (retrying)");
    }

    /** A new CHALLENGE arrived. Returns false if this request id is already being handled. */
    public static synchronized boolean startRequest(String id) {
        if (id.equals(currentId)) return false;
        currentId = id;
        main.removeCallbacks(timeout);
        for (int i = SIGNAL; i <= UNLOCKED; i++) setLocked(i, PENDING, "");
        setLocked(SIGNAL, DONE, "Request " + (id.length() > 6 ? id.substring(0, 6) : id));
        setLocked(AUTH, ACTIVE, "Touch the fingerprint sensor");
        fire();
        return true;
    }

    public static synchronized void authFailedTry() {
        setLocked(AUTH, ACTIVE, "Fingerprint not recognised, try again");
        fire();
    }

    public static synchronized void authDone() {
        setLocked(AUTH, DONE, "Identity confirmed by fingerprint");
        setLocked(SENT, ACTIVE, "Sending signed response...");
        fire();
    }

    public static synchronized void authFailed(String why) {
        setLocked(AUTH, FAILED, why);
        fire();
    }

    public static synchronized void sent(boolean ok) {
        if (ok) {
            setLocked(SENT, DONE, "Signed response delivered");
            setLocked(VERIFIED, ACTIVE, "Waiting for PC to check the signature");
            main.removeCallbacks(timeout);
            main.postDelayed(timeout, 20000);
        } else {
            setLocked(SENT, FAILED, "Not sent (connection lost)");
        }
        fire();
    }

    /** RESULT message from the PC. */
    public static synchronized void onResult(String id, String stage) {
        if (!id.equals(currentId)) return;
        main.removeCallbacks(timeout);
        if ("verified".equals(stage)) {
            setLocked(VERIFIED, DONE, "Signature is valid");
            setLocked(UNLOCKED, ACTIVE, "Releasing credentials to lock screen");
            main.postDelayed(timeout, 10000);
        } else if ("unlocked".equals(stage)) {
            setLocked(UNLOCKED, DONE, "Credentials released, PC is signing in");
        } else if ("denied".equals(stage)) {
            setLocked(VERIFIED, FAILED, "PC rejected the signature");
        }
        fire();
    }

    /** True when the current request has reached an end (success or failure). */
    public static synchronized boolean isFinished() {
        if (state[SIGNAL] != DONE) return false;
        if (state[UNLOCKED] == DONE) return true;
        for (int i = AUTH; i <= UNLOCKED; i++) if (state[i] == FAILED) return true;
        return false;
    }

    public static synchronized boolean isSuccess() { return state[UNLOCKED] == DONE; }
}