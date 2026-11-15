package com.example.pcunlock;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.nio.ByteBuffer;
import java.security.Signature;
import org.json.JSONObject;

public class ApproveActivity extends AppCompatActivity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean closing = false;
    private TextView result;

    private final StageTracker.Listener listener = () -> {
        if (!StageTracker.isFinished() || closing) return;
        closing = true;
        if (StageTracker.isSuccess()) {
            result.setText("\u2713 Unlocked successfully");
            result.setTextColor(0xFF2E7D32);
        } else {
            result.setText("\u2715 Unlock did not complete");
            result.setTextColor(0xFFC62828);
        }
        h.postDelayed(this::finish, 3500);
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_approve);
        result = findViewById(R.id.result);
        StageTracker.addListener(listener);

        String id = getIntent().getStringExtra("id");
        byte[] nonce = Base64.decode(getIntent().getStringExtra("nonce"), Base64.DEFAULT);

        long ts = ByteBuffer.wrap(nonce, 32, 8).getLong();
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > 300) {
            StageTracker.authFailed("Request expired (check the clocks)");
            return;
        }

        Signature sig;
        try {
            sig = KeyHelper.initSignature();
        } catch (Exception e) {
            StageTracker.authFailed("Key invalid (fingerprints changed?). Pair again.");
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult r) {
                        try {
                            Signature s = r.getCryptoObject().getSignature();
                            s.update(nonce);
                            JSONObject resp = new JSONObject()
                                    .put("type", "RESPONSE")
                                    .put("id", id)
                                    .put("sig", Base64.encodeToString(s.sign(), Base64.NO_WRAP));
                            StageTracker.authDone();
                            StageTracker.sent(UnlockService.sendResponse(resp));
                        } catch (Exception e) {
                            StageTracker.authFailed("Signing failed: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        StageTracker.authFailedTry();
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                        StageTracker.authFailed("Cancelled: " + msg);
                    }
                });

        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock your PC")
                .setSubtitle("Confirm with your fingerprint")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build(), new BiometricPrompt.CryptoObject(sig));
    }

    @Override
    protected void onDestroy() {
        StageTracker.removeListener(listener);
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}