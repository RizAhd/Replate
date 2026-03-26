package com.replate.app.services;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.replate.app.R;
import com.replate.app.notifications.InAppNotificationCenter;
import com.replate.app.notifications.NotificationCategories;
import com.replate.app.notifications.NotificationRouter;
import com.replate.app.utils.FcmTokenManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Cloud Messaging Service for push notifications.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "replate_notifications_v2";
    private static final String CHANNEL_NAME = "REPLATE Alerts";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        saveTokenToDatabase(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "REPLATE";
        String body = "You have a new notification";
        Map<String, String> data = new HashMap<>(remoteMessage.getData());

        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        if (data.containsKey("title")) title = data.get("title");
        if (data.containsKey("body")) body = data.get("body");
        if (data.containsKey("category")) {
            String category = NotificationCategories.normalize(value(data, "category", value(data, "type", "general")));
            data.put("category", category);
        } else {
            String category = NotificationCategories.normalize(value(data, "type", "general"));
            data.put("category", category);
        }

        saveNotification(title, body, data);

        if (isAppInForeground()) {
            InAppNotificationCenter.publish(new InAppNotificationCenter.InAppNotificationMessage(
                    title,
                    body,
                    data.get("category"),
                    value(data, "route", "notifications"),
                    value(data, "orderId", ""),
                    value(data, "hotelId", ""),
                    value(data, "deepLink", ""),
                    data
            ));
        }

        // Always attempt a system notification so users get heads-up popups too.
        showNotification(title, body, data);
    }

    private void saveTokenToDatabase(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseDatabase.getInstance().getReference()
                .child("users")
                .child(user.getUid())
                .child("fcmToken")
                .setValue(token);

        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        String role = prefs.getString("role", "customer");
        FcmTokenManager.syncTokenForCurrentUser(getApplicationContext(), role);
    }

    private void saveNotification(String title, String body, Map<String, String> data) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> notifData = new HashMap<>();
        notifData.put("title", title);
        notifData.put("body", body);
        notifData.put("timestamp", System.currentTimeMillis());
        notifData.put("read", false);
        notifData.put("type", value(data, "type", "general"));
        notifData.put("category", NotificationCategories.normalize(value(data, "category", value(data, "type", "general"))));
        notifData.put("route", value(data, "route", "notifications"));
        notifData.put("orderId", value(data, "orderId", ""));
        notifData.put("hotelId", value(data, "hotelId", ""));
        notifData.put("deepLink", value(data, "deepLink", ""));

        FirebaseDatabase.getInstance().getReference()
                .child("notifications")
                .child(user.getUid())
                .push()
                .setValue(notifData);
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        createChannelIfNeeded();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; popup skipped");
            return;
        }

        NotificationManagerCompat managerCompat = NotificationManagerCompat.from(this);
        if (!managerCompat.areNotificationsEnabled()) {
            Log.w(TAG, "App notifications disabled in system settings");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
                if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    Log.w(TAG, "Notification channel is blocked by user: " + CHANNEL_ID);
                    return;
                }
            }
        }

        Intent intent = buildIntentForNotification(data);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(ContextCompat.getColor(this, R.color.primary));

        managerCompat.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Order, deal and pickup alerts");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setShowBadge(true);
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private Intent buildIntentForNotification(Map<String, String> data) {
        return NotificationRouter.buildIntent(
                this,
                NotificationCategories.normalize(value(data, "category", value(data, "type", "general"))),
                value(data, "route", "notifications"),
                value(data, "orderId", ""),
                value(data, "hotelId", ""),
                value(data, "deepLink", ""),
                data
        );
    }

    private boolean isAppInForeground() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;

        java.util.List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
        if (processes == null) return false;

        String packageName = getPackageName();
        for (ActivityManager.RunningAppProcessInfo processInfo : processes) {
            if (processInfo == null) continue;
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && packageName.equals(processInfo.processName)) {
                return true;
            }
        }
        return false;
    }

    private String value(Map<String, String> map, String key, String fallback) {
        if (map == null) return fallback;
        String value = map.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
