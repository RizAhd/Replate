package com.replate.app.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.replate.app.models.FoodItem;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * FoodItemCacheManager - Manages local caching of food items for faster app startup
 * Uses SharedPreferences + JSON serialization for persistence
 * Implements TTL (Time-To-Live) mechanism to keep data fresh (5 minutes default)
 */
public class FoodItemCacheManager {

    private static final String TAG = "FoodCache";
    private static final String PREFS_NAME = "REPLATE_FOOD_CACHE";
    private static final String KEY_FOOD_ITEMS = "cached_food_items";
    private static final String KEY_CACHE_TIMESTAMP = "cache_timestamp";
    // The cache is only a first paint / offline fallback: live Firebase data replaces it as soon as it
    // arrives. So it may be old, but not forever - listings from days ago would be misleading.
    private static final long MAX_STALE_MS = 24L * 60 * 60 * 1000; // 24 hours

    private final SharedPreferences prefs;
    private final Gson gson;

    public FoodItemCacheManager(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    /**
     * Save food items to local cache
     */
    public void saveFoodItems(@NonNull List<FoodItem> items) {
        try {
            String json = gson.toJson(items);
            prefs.edit()
                    .putString(KEY_FOOD_ITEMS, json)
                    .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache food items", e);
        }
    }

    /**
     * Get cached food items if available and not older than {@link #MAX_STALE_MS}
     * Returns empty list if the cache is too old or missing
     */
    @NonNull
    public List<FoodItem> getCachedFoodItems() {
        try {
            // Check if cache is expired
            long cacheTime = prefs.getLong(KEY_CACHE_TIMESTAMP, 0);
            long age = System.currentTimeMillis() - cacheTime;

            if (age > MAX_STALE_MS) {
                // Too old to be useful even as an offline fallback
                clearCache();
                return new ArrayList<>();
            }

            String json = prefs.getString(KEY_FOOD_ITEMS, null);
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }

            Type listType = new TypeToken<List<FoodItem>>(){}.getType();
            List<FoodItem> items = gson.fromJson(json, listType);
            return items != null ? items : new ArrayList<>();
        } catch (Exception e) {
            Log.w(TAG, "Failed to read food cache", e);
            return new ArrayList<>();
        }
    }

    /**
     * Check if cache exists and is still valid
     */
    public boolean hasCachedData() {
        try {
            long cacheTime = prefs.getLong(KEY_CACHE_TIMESTAMP, 0);
            long age = System.currentTimeMillis() - cacheTime;

            if (age > MAX_STALE_MS) {
                clearCache();
                return false;
            }

            String json = prefs.getString(KEY_FOOD_ITEMS, null);
            return json != null && !json.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clear the cache
     */
    public void clearCache() {
        try {
            prefs.edit()
                    .remove(KEY_FOOD_ITEMS)
                    .remove(KEY_CACHE_TIMESTAMP)
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear food cache", e);
        }
    }

    /**
     * Get cache age in milliseconds
     * Returns -1 if cache doesn't exist
     */
    public long getCacheAge() {
        long cacheTime = prefs.getLong(KEY_CACHE_TIMESTAMP, 0);
        if (cacheTime == 0) return -1;
        return System.currentTimeMillis() - cacheTime;
    }
}
