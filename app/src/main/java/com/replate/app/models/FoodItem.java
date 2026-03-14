package com.replate.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.io.Serializable;

/**
 * Model class for Food Items
 * All fields use Object/String types to handle Firebase storing numbers as strings
 */
@IgnoreExtraProperties
public class FoodItem implements Serializable {
    private String foodId;
    private String hotelId;
    private String hotelName;
    private String foodName;
    private String description;
    private String category;
    private Object originalPrice;
    private Object discountedPrice;
    private Object discountPercent;
    private String imageUrl;
    private Object quantity;
    private String pickupTimeStart;
    private String pickupTimeEnd;
    private Object createdAt;
    private Object isAvailable;
    private Object available; // Firebase might use "available" instead of "isAvailable"
    private Object latitude;
    private Object longitude;
    private String hotelAddress;
    private String hotelPhone;
    private double distance; // Calculated field - not stored in Firebase

    // Empty constructor for Firebase
    public FoodItem() {}

    // --- Safe parsing helpers ---
    @Exclude
    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    @Exclude
    private static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    @Exclude
    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }

    @Exclude
    private static boolean toBool(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        return "true".equalsIgnoreCase(val.toString());
    }

    // --- Getters (return proper types) ---
    public String getFoodId() { return foodId; }
    public String getHotelId() { return hotelId; }
    public String getHotelName() { return hotelName; }
    public String getFoodName() { return foodName; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }

    @Exclude
    public double getOriginalPriceValue() { return toDouble(originalPrice); }
    public Object getOriginalPrice() { return originalPrice; }

    @Exclude
    public double getDiscountedPriceValue() { return toDouble(discountedPrice); }
    public Object getDiscountedPrice() { return discountedPrice; }

    @Exclude
    public int getDiscountPercentValue() { return toInt(discountPercent); }
    public Object getDiscountPercent() { return discountPercent; }

    public String getImageUrl() { return imageUrl; }

    @Exclude
    public int getQuantityValue() { return toInt(quantity); }
    public Object getQuantity() { return quantity; }

    public String getPickupTimeStart() { return pickupTimeStart; }
    public String getPickupTimeEnd() { return pickupTimeEnd; }

    @Exclude
    public long getCreatedAtValue() { return toLong(createdAt); }
    public Object getCreatedAt() { return createdAt; }

    @Exclude
    public boolean isAvailableValue() {
        // Check both field names
        if (isAvailable != null) return toBool(isAvailable);
        if (available != null) return toBool(available);
        return false;
    }
    public Object getIsAvailable() { return isAvailable; }
    public Object getAvailable() { return available; }

    @Exclude
    public double getLatitudeValue() { return toDouble(latitude); }
    public Object getLatitude() { return latitude; }

    @Exclude
    public double getLongitudeValue() { return toDouble(longitude); }
    public Object getLongitude() { return longitude; }

    public String getHotelAddress() { return hotelAddress; }
    public String getHotelPhone() { return hotelPhone; }

    @Exclude
    public double getDistance() { return distance; }

    // --- Setters ---
    public void setFoodId(String foodId) { this.foodId = foodId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setOriginalPrice(Object originalPrice) { this.originalPrice = originalPrice; }
    public void setDiscountedPrice(Object discountedPrice) { this.discountedPrice = discountedPrice; }
    public void setDiscountPercent(Object discountPercent) { this.discountPercent = discountPercent; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setQuantity(Object quantity) { this.quantity = quantity; }
    public void setPickupTimeStart(String pickupTimeStart) { this.pickupTimeStart = pickupTimeStart; }
    public void setPickupTimeEnd(String pickupTimeEnd) { this.pickupTimeEnd = pickupTimeEnd; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
    public void setIsAvailable(Object isAvailable) { this.isAvailable = isAvailable; }
    public void setAvailable(Object available) { this.available = available; }
    public void setLatitude(Object latitude) { this.latitude = latitude; }
    public void setLongitude(Object longitude) { this.longitude = longitude; }
    public void setHotelAddress(String hotelAddress) { this.hotelAddress = hotelAddress; }
    public void setHotelPhone(String hotelPhone) { this.hotelPhone = hotelPhone; }
    public void setDistance(double distance) { this.distance = distance; }

    // Helper method to get formatted pickup time
    @Exclude
    public String getPickupTimeFormatted() {
        String start = pickupTimeStart != null ? pickupTimeStart : "";
        String end = pickupTimeEnd != null ? pickupTimeEnd : "";
        return start + " - " + end;
    }

    // Helper method to get formatted distance
    @Exclude
    public String getDistanceFormatted() {
        if (distance < 1) {
            return String.format("%.0f m", distance * 1000);
        } else {
            return String.format("%.1f km", distance);
        }
    }
}
