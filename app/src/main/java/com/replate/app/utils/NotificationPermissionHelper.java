package com.replate.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.replate.app.R;

/**
 * Unified Android 13+ notification permission helper.
 */
public final class NotificationPermissionHelper {

    private NotificationPermissionHelper() {
    }

    // True on Android 13 and newer.
    public static boolean isRuntimePermissionRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    // True when notifications are allowed.
    public static boolean hasPermission(@NonNull Context context) {
        if (!isRuntimePermissionRequired()) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // True when we should explain why the permission is needed.
    public static boolean shouldShowRationale(@NonNull Activity activity) {
        return isRuntimePermissionRequired()
                && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS);
    }

    // Asks for the permission using an activity request code.
    public static void request(@NonNull Activity activity, int requestCode) {
        if (!isRuntimePermissionRequired() || hasPermission(activity)) return;
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
    }

    // Asks for the permission using a launcher.
    public static void request(@NonNull ActivityResultLauncher<String> launcher) {
        if (!isRuntimePermissionRequired()) return;
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    // Explains why notifications help, then continues on tap.
    public static void showRationaleDialog(@NonNull Activity activity, @NonNull Runnable onContinue) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.notifications_permission_title))
                .setMessage(activity.getString(R.string.notifications_permission_message))
                .setPositiveButton(activity.getString(R.string.notifications_permission_continue), (d, w) -> onContinue.run())
                .setNegativeButton(activity.getString(R.string.notifications_permission_not_now), null)
                .show();
    }

    // Opens this app's notification settings.
    public static void openAppNotificationSettings(@NonNull Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName())
                .setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
}

