package com.replate.app.helpers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.replate.app.models.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite Database Helper for offline chat message caching.
 * Messages are synced with Firebase but cached locally for offline access.
 */
public class ChatDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ReplateChatDB";
    private static final int DATABASE_VERSION = 1;

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
    private static final String COL_SYNCED = "synced";

    // Conversations table (for quick lookup)
    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String COL_CONV_ID = "conversation_id";
    private static final String COL_LAST_MESSAGE = "last_message";
    private static final String COL_LAST_TIMESTAMP = "last_timestamp";
    private static final String COL_UNREAD_COUNT = "unread_count";

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
                + COL_LAST_MESSAGE + " TEXT, "
                + COL_LAST_TIMESTAMP + " INTEGER, "
                + COL_UNREAD_COUNT + " INTEGER DEFAULT 0)";
        db.execSQL(createConversations);

        // Create indices for faster lookups
        db.execSQL("CREATE INDEX idx_conv_id ON " + TABLE_MESSAGES + "(" + COL_CONVERSATION_ID + ")");
        db.execSQL("CREATE INDEX idx_timestamp ON " + TABLE_MESSAGES + "(" + COL_TIMESTAMP + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONVERSATIONS);
        onCreate(db);
    }

    /**
     * Insert or update a chat message
     */
    public void saveMessage(ChatMessage msg) {
        SQLiteDatabase db = getWritableDatabase();

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
        values.put(COL_SYNCED, 1);

        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        // Update conversation summary
        updateConversationSummary(msg);
    }

    /**
     * Insert a message pending sync (offline send)
     */
    public void savePendingMessage(ChatMessage msg) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_MESSAGE_ID, msg.getMessageId());
        values.put(COL_CONVERSATION_ID, msg.getConversationId());
        values.put(COL_SENDER_ID, msg.getSenderId());
        values.put(COL_SENDER_NAME, msg.getSenderName());
        values.put(COL_SENDER_TYPE, msg.getSenderType());
        values.put(COL_RECEIVER_ID, msg.getReceiverId());
        values.put(COL_MESSAGE, msg.getMessage());
        values.put(COL_TIMESTAMP, msg.getTimestamp());
        values.put(COL_READ, 0);
        values.put(COL_HOTEL_ID, msg.getHotelId());
        values.put(COL_HOTEL_NAME, msg.getHotelName());
        values.put(COL_CUSTOMER_ID, msg.getCustomerId());
        values.put(COL_CUSTOMER_NAME, msg.getCustomerName());
        values.put(COL_SYNCED, 0);  // Not synced yet

        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Mark message as synced
     */
    public void markSynced(String messageId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNCED, 1);
        db.update(TABLE_MESSAGES, values, COL_MESSAGE_ID + "=?", new String[]{messageId});
    }

    /**
     * Get all messages for a conversation
     */
    public List<ChatMessage> getMessages(String conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(TABLE_MESSAGES, null,
                COL_CONVERSATION_ID + "=?", new String[]{conversationId},
                null, null, COL_TIMESTAMP + " ASC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                messages.add(cursorToMessage(cursor));
            } while (cursor.moveToNext());
            cursor.close();
        }

        return messages;
    }

    /**
     * Get pending (unsynced) messages
     */
    public List<ChatMessage> getPendingMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = db.query(TABLE_MESSAGES, null,
                COL_SYNCED + "=0", null,
                null, null, COL_TIMESTAMP + " ASC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                messages.add(cursorToMessage(cursor));
            } while (cursor.moveToNext());
            cursor.close();
        }

        return messages;
    }

    /**
     * Update conversation summary when new message arrives
     */
    private void updateConversationSummary(ChatMessage msg) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_CONV_ID, msg.getConversationId());
        values.put(COL_HOTEL_ID, msg.getHotelId());
        values.put(COL_HOTEL_NAME, msg.getHotelName());
        values.put(COL_CUSTOMER_ID, msg.getCustomerId());
        values.put(COL_CUSTOMER_NAME, msg.getCustomerName());
        values.put(COL_LAST_MESSAGE, msg.getMessage());
        values.put(COL_LAST_TIMESTAMP, msg.getTimestamp());

        db.insertWithOnConflict(TABLE_CONVERSATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Clear all messages for a conversation
     */
    public void clearConversation(String conversationId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MESSAGES, COL_CONVERSATION_ID + "=?", new String[]{conversationId});
        db.delete(TABLE_CONVERSATIONS, COL_CONV_ID + "=?", new String[]{conversationId});
    }

    /**
     * Clear all chat data
     */
    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_MESSAGES, null, null);
        db.delete(TABLE_CONVERSATIONS, null, null);
    }

    private ChatMessage cursorToMessage(Cursor cursor) {
        ChatMessage msg = new ChatMessage();
        msg.setMessageId(cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE_ID)));
        msg.setConversationId(cursor.getString(cursor.getColumnIndexOrThrow(COL_CONVERSATION_ID)));
        msg.setSenderId(cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER_ID)));
        msg.setSenderName(cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER_NAME)));
        msg.setSenderType(cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER_TYPE)));
        msg.setReceiverId(cursor.getString(cursor.getColumnIndexOrThrow(COL_RECEIVER_ID)));
        msg.setMessage(cursor.getString(cursor.getColumnIndexOrThrow(COL_MESSAGE)));
        msg.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)));
        msg.setRead(cursor.getInt(cursor.getColumnIndexOrThrow(COL_READ)) == 1);
        msg.setHotelId(cursor.getString(cursor.getColumnIndexOrThrow(COL_HOTEL_ID)));
        msg.setHotelName(cursor.getString(cursor.getColumnIndexOrThrow(COL_HOTEL_NAME)));
        msg.setCustomerId(cursor.getString(cursor.getColumnIndexOrThrow(COL_CUSTOMER_ID)));
        msg.setCustomerName(cursor.getString(cursor.getColumnIndexOrThrow(COL_CUSTOMER_NAME)));
        return msg;
    }
}

