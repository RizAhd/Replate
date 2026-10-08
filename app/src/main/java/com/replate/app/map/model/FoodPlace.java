package com.replate.app.map.model;

// One hotel pin on the map.
public class FoodPlace {
    private final String id;
    private final String name;
    private final String address;
    private final double latitude;
    private final double longitude;
    private final String category;
    private final float rating;

    public FoodPlace(String id,
                     String name,
                     String address,
                     double latitude,
                     double longitude,
                     String category,
                     float rating) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.rating = rating;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getCategory() {
        return category;
    }

    public float getRating() {
        return rating;
    }
}

