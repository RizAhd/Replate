package com.replate.app.models;

import com.google.firebase.database.IgnoreExtraProperties;
import java.io.Serializable;

/**
 * Model class for Notifications
 */
@IgnoreExtraProperties
public class AppNotification implements Serializable {
    private String notificationId;
    private String title;
    private String body;
    private Object timestamp;
    private boolean read;
    private String type;
    private String category;
    private String route;
    private String deepLink;
    private String orderId;
    private String hotelId;

    public AppNotification() {}

    public String getNotificationId() { return notificationId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Object getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public String getRoute() { return route; }
    public String getDeepLink() { return deepLink; }
    public String getOrderId() { return orderId; }
    public String getHotelId() { return hotelId; }

    // Reads the time as a number, whatever form it was saved in.
    public long getTimestampValue() {
        if (timestamp == null) return 0L;
        if (timestamp instanceof Number) return ((Number) timestamp).longValue();
        try { return Long.parseLong(timestamp.toString()); } catch (Exception e) { return 0L; }
    }

    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }
    public void setRead(boolean read) { this.read = read; }
    public void setType(String type) { this.type = type; }
    public void setCategory(String category) { this.category = category; }
    public void setRoute(String route) { this.route = route; }
    public void setDeepLink(String deepLink) { this.deepLink = deepLink; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
}
