package com.replate.app.models;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.io.Serializable;

/**
 * Model class for Food Items.
 * Supports legacy and canonical Firebase field names.
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
    private Object discountPercentage;

    private String imageUrl;
    private Object quantity;

    private String pickupTimeStart;
    private String pickupTimeEnd;
    private String pickupStart;
    private String pickupEnd;

    private Object createdAt;

    private Object isAvailable;
    private Object available;
    private String status;

    private Object latitude;
    private Object longitude;
    private Object hotelLatitude;
    private Object hotelLongitude;

    private String hotelAddress;
    private String hotelPhone;
    private Object rating;
    private Object hotelRating;

    @Exclude
    private double distance;

    public FoodItem() {}

    @Exclude
    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return 0.0; }
    }

    @Exclude
    private static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return 0; }
    }

    @Exclude
    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(String.valueOf(val)); } catch (Exception e) { return 0L; }
    }

    @Exclude
    private static boolean toBool(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        return "true".equalsIgnoreCase(String.valueOf(val));
    }

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
    public int getDiscountPercentValue() {
        if (discountPercent != null) return toInt(discountPercent);
        if (discountPercentage != null) return toInt(discountPercentage);
        return 0;
    }

    public Object getDiscountPercent() { return discountPercent; }
    public Object getDiscountPercentage() { return discountPercentage; }

    public String getImageUrl() { return imageUrl; }

    @Exclude
    /** Up to this many portions left counts as "only a few". */
    public static final int LOW_STOCK_MAX = 3;

    public int getQuantityValue() { return toInt(quantity); }
    public Object getQuantity() { return quantity; }

    public String getPickupTimeStart() {
        if (pickupTimeStart != null && !pickupTimeStart.isEmpty()) return pickupTimeStart;
        return pickupStart;
    }

    public String getPickupTimeEnd() {
        if (pickupTimeEnd != null && !pickupTimeEnd.isEmpty()) return pickupTimeEnd;
        return pickupEnd;
    }

    @Exclude
    public long getCreatedAtValue() { return toLong(createdAt); }
    public Object getCreatedAt() { return createdAt; }

    @Exclude
    public boolean isAvailableValue() {
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();

        // Explicit terminal states should always hide the item.
        if ("sold_out".equals(normalizedStatus)
                || "expired".equals(normalizedStatus)
                || "inactive".equals(normalizedStatus)
                || "unavailable".equals(normalizedStatus)
                || "deleted".equals(normalizedStatus)) {
            return false;
        }

        // Explicit available states should always show the item.
        if ("available".equals(normalizedStatus)
                || "active".equals(normalizedStatus)
                || "approved".equals(normalizedStatus)
                || "live".equals(normalizedStatus)) {
            return true;
        }

        if (isAvailable != null) return toBool(isAvailable);
        if (available != null) return toBool(available);

        // Legacy payloads without status flags are treated as available.
        return true;
    }

    public Object getIsAvailable() { return isAvailable; }
    public Object getAvailable() { return available; }
    public String getStatus() { return status; }

    @Exclude
    public double getLatitudeValue() {
        if (latitude != null) return toDouble(latitude);
        if (hotelLatitude != null) return toDouble(hotelLatitude);
        return 0.0;
    }

    public Object getLatitude() { return latitude; }

    @Exclude
    public double getLongitudeValue() {
        if (longitude != null) return toDouble(longitude);
        if (hotelLongitude != null) return toDouble(hotelLongitude);
        return 0.0;
    }

    public Object getLongitude() { return longitude; }
    public Object getHotelLatitude() { return hotelLatitude; }
    public Object getHotelLongitude() { return hotelLongitude; }

    public String getHotelAddress() { return hotelAddress; }
    public String getHotelPhone() { return hotelPhone; }

    @Exclude
    public double getRatingValue() {
        if (rating != null) return toDouble(rating);
        if (hotelRating != null) return toDouble(hotelRating);
        return 0.0;
    }

    public Object getRating() { return rating; }
    public Object getHotelRating() { return hotelRating; }

    @Exclude
    public double getDistance() { return distance; }

    public void setFoodId(String foodId) { this.foodId = foodId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setOriginalPrice(Object originalPrice) { this.originalPrice = originalPrice; }
    public void setDiscountedPrice(Object discountedPrice) { this.discountedPrice = discountedPrice; }
    public void setDiscountPercent(Object discountPercent) { this.discountPercent = discountPercent; }
    public void setDiscountPercentage(Object discountPercentage) { this.discountPercentage = discountPercentage; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setQuantity(Object quantity) { this.quantity = quantity; }
    public void setPickupTimeStart(String pickupTimeStart) { this.pickupTimeStart = pickupTimeStart; }
    public void setPickupTimeEnd(String pickupTimeEnd) { this.pickupTimeEnd = pickupTimeEnd; }
    public void setPickupStart(String pickupStart) { this.pickupStart = pickupStart; }
    public void setPickupEnd(String pickupEnd) { this.pickupEnd = pickupEnd; }
    public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
    public void setIsAvailable(Object isAvailable) { this.isAvailable = isAvailable; }
    public void setAvailable(Object available) { this.available = available; }
    public void setStatus(String status) { this.status = status; }
    public void setLatitude(Object latitude) { this.latitude = latitude; }
    public void setLongitude(Object longitude) { this.longitude = longitude; }
    public void setHotelLatitude(Object hotelLatitude) { this.hotelLatitude = hotelLatitude; }
    public void setHotelLongitude(Object hotelLongitude) { this.hotelLongitude = hotelLongitude; }
    public void setHotelAddress(String hotelAddress) { this.hotelAddress = hotelAddress; }
    public void setHotelPhone(String hotelPhone) { this.hotelPhone = hotelPhone; }
    public void setRating(Object rating) { this.rating = rating; }
    public void setHotelRating(Object hotelRating) { this.hotelRating = hotelRating; }
    public void setDistance(double distance) { this.distance = distance; }

    @Exclude
    public String getPickupTimeFormatted() {
        String start = getPickupTimeStart() != null ? getPickupTimeStart() : "";
        String end = getPickupTimeEnd() != null ? getPickupTimeEnd() : "";
        return start + " - " + end;
    }

    @Exclude
    public String getDistanceFormatted() {
        if (distance < 1) return String.format("%.0f m", distance * 1000);
        return String.format("%.1f km", distance);
    }

    @Exclude
    public boolean hasStock() {
        return quantity == null || getQuantityValue() > 0;
    }

    /** The hotel recorded a quantity and only a few portions are left, worth telling the customer. */
    @Exclude
    public boolean isLowStock() {
        return quantity != null && getQuantityValue() >= 1 && getQuantityValue() <= LOW_STOCK_MAX;
    }

    @Exclude
    public boolean hasValidCoordinates() {
        return getLatitudeValue() != 0 && getLongitudeValue() != 0;
    }

    @Exclude
    public boolean isListable() {
        return isAvailableValue() && hasStock() && !isPickupClosedNow();
    }

    /** True when today's pickup window for this dish has already ended (it comes back tomorrow). */
    @Exclude
    public boolean isPickupClosedNow() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int minutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        return com.replate.app.utils.PickupSlot.isClosed(
                com.replate.app.utils.PickupSlot.windowOf(getPickupTimeStart(), getPickupTimeEnd()), minutes);
    }

    @Exclude
    public boolean matchesCategory(String selectedCategory) {
        if (selectedCategory == null || selectedCategory.trim().isEmpty() || "All".equalsIgnoreCase(selectedCategory)) {
            return true;
        }
        return selectedCategory.equalsIgnoreCase(category == null ? "" : category.trim());
    }

    @Exclude
    public boolean matchesQuery(String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.toLowerCase();
        return (foodName != null && foodName.toLowerCase().contains(q))
                || (hotelName != null && hotelName.toLowerCase().contains(q))
                || (category != null && category.toLowerCase().contains(q))
                || (description != null && description.toLowerCase().contains(q));
    }
}
