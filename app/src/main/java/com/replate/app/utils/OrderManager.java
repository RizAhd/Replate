package com.replate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.replate.app.models.FoodItem;
import com.replate.app.models.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * OrderManager - Centralized order creation, processing, and notification
 * Handles complete order lifecycle from payment confirmation to delivery
 */
public class OrderManager {
    private static final String TAG = "OrderManager";
    private static final int OTP_LENGTH = 6;

    private final Context context;
    private final FirebaseAuth mAuth;
    private final DatabaseReference dbRef;

    public interface OrderCreationCallback {
        void onSuccess(Order order);
        void onFailure(String errorMessage);
    }

    public OrderManager(Context context) {
        this.context = context;
        this.mAuth = FirebaseAuth.getInstance();
        this.dbRef = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Create new order after successful payment
     */
    public void createOrderAfterPayment(
            FoodItem foodItem,
            int quantity,
            double totalPrice,
            String pickupTime,
            String specialInstructions,
            OrderCreationCallback callback) {

        if (mAuth.getCurrentUser() == null) {
            callback.onFailure("User not authenticated");
            return;
        }

        try {
            String orderId = "RPL-" + System.currentTimeMillis();
            String otpCode = generateOTP();

            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(mAuth.getCurrentUser().getUid());
            order.setHotelId(foodItem.getHotelId());
            order.setHotelName(foodItem.getHotelName());
            order.setPickupTime(pickupTime);
            order.setOtpCode(otpCode);
            order.setQrCode(orderId); // keep same value for QR and pickup code
            order.setPaymentStatus("paid");
            order.setOrderStatus("pending_pickup");
            order.setPaymentMethod("payhere");
            order.setTotalAmount(totalPrice);
            order.setOriginalTotal(totalPrice);
            order.setCreatedAt(System.currentTimeMillis());
            order.setUpdatedAt(System.currentTimeMillis());

            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> firstItem = new HashMap<>();
            firstItem.put("foodId", foodItem.getFoodId());
            firstItem.put("foodName", foodItem.getFoodName());
            firstItem.put("quantity", quantity);
            firstItem.put("price", foodItem.getDiscountedPriceValue());
            firstItem.put("imageUrl", foodItem.getImageUrl());
            items.add(firstItem);
            order.setItems(items);

            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderId", orderId);
            orderData.put("userId", order.getUserId());
            orderData.put("hotelId", order.getHotelId());
            orderData.put("hotelName", order.getHotelName());
            orderData.put("foodItemId", foodItem.getFoodId());
            orderData.put("foodName", foodItem.getFoodName());
            orderData.put("quantity", quantity);
            orderData.put("totalPrice", totalPrice);
            orderData.put("totalAmount", totalPrice);
            orderData.put("pickupTime", pickupTime);
            orderData.put("otpCode", otpCode);
            orderData.put("pickupCode", orderId);
            orderData.put("qrCode", orderId);
            orderData.put("paymentMethod", "payhere");
            orderData.put("paymentStatus", "paid");
            orderData.put("orderStatus", "pending_pickup");
            orderData.put("specialInstructions", specialInstructions == null ? "" : specialInstructions);
            orderData.put("items", items);
            orderData.put("createdAt", System.currentTimeMillis());
            orderData.put("completedAt", 0);

            dbRef.child("orders").child(orderId).setValue(orderData)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Order created: " + orderId);
                            decreaseFoodQuantity(foodItem.getFoodId(), quantity);
                            sendOrderNotifications(order, foodItem.getFoodName(), quantity);
                            indexOrder(order);
                            callback.onSuccess(order);
                        } else {
                            String error = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Unknown error creating order";
                            callback.onFailure(error);
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error creating order", e);
            callback.onFailure(e.getMessage());
        }
    }

    /**
     * Decrease food item quantity in Firebase
     */
    private void decreaseFoodQuantity(String foodId, int quantity) {
        dbRef.child("foodItems").child(foodId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Object qtyObj = snapshot.child("quantity").getValue();
                        int currentQty = (int) toLong(qtyObj);
                        int newQty = Math.max(0, currentQty - quantity);

                        dbRef.child("foodItems").child(foodId).child("quantity")
                                .setValue(newQty);

                        // Auto-mark as sold out if quantity reaches 0
                        if (newQty == 0) {
                            dbRef.child("foodItems").child(foodId).child("status")
                                    .setValue("sold_out");
                        }

                        Log.d(TAG, "Updated food qty: " + foodId + " -> " + newQty);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "Failed to decrease qty", error.toException());
                    }
                });
    }

    /**
     * Send push notifications to hotel and customer
     */
    private void sendOrderNotifications(Order order, String fallbackFoodName, int fallbackQty) {
        String foodName = extractPrimaryFoodName(order, fallbackFoodName);
        int qty = extractPrimaryQuantity(order, fallbackQty);

        // Notify hotel
        sendNotificationToHotel(order.getHotelId(),
                "New Order!",
                "You have a new order: " + foodName + " x" + qty);

        // Notify customer (via FCM topic or direct token)
        sendNotificationToCustomer(order.getUserId(),
                "Order Confirmed ✅",
                "Your " + foodName + " is ready for pickup at " +
                        order.getHotelName() + " at " + order.getPickupTime());
    }

    /**
     * Send notification to hotel (via FCM topic)
     */
    private void sendNotificationToHotel(String hotelId, String title, String message) {
        try {
            // Subscribe to hotel-specific topic
            String hotelTopic = "hotel_" + hotelId;
            FirebaseMessaging.getInstance().subscribeToTopic(hotelTopic);

            Log.d(TAG, "Notification queued for hotel topic: " + hotelTopic);
            // Note: Actual FCM send would be done from backend/Cloud Functions
        } catch (Exception e) {
            Log.w(TAG, "Error sending hotel notification", e);
        }
    }

    /**
     * Send notification to customer (via FCM topic)
     */
    private void sendNotificationToCustomer(String userId, String title, String message) {
        try {
            String userTopic = "user_" + userId;
            FirebaseMessaging.getInstance().subscribeToTopic(userTopic);

            Log.d(TAG, "Notification queued for user topic: " + userTopic);
            // Note: Actual FCM send would be done from backend/Cloud Functions
        } catch (Exception e) {
            Log.w(TAG, "Error sending customer notification", e);
        }
    }

    /**
     * Index order for quick queries
     */
    private void indexOrder(Order order) {
        try {
            // Index by hotel for hotel dashboard
            if (order.getHotelId() != null) {
                Map<String, Object> hotelIndex = new HashMap<>();
                hotelIndex.put(order.getOrderId(), true);
                dbRef.child("indexes").child("ordersByHotel")
                        .child(order.getHotelId())
                        .updateChildren(hotelIndex);
            }

            // Index by user for user orders
            if (order.getUserId() != null) {
                Map<String, Object> userIndex = new HashMap<>();
                userIndex.put(order.getOrderId(), true);
                dbRef.child("indexes").child("ordersByUser")
                        .child(order.getUserId())
                        .updateChildren(userIndex);
            }

            // Index by status
            Map<String, Object> statusIndex = new HashMap<>();
            statusIndex.put(order.getOrderId(), true);
            dbRef.child("indexes").child("ordersByStatus")
                    .child(order.getOrderStatus())
                    .updateChildren(statusIndex);

            Log.d(TAG, "Order indexed for queries");
        } catch (Exception e) {
            Log.w(TAG, "Error indexing order", e);
        }
    }

    /**
     * Mark order as completed (after hotel verifies QR/OTP)
     */
    public void completeOrder(String orderId, OrderCompletionCallback callback) {
        long completedTime = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("orderStatus", "completed");
        updates.put("completedAt", completedTime);

        dbRef.child("orders").child(orderId).updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Order completed: " + orderId);

                        dbRef.child("orders").child(orderId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        Order order = snapshot.getValue(Order.class);
                                        if (order != null) {
                                            sendOrderNotifications(order, "Order", 1);
                                            updateHotelStats(order);
                                        }
                                        callback.onSuccess(orderId);
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        callback.onSuccess(orderId);
                                    }
                                });
                    } else {
                        callback.onFailure(task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error");
                    }
                });
    }

    /**
     * Update hotel statistics after order completion
     */
    private void updateHotelStats(Order order) {
        try {
            String hotelId = order.getHotelId();
            if (hotelId == null) return;

            dbRef.child("hotels").child(hotelId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long currentRevenue = toLong(snapshot.child("totalRevenue").getValue());
                            long currentOrders = toLong(snapshot.child("totalOrders").getValue());

                            Map<String, Object> updates = new HashMap<>();
                            updates.put("totalRevenue", currentRevenue + (long) order.getTotalAmountValue());
                            updates.put("totalOrders", currentOrders + 1);
                            updates.put("lastOrderAt", System.currentTimeMillis());

                            dbRef.child("hotels").child(hotelId).updateChildren(updates);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG, "Failed to update hotel stats", error.toException());
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "Error updating hotel stats", e);
        }
    }

    private String extractPrimaryFoodName(Order order, String fallback) {
        Object itemsObj = order.getItems();
        if (itemsObj instanceof List && !((List<?>) itemsObj).isEmpty()) {
            Object first = ((List<?>) itemsObj).get(0);
            if (first instanceof Map) {
                Object name = ((Map<?, ?>) first).get("foodName");
                if (name != null) return String.valueOf(name);
            }
        }
        if (itemsObj instanceof Map && !((Map<?, ?>) itemsObj).isEmpty()) {
            Object first = ((Map<?, ?>) itemsObj).values().iterator().next();
            if (first instanceof Map) {
                Object name = ((Map<?, ?>) first).get("foodName");
                if (name != null) return String.valueOf(name);
            }
        }
        return fallback;
    }

    private int extractPrimaryQuantity(Order order, int fallback) {
        Object itemsObj = order.getItems();
        if (itemsObj instanceof List && !((List<?>) itemsObj).isEmpty()) {
            Object first = ((List<?>) itemsObj).get(0);
            if (first instanceof Map) {
                return (int) toLong(((Map<?, ?>) first).get("quantity"));
            }
        }
        if (itemsObj instanceof Map && !((Map<?, ?>) itemsObj).isEmpty()) {
            Object first = ((Map<?, ?>) itemsObj).values().iterator().next();
            if (first instanceof Map) {
                return (int) toLong(((Map<?, ?>) first).get("quantity"));
            }
        }
        return fallback;
    }

    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Double) return ((Double) obj).longValue();
        if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * Callback for order completion
     */
    public interface OrderCompletionCallback {
        void onSuccess(String orderId);
        void onFailure(String errorMessage);
    }
}
