package com.example.pcunlock;

import org.json.JSONObject;

public interface Transport {
    interface Listener {
        void onMessage(JSONObject msg);
        default void onState(String state) {}
    }

    void start(Listener l);

    /** Returns true only if the message was actually handed to the network. */
    boolean send(JSONObject msg);

    void stop();
}