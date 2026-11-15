package com.example.pcunlock;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String lastMessage = "";

    private final StageTracker.Listener stageListener = this::refresh;

    private final ActivityResultLauncher<ScanOptions> scanner =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) handleQr(result.getContents());
            });

    private final ActivityResultLauncher<String> askPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);

        if (Build.VERSION.SDK_INT >= 33)
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS);

        findViewById(R.id.btnScan).setOnClickListener(v ->
                scanner.launch(new ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the QR code shown on your PC")
                        .setBeepEnabled(false)));

        findViewById(R.id.btnStart).setOnClickListener(v -> {
            if (Prefs.load(this) == null) {
                lastMessage = "Pair first.";
                refresh();
                return;
            }
            ContextCompat.startForegroundService(this, new Intent(this, UnlockService.class));
            refresh();
        });

        findViewById(R.id.btnUnpair).setOnClickListener(v -> {
            stopService(new Intent(this, UnlockService.class));
            KeyHelper.deleteKey();
            Prefs.clear(this);
            lastMessage = "";
            refresh();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        StageTracker.addListener(stageListener);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        StageTracker.removeListener(stageListener);
    }

    private void handleQr(String text) {
        try {
            JSONObject qr = new JSONObject(text);
            lastMessage = "Pairing...";
            refresh();
            Pairing.pair(this, qr, (ok, msg) -> runOnUiThread(() -> {
                lastMessage = msg;
                if (ok)
                    ContextCompat.startForegroundService(this, new Intent(this, UnlockService.class));
                refresh();
            }));
        } catch (Exception e) {
            lastMessage = "Not a PC Unlock QR code.";
            refresh();
        }
    }

    private void refresh() {
        JSONObject cfg = Prefs.load(this);
        StringBuilder sb = new StringBuilder();
        if (cfg == null) {
            sb.append("Not paired");
        } else {
            sb.append("Paired via: ").append(cfg.optString("transport"));
            if (cfg.has("ip")) sb.append("   |   PC: ").append(cfg.optString("ip"))
                    .append(":").append(cfg.optInt("port"));
        }
        if (!lastMessage.isEmpty()) sb.append("\n").append(lastMessage);
        status.setText(sb.toString());
    }
}