package com.replate.app.utils;

import android.content.Context;
import android.util.Log;

import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.helpers.ChatDatabaseHelper;
import com.replate.app.helpers.FoodItemCacheManager;
import com.replate.app.helpers.ReplateDatabaseHelper;

/**
 * Wipes everything stored on the device for the signed-in user (cart, favorites, cached orders
 * with pickup codes, notifications, chat, profile, search history). Call on logout so the next
 * person who signs in on the same phone doesn't see the previous user's data.
 *
 * Each step is isolated so one failure doesn't stop the rest from being cleared.
 */
public final class LocalDataCleaner {

    private static final String TAG = "LocalDataCleaner";

    private LocalDataCleaner() {}

    public static void clearAll(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;

        try {
            CartDatabaseHelper.getInstance(app).clearCart();
        } catch (Exception e) {
            Log.w(TAG, "Failed clearing cart", e);
        }

        try {
            ChatDatabaseHelper.getInstance(app).clearAll();
        } catch (Exception e) {
            Log.w(TAG, "Failed clearing chat", e);
        }

        try {
            ReplateDatabaseHelper db = ReplateDatabaseHelper.getInstance(app);
            db.clearFavorites();
            db.clearSearchHistory();
            db.clearNotificationCache();
            db.clearOrderCache();
            db.clearFoodCache();
            db.clearUserProfileCache();
        } catch (Exception e) {
            Log.w(TAG, "Failed clearing local database", e);
        }

        try {
            new FoodItemCacheManager(app).clearCache();
        } catch (Exception e) {
            Log.w(TAG, "Failed clearing food cache", e);
        }
    }
}
