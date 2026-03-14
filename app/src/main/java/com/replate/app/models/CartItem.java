package com.replate.app.models;

import java.io.Serializable;

/**
 * Model class for Cart Items (stored in SQLite)
 */
public class CartItem implements Serializable {
    private int id;
    private String foodId;
    private String foodName;
    private String hotelName;
    private String hotelId;
    private String imageUrl;
    private double price;
    private double originalPrice;
    private int quantity;
    private int discountPercent;

    public CartItem() {}

    public CartItem(String foodId, String foodName, String hotelName, String hotelId,
                    String imageUrl, double price, double originalPrice, int quantity, int discountPercent) {
        this.foodId = foodId;
        this.foodName = foodName;
        this.hotelName = hotelName;
        this.hotelId = hotelId;
        this.imageUrl = imageUrl;
        this.price = price;
        this.originalPrice = originalPrice;
        this.quantity = quantity;
        this.discountPercent = discountPercent;
    }

    // Getters
    public int getId() { return id; }
    public String getFoodId() { return foodId; }
    public String getFoodName() { return foodName; }
    public String getHotelName() { return hotelName; }
    public String getHotelId() { return hotelId; }
    public String getImageUrl() { return imageUrl; }
    public double getPrice() { return price; }
    public double getOriginalPrice() { return originalPrice; }
    public int getQuantity() { return quantity; }
    public int getDiscountPercent() { return discountPercent; }
    public double getSubtotal() { return price * quantity; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setFoodId(String foodId) { this.foodId = foodId; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setPrice(double price) { this.price = price; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setDiscountPercent(int discountPercent) { this.discountPercent = discountPercent; }
}

