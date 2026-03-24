package com.replate.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.R;
import com.replate.app.map.data.FakePlacesDataSource;
import com.replate.app.map.data.FirebasePlacesDataSource;
import com.replate.app.map.data.PlacesDataSource;
import com.replate.app.map.model.FoodPlace;
import com.replate.app.map.routing.DirectionsApiClient;
import com.replate.app.map.routing.RouteResult;
import com.replate.app.utils.LocationUtils;

import java.io.IOException;
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
    private DirectionsApiClient directionsApiClient;
    private PlacesDataSource placesDataSource;

    // Views
    private FrameLayout loadingOverlay;
    private CardView bottomSheet;
    private ImageView ivRestaurantImage;
    private View ivMyLocation;
    private TextView tvRestaurantName;
    private TextView tvRestaurantAddress;
    private TextView tvDealsCount;
    private TextView tvRouteInfo;
    private TextView tvAvailableItems;
    private Button btnViewDeals;
    private SearchView searchView;

    // State
    private LatLng currentUserLatLng;
    private LatLng selectedDestinationLatLng;
    private FoodPlace selectedPlace;
    private Polyline currentRoutePolyline;
    private Marker currentSelectedMarker;
    private LatLng searchPin;
    private String searchQueryLabel;

    private final Map<String, FoodPlace> markerPlaceMap = new HashMap<>();
    private final Map<String, Marker> markerByHotelId = new HashMap<>();
    private final Map<String, String> hotelFoodPreviewCache = new HashMap<>();
    private DatabaseReference foodItemsRef;
    private String pendingFocusHotelId;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fineGranted || coarseGranted) {
                    enableMyLocationAndCenter();
                } else {
                    Toast.makeText(getContext(), "Location permission denied. Map uses default area.", Toast.LENGTH_LONG).show();
                    if (mMap != null) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9497, 80.7891), 12f));
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        directionsApiClient = new DirectionsApiClient(getString(R.string.google_maps_key));
        placesDataSource = new FirebasePlacesDataSource();
        foodItemsRef = FirebaseDatabase.getInstance().getReference("foodItems");

        Intent hostIntent = requireActivity().getIntent();
        if (hostIntent != null) {
            pendingFocusHotelId = hostIntent.getStringExtra("focus_hotel_id");
        }

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
        tvRouteInfo = view.findViewById(R.id.tvRouteInfo);
        tvAvailableItems = view.findViewById(R.id.tvAvailableItems);
        btnViewDeals = view.findViewById(R.id.btnViewDeals);
        searchView = view.findViewById(R.id.searchView);

        bottomSheet.setVisibility(View.GONE);
        setupSearch();
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupClickListeners() {
        ivMyLocation.setOnClickListener(v -> moveToCurrentLocation());

        btnViewDeals.setOnClickListener(v -> {
            if (selectedPlace == null) {
                Toast.makeText(getContext(), "Select a place marker first", Toast.LENGTH_SHORT).show();
                return;
            }
            drawRouteToSelectedPlace();
        });
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.trim().isEmpty()) {
                    searchView.clearFocus();
                    searchLocation(query.trim());
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void searchLocation(@NonNull String locationName) {
        if (!Geocoder.isPresent()) {
            Toast.makeText(getContext(), "Geocoder not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingOverlay.setVisibility(View.VISIBLE);
        new Thread(() -> {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            try {
                List<Address> result = geocoder.getFromLocationName(locationName, 1);
                requireActivity().runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    if (result != null && !result.isEmpty()) {
                        Address address = result.get(0);
                        searchPin = new LatLng(address.getLatitude(), address.getLongitude());
                        searchQueryLabel = locationName;

                        if (mMap != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(searchPin, 14f));
                            renderSearchPin();
                        }
                    } else {
                        Toast.makeText(getContext(), "Location not found", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException ioException) {
                requireActivity().runOnUiThread(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Search failed. Check internet.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        mMap.setOnMapClickListener(latLng -> clearSelectedState());

        mMap.setOnMarkerClickListener(marker -> {
            FoodPlace place = markerPlaceMap.get(marker.getId());
            if (place == null) {
                return false;
            }
            onPlaceSelected(marker, place);
            return true;
        });

        requestOrLoadLocation();
        loadAndRenderPlaceMarkers();
    }

    private void loadAndRenderPlaceMarkers() {
        loadingOverlay.setVisibility(View.VISIBLE);
        placesDataSource.fetchPlaces(new PlacesDataSource.Callback() {
            @Override
            public void onSuccess(@NonNull List<FoodPlace> places) {
                loadingOverlay.setVisibility(View.GONE);
                if (places.isEmpty()) {
                    showNoRegisteredHotelsState();
                    return;
                }
                renderPlaceMarkers(places);
            }

            @Override
            public void onError(@NonNull String message) {
                loadingOverlay.setVisibility(View.GONE);
                showNoRegisteredHotelsState();
                Toast.makeText(getContext(), "Could not load hotel locations: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showNoRegisteredHotelsState() {
        if (mMap != null) {
            mMap.clear();
            markerPlaceMap.clear();
            markerByHotelId.clear();
        }
        clearSelectedState();
    }

    private void requestOrLoadLocation() {
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        enableMyLocationAndCenter();
    }

    private void enableMyLocationAndCenter() {
        if (mMap == null) {
            return;
        }

        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            Toast.makeText(getContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this::handleLocation)
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Could not get location", Toast.LENGTH_SHORT).show();
                    if (currentUserLatLng == null) {
                        LatLng fallback = new LatLng(6.9497, 80.7891);
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback, 12f));
                    }
                });
    }

    private void handleLocation(@Nullable Location location) {
        if (mMap == null) {
            return;
        }

        if (location == null) {
            Toast.makeText(getContext(), "Using default location (Nuwara Eliya)", Toast.LENGTH_SHORT).show();
            if (currentUserLatLng == null) {
                currentUserLatLng = new LatLng(6.9497, 80.7891);
            }
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, 13f));
            return;
        }

        currentUserLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, 14f));
    }

    private void moveToCurrentLocation() {
        if (mMap == null || currentUserLatLng == null) {
            Toast.makeText(getContext(), "Current location not available yet", Toast.LENGTH_SHORT).show();
            requestOrLoadLocation();
            return;
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, 15f));
    }

    private void renderPlaceMarkers(@NonNull List<FoodPlace> places) {
        if (mMap == null) {
            return;
        }

        mMap.clear();
        markerPlaceMap.clear();
        markerByHotelId.clear();
        clearRouteOnly();

        for (FoodPlace place : places) {
            LatLng latLng = new LatLng(place.getLatitude(), place.getLongitude());
            float markerColor = markerColorForDistance(latLng);

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(place.getName())
                    .snippet(place.getAddress())
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

            if (marker != null) {
                markerPlaceMap.put(marker.getId(), place);
                markerByHotelId.put(place.getId(), marker);
            }
        }

        renderSearchPin();
        maybeFocusDeepLinkedHotel();
    }

    private void maybeFocusDeepLinkedHotel() {
        if (pendingFocusHotelId == null || pendingFocusHotelId.trim().isEmpty()) {
            return;
        }
        Marker marker = markerByHotelId.get(pendingFocusHotelId);
        if (marker == null) {
            return;
        }

        FoodPlace place = markerPlaceMap.get(marker.getId());
        if (place == null) {
            return;
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 15f));
        onPlaceSelected(marker, place);
        pendingFocusHotelId = null;
    }

    private void renderSearchPin() {
        if (mMap == null || searchPin == null) {
            return;
        }

        mMap.addMarker(new MarkerOptions()
                .position(searchPin)
                .title(searchQueryLabel != null ? searchQueryLabel : "Searched location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    private void onPlaceSelected(@NonNull Marker marker, @NonNull FoodPlace place) {
        selectedPlace = place;
        selectedDestinationLatLng = new LatLng(place.getLatitude(), place.getLongitude());

        if (currentSelectedMarker != null) {
            currentSelectedMarker.setAlpha(1.0f);
        }
        currentSelectedMarker = marker;
        currentSelectedMarker.setAlpha(0.8f);

        tvRestaurantName.setText(place.getName());
        tvRestaurantAddress.setText(place.getAddress());
        tvDealsCount.setText(place.getCategory() + " • ★" + String.format(Locale.getDefault(), "%.1f", place.getRating()));
        tvRouteInfo.setText("Preparing route...");
        tvAvailableItems.setText("Loading available items...");
        loadAvailableItemsPreview(place.getId(), place.getName());
        ivRestaurantImage.setImageResource(R.drawable.placeholder_food);
        bottomSheet.setVisibility(View.VISIBLE);

        drawRouteToSelectedPlace();
    }

    private void drawRouteToSelectedPlace() {
        if (selectedDestinationLatLng == null || selectedPlace == null) {
            Toast.makeText(getContext(), "Select destination first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserLatLng == null) {
            requestOrLoadLocation();
            Toast.makeText(getContext(), "Waiting for current location...", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingOverlay.setVisibility(View.VISIBLE);
        directionsApiClient.fetchRoute(currentUserLatLng, selectedDestinationLatLng, new DirectionsApiClient.Callback() {
            @Override
            public void onSuccess(RouteResult result) {
                loadingOverlay.setVisibility(View.GONE);
                drawPolyline(result);
            }

            @Override
            public void onError(String message) {
                loadingOverlay.setVisibility(View.GONE);
                tvRouteInfo.setText("Route unavailable");
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawPolyline(@NonNull RouteResult routeResult) {
        if (mMap == null || currentUserLatLng == null || selectedDestinationLatLng == null) {
            return;
        }

        clearRouteOnly();

        currentRoutePolyline = mMap.addPolyline(new PolylineOptions()
                .addAll(routeResult.getPolylinePoints())
                .width(12f)
                .color(requireContext().getColor(R.color.green_primary))
                .geodesic(true));

        tvRouteInfo.setText("Distance: " + routeResult.getDistanceText() + " • ETA: " + routeResult.getDurationText());

        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(currentUserLatLng)
                .include(selectedDestinationLatLng)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140));
    }

    private void clearRouteOnly() {
        if (currentRoutePolyline != null) {
            currentRoutePolyline.remove();
            currentRoutePolyline = null;
        }
    }

    private void loadAvailableItemsPreview(@NonNull String hotelId, @NonNull String hotelName) {
        if (hotelFoodPreviewCache.containsKey(hotelId)) {
            tvAvailableItems.setText(hotelFoodPreviewCache.get(hotelId));
            return;
        }

        foodItemsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                StringBuilder preview = new StringBuilder();
                int added = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    String childHotelId = asString(child.child("hotelId").getValue());
                    String childHotelName = asString(child.child("hotelName").getValue());

                    boolean sameHotel = hotelId.equals(childHotelId)
                            || (!childHotelName.isEmpty() && hotelName.equalsIgnoreCase(childHotelName));
                    if (!sameHotel) {
                        continue;
                    }

                    String status = asString(child.child("status").getValue());
                    int quantity = asInt(child.child("quantity").getValue());
                    if ((!status.isEmpty() && !"available".equalsIgnoreCase(status)) || quantity <= 0) {
                        continue;
                    }

                    String foodName = asString(child.child("foodName").getValue());
                    if (foodName.isEmpty()) {
                        continue;
                    }

                    double price = asDouble(child.child("discountedPrice").getValue());
                    preview.append("• ").append(foodName);
                    if (price > 0) {
                        preview.append("  -  LKR ").append(String.format(Locale.getDefault(), "%.0f", price));
                    }
                    preview.append("\n");

                    added++;
                    if (added >= 3) {
                        break;
                    }
                }

                String previewText;
                if (added == 0) {
                    previewText = "No active items from this hotel right now.";
                } else {
                    previewText = preview.toString().trim();
                }

                hotelFoodPreviewCache.put(hotelId, previewText);
                tvAvailableItems.setText(previewText);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvAvailableItems.setText("Unable to load items right now.");
            }
        });
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int asInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private double asDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private void clearSelectedState() {
        selectedPlace = null;
        selectedDestinationLatLng = null;
        if (currentSelectedMarker != null) {
            currentSelectedMarker.setAlpha(1.0f);
            currentSelectedMarker = null;
        }
        clearRouteOnly();
        bottomSheet.setVisibility(View.GONE);
        if (tvAvailableItems != null) {
            tvAvailableItems.setText("Tap marker to load hotel menu...");
        }
    }

    private float markerColorForDistance(@NonNull LatLng destination) {
        if (currentUserLatLng == null) {
            return BitmapDescriptorFactory.HUE_RED;
        }

        double distanceKm = LocationUtils.calculateDistance(
                currentUserLatLng.latitude,
                currentUserLatLng.longitude,
                destination.latitude,
                destination.longitude);

        if (distanceKm < 1.0) {
            return BitmapDescriptorFactory.HUE_GREEN;
        }
        if (distanceKm < 3.0) {
            return BitmapDescriptorFactory.HUE_ORANGE;
        }
        return BitmapDescriptorFactory.HUE_RED;
    }
}
