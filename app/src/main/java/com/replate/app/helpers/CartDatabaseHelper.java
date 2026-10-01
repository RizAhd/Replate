package com.replate.app.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.replate.app.models.CartItem;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite Database Helper for offline shopping cart
 */
public class CartDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ReplateCartDB";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CART = "cart_items";
    private static final String COL_ID = "id";
    private static final String COL_FOOD_ID = "food_id";
    private static final String COL_FOOD_NAME = "food_name";
    private static final String COL_HOTEL_NAME = "hotel_name";
    private static final String COL_HOTEL_ID = "hotel_id";
    private static final String COL_IMAGE_URL = "image_url";
    private static final String COL_PRICE = "price";
    private static final String COL_ORIGINAL_PRICE = "original_price";
    private static final String COL_QUANTITY = "quantity";
    private static final String COL_DISCOUNT = "discount_percent";

    private static CartDatabaseHelper instance;

    public static synchronized CartDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new CartDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private CartDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_CART + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_FOOD_ID + " TEXT UNIQUE, "
                + COL_FOOD_NAME + " TEXT, "
                + COL_HOTEL_NAME + " TEXT, "
                + COL_HOTEL_ID + " TEXT, "
                + COL_IMAGE_URL + " TEXT, "
                + COL_PRICE + " REAL, "
                + COL_ORIGINAL_PRICE + " REAL, "
                + COL_QUANTITY + " INTEGER DEFAULT 1, "
                + COL_DISCOUNT + " INTEGER DEFAULT 0)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CART);
        onCreate(db);
    }

    /**
     * Add item to cart or update quantity if it already exists
     */
    public void addItem(CartItem item) {
        // Items without an id can't be matched or bound as a query argument
        if (item == null || item.getFoodId() == null || item.getFoodId().isEmpty()) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();

        // Check if item already exists
        Cursor cursor = db.query(TABLE_CART, null,
                COL_FOOD_ID + "=?", new String[]{item.getFoodId()},
                null, null, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                // Item exists, update quantity
                int currentQty = cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY));
                ContentValues values = new ContentValues();
                values.put(COL_QUANTITY, currentQty + item.getQuantity());
                db.update(TABLE_CART, values, COL_FOOD_ID + "=?", new String[]{item.getFoodId()});
            } else {
                // New item
                ContentValues values = new ContentValues();
                values.put(COL_FOOD_ID, item.getFoodId());
                values.put(COL_FOOD_NAME, item.getFoodName());
                values.put(COL_HOTEL_NAME, item.getHotelName());
                values.put(COL_HOTEL_ID, item.getHotelId());
                values.put(COL_IMAGE_URL, item.getImageUrl());
                values.put(COL_PRICE, item.getPrice());
                values.put(COL_ORIGINAL_PRICE, item.getOriginalPrice());
                values.put(COL_QUANTITY, item.getQuantity());
                values.put(COL_DISCOUNT, item.getDiscountPercent());
                db.insert(TABLE_CART, null, values);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Update item quantity
     */
    public void updateQuantity(String foodId, int quantity) {
        if (foodId == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_QUANTITY, quantity);
        db.update(TABLE_CART, values, COL_FOOD_ID + "=?", new String[]{foodId});
    }

    /**
     * Remove item from cart
     */
    public void removeItem(String foodId) {
        if (foodId == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CART, COL_FOOD_ID + "=?", new String[]{foodId});
    }

    /**
     * Get all cart items
     */
    public List<CartItem> getAllItems() {
        List<CartItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(TABLE_CART, null, null, null, null, null, null);

        if (cursor == null) {
            return items;
        }
        try {
            if (cursor.moveToFirst()) {
                do {
                    CartItem item = new CartItem();
                    item.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)));
                    item.setFoodId(cursor.getString(cursor.getColumnIndexOrThrow(COL_FOOD_ID)));
                    item.setFoodName(cursor.getString(cursor.getColumnIndexOrThrow(COL_FOOD_NAME)));
                    item.setHotelName(cursor.getString(cursor.getColumnIndexOrThrow(COL_HOTEL_NAME)));
                    item.setHotelId(cursor.getString(cursor.getColumnIndexOrThrow(COL_HOTEL_ID)));
                    item.setImageUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)));
                    item.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)));
                    item.setOriginalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_ORIGINAL_PRICE)));
                    item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY)));
                    item.setDiscountPercent(cursor.getInt(cursor.getColumnIndexOrThrow(COL_DISCOUNT)));
                    items.add(item);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        return items;
    }

    /**
     * Get total cart amount
     */
    public double getCartTotal() {
        double total = 0;
        for (CartItem item : getAllItems()) {
            total += item.getSubtotal();
        }
        return total;
    }

    /**
     * Get number of items in cart
     */
    public int getCartCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_QUANTITY + ") FROM " + TABLE_CART, null);
        int count = 0;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    count = cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return count;
    }

    /**
     * Clear all items from cart
     */
    public void clearCart() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CART, null, null);
    }

    /**
     * How many of this item are already in the cart (0 when it is not there)
     */
    public int getQuantityInCart(String foodId) {
        if (foodId == null) return 0;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CART, new String[]{COL_QUANTITY},
                COL_FOOD_ID + "=?", new String[]{foodId},
                null, null, null);
        try {
            return cursor != null && cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Check if item is in cart
     */
    public boolean isInCart(String foodId) {
        if (foodId == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CART, null,
                COL_FOOD_ID + "=?", new String[]{foodId},
                null, null, null);
        boolean exists = cursor != null && cursor.getCount() > 0;
        if (cursor != null) cursor.close();
        return exists;
    }
}

