package com.replate.app.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.replate.app.models.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite Database Helper for offline chat message caching.
 * Messages are synced with Firebase but cached locally for offline access
 * so chat history is always available even without internet.
 */
public class ChatDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "ChatDatabaseHelper";
    private static final String DATABASE_NAME = "ReplateChatDB";
    private static final int DATABASE_VERSION = 2; // Bumped for foodId/foodName columns

    // Messages table
    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String COL_ID = "id";
    private static final String COL_MESSAGE_ID = "message_id";
    private static final String COL_CONVERSATION_ID = "conversation_id";
    private static final String COL_SENDER_ID = "sender_id";
    private static final String COL_SENDER_NAME = "sender_name";
    private static final String COL_SENDER_TYPE = "sender_type";
    private static final String COL_RECEIVER_ID = "receiver_id";
    private static final String COL_MESSAGE = "message";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_READ = "is_read";
    private static final String COL_HOTEL_ID = "hotel_id";
    private static final String COL_HOTEL_NAME = "hotel_name";
    private static final String COL_CUSTOMER_ID = "customer_id";
    private static final String COL_CUSTOMER_NAME = "customer_name";
    private static final String COL_FOOD_ID = "food_id";
    private static final String COL_FOOD_NAME = "food_name";
    private static final String COL_SYNCED = "synced";

    // Conversations table (for quick lookup & offline conversation list)
    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String COL_CONV_ID = "conversation_id";
    private static final String COL_LAST_MESSAGE = "last_message";
    private static final String COL_LAST_TIMESTAMP = "last_timestamp";
    private static final String COL_UNREAD_COUNT = "unread_count";
    private static final String COL_CUSTOMER_EMAIL = "customer_email";
    // COL_FOOD_ID and COL_FOOD_NAME are reused from messages

    private static ChatDatabaseHelper instance;

    public static synchronized ChatDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ChatDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create messages table
        String createMessages = "CREATE TABLE " + TABLE_MESSAGES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_MESSAGE_ID + " TEXT UNIQUE, "
                + COL_CONVERSATION_ID + " TEXT, "
                + COL_SENDER_ID + " TEXT, "
                + COL_SENDER_NAME + " TEXT, "
                + COL_SENDER_TYPE + " TEXT, "
                + COL_RECEIVER_ID + " TEXT, "
                + COL_MESSAGE + " TEXT, "
                + COL_TIMESTAMP + " INTEGER, "
                + COL_READ + " INTEGER DEFAULT 0, "
                + COL_HOTEL_ID + " TEXT, "
                + COL_HOTEL_NAME + " TEXT, "
                + COL_CUSTOMER_ID + " TEXT, "
                + COL_CUSTOMER_NAME + " TEXT, "
                + COL_FOOD_ID + " TEXT, "
                + COL_FOOD_NAME + " TEXT, "
                + COL_SYNCED + " INTEGER DEFAULT 1)";
        db.execSQL(createMessages);

        // Create conversations table
        String createConversations = "CREATE TABLE " + TABLE_CONVERSATIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_CONV_ID + " TEXT UNIQUE, "
                + COL_HOTEL_ID + " TEXT, "
                + COL_HOTEL_NAME + " TEXT, "
                + COL_CUSTOMER_ID + " TEXT, "
                + COL_CUSTOMER_NAME + " TEXT, "
                + COL_CUSTOMER_EMAIL + " TEXT, "
                + COL_FOOD_ID + " TEXT, "
                + COL_FOOD_NAME + " TEXT, "
                + COL_LAST_MESSAGE + " TEXT, "
                + COL_LAST_TIMESTAMP + " INTEGER, "
                + COL_UNREAD_COUNT + " INTEGER DEFAULT 0)";
        db.execSQL(createConversations);

        // Create indices for faster lookups
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conv_id ON " + TABLE_MESSAGES + "(" + COL_CONVERSATION_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON " + TABLE_MESSAGES + "(" + COL_TIMESTAMP + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_synced ON " + TABLE_MESSAGES + "(" + COL_SYNCED + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v1 → v2: add foodId, foodName columns to messages & conversations
            try {
                db.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN " + COL_FOOD_ID + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN " + COL_FOOD_NAME + " TEXT");
                Log.d(TAG, "Upgraded messages table: added food_id, food_name columns");
            } catch (Exception e) {
                Log.w(TAG, "food columns may already exist in messages table", e);
            }

            try {
                db.execSQL("ALTER TABLE " + TABLE_CONVERSATIONS + " ADD COLUMN " + COL_FOOD_ID + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_CONVERSATIONS + " ADD COLUMN " + COL_FOOD_NAME + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_CONVERSATIONS + " ADD COLUMN " + COL_CUSTOMER_EMAIL + " TEXT");
                Log.d(TAG, "Upgraded conversations table: added food_id, food_name, customer_email columns");
            } catch (Exception e) {
                Log.w(TAG, "columns may already exist in conversations table", e);
            }

            // Add synced index if missing
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_synced ON " + TABLE_MESSAGES + "(" + COL_SYNCED + ")");
            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  MESSAGE OPERATIONS
    // ─────────────────────────────────────────────────────────────

    /**
     * Insert or update a chat message (synced from Firebase).
     */
    public void saveMessage(ChatMessage msg) {
        if (msg == null || msg.getMessageId() == null) return;

        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = buildMessageValues(msg);
            values.put(COL_SYNCED, 1);
            db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);

            // Update conversation summary
            updateConversationSummary(msg);
        } catch (Exception e) {
            Log.e(TAG, "Error saving message", e);
        }
    }

    /**
     * Insert a message pending sync (offline send).
     * Marked synced=0 until Firebase confirms.
     */
    public void savePendingMessage(ChatMessage msg) {
        if (msg == null || msg.getMessageId() == null) return;

        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = buildMessageValues(msg);
            values.put(COL_SYNCED, 0); // Not synced yet
            db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);

            // Also update local conversation summary immediately
            updateConversationSummary(msg);
        } catch (Exception e) {
            Log.e(TAG, "Error saving pending message", e);
        }
    }

    /**
     * Build ContentValues from a ChatMessage (shared by save & savePending).
     */
    private ContentValues buildMessageValues(ChatMessage msg) {
        ContentValues values = new ContentValues();
        values.put(COL_MESSAGE_ID, msg.getMessageId());
        values.put(COL_CONVERSATION_ID, msg.getConversationId());
        values.put(COL_SENDER_ID, msg.getSenderId());
        values.put(COL_SENDER_NAME, msg.getSenderName());
        values.put(COL_SENDER_TYPE, msg.getSenderType());
        values.put(COL_RECEIVER_ID, msg.getReceiverId());
        values.put(COL_MESSAGE, msg.getMessage());
        values.put(COL_TIMESTAMP, msg.getTimestamp());
        values.put(COL_READ, msg.isRead() ? 1 : 0);
        values.put(COL_HOTEL_ID, msg.getHotelId());
        values.put(COL_HOTEL_NAME, msg.getHotelName());
        values.put(COL_CUSTOMER_ID, msg.getCustomerId());
        values.put(COL_CUSTOMER_NAME, msg.getCustomerName());
        values.put(COL_FOOD_ID, msg.getFoodId());
        values.put(COL_FOOD_NAME, msg.getFoodName());
        return values;
    }

    /**
     * Mark a message as synced after Firebase confirms.
     */
    public void markSynced(String messageId) {
        if (messageId == null) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_SYNCED, 1);
            db.update(TABLE_MESSAGES, values, COL_MESSAGE_ID + "=?", new String[]{messageId});
        } catch (Exception e) {
            Log.e(TAG, "Error marking synced", e);
        }
    }

    /**
     * Mark a message as read locally.
     */
    public void markMessageRead(String messageId) {
        if (messageId == null) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_READ, 1);
            db.update(TABLE_MESSAGES, values, COL_MESSAGE_ID + "=?", new String[]{messageId});
        } catch (Exception e) {
            Log.e(TAG, "Error marking read", e);
        }
    }

    /**
     * Get all messages for a conversation, ordered by timestamp.
     */
    public List<ChatMessage> getMessages(String conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        if (conversationId == null) return messages;

        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_MESSAGES, null,
                    COL_CONVERSATION_ID + "=?", new String[]{conversationId},
                    null, null, COL_TIMESTAMP + " ASC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    messages.add(cursorToMessage(cursor));
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading messages for conversation: " + conversationId, e);
        }
        return messages;
    }

    /**
     * Get the latest N messages for a conversation (for preview / quick load).
     */
    public List<ChatMessage> getRecentMessages(String conversationId, int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        if (conversationId == null) return messages;

        try {
            SQLiteDatabase db = getReadableDatabase();
            // Sub-query to get latest N then re-order ascending
            Cursor cursor = db.rawQuery(
                    "SELECT * FROM (SELECT * FROM " + TABLE_MESSAGES
                            + " WHERE " + COL_CONVERSATION_ID + "=?"
                            + " ORDER BY " + COL_TIMESTAMP + " DESC LIMIT ?"
                            + ") ORDER BY " + COL_TIMESTAMP + " ASC",
                    new String[]{conversationId, String.valueOf(limit)});

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    messages.add(cursorToMessage(cursor));
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading recent messages", e);
        }
        return messages;
    }

    /**
     * Get pending (un-synced) messages that need to be pushed to Firebase.
     */
    public List<ChatMessage> getPendingMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_MESSAGES, null,
                    COL_SYNCED + "=0", null,
                    null, null, COL_TIMESTAMP + " ASC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    messages.add(cursorToMessage(cursor));
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading pending messages", e);
        }
        return messages;
    }

    /**
     * Check if a message already exists locally by its messageId.
     */
    public boolean messageExists(String messageId) {
        if (messageId == null) return false;
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_MESSAGES,
                    new String[]{COL_MESSAGE_ID},
                    COL_MESSAGE_ID + "=?", new String[]{messageId},
                    null, null, null);
            boolean exists = cursor != null && cursor.getCount() > 0;
            if (cursor != null) cursor.close();
            return exists;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get total message count for a conversation.
     */
    public int getMessageCount(String conversationId) {
        if (conversationId == null) return 0;
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_MESSAGES
                            + " WHERE " + COL_CONVERSATION_ID + "=?",
                    new String[]{conversationId});
            int count = 0;
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            if (cursor != null) cursor.close();
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CONVERSATION OPERATIONS
    // ─────────────────────────────────────────────────────────────

    /**
     * Update conversation summary when a new message arrives.
     */
    private void updateConversationSummary(ChatMessage msg) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_CONV_ID, msg.getConversationId());
            values.put(COL_HOTEL_ID, msg.getHotelId());
            values.put(COL_HOTEL_NAME, msg.getHotelName());
            values.put(COL_CUSTOMER_ID, msg.getCustomerId());
            values.put(COL_CUSTOMER_NAME, msg.getCustomerName());
            values.put(COL_FOOD_ID, msg.getFoodId());
            values.put(COL_FOOD_NAME, msg.getFoodName());
            values.put(COL_LAST_MESSAGE, msg.getMessage());
            values.put(COL_LAST_TIMESTAMP, msg.getTimestamp());

            db.insertWithOnConflict(TABLE_CONVERSATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "Error updating conversation summary", e);
        }
    }

    /**
     * Save or update a conversation entry directly (e.g. from Firebase sync).
     */
    public void saveConversation(String conversationId, String hotelId, String hotelName,
                                  String customerId, String customerName, String customerEmail,
                                  String foodId, String foodName,
                                  String lastMessage, long lastTimestamp) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COL_CONV_ID, conversationId);
            values.put(COL_HOTEL_ID, hotelId);
            values.put(COL_HOTEL_NAME, hotelName);
            values.put(COL_CUSTOMER_ID, customerId);
            values.put(COL_CUSTOMER_NAME, customerName);
            values.put(COL_CUSTOMER_EMAIL, customerEmail);
            values.put(COL_FOOD_ID, foodId);
            values.put(COL_FOOD_NAME, foodName);
            values.put(COL_LAST_MESSAGE, lastMessage);
            values.put(COL_LAST_TIMESTAMP, lastTimestamp);
            db.insertWithOnConflict(TABLE_CONVERSATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            Log.e(TAG, "Error saving conversation", e);
        }
    }

    /**
     * Get all locally cached conversations, ordered by most recent first.
     * Returns a list of Maps so ConversationAdapter can use them directly.
     */
    public List<Map<String, Object>> getConversations() {
        List<Map<String, Object>> conversations = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_CONVERSATIONS, null,
                    null, null, null, null,
                    COL_LAST_TIMESTAMP + " DESC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Map<String, Object> conv = new HashMap<>();
                    conv.put("conversationId", getStringCol(cursor, COL_CONV_ID));
                    conv.put("hotelId", getStringCol(cursor, COL_HOTEL_ID));
                    conv.put("hotelName", getStringCol(cursor, COL_HOTEL_NAME));
                    conv.put("customerId", getStringCol(cursor, COL_CUSTOMER_ID));
                    conv.put("customerName", getStringCol(cursor, COL_CUSTOMER_NAME));
                    conv.put("customerEmail", getStringCol(cursor, COL_CUSTOMER_EMAIL));
                    conv.put("foodId", getStringCol(cursor, COL_FOOD_ID));
                    conv.put("foodName", getStringCol(cursor, COL_FOOD_NAME));
                    conv.put("lastMessage", getStringCol(cursor, COL_LAST_MESSAGE));
                    conv.put("lastTimestamp", getLongCol(cursor, COL_LAST_TIMESTAMP));
                    conversations.add(conv);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading conversations", e);
        }
        return conversations;
    }

    /**
     * Get a single conversation's data.
     */
    public Map<String, Object> getConversation(String conversationId) {
        if (conversationId == null) return null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query(TABLE_CONVERSATIONS, null,
                    COL_CONV_ID + "=?", new String[]{conversationId},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                Map<String, Object> conv = new HashMap<>();
                conv.put("conversationId", getStringCol(cursor, COL_CONV_ID));
                conv.put("hotelId", getStringCol(cursor, COL_HOTEL_ID));
                conv.put("hotelName", getStringCol(cursor, COL_HOTEL_NAME));
                conv.put("customerId", getStringCol(cursor, COL_CUSTOMER_ID));
                conv.put("customerName", getStringCol(cursor, COL_CUSTOMER_NAME));
                conv.put("customerEmail", getStringCol(cursor, COL_CUSTOMER_EMAIL));
                conv.put("foodId", getStringCol(cursor, COL_FOOD_ID));
                conv.put("foodName", getStringCol(cursor, COL_FOOD_NAME));
                conv.put("lastMessage", getStringCol(cursor, COL_LAST_MESSAGE));
                conv.put("lastTimestamp", getLongCol(cursor, COL_LAST_TIMESTAMP));
                cursor.close();
                return conv;
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error loading conversation", e);
        }
        return null;
    }

    /**
     * Get unread message count for a conversation.
     */
    public int getUnreadCount(String conversationId, String currentUserId) {
        if (conversationId == null || currentUserId == null) return 0;
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_MESSAGES
                            + " WHERE " + COL_CONVERSATION_ID + "=?"
                            + " AND " + COL_SENDER_ID + "!=?"
                            + " AND " + COL_READ + "=0",
                    new String[]{conversationId, currentUserId});
            int count = 0;
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            if (cursor != null) cursor.close();
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CLEANUP
    // ─────────────────────────────────────────────────────────────

    /**
     * Clear all messages for a conversation.
     */
    public void clearConversation(String conversationId) {
        if (conversationId == null) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_MESSAGES, COL_CONVERSATION_ID + "=?", new String[]{conversationId});
            db.delete(TABLE_CONVERSATIONS, COL_CONV_ID + "=?", new String[]{conversationId});
        } catch (Exception e) {
            Log.e(TAG, "Error clearing conversation", e);
        }
    }

    /**
     * Clear all chat data (both messages and conversations).
     */
    public void clearAll() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE_MESSAGES, null, null);
            db.delete(TABLE_CONVERSATIONS, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing all chat data", e);
        }
    }

    /**
     * Delete old messages to keep database size manageable.
     * Keeps only messages from the last N days.
     */
    public void pruneOldMessages(int keepDays) {
        try {
            long cutoff = System.currentTimeMillis() - ((long) keepDays * 24 * 60 * 60 * 1000);
            SQLiteDatabase db = getWritableDatabase();
            int deleted = db.delete(TABLE_MESSAGES,
                    COL_TIMESTAMP + "<? AND " + COL_SYNCED + "=1",
                    new String[]{String.valueOf(cutoff)});
            if (deleted > 0) {
                Log.d(TAG, "Pruned " + deleted + " old messages (older than " + keepDays + " days)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pruning old messages", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CURSOR HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Convert a cursor row to a ChatMessage object, restoring ALL fields including foodId/foodName.
     */
    private ChatMessage cursorToMessage(Cursor cursor) {
        ChatMessage msg = new ChatMessage();
        msg.setMessageId(getStringCol(cursor, COL_MESSAGE_ID));
        msg.setConversationId(getStringCol(cursor, COL_CONVERSATION_ID));
        msg.setSenderId(getStringCol(cursor, COL_SENDER_ID));
        msg.setSenderName(getStringCol(cursor, COL_SENDER_NAME));
        msg.setSenderType(getStringCol(cursor, COL_SENDER_TYPE));
        msg.setReceiverId(getStringCol(cursor, COL_RECEIVER_ID));
        msg.setMessage(getStringCol(cursor, COL_MESSAGE));
        msg.setTimestamp(getLongCol(cursor, COL_TIMESTAMP));
        msg.setRead(getIntCol(cursor, COL_READ) == 1);
        msg.setHotelId(getStringCol(cursor, COL_HOTEL_ID));
        msg.setHotelName(getStringCol(cursor, COL_HOTEL_NAME));
        msg.setCustomerId(getStringCol(cursor, COL_CUSTOMER_ID));
        msg.setCustomerName(getStringCol(cursor, COL_CUSTOMER_NAME));
        msg.setFoodId(getStringCol(cursor, COL_FOOD_ID));
        msg.setFoodName(getStringCol(cursor, COL_FOOD_NAME));
        return msg;
    }

    /** Safe string column read — returns null if column not found. */
    private String getStringCol(Cursor cursor, String col) {
        int idx = cursor.getColumnIndex(col);
        return idx >= 0 ? cursor.getString(idx) : null;
    }

    /** Safe long column read — returns 0 if column not found. */
    private long getLongCol(Cursor cursor, String col) {
        int idx = cursor.getColumnIndex(col);
        return idx >= 0 ? cursor.getLong(idx) : 0;
    }

    /** Safe int column read — returns 0 if column not found. */
    private int getIntCol(Cursor cursor, String col) {
        int idx = cursor.getColumnIndex(col);
        return idx >= 0 ? cursor.getInt(idx) : 0;
    }
}
