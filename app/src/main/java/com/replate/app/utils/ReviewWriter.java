package com.replate.app.utils;

import androidx.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.replate.app.models.Order;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves the rating a customer gives for a finished order.
 * <p>
 * One review is kept per order for the hotel, plus a copy per dish. The dish page looks reviews up
 * by foodId, so without the per-dish copies an order review never appeared there and never counted
 * as a verified purchase.
 */
public final class ReviewWriter {

    private ReviewWriter() {}

    // Writes the order review and a copy for each dish.
    public static void saveOrderReview(@NonNull DatabaseReference root, @NonNull Order order,
                                       @NonNull String userId, float rating, @NonNull String comment) {
        String orderId = order.getOrderId();
        if (orderId == null || orderId.isEmpty()) return;

        long now = System.currentTimeMillis();
        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("orderId", orderId);
        reviewData.put("userId", userId);
        reviewData.put("hotelId", order.getHotelId() != null ? order.getHotelId() : "");
        reviewData.put("hotelName", order.getHotelName() != null ? order.getHotelName() : "");
        reviewData.put("rating", rating);
        reviewData.put("comment", comment);
        reviewData.put("createdAt", now);

        Map<String, Object> writes = new HashMap<>();
        writes.put(orderId, reviewData);

        Object lines = order.getItems();
        Collection<?> entries = lines instanceof List
                ? (List<?>) lines
                : lines instanceof Map ? ((Map<?, ?>) lines).values() : null;
        if (entries != null) {
            for (Object entry : entries) {
                if (!(entry instanceof Map)) continue;
                Object foodIdValue = ((Map<?, ?>) entry).get("foodId");
                String foodId = foodIdValue instanceof String ? (String) foodIdValue : "";
                // Firebase keys can't contain . $ # [ ] /
                if (foodId.isEmpty() || !foodId.matches("[A-Za-z0-9_-]+")) continue;

                Map<String, Object> perDish = new HashMap<>(reviewData);
                perDish.put("foodId", foodId);
                perDish.put("userName", order.getCustomerName() != null ? order.getCustomerName() : "");
                perDish.put("verifiedPurchase", true);
                writes.put(orderId + "_" + foodId, perDish);
            }
        }

        root.child("reviews").updateChildren(writes);
    }
}
