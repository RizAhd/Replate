package com.replate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Locale;

/**
 * Manages FCM token lifecycle and role-based topic subscriptions.
 */
public final class FcmTokenManager {

    private static final String TAG = "FcmTokenManager";
    private static final String PREFS = "REPLATE_PREFS";
    private static final String KEY_LAST_FCM_TOKEN = "last_fcm_token";
    private static final String KEY_LAST_FCM_SYNC_AT = "last_fcm_sync_at";
    private static final String TOPIC_ALL_USERS = "all_users";
    private static final String TOPIC_CUSTOMERS = "customers";
    private static final String TOPIC_HOTELS = "hotels";
    private static final String TOPIC_ADMINS = "admins";
    private static final String TOPIC_DEALS_CUSTOMERS = "deals_customers";

    private FcmTokenManager() {}

    public static void syncTokenForCurrentUser(@NonNull Context context, String role) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        final String normalizedRole = normalizeRole(role);
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().isEmpty()) return;
                    saveTokenInPrefs(context, token);
                    writeTokenToDatabase(user.getUid(), token, normalizedRole);
                    subscribeTopics(normalizedRole);
                })
                .addOnFailureListener(e -> Log.w(TAG, "Unable to fetch FCM token", e));
    }

    public static void clearTokenForCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        String uid = user.getUid();
        root.child("users").child(uid).child("fcmToken").removeValue();
        root.child("users").child(uid).child("notification").child("fcmToken").removeValue();

        root.child("hotels")
                .orderByChild("userId")
                .equalTo(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) return;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        root.child("hotels").child(child.getKey()).child("fcmToken").removeValue();
                        root.child("hotels").child(child.getKey()).child("notification").child("fcmToken").removeValue();
                    }
                });

        unsubscribeAllTopics();
        FirebaseMessaging.getInstance().deleteToken()
                .addOnFailureListener(e -> Log.w(TAG, "Failed deleting FCM token", e));
    }

    public static void subscribeTopics(String role) {
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL_USERS);

        switch (normalizeRole(role)) {
            case "hotel":
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_HOTELS);
                break;
            case "admin":
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ADMINS);
                break;
            default:
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_CUSTOMERS);
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_DEALS_CUSTOMERS);
                break;
        }
    }

    private static void unsubscribeAllTopics() {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_ALL_USERS);
        FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_CUSTOMERS);
        FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_HOTELS);
        FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_ADMINS);
        FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC_DEALS_CUSTOMERS);
    }

    private static void writeTokenToDatabase(String uid, String token, String role) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        long now = System.currentTimeMillis();

        root.child("users").child(uid).child("fcmToken").setValue(token);
        root.child("users").child(uid).child("notification").child("fcmToken").setValue(token);
        root.child("users").child(uid).child("notification").child("role").setValue(role);
        root.child("users").child(uid).child("notification").child("lastTokenUpdateAt").setValue(now);

        if ("hotel".equals(role)) {
            root.child("hotels")
                    .orderByChild("userId")
                    .equalTo(uid)
                    .get()
                    .addOnSuccessListener(snapshot -> updateHotelTokens(root, snapshot, token, now));
        }
    }

    private static void updateHotelTokens(DatabaseReference root, DataSnapshot snapshot, String token, long updatedAt) {
        if (snapshot == null || !snapshot.exists()) return;
        for (DataSnapshot child : snapshot.getChildren()) {
            root.child("hotels").child(child.getKey()).child("fcmToken").setValue(token);
            root.child("hotels").child(child.getKey()).child("notification").child("fcmToken").setValue(token);
            root.child("hotels").child(child.getKey()).child("notification").child("lastTokenUpdateAt").setValue(updatedAt);
        }
    }

    private static void saveTokenInPrefs(Context context, String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAST_FCM_TOKEN, token)
                .putLong(KEY_LAST_FCM_SYNC_AT, System.currentTimeMillis())
                .apply();
    }

    public static String getLastToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_FCM_TOKEN, "");
    }

    public static String normalizeRole(String role) {
        if (role == null) return "customer";
        String normalized = role.trim().toLowerCase(Locale.US);
        return normalized.isEmpty() ? "customer" : normalized;
    }
}
