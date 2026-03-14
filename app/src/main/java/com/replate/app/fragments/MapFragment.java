package com.replate.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.R;
import com.replate.app.SearchActivity;
import com.replate.app.models.FoodItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Map Fragment - Shows nearby food deals on Google Maps
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST = 1002;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseReference mDatabase;

    // Views
    private FrameLayout loadingOverlay;
    private CardView bottomSheet;
    private ImageView ivRestaurantImage, ivMyLocation;
    private TextView tvRestaurantName, tvRestaurantAddress, tvDealsCount;
    private Button btnViewDeals;
    private SearchView searchView;

    // Data
    private double currentLatitude = 6.9271; // Default: Colombo, Sri Lanka
    private double currentLongitude = 79.8612;
    private Map<String, List<FoodItem>> hotelFoodItems = new HashMap<>();
    private String selectedHotelId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        initViews(view);
        initMap();
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        bottomSheet = view.findViewById(R.id.bottomSheet);
        ivRestaurantImage = view.findViewById(R.id.ivRestaurantImage);
        ivMyLocation = view.findViewById(R.id.ivMyLocation);
        tvRestaurantName = view.findViewById(R.id.tvRestaurantName);
        tvRestaurantAddress = view.findViewById(R.id.tvRestaurantAddress);
        tvDealsCount = view.findViewById(R.id.tvDealsCount);
        btnViewDeals = view.findViewById(R.id.btnViewDeals);
        searchView = view.findViewById(R.id.searchView);

        setupSearch();
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.isEmpty()) {
                    searchLocation(query);
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void searchLocation(String locationName) {
        if (!Geocoder.isPresent()) {
            Toast.makeText(getContext(), "Geocoder not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingOverlay.setVisibility(View.VISIBLE);

        new Thread(() -> {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            try {
                List<Address> addressList = geocoder.getFromLocationName(locationName, 5);

                requireActivity().runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    if (addressList != null && !addressList.isEmpty()) {
                        Address address = addressList.get(0);
                        LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());

                        if (mMap != null) {
                            // Update reference location so marker distance colors are meaningful after search.
                            currentLatitude = latLng.latitude;
                            currentLongitude = latLng.longitude;
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
                            mMap.clear(); // Clear previous search markers

                            // Re-add hotel markers (we need to make sure we don't lose them or just add the search marker)
                            loadRestaurantMarkers(); // Reload hotels so they appear relative to new location

                            mMap.addMarker(new MarkerOptions()
                                    .position(latLng)
                                    .title(locationName)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                        }
                    } else {
                        Toast.makeText(getContext(), "Location '" + locationName + "' not found", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                requireActivity().runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Search connection failed. check internet.", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                 requireActivity().runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Search error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupClickListeners() {
        ivMyLocation.setOnClickListener(v -> moveToCurrentLocation());

        btnViewDeals.setOnClickListener(v -> {
            if (selectedHotelId != null) {
                Intent intent = new Intent(getContext(), SearchActivity.class);
                intent.putExtra("hotelId", selectedHotelId);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Map settings
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        // Get user location
        getUserLocation();

        // Load restaurant markers
        loadRestaurantMarkers();

        // Marker click listener
        mMap.setOnMarkerClickListener(marker -> {
            String hotelId = (String) marker.getTag();
            if (hotelId != null) {
                showHotelBottomSheet(hotelId);
            }
            return false;
        });

        // Map click to hide bottom sheet
        mMap.setOnMapClickListener(latLng -> {
            bottomSheet.setVisibility(View.GONE);
            selectedHotelId = null;
        });
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
            return;
        }

        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        moveToCurrentLocation();
                    } else {
                        // Default location
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(currentLatitude, currentLongitude), 14f));
                    }
                });
    }

    private void moveToCurrentLocation() {
        if (mMap != null) {
            LatLng currentLocation = new LatLng(currentLatitude, currentLongitude);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
        }
    }

    private void loadRestaurantMarkers() {
        loadingOverlay.setVisibility(View.VISIBLE);

        mDatabase.child("hotels").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot hotelSnapshot) {
                if (!hotelSnapshot.hasChildren()) {
                    seedMockMapData();
                    loadingOverlay.setVisibility(View.GONE);
                    return;
                }

                Map<String, Map<String, Object>> hotels = new HashMap<>();
                for (DataSnapshot h : hotelSnapshot.getChildren()) {
                    // Store hotel details: lat, lng, address, name
                    Map<String, Object> details = (Map<String, Object>) h.getValue();
                    if (details != null) {
                        hotels.put(h.getKey(), details);
                    }
                }

                mDatabase.child("foodItems").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        hotelFoodItems.clear();
                        mMap.clear();

                        // Group food items by hotel
                        for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                            FoodItem item = itemSnapshot.getValue(FoodItem.class);
                            if (item != null && item.isAvailableValue() && item.getQuantityValue() > 0) {
                                item.setFoodId(itemSnapshot.getKey());

                                String hotelId = item.getHotelId();
                                // Fill missing location data from Hotels node if necessary
                                if (hotels.containsKey(hotelId)) {
                                    Map<String, Object> hDetails = hotels.get(hotelId);
                                    if (item.getLatitudeValue() == 0) item.setLatitude(hDetails.get("latitude"));
                                    if (item.getLongitudeValue() == 0) item.setLongitude(hDetails.get("longitude"));
                                    if (item.getHotelAddress() == null || item.getHotelAddress().isEmpty())
                                        item.setHotelAddress((String) hDetails.get("address"));
                                }

                                if (!hotelFoodItems.containsKey(hotelId)) {
                                    hotelFoodItems.put(hotelId, new ArrayList<>());
                                }
                                hotelFoodItems.get(hotelId).add(item);
                            }
                        }

                        // Add markers
                        for (Map.Entry<String, List<FoodItem>> entry : hotelFoodItems.entrySet()) {
                            List<FoodItem> items = entry.getValue();
                            if (!items.isEmpty()) {
                                FoodItem firstItem = items.get(0);

                                if (firstItem.getLatitudeValue() != 0 && firstItem.getLongitudeValue() != 0) {
                                    LatLng position = new LatLng(
                                            firstItem.getLatitudeValue(),
                                            firstItem.getLongitudeValue()
                                    );

                                    float markerColor = getMarkerColor(
                                            firstItem.getLatitudeValue(),
                                            firstItem.getLongitudeValue()
                                    );

                                    MarkerOptions markerOptions = new MarkerOptions()
                                            .position(position)
                                            .title(firstItem.getHotelName())
                                            .snippet(items.size() + " deals available")
                                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor));

                                    Marker marker = mMap.addMarker(markerOptions);
                                    if (marker != null) {
                                        marker.setTag(entry.getKey());
                                    }
                                }
                            }
                        }

                        // If we have mock data (e.g. Nuwara Eliya) and user is far away, maybe show toast or move camera?
                        // For now we just keep user location logic.
                        loadingOverlay.setVisibility(View.GONE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadingOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void showHotelBottomSheet(String hotelId) {
        selectedHotelId = hotelId;
        List<FoodItem> items = hotelFoodItems.get(hotelId);

        if (items != null && !items.isEmpty()) {
            FoodItem firstItem = items.get(0);

            tvRestaurantName.setText(firstItem.getHotelName());
            tvRestaurantAddress.setText(firstItem.getHotelAddress() != null ?
                    firstItem.getHotelAddress() : "Address not available");
            tvDealsCount.setText(items.size() + " deals available");

            // Load image
            if (firstItem.getImageUrl() != null && !firstItem.getImageUrl().isEmpty()) {
                Glide.with(requireContext())
                        .load(firstItem.getImageUrl())
                        .placeholder(R.drawable.placeholder_food)
                        .into(ivRestaurantImage);
            }

            bottomSheet.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation();
            }
        }
    }

    private void seedMockMapData() {
        long now = System.currentTimeMillis();

        Map<String, Object> hotel1 = new HashMap<>();
        hotel1.put("hotelName", "Haggala Green Hotel");
        hotel1.put("address", "Haggala, Nuwara Eliya");
        hotel1.put("latitude", 6.9279);
        hotel1.put("longitude", 80.8010);
        hotel1.put("status", "approved");

        Map<String, Object> hotel2 = new HashMap<>();
        hotel2.put("hotelName", "Nuwara Town Dine");
        hotel2.put("address", "Nuwara Eliya Town");
        hotel2.put("latitude", 6.9497);
        hotel2.put("longitude", 80.7891);
        hotel2.put("status", "approved");

        Map<String, Object> updates = new HashMap<>();
        updates.put("hotels/mock_hotel_haggala", hotel1);
        updates.put("hotels/mock_hotel_nuwara", hotel2);

        Map<String, Object> food1 = new HashMap<>();
        food1.put("hotelId", "mock_hotel_haggala");
        food1.put("hotelName", "Haggala Green Hotel");
        food1.put("foodName", "Veg Rice Bowl");
        food1.put("category", "Meals");
        food1.put("originalPrice", 950);
        food1.put("discountedPrice", 450);
        food1.put("discountPercent", 53);
        food1.put("quantity", 8);
        food1.put("status", "available");
        food1.put("pickupTimeStart", "18:00");
        food1.put("pickupTimeEnd", "21:00");
        food1.put("imageUrl", "https://images.unsplash.com/photo-1546069901-ba9599a7e63c");
        food1.put("latitude", 6.9279);
        food1.put("longitude", 80.8010);
        food1.put("createdAt", now);

        Map<String, Object> food2 = new HashMap<>();
        food2.put("hotelId", "mock_hotel_nuwara");
        food2.put("hotelName", "Nuwara Town Dine");
        food2.put("foodName", "Chicken Kottu");
        food2.put("category", "Meals");
        food2.put("originalPrice", 1200);
        food2.put("discountedPrice", 700);
        food2.put("discountPercent", 42);
        food2.put("quantity", 6);
        food2.put("status", "available");
        food2.put("pickupTimeStart", "19:00");
        food2.put("pickupTimeEnd", "22:00");
        food2.put("imageUrl", "https://images.unsplash.com/photo-1512058564366-18510be2db19");
        food2.put("latitude", 6.9497);
        food2.put("longitude", 80.7891);
        food2.put("createdAt", now);

        updates.put("foodItems/mock_food_haggala", food1);
        updates.put("foodItems/mock_food_nuwara", food2);

        mDatabase.updateChildren(updates)
                .addOnSuccessListener(unused -> Toast.makeText(getContext(), "Mock map data added", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to add mock map data", Toast.LENGTH_SHORT).show());
    }

    private float getMarkerColor(double hotelLat, double hotelLng) {
        if (currentLatitude == 0 || currentLongitude == 0) return BitmapDescriptorFactory.HUE_RED; // Default red if user loc unknown

        double distanceKm = com.replate.app.utils.LocationUtils.calculateDistance(
                currentLatitude, currentLongitude, hotelLat, hotelLng
        );

        if (distanceKm < 1.0) {
            return BitmapDescriptorFactory.HUE_GREEN; // < 1km
        } else if (distanceKm < 3.0) {
            return BitmapDescriptorFactory.HUE_ORANGE; // 1-3km
        } else {
            return BitmapDescriptorFactory.HUE_RED; // > 3km
        }
    }
}
