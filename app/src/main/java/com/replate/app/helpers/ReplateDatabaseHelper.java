package com.replate.app.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.gson.Gson;
import com.replate.app.models.AppNotification;
import com.replate.app.models.FoodItem;
import com.replate.app.models.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified SQLite Database Helper for local data caching.
 * Manages: Search History, Favorites, Notification Cache, Order History Cache.
 *
 * Requirement: Data Storage — SQLite
 *
 * Firebase remains the primary source of truth.
 * SQLite provides offline access and faster load times.
 */
public class ReplateDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "ReplateDatabaseHelper";
    private static final String DATABASE_NAME = "ReplateLocalDB";
    private static final int DATABASE_VERSION = 3;

    // ═══════════════════════════════════════════════════════
    //  TABLE 1: SEARCH HISTORY
    // ═══════════════════════════════════════════════════════
    private static final String TABLE_SEARCH_HISTORY = "search_history";
    private static final String SH_ID = "id";
    private static final String SH_QUERY = "search_query";
    private static final String SH_TIMESTAMP = "timestamp";

    // ═══════════════════════════════════════════════════════
    //  TABLE 2: FAVORITES
    // ═══════════════════════════════════════════════════════
    private static final String TABLE_FAVORITES = "favorites";
    private static final String FAV_ID = "id";
    private static final String FAV_FOOD_ID = "food_id";
    private static final String FAV_FOOD_NAME = "food_name";
    private static final String FAV_HOTEL_NAME = "hotel_name";
    private static final String FAV_HOTEL_ID = "hotel_id";
    private static final String FAV_IMAGE_URL = "image_url";
    private static final String FAV_ORIGINAL_PRICE = "original_price";
    private static final String FAV_DISCOUNTED_PRICE = "discounted_price";
    private static final String FAV_DISCOUNT_PERCENT = "discount_percent";
    private static final String FAV_CATEGORY = "category";
    private static final String FAV_DESCRIPTION = "description";
    private static final String FAV_ADDED_AT = "added_at";

    // ═══════════════════════════════════════════════════════
    //  TABLE 3: NOTIFICATION CACHE
    // ═══════════════════════════════════════════════════════
    private static final String TABLE_NOTIFICATIONS = "notification_cache";
    private static final String NT_ID = "id";
    private static final String NT_NOTIFICATION_ID = "notification_id";
    private static final String NT_TITLE = "title";
    private static final String NT_BODY = "body";
    private static final String NT_TIMESTAMP = "timestamp";
    private static final String NT_READ = "read";
    private static final String NT_TYPE = "type";
    private static final String NT_CATEGORY = "category";
    private static final String NT_ROUTE = "route";
    private static final String NT_DEEP_LINK = "deep_link";
    private static final String NT_ORDER_ID = "order_id";
    private static final String NT_HOTEL_ID = "hotel_id";

    // ═══════════════════════════════════════════════════════
    //  TABLE 4: ORDER HISTORY CACHE
    // ═══════════════════════════════════════════════════════
    private static final String TABLE_ORDERS = "order_cache";
    private static final String OC_ID = "id";
    private static final String OC_ORDER_ID = "order_id";
    private static final String OC_ORDER_JSON = "order_json";
    private static final String OC_ORDER_STATUS = "order_status";
    private static final String OC_CREATED_AT = "created_at";
    private static final String OC_CACHED_AT = "cached_at";

    // ═══════════════════════════════════════════════════════
    //  TABLE 5: FOOD ITEMS CACHE
    // ═══════════════════════════════════════════════════════
    private static final String TABLE_FOOD_CACHE = "food_cache";
    private static final String FC_ID = "id";
    private static final String FC_FOOD_ID = "food_id";
    private static final String FC_FOOD_JSON = "food_json";
    private static final String FC_CACHED_AT = "cached_at";

    // ═══════════════════════════════════════════════════════
    //  TABLE 6: USER PROFILE CACHE
    // ═══════════════════════════════════════════════════════
    private static final String TABLE_USER_PROFILE = "user_profile";
    private static final String UP_ID = "id";
    private static final String UP_USER_ID = "user_id";
    private static final String UP_NAME = "name";
    private static final String UP_EMAIL = "email";
    private static final String UP_PHONE = "phone";
    private static final String UP_PHOTO_URL = "photo_url";
    private static final String UP_ADDRESS = "address";
    private static final String UP_CACHED_AT = "cached_at";

    // ── Singleton ──────────────────────────────────────────
    private static ReplateDatabaseHelper instance;
    private final Gson gson = new Gson();

    public static synchronized ReplateDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ReplateDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private ReplateDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // ═══════════════════════════════════════════════════════
    //  CREATE / UPGRADE
    // ═══════════════════════════════════════════════════════

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Search History
        db.execSQL("CREATE TABLE " + TABLE_SEARCH_HISTORY + " ("
                + SH_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SH_QUERY + " TEXT UNIQUE, "
                + SH_TIMESTAMP + " INTEGER)");

        // Favorites
        db.execSQL("CREATE TABLE " + TABLE_FAVORITES + " ("
                + FAV_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + FAV_FOOD_ID + " TEXT UNIQUE, "
                + FAV_FOOD_NAME + " TEXT, "
                + FAV_HOTEL_NAME + " TEXT, "
                + FAV_HOTEL_ID + " TEXT, "
                + FAV_IMAGE_URL + " TEXT, "
                + FAV_ORIGINAL_PRICE + " REAL, "
                + FAV_DISCOUNTED_PRICE + " REAL, "
                + FAV_DISCOUNT_PERCENT + " INTEGER, "
                + FAV_CATEGORY + " TEXT, "
                + FAV_DESCRIPTION + " TEXT, "
                + FAV_ADDED_AT + " INTEGER)");

        // Notification Cache
        db.execSQL("CREATE TABLE " + TABLE_NOTIFICATIONS + " ("
                + NT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NT_NOTIFICATION_ID + " TEXT UNIQUE, "
                + NT_TITLE + " TEXT, "
                + NT_BODY + " TEXT, "
                + NT_TIMESTAMP + " INTEGER, "
                + NT_READ + " INTEGER DEFAULT 0, "
                + NT_TYPE + " TEXT, "
                + NT_CATEGORY + " TEXT, "
                + NT_ROUTE + " TEXT, "
                + NT_DEEP_LINK + " TEXT, "
                + NT_ORDER_ID + " TEXT, "
                + NT_HOTEL_ID + " TEXT)");

        // Order History Cache
        db.execSQL("CREATE TABLE " + TABLE_ORDERS + " ("
                + OC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + OC_ORDER_ID + " TEXT UNIQUE, "
                + OC_ORDER_JSON + " TEXT, "
                + OC_ORDER_STATUS + " TEXT, "
                + OC_CREATED_AT + " INTEGER, "
                + OC_CACHED_AT + " INTEGER)");

        // Food Items Cache
        db.execSQL("CREATE TABLE " + TABLE_FOOD_CACHE + " ("
                + FC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + FC_FOOD_ID + " TEXT UNIQUE, "
                + FC_FOOD_JSON + " TEXT, "
                + FC_CACHED_AT + " INTEGER)");

        // User Profile Cache
        db.execSQL("CREATE TABLE " + TABLE_USER_PROFILE + " ("
                + UP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + UP_USER_ID + " TEXT UNIQUE, "
                + UP_NAME + " TEXT, "
                + UP_EMAIL + " TEXT, "
                + UP_PHONE + " TEXT, "
                + UP_PHOTO_URL + " TEXT, "
                + UP_ADDRESS + " TEXT, "
                + UP_CACHED_AT + " INTEGER)");

        // Indices
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sh_timestamp ON " + TABLE_SEARCH_HISTORY + "(" + SH_TIMESTAMP + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fav_food_id ON " + TABLE_FAVORITES + "(" + FAV_FOOD_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nt_timestamp ON " + TABLE_NOTIFICATIONS + "(" + NT_TIMESTAMP + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_oc_created ON " + TABLE_ORDERS + "(" + OC_CREATED_AT + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fc_cached ON " + TABLE_FOOD_CACHE + "(" + FC_CACHED_AT + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_up_user_id ON " + TABLE_USER_PROFILE + "(" + UP_USER_ID + ")");

        Log.d(TAG, "Database created with 6 tables");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v1 → v2: add food_cache table
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_FOOD_CACHE + " ("
                        + FC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + FC_FOOD_ID + " TEXT UNIQUE, "
                        + FC_FOOD_JSON + " TEXT, "
                        + FC_CACHED_AT + " INTEGER)");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fc_cached ON " + TABLE_FOOD_CACHE + "(" + FC_CACHED_AT + ")");
                Log.d(TAG, "Upgraded to v2: added food_cache table");
            } catch (Exception e) {
                Log.w(TAG, "food_cache table may already exist", e);
            }
        }
        if (oldVersion < 3) {
            // v2 → v3: add user_profile table
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_USER_PROFILE + " ("
                        + UP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + UP_USER_ID + " TEXT UNIQUE, "
                        + UP_NAME + " TEXT, "
                        + UP_EMAIL + " TEXT, "
                        + UP_PHONE + " TEXT, "
                        + UP_PHOTO_URL + " TEXT, "
                        + UP_ADDRESS + " TEXT, "
                        + UP_CACHED_AT + " INTEGER)");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_up_user_id ON " + TABLE_USER_PROFILE + "(" + UP_USER_ID + ")");
                Log.d(TAG, "Upgraded to v3: added user_profile table");
            } catch (Exception e) {
                Log.w(TAG, "user_profile table may already exist", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  SEARCH HISTORY — CRUD
    // ═══════════════════════════════════════════════════════

    /**
     * Save a search query. If it already exists, updates the timestamp.
     */
    public void saveSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(SH_QUERY, query.trim().toLowerCase());
            values.put(SH_TIMESTAMP, System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_SEARCH_HISTORY, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "Error saving search query", e);
        }
    }

    /**
     * Get recent search queries, ordered by most recent first.
     */
    public List<String> getRecentSearches(int limit) {
        List<String> queries = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_SEARCH_HISTORY, new String[]{SH_QUERY},
                    null, null, null, null,
                    SH_TIMESTAMP + " DESC", String.valueOf(limit));
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String q = cursor.getString(0);
                    if (q != null) queries.add(q);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading search history", e);
        }
        return queries;
    }

    /**
     * Delete a single search query.
     */
    public void deleteSearchQuery(String query) {
        if (query == null) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_SEARCH_HISTORY, SH_QUERY + "=?",
                    new String[]{query.trim().toLowerCase()});
        } catch (Exception e) {
            Log.e(TAG, "Error deleting search query", e);
        }
    }

    /**
     * Clear all search history.
     */
    public void clearSearchHistory() {
        try {
            getWritableDatabase().delete(TABLE_SEARCH_HISTORY, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing search history", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  FAVORITES — CRUD
    // ═══════════════════════════════════════════════════════

    /**
     * Add a food item to favorites.
     */
    public void addFavorite(FoodItem item) {
        if (item == null || item.getFoodId() == null) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(FAV_FOOD_ID, item.getFoodId());
            values.put(FAV_FOOD_NAME, item.getFoodName());
            values.put(FAV_HOTEL_NAME, item.getHotelName());
            values.put(FAV_HOTEL_ID, item.getHotelId());
            values.put(FAV_IMAGE_URL, item.getImageUrl());
            values.put(FAV_ORIGINAL_PRICE, item.getOriginalPriceValue());
            values.put(FAV_DISCOUNTED_PRICE, item.getDiscountedPriceValue());
            values.put(FAV_DISCOUNT_PERCENT, item.getDiscountPercentValue());
            values.put(FAV_CATEGORY, item.getCategory());
            values.put(FAV_DESCRIPTION, item.getDescription());
            values.put(FAV_ADDED_AT, System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_FAVORITES, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "Error adding favorite", e);
        }
    }

    /**
     * Remove a food item from favorites.
     */
    public void removeFavorite(String foodId) {
        if (foodId == null) return;
        try {
            getWritableDatabase().delete(TABLE_FAVORITES, FAV_FOOD_ID + "=?",
                    new String[]{foodId});
        } catch (Exception e) {
            Log.e(TAG, "Error removing favorite", e);
        }
    }

    /**
     * Check if a food item is in favorites.
     */
    public boolean isFavorite(String foodId) {
        if (foodId == null) return false;
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_FAVORITES, new String[]{FAV_ID},
                    FAV_FOOD_ID + "=?", new String[]{foodId},
                    null, null, null);
            boolean exists = cursor != null && cursor.getCount() > 0;
            if (cursor != null) cursor.close();
            return exists;
        } catch (Exception e) {
            Log.e(TAG, "Error checking favorite", e);
            return false;
        }
    }

    /**
     * Get all favorite food items.
     */
    public List<FoodItem> getAllFavorites() {
        List<FoodItem> favorites = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_FAVORITES, null, null, null,
                    null, null, FAV_ADDED_AT + " DESC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    FoodItem item = new FoodItem();
                    item.setFoodId(getStr(cursor, FAV_FOOD_ID));
                    item.setFoodName(getStr(cursor, FAV_FOOD_NAME));
                    item.setHotelName(getStr(cursor, FAV_HOTEL_NAME));
                    item.setHotelId(getStr(cursor, FAV_HOTEL_ID));
                    item.setImageUrl(getStr(cursor, FAV_IMAGE_URL));
                    item.setOriginalPrice(getDbl(cursor, FAV_ORIGINAL_PRICE));
                    item.setDiscountedPrice(getDbl(cursor, FAV_DISCOUNTED_PRICE));
                    item.setDiscountPercent(getInt(cursor, FAV_DISCOUNT_PERCENT));
                    item.setCategory(getStr(cursor, FAV_CATEGORY));
                    item.setDescription(getStr(cursor, FAV_DESCRIPTION));
                    favorites.add(item);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading favorites", e);
        }
        return favorites;
    }

    /**
     * Get count of favorites.
     */
    public int getFavoriteCount() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_FAVORITES, null);
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                cursor.close();
                return count;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error counting favorites", e);
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════
    //  NOTIFICATION CACHE — CRUD
    // ═══════════════════════════════════════════════════════

    /**
     * Cache a list of notifications (bulk insert/replace).
     */
    public void cacheNotifications(List<AppNotification> notifications) {
        if (notifications == null || notifications.isEmpty()) return;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            for (AppNotification n : notifications) {
                if (n.getNotificationId() == null) continue;
                ContentValues values = new ContentValues();
                values.put(NT_NOTIFICATION_ID, n.getNotificationId());
                values.put(NT_TITLE, n.getTitle());
                values.put(NT_BODY, n.getBody());
                values.put(NT_TIMESTAMP, n.getTimestampValue());
                values.put(NT_READ, n.isRead() ? 1 : 0);
                values.put(NT_TYPE, n.getType());
                values.put(NT_CATEGORY, n.getCategory());
                values.put(NT_ROUTE, n.getRoute());
                values.put(NT_DEEP_LINK, n.getDeepLink());
                values.put(NT_ORDER_ID, n.getOrderId());
                values.put(NT_HOTEL_ID, n.getHotelId());
                db.insertWithOnConflict(TABLE_NOTIFICATIONS, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error caching notifications", e);
        } finally {
            if (db != null) {
                try { db.endTransaction(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Get cached notifications, sorted by newest first.
     */
    public List<AppNotification> getCachedNotifications() {
        List<AppNotification> notifications = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_NOTIFICATIONS, null, null, null,
                    null, null, NT_TIMESTAMP + " DESC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    AppNotification n = new AppNotification();
                    n.setNotificationId(getStr(cursor, NT_NOTIFICATION_ID));
                    n.setTitle(getStr(cursor, NT_TITLE));
                    n.setBody(getStr(cursor, NT_BODY));
                    n.setTimestamp(getLng(cursor, NT_TIMESTAMP));
                    n.setRead(getInt(cursor, NT_READ) == 1);
                    n.setType(getStr(cursor, NT_TYPE));
                    n.setCategory(getStr(cursor, NT_CATEGORY));
                    n.setRoute(getStr(cursor, NT_ROUTE));
                    n.setDeepLink(getStr(cursor, NT_DEEP_LINK));
                    n.setOrderId(getStr(cursor, NT_ORDER_ID));
                    n.setHotelId(getStr(cursor, NT_HOTEL_ID));
                    notifications.add(n);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached notifications", e);
        }
        return notifications;
    }

    /**
     * Clear notification cache.
     */
    public void clearNotificationCache() {
        try {
            getWritableDatabase().delete(TABLE_NOTIFICATIONS, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing notification cache", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  ORDER HISTORY CACHE — CRUD
    // ═══════════════════════════════════════════════════════

    /**
     * Cache a list of orders (serialized as JSON via Gson).
     */
    public void cacheOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            long now = System.currentTimeMillis();
            for (Order order : orders) {
                String orderId = order.getOrderId();
                if (orderId == null) continue;
                ContentValues values = new ContentValues();
                values.put(OC_ORDER_ID, orderId);
                values.put(OC_ORDER_JSON, gson.toJson(order));
                values.put(OC_ORDER_STATUS, order.getOrderStatus());
                values.put(OC_CREATED_AT, order.getCreatedAtValue());
                values.put(OC_CACHED_AT, now);
                db.insertWithOnConflict(TABLE_ORDERS, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error caching orders", e);
        } finally {
            if (db != null) {
                try { db.endTransaction(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Get cached orders, sorted by newest first.
     */
    public List<Order> getCachedOrders() {
        List<Order> orders = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_ORDERS, null, null, null,
                    null, null, OC_CREATED_AT + " DESC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String json = getStr(cursor, OC_ORDER_JSON);
                    if (json != null) {
                        try {
                            Order order = gson.fromJson(json, Order.class);
                            if (order != null) {
                                // Ensure orderId is set (Gson may not serialize @Exclude)
                                String oid = getStr(cursor, OC_ORDER_ID);
                                if (order.getOrderId() == null && oid != null) {
                                    order.setOrderId(oid);
                                }
                                orders.add(order);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to deserialize cached order", e);
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached orders", e);
        }
        return orders;
    }

    /**
     * Clear order cache.
     */
    public void clearOrderCache() {
        try {
            getWritableDatabase().delete(TABLE_ORDERS, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing order cache", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  FOOD ITEMS CACHE — CRUD
    // ═══════════════════════════════════════════════════════

    /**
     * Cache a list of food items (serialized as JSON).
     * Replaces previous cache completely.
     */
    public void cacheFoodItems(List<FoodItem> items) {
        if (items == null || items.isEmpty()) return;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            // Clear old cache
            db.delete(TABLE_FOOD_CACHE, null, null);
            long now = System.currentTimeMillis();
            for (FoodItem item : items) {
                String foodId = item.getFoodId();
                if (foodId == null) continue;
                ContentValues values = new ContentValues();
                values.put(FC_FOOD_ID, foodId);
                values.put(FC_FOOD_JSON, gson.toJson(item));
                values.put(FC_CACHED_AT, now);
                db.insertWithOnConflict(TABLE_FOOD_CACHE, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error caching food items", e);
        } finally {
            if (db != null) {
                try { db.endTransaction(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Get cached food items.
     */
    public List<FoodItem> getCachedFoodItems() {
        List<FoodItem> items = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_FOOD_CACHE, null, null, null,
                    null, null, FC_ID + " ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String json = getStr(cursor, FC_FOOD_JSON);
                    if (json != null) {
                        try {
                            FoodItem item = gson.fromJson(json, FoodItem.class);
                            if (item != null) {
                                String fid = getStr(cursor, FC_FOOD_ID);
                                if (item.getFoodId() == null && fid != null) {
                                    item.setFoodId(fid);
                                }
                                items.add(item);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to deserialize cached food item", e);
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached food items", e);
        }
        return items;
    }

    /**
     * Get the timestamp when food items were last cached.
     * Returns 0 if no cache exists.
     */
    public long getFoodCacheTimestamp() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT MAX(" + FC_CACHED_AT + ") FROM " + TABLE_FOOD_CACHE, null);
            if (cursor != null && cursor.moveToFirst()) {
                long ts = cursor.getLong(0);
                cursor.close();
                return ts;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting food cache timestamp", e);
        }
        return 0;
    }

    /**
     * Check if food cache is still fresh (within TTL).
     */
    public boolean isFoodCacheFresh(long ttlMs) {
        long cacheTs = getFoodCacheTimestamp();
        if (cacheTs == 0) return false;
        return (System.currentTimeMillis() - cacheTs) < ttlMs;
    }

    /**
     * Clear food items cache.
     */
    public void clearFoodCache() {
        try {
            getWritableDatabase().delete(TABLE_FOOD_CACHE, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing food cache", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  USER PROFILE CACHE — CRUD
    // ═══════════════════════════════════════════════════════

    /**
     * Cache user profile data for instant display.
     */
    public void cacheUserProfile(String userId, String name, String email, String phone, String photoUrl, String address) {
        if (userId == null) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(UP_USER_ID, userId);
            values.put(UP_NAME, name);
            values.put(UP_EMAIL, email);
            values.put(UP_PHONE, phone);
            values.put(UP_PHOTO_URL, photoUrl);
            values.put(UP_ADDRESS, address);
            values.put(UP_CACHED_AT, System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_USER_PROFILE, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "Error caching user profile", e);
        }
    }

    /**
     * Get cached user profile. Returns null if not cached.
     * Returns a Map with keys: name, email, phone, photoUrl, address
     */
    public java.util.Map<String, String> getCachedUserProfile(String userId) {
        if (userId == null) return null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_USER_PROFILE, null,
                    UP_USER_ID + "=?", new String[]{userId},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                java.util.Map<String, String> profile = new java.util.HashMap<>();
                profile.put("name", getStr(cursor, UP_NAME));
                profile.put("email", getStr(cursor, UP_EMAIL));
                profile.put("phone", getStr(cursor, UP_PHONE));
                profile.put("photoUrl", getStr(cursor, UP_PHOTO_URL));
                profile.put("address", getStr(cursor, UP_ADDRESS));
                cursor.close();
                return profile;
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached user profile", e);
        }
        return null;
    }

    /**
     * Clear user profile cache.
     */
    public void clearUserProfileCache() {
        try {
            getWritableDatabase().delete(TABLE_USER_PROFILE, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing user profile cache", e);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  UTILITY — safe cursor reading
    // ═══════════════════════════════════════════════════════

    private String getStr(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getString(idx) : null;
    }

    private long getLng(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getLong(idx) : 0;
    }

    private int getInt(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getInt(idx) : 0;
    }

    private double getDbl(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getDouble(idx) : 0.0;
    }
}

