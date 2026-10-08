package com.replate.app.utils;

/** Helpers for chat conversation keys, which are always "{hotelKey}_{customerUid}". */
public final class ChatKeys {

    private ChatKeys() {}

    /** The hotel key of a chat where {@code uid} is the customer, or null when it is not one. */
    public static String hotelKeyForCustomerChat(String conversationId, String uid) {
        if (conversationId == null || uid == null || uid.isEmpty()) return null;
        String suffix = "_" + uid;
        if (!conversationId.endsWith(suffix)) return null;
        String hotelKey = conversationId.substring(0, conversationId.length() - suffix.length());
        return hotelKey.isEmpty() ? null : hotelKey;
    }
}
