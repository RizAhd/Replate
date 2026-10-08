package com.replate.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Handles the "Mark as read" button on a push notification: marks that notification read in
 * Firebase and removes it from the notification shade, without opening the app.
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_MARK_READ = "com.replate.app.action.MARK_NOTIFICATION_READ";
    public static final String EXTRA_NOTIFICATION_KEY = "notification_key";
    public static final String EXTRA_SYSTEM_ID = "system_notification_id";

    // Clears the notification and marks it read in Firebase.
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_MARK_READ.equals(intent.getAction())) {
            return;
        }

        int systemId = intent.getIntExtra(EXTRA_SYSTEM_ID, 0);
        NotificationManagerCompat.from(context).cancel(systemId);

        String key = intent.getStringExtra(EXTRA_NOTIFICATION_KEY);
        String uid = FirebaseAuth.getInstance().getUid();
        if (key == null || key.isEmpty() || uid == null) {
            return;
        }

        // Keep the process alive until the write is acknowledged
        final PendingResult pending = goAsync();
        FirebaseDatabase.getInstance().getReference("notifications")
                .child(uid).child(key).child("read").setValue(true)
                .addOnCompleteListener(task -> pending.finish());
    }
}
