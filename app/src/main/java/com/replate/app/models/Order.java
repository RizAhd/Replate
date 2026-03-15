package com.replate.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Model class for Orders
 * All numeric fields use Object to handle Firebase String/Number mismatch
 */
@IgnoreExtraProperties
public class Order implements Serializable {
    private String orderId;
    private String oderId; // Firebase sometimes uses this typo
    private String userId;
    private String hotelId;
    private String hotelName;
    private Object items; // Can be List or Map from Firebase
    private Object totalAmount;
    private String orderStatus;
    private String paymentStatus;
    private String paymentMethod;
    private String pickupTime;
    private String qrCode;
    private String otpCode;
    private Object createdAt;
    private Object updatedAt;
    private Object originalTotal;
    private String hotelAddress;
    private String customerName;
    private String customerPhone;

    public Order() {}

    // --- Safe parsing helpers ---
    @Exclude
    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    @Exclude
    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }

    // --- Getters ---
    public String getOrderId() { return orderId != null ? orderId : oderId; }
    public String getOderId() { return oderId; }
    public String getUserId() { return userId; }
    public String getHotelId() { return hotelId; }
    public String getHotelName() { return hotelName; }
    public Object getItems() { return items; }

    @Exclude
    public double getTotalAmountValue() { return toDouble(totalAmount); }
    public Object getTotalAmount() { return totalAmount; }

    public String getOrderStatus() { return orderStatus; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPickupTime() { return pickupTime; }
    public String getQrCode() { return qrCode; }
    public String getOtpCode() { return otpCode; }

    @Exclude
    public long getCreatedAtValue() { return toLong(createdAt); }
    public Object getCreatedAt() { return createdAt; }

    @Exclude
    public long getUpdatedAtValue() { return toLong(updatedAt); }
    public Object getUpdatedAt() { return updatedAt; }

    @Exclude
    public double getOriginalTotalValue() { return toDouble(originalTotal); }
    public Object getOriginalTotal() { return originalTotal; }

    public String getHotelAddress() { return hotelAddress; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }

    // --- Setters ---
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public void setOderId(String oderId) { this.oderId = oderId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }
    public void setItems(Object items) { this.items = items; }
    public void setTotalAmount(Object totalAmount) { this.totalAmount = totalAmount; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setPickupTime(String pickupTime) { this.pickupTime = pickupTime; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Object updatedAt) { this.updatedAt = updatedAt; }
    public void setOriginalTotal(Object originalTotal) { this.originalTotal = originalTotal; }
    public void setHotelAddress(String hotelAddress) { this.hotelAddress = hotelAddress; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    // --- Helper methods ---
    @Exclude
    public boolean isActive() {
        return orderStatus != null &&
               (orderStatus.equals("pending") ||
                orderStatus.equals("pending_pickup") ||
                orderStatus.equals("confirmed") ||
                orderStatus.equals("ready"));
    }

    @Exclude
    public boolean isCompleted() {
        return orderStatus != null && orderStatus.equals("completed");
    }

    @Exclude
    public boolean isCancelled() {
        return orderStatus != null && orderStatus.equals("cancelled");
    }

    @Exclude
    public String getStatusDisplay() {
        if (orderStatus == null) return "Unknown";
        switch (orderStatus) {
            case "pending": return "Pending Confirmation";
            case "pending_pickup": return "Pending Pickup";
            case "confirmed": return "Confirmed";
            case "ready": return "Ready for Pickup";
            case "completed": return "Completed";
            case "cancelled": return "Cancelled";
            default: return orderStatus;
        }
    }

    /**
     * Inner class for order items
     */
    @IgnoreExtraProperties
    public static class OrderItem implements Serializable {
        private String foodId;
        private String foodName;
        private Object quantity;
        private Object price;
        private String imageUrl;

        public OrderItem() {}

        public String getFoodId() { return foodId; }
        public String getFoodName() { return foodName; }

        @Exclude
        public int getQuantityValue() {
            if (quantity == null) return 0;
            if (quantity instanceof Number) return ((Number) quantity).intValue();
            try { return Integer.parseInt(quantity.toString()); } catch (Exception e) { return 0; }
        }
        public Object getQuantity() { return quantity; }

        @Exclude
        public double getPriceValue() {
            if (price == null) return 0.0;
            if (price instanceof Number) return ((Number) price).doubleValue();
            try { return Double.parseDouble(price.toString()); } catch (Exception e) { return 0.0; }
        }
        public Object getPrice() { return price; }

        public String getImageUrl() { return imageUrl; }

        public void setFoodId(String foodId) { this.foodId = foodId; }
        public void setFoodName(String foodName) { this.foodName = foodName; }
        public void setQuantity(Object quantity) { this.quantity = quantity; }
        public void setPrice(Object price) { this.price = price; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}
