package com.replate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;

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

    public interface TokenLookupCallback {
        void onLoaded(@Nullable TokenLookupResult result);
    }

    public static final class TokenLookupResult {
        public final String uid;
        public final String role;
        public final String token;
        public final List<String> cleanupPaths;

        TokenLookupResult(String uid, String role, String token, List<String> cleanupPaths) {
            this.uid = uid;
            this.role = role;
            this.token = token;
            this.cleanupPaths = cleanupPaths;
        }
    }

    public static void syncTokenForCurrentUser(@NonNull Context context, @Nullable String role) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "Skipping token sync because no authenticated user is available yet.");
            return;
        }

        final String normalizedRole = UserRoleHelper.normalize(role);
        FirebaseMessaging.getInstance().setAutoInitEnabled(true);
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().isEmpty()) {
                        Log.w(TAG, "FCM token fetch returned empty token for uid=" + user.getUid());
                        return;
                    }
                    persistLatestToken(context, token);
                    writeTokenToDatabase(user.getUid(), token.trim(), normalizedRole);
                    subscribeTopics(normalizedRole);
                    Log.d(TAG, "FCM token synced for uid=" + user.getUid() + " role=" + normalizedRole);
                })
                .addOnFailureListener(e -> Log.w(TAG, "Unable to fetch FCM token", e));
    }

    public static void persistLatestToken(@NonNull Context context, @NonNull String token) {
        if (token.trim().isEmpty()) return;
        saveTokenInPrefs(context.getApplicationContext(), token.trim());
    }

    public static void clearTokenForCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        String uid = user.getUid();
        clearTokenAtPath(root, "users/" + uid + "/fcmToken");
        clearTokenAtPath(root, "users/" + uid + "/notification/fcmToken");

        root.child("hotels")
                .orderByChild("userId")
                .equalTo(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) return;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String hotelKey = child.getKey();
                        if (hotelKey == null || hotelKey.trim().isEmpty()) continue;
                        clearTokenAtPath(root, "hotels/" + hotelKey + "/fcmToken");
                        clearTokenAtPath(root, "hotels/" + hotelKey + "/notification/fcmToken");
                    }
                });

        unsubscribeAllTopics();
        FirebaseMessaging.getInstance().deleteToken()
                .addOnFailureListener(e -> Log.w(TAG, "Failed deleting FCM token", e));
    }

    public static void fetchReceiverToken(@NonNull String uid, @NonNull TokenLookupCallback callback) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        root.child("users").child(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onLoaded(null);
                        return;
                    }

                    String role = UserRoleHelper.fromSnapshot(snapshot);
                    String userToken = safeString(snapshot.child("fcmToken").getValue(String.class));
                    String notifToken = safeString(snapshot.child("notification").child("fcmToken").getValue(String.class));
                    List<String> cleanupPaths = new ArrayList<>();
                    cleanupPaths.add("users/" + uid + "/fcmToken");
                    cleanupPaths.add("users/" + uid + "/notification/fcmToken");

                    if (UserRoleHelper.isHotel(role)) {
                        fetchHotelToken(root, uid, role, userToken, notifToken, cleanupPaths, callback);
                        return;
                    }

                    String resolvedToken = !userToken.isEmpty() ? userToken : notifToken;
                    callback.onLoaded(new TokenLookupResult(uid, role, resolvedToken, cleanupPaths));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to read receiver token for uid=" + uid, e);
                    callback.onLoaded(null);
                });
    }

    public static void clearStaleToken(@NonNull TokenLookupResult result) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        for (String path : result.cleanupPaths) {
            clearTokenAtPath(root, path);
        }
    }

    public static void subscribeTopics(@Nullable String role) {
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL_USERS);

        switch (UserRoleHelper.normalize(role)) {
            case UserRoleHelper.ROLE_HOTEL:
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_HOTELS);
                break;
            case UserRoleHelper.ROLE_ADMIN:
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

    private static void writeTokenToDatabase(@NonNull String uid, @NonNull String token, @Nullable String role) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        long now = System.currentTimeMillis();
        String normalizedRole = UserRoleHelper.normalize(role);

        root.child("users").child(uid).child("fcmToken").setValue(token);
        root.child("users").child(uid).child("notification").child("fcmToken").setValue(token);
        root.child("users").child(uid).child("notification").child("role").setValue(normalizedRole);
        root.child("users").child(uid).child("notification").child("lastTokenUpdateAt").setValue(now);

        if (UserRoleHelper.isHotel(normalizedRole)) {
            root.child("hotels")
                    .orderByChild("userId")
                    .equalTo(uid)
                    .get()
                    .addOnSuccessListener(snapshot -> updateHotelTokens(root, snapshot, token, now));
        }
    }

    private static void fetchHotelToken(DatabaseReference root,
                                        String uid,
                                        String role,
                                        String userToken,
                                        String notifToken,
                                        List<String> cleanupPaths,
                                        TokenLookupCallback callback) {
        root.child("hotels")
                .orderByChild("userId")
                .equalTo(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String resolvedToken = !userToken.isEmpty() ? userToken : notifToken;
                    if (snapshot != null && snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String hotelKey = child.getKey();
                            if (hotelKey == null || hotelKey.trim().isEmpty()) continue;
                            cleanupPaths.add("hotels/" + hotelKey + "/fcmToken");
                            cleanupPaths.add("hotels/" + hotelKey + "/notification/fcmToken");

                            String hotelToken = safeString(child.child("fcmToken").getValue(String.class));
                            if (resolvedToken.isEmpty()) {
                                resolvedToken = !hotelToken.isEmpty()
                                        ? hotelToken
                                        : safeString(child.child("notification").child("fcmToken").getValue(String.class));
                            }
                        }
                    }
                    callback.onLoaded(new TokenLookupResult(uid, role, resolvedToken, cleanupPaths));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to resolve hotel token for uid=" + uid, e);
                    callback.onLoaded(new TokenLookupResult(uid, role, !userToken.isEmpty() ? userToken : notifToken, cleanupPaths));
                });
    }

    private static void updateHotelTokens(DatabaseReference root, DataSnapshot snapshot, String token, long updatedAt) {
        if (snapshot == null || !snapshot.exists()) return;
        for (DataSnapshot child : snapshot.getChildren()) {
            String hotelKey = child.getKey();
            if (hotelKey == null || hotelKey.trim().isEmpty()) continue;
            root.child("hotels").child(hotelKey).child("fcmToken").setValue(token);
            root.child("hotels").child(hotelKey).child("notification").child("fcmToken").setValue(token);
            root.child("hotels").child(hotelKey).child("notification").child("lastTokenUpdateAt").setValue(updatedAt);
        }
    }

    private static void clearTokenAtPath(DatabaseReference root, String path) {
        if (path == null || path.trim().isEmpty()) return;
        root.child(path).removeValue()
                .addOnFailureListener(e -> Log.w(TAG, "Failed clearing stale token path: " + path, e));
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

    private static String safeString(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
