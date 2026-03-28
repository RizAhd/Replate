# REPLATE Firebase Cloud Messaging Implementation Guide

## Checklist
- [x] Firebase Messaging dependency setup
- [x] Android 13+ notification permission flow
- [x] `FirebaseMessagingService`
- [x] `onNewToken()`
- [x] `onMessageReceived()`
- [x] Save token to `users/{uid}/fcmToken`
- [x] Save timestamp to `users/{uid}/notification/lastTokenUpdateAt`
- [x] Local notification display
- [x] Notification channel
- [x] Open correct screen when tapped
- [x] Server-side secure sending via Firebase Admin SDK / Cloud Functions
- [x] Role flows: customer→hotel, hotel→customer, admin→hotel
- [x] Invalid/stale token cleanup
- [x] Settings screen token refresh + self-test push tools

## Verified build status
- Android Java compile: `:app:compileDebugJavaWithJavac` passed during validation
- Android debug assemble: `:app:assembleDebug` passed during validation
- Cloud Functions syntax: `node --check firebase-functions/index.js` passed

---

## Firebase Realtime Database paths used

### Token paths
- `users/{uid}/fcmToken`
- `users/{uid}/notification/fcmToken`
- `users/{uid}/notification/role`
- `users/{uid}/notification/lastTokenUpdateAt`

### Hotel token mirror paths
- `hotels/{hotelId}/fcmToken`
- `hotels/{hotelId}/notification/fcmToken`
- `hotels/{hotelId}/notification/lastTokenUpdateAt`

### Notification inbox history
- `notifications/{uid}/{notificationId}`

### Trigger source paths
- `orders/{orderId}`
- `hotels/{hotelId}`

---

# 1) Android client setup

## File: `app/build.gradle`
Already correct. Keep these lines present:

```groovy
plugins {
    id 'com.android.application'
    id 'com.google.gms.google-services'
}

dependencies {
    implementation platform('com.google.firebase:firebase-bom:32.3.1')
    implementation 'com.google.firebase:firebase-auth'
    implementation 'com.google.firebase:firebase-database'
    implementation 'com.google.firebase:firebase-storage'
    implementation 'com.google.firebase:firebase-messaging'

    implementation 'androidx.lifecycle:lifecycle-process:2.7.0'
}
```

---

## File: `app/src/main/AndroidManifest.xml`
Already correct. Keep these lines present:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".services.MyFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>

<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="replate_notifications_v3" />

<meta-data
    android:name="com.google.firebase.messaging.default_notification_icon"
    android:resource="@drawable/ic_notification" />

<meta-data
    android:name="com.google.firebase.messaging.default_notification_color"
    android:resource="@color/primary" />
```

---

# 2) Role normalization helper

## File: `app/src/main/java/com/replate/app/utils/UserRoleHelper.java`

```java
package com.replate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;

import java.util.Locale;

/**
 * Central place for role normalization and legacy role/userType fallback handling.
 */
public final class UserRoleHelper {

    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_HOTEL = "hotel";
    public static final String ROLE_ADMIN = "admin";

    private static final String PREFS = "REPLATE_PREFS";

    private UserRoleHelper() {
    }

    @NonNull
    public static String normalize(@Nullable String rawRole) {
        if (rawRole == null) return ROLE_CUSTOMER;
        String normalized = rawRole.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) return ROLE_CUSTOMER;
        if (ROLE_HOTEL.equals(normalized) || ROLE_ADMIN.equals(normalized) || ROLE_CUSTOMER.equals(normalized)) {
            return normalized;
        }
        if (normalized.contains("hotel")) return ROLE_HOTEL;
        if (normalized.contains("admin")) return ROLE_ADMIN;
        return ROLE_CUSTOMER;
    }

    @NonNull
    public static String fromPrefs(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return fromPrefs(prefs);
    }

    @NonNull
    public static String fromPrefs(@Nullable SharedPreferences prefs) {
        if (prefs == null) return ROLE_CUSTOMER;
        String role = prefs.getString("role", null);
        String userType = prefs.getString("userType", null);
        return !isBlank(role) ? normalize(role) : normalize(userType);
    }

    @NonNull
    public static String fromSnapshot(@Nullable DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return ROLE_CUSTOMER;
        String role = snapshot.child("role").getValue(String.class);
        String userType = snapshot.child("userType").getValue(String.class);
        return !isBlank(role) ? normalize(role) : normalize(userType);
    }

    public static boolean isHotel(@Nullable String role) {
        return ROLE_HOTEL.equals(normalize(role));
    }

    public static boolean isAdmin(@Nullable String role) {
        return ROLE_ADMIN.equals(normalize(role));
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
```

---

# 3) Android 13+ permission helper

## File: `app/src/main/java/com/replate/app/utils/NotificationPermissionHelper.java`

```java
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
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.replate.app.R;

/**
 * Unified Android 13+ notification permission helper.
 */
public final class NotificationPermissionHelper {

    private NotificationPermissionHelper() {
    }

    public static boolean isRuntimePermissionRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    public static boolean hasPermission(@NonNull Context context) {
        if (!isRuntimePermissionRequired()) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean shouldShowRationale(@NonNull Activity activity) {
        return isRuntimePermissionRequired()
                && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS);
    }

    public static void request(@NonNull Activity activity, int requestCode) {
        if (!isRuntimePermissionRequired() || hasPermission(activity)) return;
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
    }

    public static void request(@NonNull ActivityResultLauncher<String> launcher) {
        if (!isRuntimePermissionRequired()) return;
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    public static void showRationaleDialog(@NonNull Activity activity, @NonNull Runnable onContinue) {
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.notifications_permission_title))
                .setMessage(activity.getString(R.string.notifications_permission_message))
                .setPositiveButton(activity.getString(R.string.notifications_permission_continue), (d, w) -> onContinue.run())
                .setNegativeButton(activity.getString(R.string.notifications_permission_not_now), null)
                .show();
    }

    public static void openAppNotificationSettings(@NonNull Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName())
                .setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
}
```

---

# 4) Notification categories

## File: `app/src/main/java/com/replate/app/notifications/NotificationCategories.java`

```java
package com.replate.app.notifications;

import java.util.Locale;

public final class NotificationCategories {
    public static final String WELCOME = "welcome";
    public static final String NEW_DEALS = "new_deals";
    public static final String HOT_DEALS = "hot_deals";
    public static final String ORDER_PLACED = "order_placed";
    public static final String ORDER_ACCEPTED = "order_accepted";
    public static final String ORDER_REJECTED = "order_rejected";
    public static final String ORDER_CANCELLED = "order_cancelled";
    public static final String PICKUP_READY = "pickup_ready";
    public static final String PICKUP_REMINDER = "pickup_reminder";
    public static final String ADMIN_ANNOUNCEMENT = "admin_announcement";
    public static final String ADMIN_BROADCAST = "admin_broadcast";
    public static final String HOTEL_NEW_ORDER = "hotel_new_order";
    public static final String HOTEL_APPROVED = "hotel_approved";
    public static final String PROFILE_UPDATED = "profile_updated";
    public static final String LOGIN_SUCCESS = "login_success";
    public static final String GENERAL = "general";

    private NotificationCategories() {}

    public static String normalize(String raw) {
        String category = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (category.isEmpty()) return GENERAL;

        switch (category) {
            case WELCOME:
            case NEW_DEALS:
            case HOT_DEALS:
            case ORDER_PLACED:
            case ORDER_ACCEPTED:
            case ORDER_REJECTED:
            case ORDER_CANCELLED:
            case PICKUP_READY:
            case PICKUP_REMINDER:
            case ADMIN_ANNOUNCEMENT:
            case ADMIN_BROADCAST:
            case HOTEL_NEW_ORDER:
            case HOTEL_APPROVED:
            case PROFILE_UPDATED:
            case LOGIN_SUCCESS:
                return category;
            default:
                if (category.contains("deal")) return category.contains("hot") ? HOT_DEALS : NEW_DEALS;
                if (category.contains("reject")) return ORDER_REJECTED;
                if (category.contains("cancel")) return ORDER_CANCELLED;
                if (category.contains("accept")) return ORDER_ACCEPTED;
                if (category.contains("pickup") && category.contains("remind")) return PICKUP_REMINDER;
                if (category.contains("pickup") || category.contains("ready")) return PICKUP_READY;
                if (category.contains("approved") && category.contains("hotel")) return HOTEL_APPROVED;
                if (category.contains("new_order") || category.contains("hotel")) return HOTEL_NEW_ORDER;
                if (category.contains("place") || category.contains("create") || category.contains("order")) return ORDER_PLACED;
                if (category.contains("announce") || category.contains("broadcast") || category.contains("admin")) {
                    return ADMIN_ANNOUNCEMENT;
                }
                if (category.contains("profile")) return PROFILE_UPDATED;
                if (category.contains("login")) return LOGIN_SUCCESS;
                if (category.contains("welcome")) return WELCOME;
                return GENERAL;
        }
    }
}
```

---

# 5) Deep-link / tap routing

## File: `app/src/main/java/com/replate/app/notifications/NotificationRouter.java`

```java
package com.replate.app.notifications;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.replate.app.MainActivity;
import com.replate.app.NotificationsActivity;
import com.replate.app.OrderDetailActivity;

import java.util.Locale;
import java.util.Map;

public final class NotificationRouter {

    private NotificationRouter() {}

    public static Intent buildIntent(Context context,
                                     String category,
                                     String route,
                                     String orderId,
                                     String hotelId,
                                     String deepLink,
                                     Map<String, String> data) {
        String normalizedCategory = NotificationCategories.normalize(category);
        String normalizedRoute = route == null ? "notifications" : route.trim().toLowerCase(Locale.US);
        String safeOrderId = orderId == null ? "" : orderId.trim();
        String safeHotelId = hotelId == null ? "" : hotelId.trim();
        String safeDeepLink = deepLink == null ? "" : deepLink.trim();

        if (NotificationCategories.HOTEL_APPROVED.equals(normalizedCategory)
                || normalizedRoute.contains("hotel_dashboard")
                || normalizedRoute.contains("approval")) {
            Intent dashboardIntent = tryHotelDashboardIntent(context);
            return applyCommonExtras(dashboardIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (NotificationCategories.PICKUP_READY.equals(normalizedCategory)
                || NotificationCategories.PICKUP_REMINDER.equals(normalizedCategory)
                || normalizedRoute.contains("map")
                || normalizedRoute.contains("pickup")) {
            Intent mapIntent = new Intent(context, MainActivity.class);
            mapIntent.putExtra("open_tab", "map");
            if (!safeHotelId.isEmpty()) {
                mapIntent.putExtra("focus_hotel_id", safeHotelId);
            }
            if (!safeOrderId.isEmpty()) {
                mapIntent.putExtra("orderId", safeOrderId);
            }
            return applyCommonExtras(mapIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (isOrderCategory(normalizedCategory) || normalizedRoute.contains("order")) {
            if (NotificationCategories.HOTEL_NEW_ORDER.equals(normalizedCategory)) {
                Intent hotelOrders = tryHotelOrdersIntent(context);
                return applyCommonExtras(hotelOrders, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
            }

            Intent orderIntent = new Intent(context, OrderDetailActivity.class);
            if (!safeOrderId.isEmpty()) {
                orderIntent.putExtra("orderId", safeOrderId);
            }
            return applyCommonExtras(orderIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (NotificationCategories.NEW_DEALS.equals(normalizedCategory)
                || NotificationCategories.HOT_DEALS.equals(normalizedCategory)
                || normalizedRoute.contains("deal")
                || normalizedRoute.contains("home")) {
            Intent homeIntent = new Intent(context, MainActivity.class);
            homeIntent.putExtra("open_tab", "home");
            return applyCommonExtras(homeIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (!safeDeepLink.isEmpty()) {
            try {
                Intent deepLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeDeepLink));
                return applyCommonExtras(deepLinkIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
            } catch (Exception ignored) {
            }
        }

        Intent fallback = new Intent(context, NotificationsActivity.class);
        return applyCommonExtras(fallback, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
    }

    private static Intent applyCommonExtras(Intent intent,
                                            String category,
                                            String route,
                                            String orderId,
                                            String hotelId,
                                            String deepLink) {
        if (intent == null) {
            return new Intent();
        }
        intent.putExtra("category", category == null ? "general" : category);
        intent.putExtra("route", route == null ? "notifications" : route);
        intent.putExtra("orderId", orderId == null ? "" : orderId);
        intent.putExtra("hotelId", hotelId == null ? "" : hotelId);
        intent.putExtra("deepLink", deepLink == null ? "" : deepLink);
        return intent;
    }

    private static boolean isOrderCategory(String category) {
        return NotificationCategories.ORDER_PLACED.equals(category)
                || NotificationCategories.ORDER_ACCEPTED.equals(category)
                || NotificationCategories.ORDER_REJECTED.equals(category)
                || NotificationCategories.ORDER_CANCELLED.equals(category)
                || NotificationCategories.PICKUP_READY.equals(category)
                || NotificationCategories.PICKUP_REMINDER.equals(category)
                || NotificationCategories.HOTEL_NEW_ORDER.equals(category);
    }

    private static Intent tryHotelOrdersIntent(Context context) {
        try {
            Class<?> hotelOrdersClass = Class.forName("com.replate.app.hotel.HotelOrdersActivity");
            return new Intent(context, hotelOrdersClass);
        } catch (ClassNotFoundException ignored) {
            Intent fallback = new Intent(context, MainActivity.class);
            fallback.putExtra("open_tab", "orders");
            return fallback;
        }
    }

    private static Intent tryHotelDashboardIntent(Context context) {
        try {
            Class<?> hotelDashboardClass = Class.forName("com.replate.app.hotel.HotelDashboardActivity");
            return new Intent(context, hotelDashboardClass);
        } catch (ClassNotFoundException ignored) {
            Intent fallback = new Intent(context, MainActivity.class);
            fallback.putExtra("open_tab", "profile");
            return fallback;
        }
    }
}
```

---

# 6) Token lifecycle manager

## File: `app/src/main/java/com/replate/app/utils/FcmTokenManager.java`

```java
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
```

---

# 7) Firebase messaging service

## File: `app/src/main/java/com/replate/app/services/MyFirebaseMessagingService.java`

```java
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
import com.replate.app.utils.UserRoleHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Cloud Messaging Service for push notifications.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "replate_notifications_v3";
    private static final String CHANNEL_NAME = "REPLATE Alerts";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "onNewToken received new token=" + token);
        FcmTokenManager.persistLatestToken(getApplicationContext(), token);
        syncTokenToActiveUser(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "FCM message received from=" + remoteMessage.getFrom());
        Log.d(TAG, "FCM data payload=" + remoteMessage.getData());
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "FCM notification payload title=" + remoteMessage.getNotification().getTitle()
                    + " body=" + remoteMessage.getNotification().getBody());
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

        showNotification(title, body, data);
    }

    private void syncTokenToActiveUser(@NonNull String token) {
        if (token.trim().isEmpty()) {
            Log.w(TAG, "Skipping token sync because token is blank");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No authenticated user while syncing token; token stored locally for later sync.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        String role = UserRoleHelper.fromPrefs(prefs);
        Log.d(TAG, "Syncing refreshed token for uid=" + user.getUid() + " role=" + role);
        FcmTokenManager.syncTokenForCurrentUser(getApplicationContext(), role);
    }

    private void saveNotification(String title, String body, Map<String, String> data) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "Skipping local notification persistence because no user session is active.");
            return;
        }

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
        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_round);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
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
}
```

---

# 8) Hook permission + token sync into login/home/settings

## File: `app/src/main/java/com/replate/app/LoginActivity.java`
Add or keep these blocks.

### Permission request
```java
private static final int REQ_POST_NOTIFICATIONS = 1201;

private void requestNotificationPermissionIfNeeded() {
    if (!NotificationPermissionHelper.isRuntimePermissionRequired()
            || NotificationPermissionHelper.hasPermission(this)) {
        return;
    }

    if (NotificationPermissionHelper.shouldShowRationale(this)) {
        NotificationPermissionHelper.showRationaleDialog(this,
                () -> NotificationPermissionHelper.request(this, REQ_POST_NOTIFICATIONS));
        return;
    }

    NotificationPermissionHelper.request(this, REQ_POST_NOTIFICATIONS);
}
```

### Resolve role safely after login
```java
private void resolveUserRoleAndProceed(FirebaseUser user) {
    mDatabase.child("users").child(user.getUid())
        .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String role = snapshot.exists()
                        ? UserRoleHelper.fromSnapshot(snapshot)
                        : UserRoleHelper.ROLE_CUSTOMER;
                if (!snapshot.exists()) {
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("name", user.getDisplayName() == null ? "Customer" : user.getDisplayName());
                    fallback.put("email", user.getEmail());
                    fallback.put("phone", "");
                    fallback.put("userType", UserRoleHelper.ROLE_CUSTOMER);
                    fallback.put("role", UserRoleHelper.ROLE_CUSTOMER);
                    fallback.put("createdAt", System.currentTimeMillis());
                    fallback.put("profileImage", "");
                    fallback.put("emailVerified", true);
                    mDatabase.child("users").child(user.getUid()).setValue(fallback);
                }

                showLoading(false);
                saveUserSession(user, role);
                createLoginNotification(user.getUid(), role);
                ToastUtils.showCustomToast(LoginActivity.this, "Welcome back!");
                goToHomeByRole(role, user.getUid());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                saveUserSession(user, UserRoleHelper.ROLE_CUSTOMER);
                goToHomeByRole(UserRoleHelper.ROLE_CUSTOMER, user.getUid());
            }
        });
}
```

### Save session + sync token
```java
private void saveUserSession(FirebaseUser user, String role) {
    if (user != null) {
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
            .putString("userId", user.getUid())
            .putString("userEmail", user.getEmail())
            .putString("role", role)
            .putString("userType", role)
            .putBoolean("isLoggedIn", true);

        if ("customer".equalsIgnoreCase(role)) {
            editor.putBoolean("pending_location_permission_prompt", true);
        }

        editor.apply();
        FcmTokenManager.syncTokenForCurrentUser(this, role);
    }
}
```

---

## File: `app/src/main/java/com/replate/app/MainActivity.java`

### Sync token on app shell startup
```java
SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
String sessionRole = UserRoleHelper.fromPrefs(prefs);
FcmTokenManager.syncTokenForCurrentUser(this, sessionRole);
```

### Request notification permission
```java
private void checkNotificationPermissions() {
    if (!NotificationPermissionHelper.isRuntimePermissionRequired()
            || NotificationPermissionHelper.hasPermission(this)) {
        return;
    }

    if (NotificationPermissionHelper.shouldShowRationale(this)) {
        NotificationPermissionHelper.showRationaleDialog(this,
                () -> NotificationPermissionHelper.request(this, 101));
        return;
    }

    NotificationPermissionHelper.request(this, 101);
}
```

---

## File: `app/src/main/java/com/replate/app/SettingsActivity.java`

### New notification tools fields + launcher
```java
private Button btnSendTestPush, btnRefreshFcmToken;
private TextView tvCurrentFcmToken;

private final ActivityResultLauncher<String> notificationsPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (switchNotifications != null) {
                switchNotifications.setChecked(granted || !NotificationPermissionHelper.isRuntimePermissionRequired());
            }
            prefs.edit().putBoolean("notifications_enabled", granted || !NotificationPermissionHelper.isRuntimePermissionRequired()).apply();
            if (!granted && NotificationPermissionHelper.isRuntimePermissionRequired()) {
                ToastUtils.showCustomToast(this, getString(R.string.notifications_permission_denied));
            }
        });
```

### Bind token tools in `onCreate()`
```java
loadPreferences();
bindCurrentTokenText(FcmTokenManager.getLastToken(this));
refreshSavedTokenFromFirebase();
```

### Request permission from settings toggle
```java
private void requestNotificationPermissionFromSettings() {
    if (!NotificationPermissionHelper.isRuntimePermissionRequired()) {
        prefs.edit().putBoolean("notifications_enabled", true).apply();
        if (switchNotifications != null) switchNotifications.setChecked(true);
        return;
    }

    if (NotificationPermissionHelper.hasPermission(this)) {
        prefs.edit().putBoolean("notifications_enabled", true).apply();
        if (switchNotifications != null) switchNotifications.setChecked(true);
        return;
    }

    if (NotificationPermissionHelper.shouldShowRationale(this)) {
        NotificationPermissionHelper.showRationaleDialog(this,
                () -> NotificationPermissionHelper.request(notificationsPermissionLauncher));
        return;
    }

    NotificationPermissionHelper.request(notificationsPermissionLauncher);
}
```

### Manual token refresh
```java
private void refreshAndSyncToken() {
    FirebaseUser user = mAuth.getCurrentUser();
    if (user == null) {
        ToastUtils.showCustomToast(this, getString(R.string.settings_token_refresh_failed));
        return;
    }

    ToastUtils.showCustomToast(this, getString(R.string.settings_token_refreshing));
    FirebaseMessaging.getInstance().setAutoInitEnabled(true);
    FirebaseMessaging.getInstance().getToken()
            .addOnSuccessListener(token -> {
                if (token == null || token.trim().isEmpty()) {
                    ToastUtils.showCustomToast(this, getString(R.string.settings_token_refresh_failed));
                    return;
                }
                bindCurrentTokenText(token);
                String role = UserRoleHelper.fromPrefs(prefs);
                FcmTokenManager.persistLatestToken(this, token);
                FcmTokenManager.syncTokenForCurrentUser(this, role);
                ToastUtils.showCustomToast(this, getString(R.string.settings_token_refreshed));
            })
            .addOnFailureListener(e -> ToastUtils.showCustomToast(this, getString(R.string.settings_token_refresh_failed)));
}
```

### Read current token from Firebase/user role-safe lookup
```java
private void refreshSavedTokenFromFirebase() {
    FirebaseUser user = mAuth.getCurrentUser();
    if (user == null) return;

    FcmTokenManager.fetchReceiverToken(user.getUid(), result -> runOnUiThread(() -> {
        if (!canUseUi()) return;
        String token = result == null ? "" : result.token;
        if (token == null || token.trim().isEmpty()) {
            token = FcmTokenManager.getLastToken(SettingsActivity.this);
        }
        bindCurrentTokenText(token);
    }));
}
```

### Token label binding
```java
private void bindCurrentTokenText(String token) {
    if (tvCurrentFcmToken == null) return;
    if (token == null || token.trim().isEmpty()) {
        tvCurrentFcmToken.setText(getString(R.string.settings_current_fcm_token_empty));
        return;
    }
    tvCurrentFcmToken.setText(getString(R.string.settings_current_fcm_token_format, token.trim()));
}
```

### Self-test push
```java
private void sendSelfTestPush() {
    FirebaseUser user = mAuth.getCurrentUser();
    if (user == null) {
        ToastUtils.showCustomToast(this, getString(R.string.settings_test_push_failed));
        return;
    }

    if (!NotificationPermissionHelper.hasPermission(this)) {
        requestNotificationPermissionFromSettings();
        return;
    }

    ToastUtils.showCustomToast(this, getString(R.string.settings_test_push_sending));

    networkExecutor.execute(() -> {
        HttpURLConnection connection = null;
        try {
            String idToken = Tasks.await(user.getIdToken(true), 20, TimeUnit.SECONDS).getToken();
            if (idToken == null || idToken.trim().isEmpty()) {
                throw new IllegalStateException("Missing auth token");
            }

            URL url = new URL(FUNCTIONS_BASE_URL + "/sendSelfTestNotification");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + idToken);

            JSONObject payload = new JSONObject();
            payload.put("title", "REPLATE Test Push");
            payload.put("body", "If you can see this popup, push notifications are working.");
            payload.put("category", "admin_announcement");
            payload.put("route", "notifications");
            payload.put("data", new JSONObject().put("debug", "true").put("source", "settings_self_test"));

            byte[] out = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(out);

            int code = connection.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(),
                    StandardCharsets.UTF_8
            ));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            if (code >= 200 && code < 300) {
                runOnUiThread(() -> ToastUtils.showCustomToast(SettingsActivity.this,
                        getString(R.string.settings_test_push_sent)));
            } else {
                throw new IllegalStateException("HTTP " + code + ": " + response);
            }
        } catch (Exception e) {
            runOnUiThread(() -> ToastUtils.showCustomToast(SettingsActivity.this,
                    getString(R.string.settings_test_push_failed) + " " + e.getMessage()));
        } finally {
            if (connection != null) connection.disconnect();
        }
    });
}
```

### Preference binding
```java
private void loadPreferences() {
    boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
    if (NotificationPermissionHelper.isRuntimePermissionRequired() && !NotificationPermissionHelper.hasPermission(this)) {
        notificationsEnabled = false;
    }
    switchNotifications.setChecked(notificationsEnabled);
    switchLocation.setChecked(prefs.getBoolean("location_enabled", true));
}
```

---

## File: `app/src/main/res/layout/activity_settings.xml`
Add this card to the settings layout:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardNotificationTools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    app:cardBackgroundColor="@color/white"
    app:cardCornerRadius="@dimen/radius_xl"
    app:cardElevation="@dimen/elevation_card">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="@dimen/space_2">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/settings_notification_tools"
            android:textAppearance="@style/TextAppearance.REPLATE.Subheading" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:text="@string/settings_notification_tools_desc"
            android:textAppearance="@style/TextAppearance.REPLATE.Body"
            android:textColor="@color/text_secondary" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/space_2"
            android:text="@string/settings_current_fcm_token_label"
            android:textAppearance="@style/TextAppearance.REPLATE.Label" />

        <TextView
            android:id="@+id/tvCurrentFcmToken"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/space_0_5"
            android:background="@drawable/card_background_shape"
            android:padding="@dimen/space_1_5"
            android:text="@string/settings_current_fcm_token_empty"
            android:textAppearance="@style/TextAppearance.REPLATE.Caption"
            android:textColor="@color/text_secondary" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnRefreshFcmToken"
            style="@style/Widget.REPLATE.Button.Secondary"
            android:layout_width="match_parent"
            android:layout_height="@dimen/button_height"
            android:layout_marginTop="@dimen/space_1_5"
            android:text="@string/settings_refresh_fcm_token" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnSendTestPush"
            style="@style/Widget.REPLATE.Button.Primary"
            android:layout_width="match_parent"
            android:layout_height="@dimen/button_height"
            android:layout_marginTop="@dimen/space_1"
            android:text="@string/settings_send_test_push" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

---

# 9) Strings needed

## File: `app/src/main/res/values/strings.xml`
Add these strings:

```xml
<string name="settings_notification_tools">Notification Tools</string>
<string name="settings_notification_tools_desc">Refresh your FCM token, verify what is saved for this account, and send a secure self-test push.</string>
<string name="settings_send_test_push">Send Test Push</string>
<string name="settings_refresh_fcm_token">Refresh FCM Token</string>
<string name="settings_current_fcm_token_label">Current device token</string>
<string name="settings_current_fcm_token_empty">Token not available yet. Refresh once Firebase finishes registration.</string>
<string name="settings_current_fcm_token_format">%1$s</string>
<string name="settings_test_push_sending">Sending test push...</string>
<string name="settings_test_push_sent">Test push sent. Check popup banner now.</string>
<string name="settings_test_push_failed">Failed to send test push.</string>
<string name="settings_token_refreshing">Refreshing token...</string>
<string name="settings_token_refreshed">FCM token refreshed and synced.</string>
<string name="settings_token_refresh_failed">Couldn’t refresh FCM token.</string>

<string name="notifications_permission_title">Enable notifications</string>
<string name="notifications_permission_message">Allow notifications to get new orders, order updates, pickup alerts, and admin approvals in real time.</string>
<string name="notifications_permission_continue">Allow</string>
<string name="notifications_permission_not_now">Not now</string>
<string name="notifications_permission_denied">Notifications are disabled. You can enable them later from app settings.</string>
<string name="notifications_permission_settings">Open notification settings</string>
```

---

# 10) Cloud Functions / Admin SDK sender

## File: `firebase-functions/package.json`
No new dependency was required beyond the Admin SDK already present.

```json
{
  "name": "replate-otp-functions",
  "version": "1.0.0",
  "private": true,
  "main": "index.js",
  "engines": {
    "node": "20"
  },
  "scripts": {
    "serve": "firebase emulators:start --only functions",
    "deploy": "firebase deploy --only functions"
  },
  "dependencies": {
    "firebase-admin": "^12.7.0",
    "firebase-functions": "^5.1.1",
    "nodemailer": "^6.9.16"
  }
}
```

---

## File: `firebase-functions/index.js`

### Add notification category support
```javascript
const VALID_NOTIFICATION_CATEGORIES = new Set([
  "welcome",
  "new_deals",
  "hot_deals",
  "order_placed",
  "order_accepted",
  "order_rejected",
  "order_cancelled",
  "pickup_ready",
  "pickup_reminder",
  "admin_announcement",
  "admin_broadcast",
  "hotel_new_order",
  "hotel_approved",
  "profile_updated",
  "login_success",
]);
```

### Normalize hotel approval category
```javascript
function normalizeCategory(rawCategory, fallback = "admin_broadcast") {
  const candidate = String(rawCategory || fallback).trim().toLowerCase();
  if (VALID_NOTIFICATION_CATEGORIES.has(candidate)) return candidate;

  if (candidate.includes("deal")) return candidate.includes("hot") ? "hot_deals" : "new_deals";
  if (candidate.includes("reject")) return "order_rejected";
  if (candidate.includes("accept")) return "order_accepted";
  if (candidate.includes("cancel")) return "order_cancelled";
  if (candidate.includes("pickup") && candidate.includes("remind")) return "pickup_reminder";
  if (candidate.includes("pickup") || candidate.includes("ready")) return "pickup_ready";
  if (candidate.includes("approved") && candidate.includes("hotel")) return "hotel_approved";
  if (candidate.includes("hotel") || candidate.includes("new_order")) return "hotel_new_order";
  if (candidate.includes("place") || candidate.includes("create") || candidate.includes("order")) return "order_placed";
  if (candidate.includes("profile")) return "profile_updated";
  if (candidate.includes("login")) return "login_success";
  if (candidate.includes("welcome")) return "welcome";
  if (candidate.includes("announce") || candidate.includes("broadcast") || candidate.includes("admin")) {
    return "admin_announcement";
  }
  return fallback;
}
```

### Safe token lookup helpers
```javascript
async function loadUserNotificationRecord(uid) {
  if (!uid) return null;

  const userSnap = await db.ref(`users/${uid}`).get();
  if (!userSnap.exists()) return null;

  const user = userSnap.val() || {};
  const role = roleFromUser(user);
  let token = String(user.fcmToken || user.notification?.fcmToken || "").trim();
  const cleanupPaths = [
    `users/${uid}/fcmToken`,
    `users/${uid}/notification/fcmToken`,
  ];

  if (role === "hotel") {
    const hotelsSnap = await db.ref("hotels").orderByChild("userId").equalTo(uid).get();
    if (hotelsSnap.exists()) {
      hotelsSnap.forEach((child) => {
        const hotel = child.val() || {};
        cleanupPaths.push(`hotels/${child.key}/fcmToken`);
        cleanupPaths.push(`hotels/${child.key}/notification/fcmToken`);
        if (!token) {
          token = String(hotel.fcmToken || hotel.notification?.fcmToken || "").trim();
        }
      });
    }
  }

  return { uid, role, token, cleanupPaths, user };
}

async function loadHotelNotificationRecord(hotelId) {
  if (!hotelId) return null;

  const directHotelSnap = await db.ref(`hotels/${hotelId}`).get();
  if (directHotelSnap.exists()) {
    const hotel = directHotelSnap.val() || {};
    const userId = String(hotel.userId || hotel.ownerUid || hotel.uid || hotelId).trim();
    const userRecord = await loadUserNotificationRecord(userId);
    if (userRecord) return userRecord;

    const token = String(hotel.fcmToken || hotel.notification?.fcmToken || "").trim();
    return {
      uid: userId,
      role: "hotel",
      token,
      cleanupPaths: [
        `hotels/${hotelId}/fcmToken`,
        `hotels/${hotelId}/notification/fcmToken`,
      ],
      user: hotel,
    };
  }

  return loadUserNotificationRecord(String(hotelId).trim());
}
```

### Invalid/stale token cleanup
```javascript
async function cleanupInvalidTokens(records, failures) {
  const removals = [];
  failures.forEach((failure) => {
    const code = failure.error && failure.error.code ? failure.error.code : "";
    if (!code.includes("registration-token-not-registered") && !code.includes("invalid-registration-token")) {
      return;
    }

    const record = records[failure.index];
    if (!record || !Array.isArray(record.cleanupPaths)) {
      return;
    }

    record.cleanupPaths.forEach((path) => {
      if (!path) return;
      removals.push(db.ref(path).remove());
    });
  });

  if (removals.length > 0) {
    await Promise.allSettled(removals);
  }
}
```

### Secure self-test endpoint
```javascript
exports.sendSelfTestNotification = functions.region(REGION).https.onRequest(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const requester = await verifyRequestAuth(req, ["admin", "hotel", "customer"]);
    const record = await loadUserNotificationRecord(requester.uid);
    if (!record || !record.token) {
      return res.status(400).json({ success: false, message: "Missing FCM token for current user" });
    }

    const payload = buildPayload({
      title: req.body?.title || "REPLATE Test Push",
      body: req.body?.body || "If you can see this popup, push notifications are working.",
      category: req.body?.category || "admin_announcement",
      route: req.body?.route || "notifications",
      ...req.body,
    });

    const result = await sendToUserRecords([record], payload);
    await persistNotificationForUsers([requester.uid], payload, "self_test");

    return res.status(200).json({ success: true, result });
  } catch (err) {
    console.error("sendSelfTestNotification error", err);
    return res.status(500).json({ success: false, message: err.message || "Failed to send self-test notification" });
  }
});
```

### Customer places order -> hotel and customer
```javascript
exports.onOrderCreatedNotify = functions
  .region(REGION)
  .database.ref("/orders/{orderId}")
  .onCreate(async (snapshot, context) => {
    const order = snapshot.val() || {};
    const orderId = context.params.orderId;

    const customerId = String(order.userId || "").trim();
    const hotelId = String(order.hotelId || "").trim();
    const customerName = String(order.customerName || order.userName || "Customer");
    const hotelName = String(order.hotelName || "Hotel");

    if (customerId) {
      const customerRecord = await loadUserNotificationRecord(customerId);
      if (customerRecord) {
        const payload = buildPayload({
          title: "Order placed successfully",
          body: `Your order at ${hotelName} has been placed.`,
          type: "order_placed",
          category: "order_placed",
          route: "order",
          orderId,
          hotelId,
        });

        if (customerRecord.token) {
          await sendToUserRecords([customerRecord], payload);
        }
        await persistNotificationForUsers([customerRecord.uid], payload, "order_created");
      }
    }

    if (hotelId) {
      const hotelRecord = await loadHotelNotificationRecord(hotelId);
      if (hotelRecord && hotelRecord.uid) {
        const payload = buildPayload({
          title: "New order received",
          body: `${customerName} placed a new order.`,
          type: "order_placed",
          category: "hotel_new_order",
          route: "order",
          orderId,
          hotelId,
        });

        if (hotelRecord.token) {
          await sendToUserRecords([hotelRecord], payload);
        }
        await persistNotificationForUsers([hotelRecord.uid], payload, "order_created");
      }
    }

    return null;
  });
```

### Hotel accepts / ready / cancels -> customer, cancellation -> hotel
```javascript
exports.onOrderStatusChangedNotify = functions
  .region(REGION)
  .database.ref("/orders/{orderId}")
  .onUpdate(async (change, context) => {
    const before = change.before.val() || {};
    const after = change.after.val() || {};

    if (before.orderStatus === after.orderStatus) return null;

    const orderId = context.params.orderId;
    const userId = String(after.userId || "").trim();
    const hotelId = String(after.hotelId || "").trim();
    const status = String(after.orderStatus || "updated").toLowerCase();

    const statusMap = {
      pending_pickup: {
        title: "Order accepted",
        body: "Your order is accepted and waiting for pickup.",
        category: "order_accepted",
      },
      accepted: {
        title: "Order accepted",
        body: "Your order is accepted and waiting for pickup.",
        category: "order_accepted",
      },
      rejected: {
        title: "Order rejected",
        body: "Your order was rejected by the hotel.",
        category: "order_rejected",
      },
      cancelled: {
        title: "Order cancelled",
        body: "Your order has been cancelled.",
        category: "order_cancelled",
      },
      ready: {
        title: "Pickup ready",
        body: "Your meal is ready for pickup.",
        category: "pickup_ready",
      },
      completed: {
        title: "Order completed",
        body: "Order marked completed. Enjoy your meal!",
        category: "pickup_ready",
      },
    };

    const content = statusMap[status] || {
      title: "Order updated",
      body: `Your order status is now ${status.replace(/_/g, " ")}.`,
      category: "order_accepted",
    };

    const customerPayload = buildPayload({
      ...content,
      type: "order_status",
      route: "order",
      orderId,
      hotelId,
      data: { newStatus: status },
    });

    if (userId) {
      const customerRecord = await loadUserNotificationRecord(userId);
      if (customerRecord) {
        if (customerRecord.token) {
          await sendToUserRecords([customerRecord], customerPayload);
        }
        await persistNotificationForUsers([customerRecord.uid], customerPayload, "order_status_update");
      }
    }

    if (status === "cancelled" && hotelId) {
      const hotelRecord = await loadHotelNotificationRecord(hotelId);
      if (hotelRecord && hotelRecord.uid) {
        const hotelPayload = buildPayload({
          title: "Order cancelled",
          body: `${after.customerName || after.userName || "A customer"} cancelled an order.`,
          type: "order_cancelled",
          category: "order_cancelled",
          route: "order",
          orderId,
          hotelId,
          data: { newStatus: status },
        });

        if (hotelRecord.token) {
          await sendToUserRecords([hotelRecord], hotelPayload);
        }
        await persistNotificationForUsers([hotelRecord.uid], hotelPayload, "order_status_update");
      }
    }

    return null;
  });
```

### Admin approves hotel -> hotel
```javascript
exports.onHotelStatusChangedNotify = functions
  .region(REGION)
  .database.ref("/hotels/{hotelId}")
  .onUpdate(async (change, context) => {
    const before = change.before.val() || {};
    const after = change.after.val() || {};
    const beforeStatus = String(before.status || "").trim().toLowerCase();
    const afterStatus = String(after.status || "").trim().toLowerCase();

    if (!afterStatus || beforeStatus === afterStatus) return null;

    const hotelId = context.params.hotelId;
    const hotelRecord = await loadHotelNotificationRecord(hotelId);
    if (!hotelRecord || !hotelRecord.uid) return null;

    let payload = null;
    if (afterStatus === "approved") {
      payload = buildPayload({
        title: "Hotel approved",
        body: "Your hotel account is approved. You can now manage orders and listings.",
        type: "hotel_approved",
        category: "hotel_approved",
        route: "hotel_dashboard",
        hotelId,
        data: { status: afterStatus },
      });
    } else if (afterStatus === "rejected") {
      payload = buildPayload({
        title: "Hotel status updated",
        body: "Your hotel registration needs attention. Please contact support or review your submitted details.",
        type: "admin_announcement",
        category: "admin_announcement",
        route: "profile",
        hotelId,
        data: { status: afterStatus },
      });
    }

    if (!payload) return null;

    if (hotelRecord.token) {
      await sendToUserRecords([hotelRecord], payload);
    }
    await persistNotificationForUsers([hotelRecord.uid], payload, "hotel_status_update");
    return null;
  });
```

---

# 11) Setup steps

## Install and deploy Cloud Functions
```powershell
cd C:\Users\rizla\AndroidStudioProjects\REPLATE\firebase-functions
npm install
firebase deploy --only functions
```

If you need to select the Firebase project first:

```powershell
cd C:\Users\rizla\AndroidStudioProjects\REPLATE\firebase-functions
firebase use YOUR_PROJECT_ID
firebase deploy --only functions
```

## Build the Android app
```powershell
cd C:\Users\rizla\AndroidStudioProjects\REPLATE
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:assembleDebug
```

---

# 12) How to test

## A. Token generation and save path
1. Install the app on a real device.
2. Login.
3. Open `Settings`.
4. Tap `Refresh FCM Token`.
5. Verify these DB values update:
   - `users/{uid}/fcmToken`
   - `users/{uid}/notification/lastTokenUpdateAt`

## B. Android 13 permission
1. On Android 13+, deny notification permission first.
2. Open `Settings` and enable the notification switch.
3. Confirm the permission dialog appears.
4. Grant it.
5. Confirm the switch stays enabled.

## C. Self-test push
1. Open `Settings`.
2. Tap `Send Test Push`.
3. Confirm:
   - heads-up notification appears if app is foreground/background
   - tapping opens the app route

## D. Customer places order -> hotel
1. Create a new order under `orders/{orderId}`.
2. Verify hotel receives `hotel_new_order` notification.

## E. Hotel accepts order -> customer
1. Update `orders/{orderId}/orderStatus = pending_pickup` or `accepted`.
2. Verify customer receives `order_accepted`.

## F. Hotel marks ready -> customer
1. Update `orders/{orderId}/orderStatus = ready`.
2. Verify customer receives `pickup_ready`.

## G. Admin approves hotel -> hotel
1. Update `hotels/{hotelId}/status = approved`.
2. Verify hotel receives `hotel_approved`.
3. Tap notification and verify it opens hotel dashboard fallback route.

## H. Stale token cleanup
1. Send a push to an intentionally invalid token.
2. Backend should remove it from:
   - `users/{uid}/fcmToken`
   - `users/{uid}/notification/fcmToken`
   - hotel token mirror paths if applicable

---

# 13) Notes
- No legacy FCM API is used.
- All secure sending is done through Firebase Admin SDK in Cloud Functions.
- Android never stores a server key.
- `users/{uid}/fcmToken` is the primary token path.
- `users/{uid}/notification/fcmToken` is kept for compatibility with older flows.
- Hotel token lookup supports both `users/{uid}` and linked `hotels/{hotelId}` records.

