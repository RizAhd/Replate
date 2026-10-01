package com.replate.app.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
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
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.R;
import com.replate.app.map.data.FirebasePlacesDataSource;
import com.replate.app.map.data.PlacesDataSource;
import com.replate.app.map.model.FoodPlace;
import com.replate.app.map.routing.DirectionsApiClient;
import com.replate.app.map.routing.RouteResult;
import com.replate.app.utils.LocationUtils;
import com.replate.app.utils.ToastUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Map Fragment - Shows nearby food deals on Google Maps
 */
public class MapFragment extends Fragment implements OnMapReadyCallback {

    // Colombo, used until the user's location or the hotel list is known
    private static final LatLng DEFAULT_MAP_CENTER = new LatLng(6.9271, 79.8612);
    private static final float DEFAULT_MAP_ZOOM = 11f;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private DirectionsApiClient directionsApiClient;
    private PlacesDataSource placesDataSource;

    // Views
    private FrameLayout loadingOverlay;
    private CardView bottomSheet;
    private View ivRestaurantImage;
    private View ivMyLocation;
    private TextView tvRestaurantName;
    private TextView tvRestaurantAddress;
    private TextView tvDealsCount;
    private TextView tvRouteInfo;
    private TextView tvAvailableItems;
    private Button btnViewDeals;
    private SearchView searchView;
    private View topPanel;

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
    private final List<FoodPlace> allPlaces = new ArrayList<>();
    private Marker searchMarker;
    private DatabaseReference foodItemsRef;
    private String pendingFocusHotelId;
    private LocationCallback liveLocationCallback;
    private boolean hasCenteredOnUser;
    private long lastRouteInfoUpdateAt;
    private LatLng lastRouteInfoOrigin;
    private long placesLoadVersion;
    private long searchRequestVersion;
    private long routeRequestVersion;
    private boolean isLocationUpdatesRunning;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fineGranted || coarseGranted) {
                    enableMyLocationAndCenter();
                } else {
                    ToastUtils.showCustomToast(getContext(), "Location permission denied. Enable location for live map updates.");
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
            String focusId = hostIntent.getStringExtra("focus_hotel_id");
            if (focusId != null && !focusId.trim().isEmpty()) {
                pendingFocusHotelId = focusId;
                // Consume it so revisiting the Map tab doesn't re-select the hotel and re-request a route
                hostIntent.removeExtra("focus_hotel_id");
            }
        }

        initViews(view);
        applyInsets(view);
        initMap();
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        topPanel = view.findViewById(R.id.topPanel);
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

        if (bottomSheet != null) {
            bottomSheet.setVisibility(View.GONE);
        }
        setupSearch();
    }

    private void applyInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            if (topPanel != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) topPanel.getLayoutParams();
                lp.topMargin = topInset + dp(8);
                topPanel.setLayoutParams(lp);
            }

            if (bottomSheet != null) {
                bottomSheet.setPadding(
                        bottomSheet.getPaddingLeft(),
                        bottomSheet.getPaddingTop(),
                        bottomSheet.getPaddingRight(),
                        bottomInset + dp(8)
                );
            }

            if (mMap != null) {
                mMap.setPadding(dp(12), topInset + dp(130), dp(12), bottomInset + dp(180));
            }
            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private boolean isViewActive() {
        return isAdded() && getView() != null;
    }

    private boolean isMapUsable() {
        return isViewActive() && mMap != null;
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupClickListeners() {
        if (ivMyLocation != null) {
            ivMyLocation.setOnClickListener(v -> moveToCurrentLocation());
        }

        if (btnViewDeals != null) {
            btnViewDeals.setOnClickListener(v -> {
                if (selectedPlace == null) {
                    ToastUtils.showCustomToast(getContext(), "Select a place marker first");
                    return;
                }
                drawRouteToSelectedPlace();
            });
        }
    }

    private void setupSearch() {
        if (searchView == null) {
            return;
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String normalized = query == null ? "" : query.trim();
                if (!normalized.isEmpty()) {
                    searchView.clearFocus();
                    boolean matchedPlaces = applyPlaceFilter(normalized, true);
                    if (!matchedPlaces) {
                        searchLocation(normalized);
                    }
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                String query = newText == null ? "" : newText.trim();
                if (query.isEmpty()) {
                    applyPlaceFilter("", false);
                } else if (query.length() >= 2) {
                    applyPlaceFilter(query, false);
                }
                return true;
            }
        });
    }

    private boolean applyPlaceFilter(@NonNull String query, boolean moveCameraToFirst) {
        if (allPlaces.isEmpty()) {
            return false;
        }

        String normalized = query.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            renderPlaceMarkers(allPlaces);
            return true;
        }

        List<FoodPlace> filtered = new ArrayList<>();
        for (FoodPlace place : allPlaces) {
            String name = place.getName() == null ? "" : place.getName().toLowerCase(Locale.US);
            String address = place.getAddress() == null ? "" : place.getAddress().toLowerCase(Locale.US);
            String category = place.getCategory() == null ? "" : place.getCategory().toLowerCase(Locale.US);
            if (name.contains(normalized) || address.contains(normalized) || category.contains(normalized)) {
                filtered.add(place);
            }
        }

        if (filtered.isEmpty()) {
            return false;
        }

        renderPlaceMarkers(filtered);
        if (moveCameraToFirst && mMap != null) {
            FoodPlace first = filtered.get(0);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(first.getLatitude(), first.getLongitude()),
                    14f
            ));
        }
        return true;
    }

    private void searchLocation(@NonNull String locationName) {
        if (!Geocoder.isPresent()) {
            ToastUtils.showCustomToast(getContext(), "Geocoder not available on this device");
            return;
        }

        if (!isViewActive()) {
            return;
        }

        final long requestId = ++searchRequestVersion;

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            if (!isAdded() || requestId != searchRequestVersion) {
                return;
            }
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            try {
                List<Address> result = geocoder.getFromLocationName(locationName, 1);
                if (!isAdded() || requestId != searchRequestVersion) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isViewActive() || requestId != searchRequestVersion) {
                        return;
                    }
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    if (result != null && !result.isEmpty()) {
                        Address address = result.get(0);
                        searchPin = new LatLng(address.getLatitude(), address.getLongitude());
                        searchQueryLabel = locationName;

                        if (mMap != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(searchPin, 14f));
                            renderSearchPin();
                        }
                    } else {
                        ToastUtils.showCustomToast(getContext(), "Location not found");
                    }
                });
            } catch (IOException ioException) {
                if (!isAdded() || requestId != searchRequestVersion) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (!isViewActive() || requestId != searchRequestVersion) {
                        return;
                    }
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    ToastUtils.showCustomToast(getContext(), "Search failed. Check internet.");
                });
            }
        }).start();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        // The screen has its own "my location" button; hide Google's duplicate
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        // Start somewhere useful instead of the world view while location and hotels load
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM));

        View root = getView();
        if (root != null) {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            int topInset = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            mMap.setPadding(dp(12), topInset + dp(130), dp(12), bottomInset + dp(180));
        }

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
        final long requestId = ++placesLoadVersion;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        placesDataSource.fetchPlaces(new PlacesDataSource.Callback() {
            @Override
            public void onSuccess(@NonNull List<FoodPlace> places) {
                if (!isViewActive() || requestId != placesLoadVersion) {
                    return;
                }
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                allPlaces.clear();
                allPlaces.addAll(places);
                if (places.isEmpty()) {
                    showNoRegisteredHotelsState();
                    return;
                }
                renderPlaceMarkers(places);
                fitCameraToPlaces(places);
            }

            @Override
            public void onError(@NonNull String message) {
                if (!isViewActive() || requestId != placesLoadVersion) {
                    return;
                }
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                showNoRegisteredHotelsState();
                ToastUtils.showCustomToast(getContext(), "Could not load hotel locations: " + message);
            }
        });
    }

    /**
     * Frames every hotel when there's no better target yet. Skipped once the camera has centred
     * on the user, or while a notification is about to focus a specific hotel.
     */
    private void fitCameraToPlaces(@NonNull List<FoodPlace> places) {
        if (mMap == null || places.isEmpty() || hasCenteredOnUser || pendingFocusHotelId != null) {
            return;
        }
        if (places.size() == 1) {
            FoodPlace only = places.get(0);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(only.getLatitude(), only.getLongitude()), 14f));
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (FoodPlace place : places) {
            builder.include(new LatLng(place.getLatitude(), place.getLongitude()));
        }
        try {
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), dp(48)));
        } catch (IllegalStateException e) {
            // Map isn't laid out yet; the default camera stays in place
        }
    }

    private void showNoRegisteredHotelsState() {
        if (mMap != null) {
            mMap.clear();
            markerPlaceMap.clear();
            markerByHotelId.clear();
            searchMarker = null;
            clearRouteOnly();
        }
        clearSelectedState();
    }

    private void requestOrLoadLocation() {
        if (!isAdded()) {
            return;
        }
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

    @SuppressLint("MissingPermission")
    private void enableMyLocationAndCenter() {
        if (mMap == null || !isAdded()) {
            return;
        }

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

        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            ToastUtils.showCustomToast(getContext(), "Location permission denied");
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    handleLocation(location);
                    if (location == null) {
                        requestFreshMapLocation();
                    }
                })
                .addOnFailureListener(e -> requestFreshMapLocation());

        startLiveLocationUpdates();
    }

    @SuppressLint("MissingPermission")
    private void requestFreshMapLocation() {
        if (!isAdded()) return;
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(this::handleLocation)
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    ToastUtils.showCustomToast(getContext(), "Unable to fetch live location");
                });
    }

    private void startLiveLocationUpdates() {
        if (!isAdded() || getContext() == null || getActivity() == null || isLocationUpdatesRunning) {
            return;
        }

        boolean fineGranted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            return;
        }

        if (liveLocationCallback == null) {
            liveLocationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location == null || mMap == null) {
                        return;
                    }

                    currentUserLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    if (!hasCenteredOnUser) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, 14f));
                        hasCenteredOnUser = true;
                    }


                    // Keep route info fresh while user is moving.
                    if (selectedDestinationLatLng != null && tvRouteInfo != null) {
                        maybeUpdateRouteInfo(currentUserLatLng, selectedDestinationLatLng);
                    }
                }
            };
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2500)
                .build();

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, liveLocationCallback, getActivity().getMainLooper());
            isLocationUpdatesRunning = true;
        } catch (SecurityException ignored) {
        }
    }

    private void stopLiveLocationUpdates() {
        if (liveLocationCallback != null && isLocationUpdatesRunning) {
            fusedLocationClient.removeLocationUpdates(liveLocationCallback);
            isLocationUpdatesRunning = false;
        }
    }

    private void handleLocation(@Nullable Location location) {
        if (mMap == null) {
            return;
        }

        if (location == null) {
            ToastUtils.showCustomToast(getContext(), "Waiting for live location...");
            return;
        }

        currentUserLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (!hasCenteredOnUser) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, 14f));
            hasCenteredOnUser = true;
        }
    }

    private void moveToCurrentLocation() {
        if (mMap == null || currentUserLatLng == null) {
            ToastUtils.showCustomToast(getContext(), "Current location not available yet");
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
        searchMarker = null;
        clearRouteOnly();

        List<FoodPlace> sortedPlaces = new ArrayList<>(places);
        if (currentUserLatLng != null) {
            sortedPlaces.sort(Comparator.comparingDouble(place ->
                    LocationUtils.calculateDistance(
                            currentUserLatLng.latitude,
                            currentUserLatLng.longitude,
                            place.getLatitude(),
                            place.getLongitude()
                    )));
        }

        for (FoodPlace place : sortedPlaces) {
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

        if (searchMarker != null) {
            searchMarker.remove();
        }

        searchMarker = mMap.addMarker(new MarkerOptions()
                .position(searchPin)
                .title(searchQueryLabel != null ? searchQueryLabel : "Searched location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    private void onPlaceSelected(@NonNull Marker marker, @NonNull FoodPlace place) {
        selectedPlace = place;
        selectedDestinationLatLng = new LatLng(place.getLatitude(), place.getLongitude());

        if (currentSelectedMarker != null) {
            currentSelectedMarker.setAlpha(1.0f);
            currentSelectedMarker.setZIndex(0f);
        }
        currentSelectedMarker = marker;
        currentSelectedMarker.setAlpha(0.85f);
        currentSelectedMarker.setZIndex(10f);

        tvRestaurantName.setText(place.getName());
        tvRestaurantAddress.setText(place.getAddress());
        tvDealsCount.setText(getString(
                R.string.map_deals_rating_template,
                place.getCategory(),
                place.getRating()
        ));
        // Routes are fetched only from the "Show route" button (each one is a paid API call),
        // so a marker tap just shows the straight-line distance.
        clearRouteOnly();
        showStraightLineHint(currentUserLatLng, selectedDestinationLatLng);
        tvAvailableItems.setText(getString(R.string.map_loading_items));
        loadAvailableItemsPreview(place.getId());
        if (ivRestaurantImage instanceof ImageView) {
            ((ImageView) ivRestaurantImage).setImageResource(R.drawable.placeholder_food);
        }
        bottomSheet.setVisibility(View.VISIBLE);

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedDestinationLatLng, 15f));
    }

    private void showStraightLineHint(@Nullable LatLng origin, @Nullable LatLng destination) {
        if (tvRouteInfo == null) {
            return;
        }
        if (origin == null || destination == null) {
            tvRouteInfo.setText(getString(R.string.map_route_hint));
            return;
        }
        double distanceKm = LocationUtils.calculateDistance(
                origin.latitude, origin.longitude, destination.latitude, destination.longitude);
        tvRouteInfo.setText(getString(R.string.map_distance_hint_template, distanceKm));
    }

    private void drawRouteToSelectedPlace() {
        if (selectedDestinationLatLng == null || selectedPlace == null) {
            ToastUtils.showCustomToast(getContext(), getString(R.string.map_select_destination_first));
            return;
        }

        if (currentUserLatLng == null) {
            requestOrLoadLocation();
            ToastUtils.showCustomToast(getContext(), getString(R.string.map_waiting_current_location));
            return;
        }

        final LatLng expectedDestination = selectedDestinationLatLng;
        final long requestId = ++routeRequestVersion;

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        if (tvRouteInfo != null) {
            tvRouteInfo.setText(getString(R.string.map_calculating_route));
        }
        directionsApiClient.fetchRoute(currentUserLatLng, selectedDestinationLatLng, new DirectionsApiClient.Callback() {
            @Override
            public void onSuccess(RouteResult result) {
                if (!isMapUsable() || requestId != routeRequestVersion || !isSameLatLng(expectedDestination, selectedDestinationLatLng)) {
                    return;
                }
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                drawPolyline(result);
            }

            @Override
            public void onError(String message) {
                if (!isViewActive() || requestId != routeRequestVersion || !isSameLatLng(expectedDestination, selectedDestinationLatLng)) {
                    return;
                }
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                if (tvRouteInfo != null) {
                    tvRouteInfo.setText(getString(R.string.map_route_unavailable));
                }
                ToastUtils.showCustomToast(getContext(), message);
            }
        });
    }

    private boolean isSameLatLng(@Nullable LatLng a, @Nullable LatLng b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.latitude - b.latitude) < 0.00001
                && Math.abs(a.longitude - b.longitude) < 0.00001;
    }

    private void maybeUpdateRouteInfo(@NonNull LatLng origin, @NonNull LatLng destination) {
        // A drawn route already shows road distance and ETA; don't replace it with a straight-line figure
        if (currentRoutePolyline != null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean enoughTimePassed = now - lastRouteInfoUpdateAt >= 1500;
        boolean movedEnough = true;

        if (lastRouteInfoOrigin != null) {
            double movedKm = LocationUtils.calculateDistance(
                    lastRouteInfoOrigin.latitude,
                    lastRouteInfoOrigin.longitude,
                    origin.latitude,
                    origin.longitude
            );
            movedEnough = movedKm >= 0.025; // 25m movement threshold
        }

        if (!enoughTimePassed || !movedEnough) {
            return;
        }

        showStraightLineHint(origin, destination);
        lastRouteInfoUpdateAt = now;
        lastRouteInfoOrigin = origin;
    }

    private void drawPolyline(@NonNull RouteResult routeResult) {
        if (mMap == null || currentUserLatLng == null || selectedDestinationLatLng == null || !isAdded() || getContext() == null) {
            return;
        }

        clearRouteOnly();

        currentRoutePolyline = mMap.addPolyline(new PolylineOptions()
                .addAll(routeResult.getPolylinePoints())
                .width(11f)
                .color(ContextCompat.getColor(requireContext(), R.color.green_primary))
                .geodesic(true));

        tvRouteInfo.setText(getString(
                R.string.map_route_template_clear,
                routeResult.getDistanceText(),
                routeResult.getDurationText()
        ));

        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(currentUserLatLng)
                .include(selectedDestinationLatLng)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(96)));
    }

    private void clearRouteOnly() {
        if (currentRoutePolyline != null) {
            currentRoutePolyline.remove();
            currentRoutePolyline = null;
        }
    }

    private void loadAvailableItemsPreview(@NonNull String hotelId) {
        if (hotelFoodPreviewCache.containsKey(hotelId)) {
            tvAvailableItems.setText(hotelFoodPreviewCache.get(hotelId));
            return;
        }

        // Use orderByChild query to avoid scanning all food items
        foodItemsRef.orderByChild("hotelId").equalTo(hotelId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                StringBuilder preview = new StringBuilder();
                int added = 0;

                for (DataSnapshot child : snapshot.getChildren()) {

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
                if (tvAvailableItems != null && selectedPlace != null && hotelId.equals(selectedPlace.getId())) {
                    tvAvailableItems.setText(previewText);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                if (tvAvailableItems != null) {
                    tvAvailableItems.setText(getString(R.string.map_items_load_failed));
                }
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
        if (bottomSheet != null) {
            bottomSheet.setVisibility(View.GONE);
        }
        if (tvAvailableItems != null) {
            tvAvailableItems.setText(getString(R.string.map_items_tap_marker));
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

    @Override
    public void onResume() {
        super.onResume();
        if (mMap != null) {
            startLiveLocationUpdates();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLiveLocationUpdates();
        // In-flight route results are ignored after this bump, so they'd never hide the spinner
        routeRequestVersion++;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        stopLiveLocationUpdates();
        placesLoadVersion++;
        searchRequestVersion++;
        routeRequestVersion++;
        mMap = null;
        currentRoutePolyline = null;
        liveLocationCallback = null;
        isLocationUpdatesRunning = false;

        // MainActivity keeps this fragment instance alive between tab visits, so anything tied
        // to the destroyed view/map must be reset or it points at objects that no longer exist.
        hasCenteredOnUser = false;
        markerPlaceMap.clear();
        markerByHotelId.clear();
        currentSelectedMarker = null;
        searchMarker = null;
        selectedPlace = null;
        selectedDestinationLatLng = null;
        lastRouteInfoOrigin = null;

        loadingOverlay = null;
        bottomSheet = null;
        ivRestaurantImage = null;
        ivMyLocation = null;
        tvRestaurantName = null;
        tvRestaurantAddress = null;
        tvDealsCount = null;
        tvRouteInfo = null;
        tvAvailableItems = null;
        btnViewDeals = null;
        searchView = null;
        topPanel = null;
        super.onDestroyView();
    }
}
