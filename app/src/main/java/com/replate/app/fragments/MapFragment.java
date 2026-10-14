package com.replate.app.fragments;

import android.Manifest;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.bumptech.glide.Glide;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationSettingsRequest;
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
import com.replate.app.SearchActivity;
import com.replate.app.map.data.FirebasePlacesDataSource;
import com.replate.app.map.data.MapRules;
import com.replate.app.map.data.PlacesDataSource;
import com.replate.app.map.model.FoodPlace;
import com.replate.app.map.routing.DirectionsApiClient;
import com.replate.app.map.routing.RouteResult;
import com.replate.app.utils.FavoriteHotelToggle;
import com.replate.app.utils.LocationUtils;
import com.replate.app.receivers.GpsStateReceiver;
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
    private ImageView ivFavoriteHotel;
    private TextView tvRestaurantAddress;
    private TextView tvDealsCount;
    private TextView tvRouteInfo;
    private TextView tvAvailableItems;
    private Button btnViewDeals;
    private Button btnShowRoute;
    private SearchView searchView;
    private View topPanel;
    private View cardMapStatus;
    private TextView tvMapStatus;
    private Button btnMapStatusAction;
    private boolean placesLoadFailed;
    private GpsStateReceiver gpsReceiver;

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
    private final Map<String, Long> hotelFoodPreviewCachedAt = new HashMap<>();
    private final Map<String, String> hotelPreviewImageCache = new HashMap<>();
    // Sold-out items must not stay listed for the whole session
    private static final long PREVIEW_CACHE_MS = 60_000L;
    private final List<FoodPlace> allPlaces = new ArrayList<>();
    private Marker searchMarker;
    private DatabaseReference foodItemsRef;
    private String pendingFocusHotelId;
    private LocationCallback liveLocationCallback;
    private boolean hasCenteredOnUser;
    private long lastPlacesLoadAt;
    // The tab is kept alive between visits, so hotels are re-read when you return after a while
    private static final long PLACES_REFRESH_MS = 2 * 60 * 1000L;
    private int lastTopInset;
    private int lastBottomInset;
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
                    onLocationPermissionDenied();
                }
                updateMapStatus();
            });

    // "Turn on location?" dialog from Google Play services (shown when GPS is switched off)
    private boolean askedToEnableGps;
    private final ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (!isAdded()) {
                    return;
                }
                if (result.getResultCode() == Activity.RESULT_OK) {
                    requestFreshMapLocation();
                    startLiveLocationUpdates();
                } else {
                    updateMapStatus();
                }
            });

    // Builds the Map screen and starts the map.
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        directionsApiClient = new DirectionsApiClient(requireContext(), getString(R.string.google_maps_key));
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

    // Finds the views on the screen.
    private void initViews(View view) {
        loadingOverlay = view.findViewById(R.id.loadingOverlay);
        topPanel = view.findViewById(R.id.topPanel);
        cardMapStatus = view.findViewById(R.id.cardMapStatus);
        tvMapStatus = view.findViewById(R.id.tvMapStatus);
        btnMapStatusAction = view.findViewById(R.id.btnMapStatusAction);
        bottomSheet = view.findViewById(R.id.bottomSheet);
        ivRestaurantImage = view.findViewById(R.id.ivRestaurantImage);
        ivMyLocation = view.findViewById(R.id.ivMyLocation);
        tvRestaurantName = view.findViewById(R.id.tvRestaurantName);
        ivFavoriteHotel = view.findViewById(R.id.ivFavoriteHotel);
        tvRestaurantAddress = view.findViewById(R.id.tvRestaurantAddress);
        tvDealsCount = view.findViewById(R.id.tvDealsCount);
        tvRouteInfo = view.findViewById(R.id.tvRouteInfo);
        tvAvailableItems = view.findViewById(R.id.tvAvailableItems);
        btnViewDeals = view.findViewById(R.id.btnViewDeals);
        btnShowRoute = view.findViewById(R.id.btnShowRoute);
        searchView = view.findViewById(R.id.searchView);

        if (bottomSheet != null) {
            bottomSheet.setVisibility(View.GONE);
        }
        // Re-measure the map's padding whenever either overlay changes size or appears
        View.OnLayoutChangeListener overlayResized = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((bottom - top) != (oldBottom - oldTop)) {
                updateMapPadding();
            }
        };
        if (topPanel != null) {
            topPanel.addOnLayoutChangeListener(overlayResized);
        }
        if (bottomSheet != null) {
            bottomSheet.addOnLayoutChangeListener(overlayResized);
        }
        setupSearch();
    }

    // Keeps the panels clear of the status and navigation bars.
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

            lastTopInset = topInset;
            lastBottomInset = bottomInset;
            updateMapPadding();
            return insets;
        });
    }

    /**
     * Keeps the map's usable area between the search panel at the top and the hotel card at the
     * bottom, using their real measured heights, so markers and camera moves never land underneath.
     */
    private void updateMapPadding() {
        if (mMap == null || !isAdded() || getContext() == null) {
            return;
        }
        int top = lastTopInset + dp(8)
                + (topPanel != null && topPanel.getHeight() > 0 ? topPanel.getHeight() : dp(122))
                + dp(8);
        int bottom = lastBottomInset + dp(16);
        if (bottomSheet != null && bottomSheet.getVisibility() == View.VISIBLE && bottomSheet.getHeight() > 0) {
            bottom += bottomSheet.getHeight();
        }
        mMap.setPadding(dp(12), top, dp(12), bottom);
    }

    // Converts dp to pixels.
    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    // True while the screen is still showing.
    private boolean isViewActive() {
        return isAdded() && getView() != null;
    }

    // True when the screen and the map are both ready.
    private boolean isMapUsable() {
        return isViewActive() && mMap != null;
    }

    // Asks the map fragment to load the map.
    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    // Wires up the location, route and deals buttons.
    private void setupClickListeners() {
        if (ivMyLocation != null) {
            ivMyLocation.setOnClickListener(v -> moveToCurrentLocation());
        }

        if (btnShowRoute != null) {
            btnShowRoute.setOnClickListener(v -> {
                if (selectedPlace == null) {
                    ToastUtils.showCustomToast(getContext(), R.string.map_select_a_place_marker_first);
                    return;
                }
                drawRouteToSelectedPlace();
            });
        }

        if (btnViewDeals != null) {
            btnViewDeals.setOnClickListener(v -> {
                if (selectedPlace == null) {
                    ToastUtils.showCustomToast(getContext(), R.string.map_select_a_place_marker_first);
                    return;
                }
                // Search matches hotel names, so this lists just this place's dishes
                Intent intent = new Intent(requireContext(), SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_QUERY, selectedPlace.getName());
                startActivity(intent);
            });
        }
    }

    // Sets up the search box that filters hotels or finds a place.
    private void setupSearch() {
        if (searchView == null) {
            return;
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            // Runs the search when the user submits it.
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

            // Filters the hotel pins as the user types.
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

    // Shows only the hotels that match the text.
    private boolean applyPlaceFilter(@NonNull String query, boolean moveCameraToFirst) {
        if (allPlaces.isEmpty()) {
            return false;
        }

        String normalized = query.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            showOnlyPlaces(allPlaces);
            return true;
        }

        // Match on name and address only. "category" holds display text like "Food • 5 items",
        // which made queries such as "items" match every hotel.
        List<FoodPlace> filtered = new ArrayList<>();
        for (FoodPlace place : allPlaces) {
            String name = place.getName() == null ? "" : place.getName().toLowerCase(Locale.US);
            String address = place.getAddress() == null ? "" : place.getAddress().toLowerCase(Locale.US);
            if (name.contains(normalized) || address.contains(normalized)) {
                filtered.add(place);
            }
        }

        if (filtered.isEmpty()) {
            return false;
        }

        showOnlyPlaces(filtered);
        if (moveCameraToFirst && mMap != null) {
            FoodPlace first = filtered.get(0);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(first.getLatitude(), first.getLongitude()),
                    14f
            ));
        }
        return true;
    }

    /**
     * Shows only the given places by toggling existing markers, so typing in the search box
     * doesn't rebuild the whole map or wipe a route that is already drawn.
     */
    private void showOnlyPlaces(@NonNull List<FoodPlace> visiblePlaces) {
        if (markerByHotelId.isEmpty()) {
            renderPlaceMarkers(visiblePlaces);
            return;
        }

        java.util.Set<String> visibleIds = new java.util.HashSet<>();
        for (FoodPlace place : visiblePlaces) {
            visibleIds.add(place.getId());
        }
        for (Map.Entry<String, Marker> entry : markerByHotelId.entrySet()) {
            Marker marker = entry.getValue();
            if (marker != null) {
                marker.setVisible(visibleIds.contains(entry.getKey()));
            }
        }

        if (selectedPlace != null && !visibleIds.contains(selectedPlace.getId())) {
            clearSelectedState();
        }
    }

    // Looks up a place name and moves the map there.
    private void searchLocation(@NonNull String locationName) {
        if (!Geocoder.isPresent()) {
            ToastUtils.showCustomToast(getContext(), R.string.map_geocoder_not_available_on_this);
            return;
        }

        if (!isViewActive()) {
            return;
        }

        final long requestId = ++searchRequestVersion;

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        // Grab these on the main thread; requireContext()/requireActivity() can throw if the
        // fragment detaches while the lookup is running on the background thread.
        final android.content.Context appContext = requireContext().getApplicationContext();
        final androidx.fragment.app.FragmentActivity hostActivity = requireActivity();
        new Thread(() -> {
            if (requestId != searchRequestVersion) {
                return;
            }
            Geocoder geocoder = new Geocoder(appContext, Locale.getDefault());
            try {
                List<Address> result = geocoder.getFromLocationName(locationName, 1);
                if (requestId != searchRequestVersion) {
                    return;
                }
                hostActivity.runOnUiThread(() -> {
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
                        ToastUtils.showCustomToast(getContext(), R.string.map_location_not_found);
                    }
                });
            } catch (IOException | IllegalArgumentException lookupError) {
                if (requestId != searchRequestVersion) {
                    return;
                }
                hostActivity.runOnUiThread(() -> {
                    if (!isViewActive() || requestId != searchRequestVersion) {
                        return;
                    }
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    ToastUtils.showCustomToast(getContext(), R.string.map_search_failed_check_internet);
                });
            }
        }).start();
    }

    // Sets up the map settings and loads the hotel pins.
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        // The screen has its own "my location" button; hide Google's duplicate
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        // Hide Google's own restaurant/shop pins so REPLATE's hotel markers are the ones you see
        try {
            mMap.setMapStyle(com.google.android.gms.maps.model.MapStyleOptions
                    .loadRawResourceStyle(requireContext(), R.raw.map_style_clean));
        } catch (RuntimeException e) {
            // The default map style is fine if the custom one can't be applied
        }

        // Start somewhere useful instead of the world view while location and hotels load
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM));

        View root = getView();
        if (root != null) {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            int topInset = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            lastTopInset = topInset;
            lastBottomInset = bottomInset;
            updateMapPadding();
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

    // Loads the hotels and draws their pins.
    private void loadAndRenderPlaceMarkers() {
        final long requestId = ++placesLoadVersion;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        placesDataSource.fetchPlaces(new PlacesDataSource.Callback() {
            // Draws the pins for the loaded hotels.
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
                lastPlacesLoadAt = System.currentTimeMillis();
                placesLoadFailed = false;
                updateMapStatus();
                if (places.isEmpty()) {
                    showNoRegisteredHotelsState();
                    return;
                }
                renderPlaceMarkers(places);
                fitCameraToPlaces(places);
            }

            // Shows a message that hotels could not load.
            @Override
            public void onError(@NonNull String message) {
                if (!isViewActive() || requestId != placesLoadVersion) {
                    return;
                }
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                showNoRegisteredHotelsState();
                // Shown as a banner with a Retry button; the raw error text isn't useful to the user
                placesLoadFailed = true;
                updateMapStatus();
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

    /** One persistent hint at the top of the map: location denied or off, hotels failed to load, or none listed. */
    private void updateMapStatus() {
        if (!isViewActive() || cardMapStatus == null) {
            return;
        }
        Context context = requireContext();
        boolean hasLocationPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsOn = lm != null && LocationManagerCompat.isLocationEnabled(lm);

        int message;
        int action = 0;
        View.OnClickListener onAction = null;
        if (!hasLocationPermission) {
            message = R.string.map_status_permission;
            action = R.string.map_status_allow;
            onAction = v -> locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        } else if (!gpsOn) {
            message = R.string.map_status_gps_off;
            action = R.string.map_status_turn_on;
            onAction = v -> startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } else if (placesLoadFailed) {
            message = R.string.map_status_load_failed;
            action = R.string.map_status_retry;
            onAction = v -> loadAndRenderPlaceMarkers();
        } else if (lastPlacesLoadAt > 0 && allPlaces.isEmpty()) {
            message = R.string.map_status_no_places;
        } else {
            cardMapStatus.setVisibility(View.GONE);
            return;
        }

        tvMapStatus.setText(message);
        if (onAction != null) {
            btnMapStatusAction.setText(action);
            btnMapStatusAction.setOnClickListener(onAction);
            btnMapStatusAction.setVisibility(View.VISIBLE);
        } else {
            btnMapStatusAction.setVisibility(View.GONE);
        }
        cardMapStatus.setVisibility(View.VISIBLE);
    }

    // Shows a hint that no hotels are listed yet.
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

    // Asks for location permission, or uses it if already given.
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

    // Turns on the blue dot and centres the map on the user.
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
            ToastUtils.showCustomToast(getContext(), R.string.map_location_permission_denied);
            return;
        }

        ensureLocationSettings();

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

    // Asks the phone for a fresh location fix.
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
                    ToastUtils.showCustomToast(getContext(), R.string.map_unable_to_fetch_live_location);
                });
    }

    // Starts getting location updates while the map is open.
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
                // Handles each new location from the phone.
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location == null || mMap == null) {
                        return;
                    }

                    boolean firstFix = currentUserLatLng == null;
                    currentUserLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    if (firstFix) {
                        refreshMarkerColors();
                    }
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

        // The map only needs a rough position to centre and colour markers, so balanced accuracy
        // every 15 s (and only after moving 30 m) is plenty and far easier on the battery.
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000)
                .setMinUpdateIntervalMillis(8000)
                .setMinUpdateDistanceMeters(30f)
                .build();

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, liveLocationCallback, Looper.getMainLooper());
            isLocationUpdatesRunning = true;
        } catch (SecurityException ignored) {
        }
    }

    // Stops location updates.
    private void stopLiveLocationUpdates() {
        if (liveLocationCallback != null && isLocationUpdatesRunning) {
            fusedLocationClient.removeLocationUpdates(liveLocationCallback);
            isLocationUpdatesRunning = false;
        }
    }

    // Remembers the user position and centres the map once.
    private void handleLocation(@Nullable Location location) {
        if (mMap == null) {
            return;
        }

        if (location == null) {
            ToastUtils.showCustomToast(getContext(), R.string.map_waiting_for_live_location);
            return;
        }

        boolean firstFix = currentUserLatLng == null;
        currentUserLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (firstFix) {
            refreshMarkerColors();
        }
        if (!hasCenteredOnUser) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, 14f));
            hasCenteredOnUser = true;
        }
    }

    /**
     * After a first "no" Android will ask again; after "don't ask again" the system dialog never
     * appears, so the only way forward is the app's settings page. Say so instead of doing nothing.
     */
    private void onLocationPermissionDenied() {
        if (!isAdded()) {
            return;
        }
        boolean canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (canAskAgain) {
            ToastUtils.showCustomToast(getContext(), getString(R.string.map_location_denied));
        } else {
            showOpenSettingsDialog();
        }
    }

    // Offers to open the app settings to allow location.
    private void showOpenSettingsDialog() {
        if (!isAdded() || getContext() == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.map_location_needed_title)
                .setMessage(R.string.map_location_needed_message)
                .setPositiveButton(R.string.map_open_settings, (d, w) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", requireContext().getPackageName(), null));
                    try {
                        startActivity(intent);
                    } catch (android.content.ActivityNotFoundException ignored) {
                        // no settings app to open; nothing more we can do
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Offers the standard "Turn on location" prompt when GPS is off. Asked once per screen visit. */
    private void ensureLocationSettings() {
        if (!isAdded() || askedToEnableGps) {
            return;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15000).build();
        LocationSettingsRequest settings = new LocationSettingsRequest.Builder().addLocationRequest(request).build();
        LocationServices.getSettingsClient(requireActivity()).checkLocationSettings(settings)
                .addOnFailureListener(e -> {
                    if (!isAdded() || askedToEnableGps || !(e instanceof ResolvableApiException)) {
                        return;
                    }
                    askedToEnableGps = true;
                    try {
                        locationSettingsLauncher.launch(
                                new IntentSenderRequest.Builder(((ResolvableApiException) e).getResolution()).build());
                    } catch (RuntimeException ignored) {
                        // the prompt could not be shown; the user can still use the map without location
                    }
                });
    }

    // Moves the camera to the user.
    private void moveToCurrentLocation() {
        if (mMap == null || currentUserLatLng == null) {
            ToastUtils.showCustomToast(getContext(), R.string.map_current_location_not_available_yet);
            requestOrLoadLocation();
            return;
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentUserLatLng, 15f));
    }

    // Draws one pin for each hotel.
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

    /**
     * Called by MainActivity when a notification or order screen asks to show a hotel while this
     * tab already exists. If the pins aren't drawn yet it is applied as soon as they are.
     */
    public void focusHotel(@Nullable String hotelId) {
        if (hotelId == null || hotelId.trim().isEmpty()) {
            return;
        }
        pendingFocusHotelId = hotelId.trim();
        if (mMap != null && !markerByHotelId.isEmpty()) {
            maybeFocusDeepLinkedHotel();
        }
    }

    // Selects the hotel a notification asked for, once its pin exists.
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

    // Drops a pin on the searched place.
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

    // Opens the hotel card and shows the straight-line distance.
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
        FavoriteHotelToggle.bind(requireContext(), ivFavoriteHotel, place.getId(), place.getName(), place.getAddress());
        tvRestaurantAddress.setText(place.getAddress());
        tvDealsCount.setText(place.getRating() > 0
                ? getString(R.string.map_deals_rating_template, place.getCategory(), place.getRating())
                : getString(R.string.map_deals_new_template, place.getCategory()));
        // Routes are fetched only from the "Show route" button (each one is a paid API call),
        // so a marker tap just shows the straight-line distance.
        clearRouteOnly();
        showStraightLineHint(currentUserLatLng, selectedDestinationLatLng);
        tvAvailableItems.setText(getString(R.string.map_loading_items));
        loadAvailableItemsPreview(place.getId());
        showPreviewImage(place.getId());
        showSheet();

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedDestinationLatLng, 15f));
    }

    // Shows the straight-line distance to the hotel.
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

    // Gets the driving route to the hotel and draws it.
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
            // Draws the route if it is still the one wanted.
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

            // Shows why the route could not be found.
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

    // True when two points are almost the same spot.
    private boolean isSameLatLng(@Nullable LatLng a, @Nullable LatLng b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.latitude - b.latitude) < 0.00001
                && Math.abs(a.longitude - b.longitude) < 0.00001;
    }

    // Updates the distance line unless a route is already drawn.
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

    // Draws the route line on the map.
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

    // Removes the route line but keeps the selected hotel.
    private void clearRouteOnly() {
        if (currentRoutePolyline != null) {
            currentRoutePolyline.remove();
            currentRoutePolyline = null;
        }
    }

    // Counts the hotel's available dishes for the card.
    private void loadAvailableItemsPreview(@NonNull String hotelId) {
        Long cachedAt = hotelFoodPreviewCachedAt.get(hotelId);
        if (hotelFoodPreviewCache.containsKey(hotelId) && cachedAt != null
                && System.currentTimeMillis() - cachedAt < PREVIEW_CACHE_MS) {
            tvAvailableItems.setText(hotelFoodPreviewCache.get(hotelId));
            showPreviewImage(hotelId);
            return;
        }

        // Use orderByChild query to avoid scanning all food items
        foodItemsRef.orderByChild("hotelId").equalTo(hotelId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            // Counts the available dishes and updates the card.
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                StringBuilder preview = new StringBuilder();
                int added = 0;
                String firstImage = "";

                for (DataSnapshot child : snapshot.getChildren()) {

                    // Same definition of "available" as the pin's item count on the map
                    String status = asString(child.child("status").getValue());
                    if (!MapRules.isListableItem(status, readQuantity(child))) {
                        continue;
                    }

                    String foodName = asString(child.child("foodName").getValue());
                    if (foodName.isEmpty()) {
                        continue;
                    }

                    if (firstImage.isEmpty()) {
                        firstImage = asString(child.child("imageUrl").getValue());
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
                hotelFoodPreviewCachedAt.put(hotelId, System.currentTimeMillis());
                hotelPreviewImageCache.put(hotelId, firstImage);
                if (tvAvailableItems != null && selectedPlace != null && hotelId.equals(selectedPlace.getId())) {
                    tvAvailableItems.setText(previewText);
                    showPreviewImage(hotelId);
                }
            }

            // Shows that the dish count could not load.
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                if (tvAvailableItems != null) {
                    tvAvailableItems.setText(getString(R.string.map_items_load_failed));
                }
            }
        });
    }

    /** Shows the hotel's first available dish on the card, or the placeholder until one is known. */
    private void showPreviewImage(@NonNull String hotelId) {
        if (!(ivRestaurantImage instanceof ImageView) || !isAdded()) {
            return;
        }
        ImageView target = (ImageView) ivRestaurantImage;
        String url = hotelPreviewImageCache.get(hotelId);
        if (url == null || url.isEmpty()) {
            target.setImageResource(R.drawable.placeholder_food);
            return;
        }
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.placeholder_food)
                .error(R.drawable.placeholder_food)
                .centerCrop()
                .into(target);
    }

    /** Quantity under any of the names listings use, or null when none is set. */
    @Nullable
    private Integer readQuantity(@NonNull DataSnapshot item) {
        for (String key : new String[]{"quantity", "qty", "stock"}) {
            Object value = item.child(key).getValue();
            if (value == null) continue;
            if (value instanceof Number) return ((Number) value).intValue();
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                // try the next name
            }
        }
        return null;
    }

    // Turns a value into trimmed text.
    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    // Turns a value into a whole number, or 0.
    private int asInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    // Turns a value into a decimal number, or 0.
    private double asDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    // True when the phone allows animations.
    private boolean sheetAnimationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || android.animation.ValueAnimator.areAnimatorsEnabled();
    }

    /** Slides the hotel card up from the bottom edge instead of popping it in. */
    private void showSheet() {
        if (bottomSheet == null) {
            return;
        }
        bottomSheet.animate().cancel();
        boolean wasHidden = bottomSheet.getVisibility() != View.VISIBLE;
        bottomSheet.setVisibility(View.VISIBLE);
        if (wasHidden && sheetAnimationsEnabled()) {
            float distance = getResources().getDisplayMetrics().density * 160f;
            bottomSheet.setTranslationY(distance);
            bottomSheet.setAlpha(0f);
            bottomSheet.animate().translationY(0f).alpha(1f).setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else {
            bottomSheet.setTranslationY(0f);
            bottomSheet.setAlpha(1f);
        }
    }

    // Slides the hotel card down and hides it.
    private void hideSheet() {
        if (bottomSheet == null || bottomSheet.getVisibility() != View.VISIBLE) {
            return;
        }
        bottomSheet.animate().cancel();
        if (!sheetAnimationsEnabled()) {
            bottomSheet.setVisibility(View.GONE);
            return;
        }
        final View sheet = bottomSheet;
        float distance = getResources().getDisplayMetrics().density * 160f;
        sheet.animate().translationY(distance).alpha(0f).setDuration(200)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    sheet.setVisibility(View.GONE);
                    sheet.setTranslationY(0f);
                    sheet.setAlpha(1f);
                }).start();
    }

    // Forgets the selected hotel and its route.
    private void clearSelectedState() {
        selectedPlace = null;
        selectedDestinationLatLng = null;
        if (currentSelectedMarker != null) {
            currentSelectedMarker.setAlpha(1.0f);
            currentSelectedMarker = null;
        }
        clearRouteOnly();
        hideSheet();
        if (tvAvailableItems != null) {
            tvAvailableItems.setText(getString(R.string.map_items_tap_marker));
        }
    }

    /** Re-colours existing markers by distance once the user's position becomes known. */
    private void refreshMarkerColors() {
        for (Marker marker : markerByHotelId.values()) {
            if (marker == null) continue;
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(markerColorForDistance(marker.getPosition())));
        }
    }

    // Picks a pin colour from the distance to the user.
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

    // Rechecks GPS, restarts location updates and reloads old hotels.
    @Override
    public void onResume() {
        super.onResume();
        // The user may have changed location access or turned GPS on from the phone settings
        updateMapStatus();
        if (gpsReceiver == null) {
            gpsReceiver = new GpsStateReceiver(this::updateMapStatus);
            ContextCompat.registerReceiver(requireContext(), gpsReceiver,
                    new android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        }
        if (mMap != null) {
            startLiveLocationUpdates();
            // Not while a hotel is selected: reloading would wipe the route being looked at
            if (selectedPlace == null && lastPlacesLoadAt > 0
                    && System.currentTimeMillis() - lastPlacesLoadAt > PLACES_REFRESH_MS) {
                loadAndRenderPlaceMarkers();
            }
        }
    }

    // Stops location updates and cancels pending route work.
    @Override
    public void onPause() {
        super.onPause();
        if (gpsReceiver != null) {
            try {
                requireContext().unregisterReceiver(gpsReceiver);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // already unregistered
            }
            gpsReceiver = null;
        }
        stopLiveLocationUpdates();
        // In-flight route results are ignored after this bump, so they'd never hide the spinner
        routeRequestVersion++;
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    // Clears everything tied to the destroyed view and map.
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
        lastPlacesLoadAt = 0;
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
        ivFavoriteHotel = null;
        tvRestaurantAddress = null;
        tvDealsCount = null;
        tvRouteInfo = null;
        tvAvailableItems = null;
        btnViewDeals = null;
        btnShowRoute = null;
        searchView = null;
        topPanel = null;
        super.onDestroyView();
    }
}
