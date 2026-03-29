package com.replate.app.models;

import java.io.Serializable;

/**
 * Chat Message model for customer-hotel conversations.
 */
public class ChatMessage implements Serializable {

    private String messageId;
    private String conversationId;  // Format: hotelId_customerId
    private String senderId;
    private String senderName;
    private String senderType;  // "customer" or "hotel"
    private String receiverId;
    private String message;
    private long timestamp;
    private boolean read;
    private String hotelId;
    private String hotelName;
    private String customerId;
    private String customerName;

    public ChatMessage() {}

    public ChatMessage(String messageId, String conversationId, String senderId,
                       String senderName, String senderType, String receiverId,
                       String message, long timestamp, boolean read,
                       String hotelId, String hotelName, String customerId, String customerName) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderType = senderType;
        this.receiverId = receiverId;
        this.message = message;
        this.timestamp = timestamp;
        this.read = read;
        this.hotelId = hotelId;
        this.hotelName = hotelName;
        this.customerId = customerId;
        this.customerName = customerName;
    }

    // Getters and Setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }

    public String getHotelName() { return hotelName; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    /**
     * Generate conversation ID from hotel and customer IDs
     */
    public static String generateConversationId(String hotelId, String customerId) {
        return hotelId + "_" + customerId;
    }

    /**
     * Check if this message was sent by the current user
     */
    public boolean isSentByMe(String currentUserId) {
        return senderId != null && senderId.equals(currentUserId);
    }
}

