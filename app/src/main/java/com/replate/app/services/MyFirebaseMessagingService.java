package com.replate.app.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.replate.app.NotificationsActivity;
import com.replate.app.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Cloud Messaging Service for push notifications
 * Requirement: Notifications Broadcast
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "replate_notifications";
    private static final String CHANNEL_NAME = "REPLATE Notifications";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        saveTokenToDatabase(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        String title = "REPLATE";
        String body = "You have a new notification";
        Map<String, String> data = remoteMessage.getData();

        // Check for notification payload
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null ?
                    remoteMessage.getNotification().getTitle() : title;
            body = remoteMessage.getNotification().getBody() != null ?
                    remoteMessage.getNotification().getBody() : body;
        }

        // Check for data payload
        if (data.size() > 0) {
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("body")) body = data.get("body");
        }

        // Save notification to Firebase and show local system notification
        saveNotification(title, body, data);
        showNotification(title, body, data);
    }

    private void saveTokenToDatabase(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseDatabase.getInstance().getReference()
                    .child("users")
                    .child(user.getUid())
                    .child("fcmToken")
                    .setValue(token);
        }
    }

    private void saveNotification(String title, String body, Map<String, String> data) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Map<String, Object> notifData = new HashMap<>();
            notifData.put("title", title);
            notifData.put("body", body);
            notifData.put("timestamp", System.currentTimeMillis());
            notifData.put("read", false);
            notifData.put("type", data != null ? data.getOrDefault("type", "general") : "general");
            notifData.put("orderId", data != null ? data.getOrDefault("orderId", "") : "");
            notifData.put("hotelId", data != null ? data.getOrDefault("hotelId", "") : "");

            FirebaseDatabase.getInstance().getReference()
                    .child("notifications")
                    .child(user.getUid())
                    .push()
                    .setValue(notifData);
        }
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        Intent intent = new Intent(this, NotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null && data.containsKey("orderId")) {
            intent.putExtra("orderId", data.get("orderId"));
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setColor(ContextCompat.getColor(this, R.color.primary));

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for orders, deals and updates");
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }
}
