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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.replate.app.BuildConfig;
import com.replate.app.MainActivity;
import com.replate.app.R;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.AppNotification;
import com.replate.app.notifications.InAppNotificationCenter;
import com.replate.app.notifications.NotificationCategories;
import com.replate.app.notifications.NotificationRouter;
import com.replate.app.utils.FcmTokenManager;
import com.replate.app.receivers.NotificationActionReceiver;
import com.replate.app.utils.NotificationPrefs;
import com.replate.app.utils.UserRoleHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Cloud Messaging Service for push notifications.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "replate_notifications_v4";
    private static final String CHANNEL_NAME = "REPLATE Alerts";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if (BuildConfig.DEBUG) Log.d(TAG, "onNewToken received");
        FcmTokenManager.persistLatestToken(getApplicationContext(), token);
        syncTokenToActiveUser(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FCM message received from=" + remoteMessage.getFrom());
            Log.d(TAG, "FCM data payload=" + remoteMessage.getData());
            if (remoteMessage.getNotification() != null) {
                Log.d(TAG, "FCM notification title=" + remoteMessage.getNotification().getTitle());
            }
        }

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

        // The server (or checkout) already saved this one in the inbox: do not save a second copy
        boolean alreadySaved = "1".equals(data.remove("serverSaved"));
        String notificationKey = alreadySaved ? null : saveNotification(title, body, data);
        if (notificationKey != null) {
            data.put("notificationKey", notificationKey);
        }

        // Turned off in Settings: keep it in the in-app list but don't pop up or ring
        if (!NotificationPrefs.isAllowed(getApplicationContext(), data.get("category"))) {
            return;
        }

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

        showNotification(title, body, data);
    }

    private void syncTokenToActiveUser(@NonNull String token) {
        if (token.trim().isEmpty()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Skipping token sync because token is blank");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "No authenticated user while syncing token; stored locally.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        String role = UserRoleHelper.fromPrefs(prefs);
        if (BuildConfig.DEBUG) Log.d(TAG, "Syncing refreshed token for uid=" + user.getUid());
        FcmTokenManager.syncTokenForCurrentUser(getApplicationContext(), role);
    }

    /** Saves the notification for the in-app list and returns its Firebase key (null when not signed in). */
    private String saveNotification(String title, String body, Map<String, String> data) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skipping notification persistence: no active user session.");
            return null;
        }

        long timestamp = System.currentTimeMillis();
        String category = NotificationCategories.normalize(value(data, "category", value(data, "type", "general")));

        Map<String, Object> notifData = new HashMap<>();
        notifData.put("title", title);
        notifData.put("body", body);
        notifData.put("timestamp", timestamp);
        notifData.put("read", false);
        notifData.put("type", value(data, "type", "general"));
        notifData.put("category", category);
        notifData.put("route", value(data, "route", "notifications"));
        notifData.put("orderId", value(data, "orderId", ""));
        notifData.put("hotelId", value(data, "hotelId", ""));
        notifData.put("deepLink", value(data, "deepLink", ""));

        // Save to Firebase
        DatabaseReference notificationRef = FirebaseDatabase.getInstance().getReference()
                .child("notifications")
                .child(user.getUid())
                .push();
        notificationRef.setValue(notifData);

        // Also cache to SQLite for instant offline access
        try {
            AppNotification appNotif = new AppNotification();
            appNotif.setNotificationId("push_" + timestamp);
            appNotif.setTitle(title);
            appNotif.setBody(body);
            appNotif.setTimestamp(timestamp);
            appNotif.setRead(false);
            appNotif.setType(value(data, "type", "general"));
            appNotif.setCategory(category);
            appNotif.setRoute(value(data, "route", "notifications"));
            appNotif.setOrderId(value(data, "orderId", ""));
            appNotif.setHotelId(value(data, "hotelId", ""));
            appNotif.setDeepLink(value(data, "deepLink", ""));

            java.util.List<AppNotification> singleList = new java.util.ArrayList<>();
            singleList.add(appNotif);
            ReplateDatabaseHelper.getInstance(getApplicationContext()).cacheNotifications(singleList);
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache notification to SQLite", e);
        }
        return notificationRef.getKey();
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

        int requestCode = (int) System.currentTimeMillis();
        final int systemId = requestCode;
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = null;
        // Screens opened from a notification (order, notifications list) get Home underneath them,
        // so Back lands on the app instead of closing it.
        if (intent.getComponent() != null && intent.getAction() == null
                && !MainActivity.class.getName().equals(intent.getComponent().getClassName())) {
            try {
                pendingIntent = TaskStackBuilder.create(this)
                        .addNextIntentWithParentStack(intent)
                        .getPendingIntent(requestCode, pendingFlags);
            } catch (Exception e) {
                // Target has no declared parent screen; fall back to opening it directly
                Log.w(TAG, "Could not build a back stack for the notification", e);
            }
        }
        if (pendingIntent == null) {
            pendingIntent = PendingIntent.getActivity(this, requestCode, intent, pendingFlags);
        }

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        // 192px brand icon; the old 500px logo cost about 1 MB of memory per notification
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_replate_large);

        // Strip emoji characters so they don't appear in the popup title/body
        String cleanTitle = stripEmoji(title);
        String cleanBody  = stripEmoji(body);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_replate)
                .setLargeIcon(largeIcon)
                .setContentTitle(cleanTitle)
                .setContentText(cleanBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(cleanBody))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(ContextCompat.getColor(this, R.color.primary));

        // "Mark as read" button, handled by NotificationActionReceiver without opening the app
        String notificationKey = data.get("notificationKey");
        if (notificationKey != null && !notificationKey.isEmpty()) {
            Intent markRead = new Intent(this, NotificationActionReceiver.class)
                    .setAction(NotificationActionReceiver.ACTION_MARK_READ)
                    .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_KEY, notificationKey)
                    .putExtra(NotificationActionReceiver.EXTRA_SYSTEM_ID, systemId);
            PendingIntent markReadIntent = PendingIntent.getBroadcast(this, systemId, markRead, pendingFlags);
            builder.addAction(R.drawable.ic_checkmark_white, getString(R.string.notification_mark_read), markReadIntent);
        }

        managerCompat.notify(systemId, builder.build());
    }

    private void createChannelIfNeeded() {
        createChannel(this);
    }

    /** Also called when the app starts, so a push that arrives in the background already has its channel. */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
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
        Intent intent = NotificationRouter.buildIntent(
                this,
                NotificationCategories.normalize(value(data, "category", value(data, "type", "general"))),
                value(data, "route", "notifications"),
                value(data, "orderId", ""),
                value(data, "hotelId", ""),
                value(data, "deepLink", ""),
                data
        );

        intent.putExtra("category", value(data, "category", value(data, "type", "general")));
        intent.putExtra("route", value(data, "route", "notifications"));
        intent.putExtra("orderId", value(data, "orderId", ""));
        intent.putExtra("hotelId", value(data, "hotelId", ""));
        intent.putExtra("deepLink", value(data, "deepLink", ""));
        return intent;
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

    /**
     * Removes emoji and decorative Unicode characters from notification text.
     * Preserves currency symbols (e.g. ₨, €, £) and standard punctuation.
     */
    private String stripEmoji(String input) {
        if (input == null) return "";
        return input.codePoints()
                .filter(cp -> {
                    // Keep ASCII printable characters
                    if (cp <= 0x7F) return true;
                    // Keep Latin Extended, currency symbols, general punctuation
                    if (cp >= 0x00A0 && cp <= 0x024F) return true;
                    // Keep currency symbols block
                    if (cp >= 0x20A0 && cp <= 0x20CF) return true;
                    // Keep general punctuation
                    if (cp >= 0x2000 && cp <= 0x206F) return true;
                    // Block emoji and miscellaneous symbols
                    if (cp >= 0x2600 && cp <= 0x27BF) return false;
                    if (cp >= 0x1F000) return false;
                    // Keep everything else in the BMP that isn't a surrogate
                    return cp <= 0xFFFF && !Character.isSurrogate((char) cp);
                })
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}
