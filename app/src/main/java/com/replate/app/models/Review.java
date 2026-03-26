package com.replate.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.io.Serializable;

@IgnoreExtraProperties
public class Review implements Serializable {
    private String reviewId;
    private String foodId;
    private String userId;
    private String userName;
    private Object rating;
    private String comment;
    private Object createdAt;
    private Object verifiedPurchase;
    private String hotelId;
    private String hotelName;
    private String orderId;

    public Review() {
    }

    public String getReviewId() {
        return reviewId;
    }

    public void setReviewId(String reviewId) {
        this.reviewId = reviewId;
    }

    public String getFoodId() {
        return foodId;
    }

    public void setFoodId(String foodId) {
        this.foodId = foodId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName == null || userName.trim().isEmpty() ? "Anonymous" : userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Object getRating() {
        return rating;
    }

    public void setRating(Object rating) {
        this.rating = rating;
    }

    public float getRatingValue() {
        if (rating == null) return 0f;
        if (rating instanceof Number) return ((Number) rating).floatValue();
        try {
            return Float.parseFloat(String.valueOf(rating));
        } catch (Exception ignored) {
            return 0f;
        }
    }

    public String getComment() {
        return comment == null ? "" : comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Object getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Object createdAt) {
        this.createdAt = createdAt;
    }

    public long getCreatedAtValue() {
        if (createdAt == null) return 0L;
        if (createdAt instanceof Number) return ((Number) createdAt).longValue();
        try {
            return Long.parseLong(String.valueOf(createdAt));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public Object getVerifiedPurchase() {
        return verifiedPurchase;
    }

    public void setVerifiedPurchase(Object verifiedPurchase) {
        this.verifiedPurchase = verifiedPurchase;
    }

    @Exclude
    public boolean isVerifiedPurchase() {
        if (verifiedPurchase == null) return false;
        if (verifiedPurchase instanceof Boolean) return (Boolean) verifiedPurchase;
        return "true".equalsIgnoreCase(String.valueOf(verifiedPurchase));
    }

    public String getHotelId() {
        return hotelId;
    }

    public void setHotelId(String hotelId) {
        this.hotelId = hotelId;
    }

    public String getHotelName() {
        return hotelName;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
