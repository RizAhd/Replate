package com.replate.app.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles safe, complete deletion of a user account.
 *
 * Deletion order:
 *  1. Re-authenticate (password users) or confirm a recent sign-in (Google/Facebook/phone users)
 *  2. Anonymize orders (keep hotel/admin records, strip PII fields)
 *  3. Delete notifications/{uid}
 *  4. Delete indexes/ordersByUser/{uid}
 *  5. Clear FCM token + unsubscribe topics
 *  6. Delete users/{uid}
 *  7. Delete the Firebase Auth account (retried once) - the point of no return
 *  8. Clear local SQLite data + SharedPreferences
 *
 * Every step is idempotent, so if step 7 still fails the user can simply run deletion again.
 * Local data is only cleared after the account is really gone.
 */
public final class AccountDeletionManager {

    public interface DeletionCallback {
        void onSuccess();

        void onWrongPassword();

        void onFailure(@NonNull String reason);

        /** Social/phone accounts must have signed in recently; ask them to sign in again. */
        default void onRecentLoginRequired() {
            onFailure("Recent login required");
        }
    }

    private static final String TAG = "AccountDeletion";
    private static final long RECENT_LOGIN_WINDOW_MS = 5 * 60 * 1000L;

    private AccountDeletionManager() {}

    // -------------------------------------------------------------------------
    // Public entry points
    // -------------------------------------------------------------------------

    /** True when the account has an email/password login, so a password must be confirmed. */
    public static boolean requiresPassword(@Nullable FirebaseUser user) {
        if (user == null) {
            return false;
        }
        for (UserInfo info : user.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param password the account password for email/password users; ignored (may be null) for
     *                 Google, Facebook and phone accounts
     */
    public static void deleteAccount(@NonNull Context context,
                                     @Nullable String password,
                                     @NonNull DeletionCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onFailure("No authenticated user");
            return;
        }

        if (requiresPassword(user)) {
            if (user.getEmail() == null || password == null || password.isEmpty()) {
                callback.onWrongPassword();
                return;
            }
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
            user.reauthenticate(credential)
                    .addOnSuccessListener(unused -> step1_anonymizeOrders(context, user, callback))
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Re-authentication failed", e);
                        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                        if (msg.contains("password") || msg.contains("credential")
                                || msg.contains("invalid") || msg.contains("wrong")) {
                            callback.onWrongPassword();
                        } else {
                            callback.onFailure(e.getMessage() != null ? e.getMessage() : "Re-auth failed");
                        }
                    });
            return;
        }

        // Google / Facebook / phone: Firebase only allows deleting an account that signed in recently
        long lastSignIn = user.getMetadata() != null ? user.getMetadata().getLastSignInTimestamp() : 0L;
        if (System.currentTimeMillis() - lastSignIn > RECENT_LOGIN_WINDOW_MS) {
            callback.onRecentLoginRequired();
            return;
        }
        step1_anonymizeOrders(context, user, callback);
    }

    // -------------------------------------------------------------------------
    // Deletion pipeline - each step calls the next on completion
    // -------------------------------------------------------------------------

    /** Step 1: Anonymize orders belonging to this user */
    private static void step1_anonymizeOrders(@NonNull Context ctx,
                                              @NonNull FirebaseUser user,
                                              @NonNull DeletionCallback cb) {
        String uid = user.getUid();
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();

        root.child("orders").orderByChild("userId").equalTo(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Map<String, Object> updates = new HashMap<>();
                        for (DataSnapshot order : snapshot.getChildren()) {
                            String key = order.getKey();
                            if (key == null) continue;
                            updates.put("orders/" + key + "/userId", "[deleted]");
                            updates.put("orders/" + key + "/customerName", "[deleted]");
                            updates.put("orders/" + key + "/customerPhone", "[deleted]");
                            updates.put("orders/" + key + "/userName", "[deleted]");
                            updates.put("orders/" + key + "/userEmail", "[deleted]");
                            updates.put("orders/" + key + "/userPhone", "[deleted]");
                            updates.put("orders/" + key + "/accountDeleted", true);
                        }
                        if (!updates.isEmpty()) {
                            root.updateChildren(updates)
                                    .addOnCompleteListener(t -> step2_deleteNotifications(ctx, user, root, cb));
                            return;
                        }
                    }
                    step2_deleteNotifications(ctx, user, root, cb);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Order anonymization failed (non-fatal), continuing", e);
                    step2_deleteNotifications(ctx, user, root, cb);
                });
    }

    /** Step 2: Delete notification history */
    private static void step2_deleteNotifications(@NonNull Context ctx,
                                                  @NonNull FirebaseUser user,
                                                  @NonNull DatabaseReference root,
                                                  @NonNull DeletionCallback cb) {
        root.child("notifications").child(user.getUid()).removeValue()
                .addOnCompleteListener(t -> step3_deleteIndex(ctx, user, root, cb));
    }

    /** Step 3: Delete user order index */
    private static void step3_deleteIndex(@NonNull Context ctx,
                                          @NonNull FirebaseUser user,
                                          @NonNull DatabaseReference root,
                                          @NonNull DeletionCallback cb) {
        root.child("indexes").child("ordersByUser").child(user.getUid()).removeValue()
                .addOnCompleteListener(t -> step4_clearFcm(ctx, user, root, cb));
    }

    /** Step 4: Clear FCM token + unsubscribe topics */
    private static void step4_clearFcm(@NonNull Context ctx,
                                       @NonNull FirebaseUser user,
                                       @NonNull DatabaseReference root,
                                       @NonNull DeletionCallback cb) {
        try {
            FcmTokenManager.clearTokenForCurrentUser();
            FirebaseMessaging fm = FirebaseMessaging.getInstance();
            fm.unsubscribeFromTopic("all_users");
            fm.unsubscribeFromTopic("customers");
            fm.unsubscribeFromTopic("deals_customers");
            fm.unsubscribeFromTopic("hotels");
            fm.unsubscribeFromTopic("admins");
            fm.unsubscribeFromTopic("user_" + user.getUid());
        } catch (Exception e) {
            Log.w(TAG, "FCM cleanup error (non-fatal)", e);
        }
        step5_deleteUserNode(ctx, user, root, cb);
    }

    /** Step 5: Delete users/{uid} node */
    private static void step5_deleteUserNode(@NonNull Context ctx,
                                             @NonNull FirebaseUser user,
                                             @NonNull DatabaseReference root,
                                             @NonNull DeletionCallback cb) {
        root.child("users").child(user.getUid()).removeValue()
                .addOnSuccessListener(v -> step6_deleteAuthAccount(ctx, user, cb, true))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "User node deletion failed", e);
                    cb.onFailure("Failed to delete user data");
                });
    }

    /** Step 6: Delete Firebase Auth account - point of no return. Retried once on failure. */
    private static void step6_deleteAuthAccount(@NonNull Context ctx,
                                                @NonNull FirebaseUser user,
                                                @NonNull DeletionCallback cb,
                                                boolean canRetry) {
        user.delete()
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Account fully deleted uid=" + user.getUid());
                    step7_clearLocal(ctx);
                    cb.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                        cb.onRecentLoginRequired();
                    } else if (canRetry) {
                        Log.w(TAG, "Auth account deletion failed, retrying once", e);
                        step6_deleteAuthAccount(ctx, user, cb, false);
                    } else {
                        Log.e(TAG, "Auth account deletion failed", e);
                        cb.onFailure(e.getMessage() != null ? e.getMessage() : "Auth deletion failed");
                    }
                });
    }

    /** Step 7: Clear SQLite databases + SharedPreferences, only once the account is really gone */
    private static void step7_clearLocal(@NonNull Context ctx) {
        LocalDataCleaner.clearAll(ctx);

        try {
            for (String pref : new String[]{"REPLATE_PREFS", "REPLATE_SESSION", "FCM_PREFS", "REPLATE_FOOD_CACHE", "REPLATE_BANNERS"}) {
                ctx.getSharedPreferences(pref, Context.MODE_PRIVATE).edit().clear().apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Prefs clear error (non-fatal)", e);
        }
    }
}
