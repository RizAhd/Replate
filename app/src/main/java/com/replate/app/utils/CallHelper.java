package com.replate.app.utils;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Calls a phone number: directly when CALL_PHONE is granted, asking for it at the moment the
 * customer taps Call, and opening the dialer with the number filled in if they refuse.
 * Create it in onCreate (before the activity starts) because it registers a permission launcher.
 */
public final class CallHelper {

    private final ComponentActivity activity;
    private final ActivityResultLauncher<String> permissionLauncher;
    @Nullable
    private String pendingPhone;
    @Nullable
    private Runnable pendingOnUnavailable;

    public CallHelper(@NonNull ComponentActivity activity) {
        this.activity = activity;
        this.permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    String phone = pendingPhone;
                    Runnable onUnavailable = pendingOnUnavailable;
                    pendingPhone = null;
                    pendingOnUnavailable = null;
                    if (phone != null) {
                        dial(phone, granted, onUnavailable);
                    }
                });
    }

    /** @param onUnavailable run when the phone has no app that can place a call (may be null) */
    public void call(@NonNull String phone, @Nullable Runnable onUnavailable) {
        boolean granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            dial(phone, true, onUnavailable);
            return;
        }
        pendingPhone = phone;
        pendingOnUnavailable = onUnavailable;
        permissionLauncher.launch(Manifest.permission.CALL_PHONE);
    }

    private void dial(String phone, boolean canCallDirectly, @Nullable Runnable onUnavailable) {
        try {
            if (canCallDirectly) {
                TelephonyUtils.callNow(activity, phone);
            } else {
                TelephonyUtils.openDialer(activity, phone);
            }
        } catch (SecurityException | ActivityNotFoundException first) {
            // Permission revoked in the meantime, or no direct-call support: try the dialer
            try {
                TelephonyUtils.openDialer(activity, phone);
            } catch (ActivityNotFoundException second) {
                if (onUnavailable != null) onUnavailable.run();
            }
        }
    }
}
