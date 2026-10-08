package com.replate.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.replate.app.utils.ToastUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Map screen where the customer picks a pickup or delivery spot.
public class LocationPickerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private TextView tvSelectedAddress;
    private Button btnConfirmLocation;
    private SearchView searchLocation;
    private Button btnUseLiveLocation;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng selectedLatLng;
    private String selectedAddress;
    private final ExecutorService geocoderExecutor = Executors.newSingleThreadExecutor();
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int addressRequestId;
    // True once the pin sits on a place the customer picked or the phone really found. Until then the
    // map may show a default city, but Confirm must not hand that guess back as their address.
    private boolean positionChosen;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fineGranted || coarseGranted) {
                    moveToLiveLocation(true);
                } else {
                    ToastUtils.showCustomToast(this, getString(R.string.location_picker_select_prompt));
                }
            });

    // Finds the views, loads the map, wires search and buttons.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        tvSelectedAddress = findViewById(R.id.tvSelectedAddress);
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        searchLocation = findViewById(R.id.searchLocation);
        btnUseLiveLocation = findViewById(R.id.btnUseLiveLocation);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupSearch();
        findViewById(R.id.btnLocationBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        btnUseLiveLocation.setOnClickListener(v -> moveToLiveLocation(false));

        // Confirm sends the chosen spot and address back.
        btnConfirmLocation.setOnClickListener(v -> {
            if (selectedLatLng != null && positionChosen) {
                // The address lookup may still be running; fall back to the coordinates
                String address = selectedAddress != null && !selectedAddress.trim().isEmpty()
                        ? selectedAddress
                        : coordinatesText(selectedLatLng);
                Intent resultIntent = new Intent();
                resultIntent.putExtra("lat", selectedLatLng.latitude);
                resultIntent.putExtra("lng", selectedLatLng.longitude);
                resultIntent.putExtra("address", address);
                setResult(RESULT_OK, resultIntent);
                finish();
            } else {
                ToastUtils.showCustomToast(this, getString(R.string.location_picker_select_prompt));
            }
        });
    }

    // Runs a place search when the user submits text.
    private void setupSearch() {
        if (searchLocation == null) return;

        searchLocation.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.trim().isEmpty()) {
                    searchLocation.clearFocus();
                    searchAndMove(query.trim());
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    // Finds the typed place and moves the map to it.
    private void searchAndMove(@NonNull String query) {
        if (!Geocoder.isPresent()) {
            ToastUtils.showCustomToast(this, getString(R.string.location_picker_search_failed));
            return;
        }

        runGeocoderTask(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocationName(query, 1);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        LatLng target = new LatLng(address.getLatitude(), address.getLongitude());
                        if (mMap != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16f));
                        }
                        positionChosen = true;
                        selectedLatLng = target;
                        getAddress(target);
                    } else {
                        ToastUtils.showCustomToast(this, getString(R.string.location_picker_not_found));
                    }
                });
            } catch (IOException | IllegalArgumentException e) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        ToastUtils.showCustomToast(this, getString(R.string.location_picker_search_failed));
                    }
                });
            }
        });
    }

    // Sets up the map and tracks where the pin ends up.
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        moveToLiveLocation(true);

        mMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                positionChosen = true;
            }
        });
        mMap.setOnCameraIdleListener(() -> {
            if (!positionChosen) return;
            selectedLatLng = mMap.getCameraPosition().target;
            // Wait for the map to settle: panning fires many idle events in a row
            uiHandler.removeCallbacks(lookupRunnable);
            uiHandler.postDelayed(lookupRunnable, 350);
        });
    }

    // Asks for permission if needed, then jumps to the phone's position.
    private void moveToLiveLocation(boolean silent) {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        if (mMap != null) {
            try {
                mMap.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
            }
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        handleLocationResult(location);
                    } else {
                        requestFreshLocation();
                    }
                })
                .addOnFailureListener(e -> requestFreshLocation());
        if (!silent) {
            ToastUtils.showCustomToast(this, getString(R.string.location_picker_use_live));
        }
    }

    /** No saved position (GPS was off or just started): ask for a new one before giving up. */
    @android.annotation.SuppressLint("MissingPermission")
    private void requestFreshLocation() {
        try {
            fusedLocationClient.getCurrentLocation(
                            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            new com.google.android.gms.tasks.CancellationTokenSource().getToken())
                    .addOnSuccessListener(this::handleLocationResult)
                    .addOnFailureListener(e -> handleLocationResult(null));
        } catch (SecurityException e) {
            handleLocationResult(null);
        }
    }

    // Moves the map to the found position, or a default city.
    private void handleLocationResult(Location location) {
        if (mMap == null || isFinishing() || isDestroyed()) return;

        if (location == null) {
            // Show the default city so the map isn't empty, but leave the choice to the customer
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(6.9271, 79.8612), 12f));
            ToastUtils.showCustomToast(this, getString(R.string.location_picker_live_unavailable));
            return;
        }

        LatLng target = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15f));
        positionChosen = true;
        selectedLatLng = target;
        getAddress(target);
    }

    // Formats a position as plain numbers.
    private static String coordinatesText(LatLng latLng) {
        return String.format(Locale.getDefault(), "%.5f, %.5f", latLng.latitude, latLng.longitude);
    }

    // Remembers the address and shows it.
    private void applyAddress(String address) {
        selectedAddress = address;
        tvSelectedAddress.setText(address);
    }

    /** Looks up the address of the camera centre once the map has stopped moving. */
    private final Runnable lookupRunnable = () -> {
        if (selectedLatLng != null) {
            getAddress(selectedLatLng);
        }
    };

    // Looks up the street address for a position.
    private void getAddress(LatLng latLng) {
        if (!Geocoder.isPresent()) {
            applyAddress(coordinatesText(latLng));
            return;
        }

        // Only the newest lookup may update the screen; a slow older one must not overwrite it
        final int requestId = ++addressRequestId;

        runGeocoderTask(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            String resolved;
            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                String line = (addresses != null && !addresses.isEmpty()) ? addresses.get(0).getAddressLine(0) : null;
                resolved = (line != null && !line.trim().isEmpty()) ? line : coordinatesText(latLng);
            } catch (IOException | IllegalArgumentException e) {
                resolved = coordinatesText(latLng);
            }
            final String result = resolved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || requestId != addressRequestId) return;
                applyAddress(result);
            });
        });
    }

    /** Runs a lookup on the background executor; ignored quietly once the screen is closing. */
    private void runGeocoderTask(Runnable task) {
        try {
            geocoderExecutor.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The activity is being destroyed; nothing left to update
        }
    }

    // Stops pending lookups.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        geocoderExecutor.shutdownNow();
    }
}

