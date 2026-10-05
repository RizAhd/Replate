package com.replate.app.models;

/** A hotel the customer saved, kept in the phone's SQLite database. */
public class FavoriteHotel {
    private final String hotelId;
    private final String name;
    private final String address;

    public FavoriteHotel(String hotelId, String name, String address) {
        this.hotelId = hotelId;
        this.name = name;
        this.address = address;
    }

    public String getHotelId() { return hotelId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
}
