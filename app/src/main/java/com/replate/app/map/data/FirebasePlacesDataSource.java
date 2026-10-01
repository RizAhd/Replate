package com.replate.app.map.data;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.map.model.FoodPlace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebasePlacesDataSource implements PlacesDataSource {

    private static final java.util.concurrent.ExecutorService PARSE_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private final DatabaseReference hotelsRef;
    private final DatabaseReference foodItemsRef;

    public FirebasePlacesDataSource() {
        this.hotelsRef = FirebaseDatabase.getInstance().getReference("hotels");
        this.foodItemsRef = FirebaseDatabase.getInstance().getReference("foodItems");
    }

    @Override
    public void fetchPlaces(@NonNull Callback callback) {
        hotelsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot hotelSnapshot) {
                if (!hotelSnapshot.exists()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                Map<String, HotelSeed> approvedHotels = new HashMap<>();
                Map<String, String> hotelIdByUserId = new HashMap<>();
                for (DataSnapshot hotelNode : hotelSnapshot.getChildren()) {
                    String hotelId = hotelNode.getKey();
                    if (hotelId == null || hotelId.trim().isEmpty()) continue;

                    // Empty status has always meant "fine"; pending/rejected hotels stay off the map
                    if (!MapRules.isActiveStatus(readString(hotelNode, "status"))) continue;

                    double lat = readDoubleAny(hotelNode, "latitude", "lat", "hotelLatitude");
                    double lng = readDoubleAny(hotelNode, "longitude", "lng", "hotelLongitude", "long");
                    if (!MapRules.isValidCoordinate(lat, lng)) continue;

                    String hotelName = readString(hotelNode, "hotelName");
                    if (hotelName.isEmpty()) hotelName = readString(hotelNode, "name");
                    if (hotelName.isEmpty()) hotelName = "Hotel";

                    String address = readString(hotelNode, "address");
                    if (address.isEmpty()) {
                        address = "Address not provided";
                    }

                    // 0 means "no rating yet"; the screen shows "New" instead of an invented 4.0
                    float rating = MapRules.ratingOrZero(readDouble(hotelNode, "rating"));

                    approvedHotels.put(hotelId, new HotelSeed(hotelId, hotelName, address, lat, lng, rating));

                    String hotelUserId = readString(hotelNode, "userId");
                    if (!hotelUserId.isEmpty()) {
                        hotelIdByUserId.put(hotelUserId, hotelId);
                    }
                }

                if (approvedHotels.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                foodItemsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot foodSnapshot) {
                        // Scanning every listing can be slow on a big catalogue, so keep it off the UI thread
                        PARSE_EXECUTOR.execute(() -> {
                        Map<String, Integer> hotelDealCount = new HashMap<>();
                        Map<String, String> hotelCategory = new HashMap<>();

                        for (DataSnapshot itemSnapshot : foodSnapshot.getChildren()) {
                            String itemStatus = readString(itemSnapshot, "status");
                            Integer quantity = readNullableInt(itemSnapshot, "quantity", "qty", "stock");
                            // Same definition of "available" as the bottom card's item list
                            if (!MapRules.isListableItem(itemStatus, quantity)) continue;

                            String hotelId = readString(itemSnapshot, "hotelId");
                            if (hotelId.isEmpty()) hotelId = readString(itemSnapshot, "hotelKey");

                            if (hotelId.isEmpty()) {
                                String hotelUserId = readString(itemSnapshot, "hotelUserId");
                                if (hotelUserId.isEmpty()) {
                                    hotelUserId = readString(itemSnapshot, "userId");
                                }
                                if (!hotelUserId.isEmpty() && hotelIdByUserId.containsKey(hotelUserId)) {
                                    hotelId = hotelIdByUserId.get(hotelUserId);
                                }
                            }

                            if (hotelId == null) hotelId = "";
                            if (hotelId.isEmpty() || !approvedHotels.containsKey(hotelId)) continue;

                            int count = hotelDealCount.getOrDefault(hotelId, 0);
                            hotelDealCount.put(hotelId, count + 1);

                            if (!hotelCategory.containsKey(hotelId)) {
                                String category = readString(itemSnapshot, "category");
                                hotelCategory.put(hotelId, category.isEmpty() ? "Food" : category);
                            }
                        }

                        List<FoodPlace> places = new ArrayList<>();
                        for (HotelSeed hotel : approvedHotels.values()) {
                            int deals = hotelDealCount.getOrDefault(hotel.hotelId, 0);
                            String category = hotelCategory.getOrDefault(hotel.hotelId, "Food");
                            String itemsLabel = deals == 1 ? "1 item" : deals + " items";
                            String categoryWithCount = deals > 0 ? category + " • " + itemsLabel : category + " • No items";

                            places.add(new FoodPlace(
                                    hotel.hotelId,
                                    hotel.name,
                                    hotel.address,
                                    hotel.latitude,
                                    hotel.longitude,
                                    categoryWithCount,
                                    hotel.rating
                            ));
                        }

                        MAIN.post(() -> callback.onSuccess(places));
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError(error.getMessage());
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    private String readString(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double readDouble(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private int readInt(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private Integer readNullableInt(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Object value = snapshot.child(key).getValue();
            if (value == null) continue;
            if (value instanceof Number) return ((Number) value).intValue();
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private double readDoubleAny(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            double value = readDouble(snapshot, key);
            if (value != 0) return value;
        }
        return 0;
    }

    private static class HotelSeed {
        final String hotelId;
        final String name;
        final String address;
        final double latitude;
        final double longitude;
        final float rating;

        HotelSeed(String hotelId, String name, String address, double latitude, double longitude, float rating) {
            this.hotelId = hotelId;
            this.name = name;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.rating = rating;
        }
    }
}
