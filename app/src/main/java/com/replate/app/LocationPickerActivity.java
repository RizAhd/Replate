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

        btnUseLiveLocation.setOnClickListener(v -> moveToLiveLocation(false));

        btnConfirmLocation.setOnClickListener(v -> {
            if (selectedLatLng != null && selectedAddress != null) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("lat", selectedLatLng.latitude);
                resultIntent.putExtra("lng", selectedLatLng.longitude);
                resultIntent.putExtra("address", selectedAddress);
                setResult(RESULT_OK, resultIntent);
                finish();
            } else {
                ToastUtils.showCustomToast(this, getString(R.string.location_picker_select_prompt));
            }
        });
    }

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

    private void searchAndMove(@NonNull String query) {
        if (!Geocoder.isPresent()) {
            ToastUtils.showCustomToast(this, getString(R.string.location_picker_search_failed));
            return;
        }

        geocoderExecutor.execute(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocationName(query, 1);
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        LatLng target = new LatLng(address.getLatitude(), address.getLongitude());
                        if (mMap != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16f));
                        }
                        selectedLatLng = target;
                        getAddress(target);
                    } else {
                        ToastUtils.showCustomToast(this, getString(R.string.location_picker_not_found));
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> ToastUtils.showCustomToast(this, getString(R.string.location_picker_search_failed)));
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        moveToLiveLocation(true);

        mMap.setOnCameraIdleListener(() -> {
            selectedLatLng = mMap.getCameraPosition().target;
            getAddress(selectedLatLng);
        });
    }

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

        fusedLocationClient.getLastLocation().addOnSuccessListener(this::handleLocationResult);
        if (!silent) {
            ToastUtils.showCustomToast(this, getString(R.string.location_picker_use_live));
        }
    }

    private void handleLocationResult(Location location) {
        if (mMap == null) return;

        LatLng target;
        if (location != null) {
            target = new LatLng(location.getLatitude(), location.getLongitude());
        } else {
            target = new LatLng(6.9271, 79.8612); // Colombo fallback
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15f));
        selectedLatLng = target;
        getAddress(target);
    }

    private void getAddress(LatLng latLng) {
        if (!Geocoder.isPresent()) {
            selectedAddress = String.format(Locale.getDefault(), "%.5f, %.5f", latLng.latitude, latLng.longitude);
            tvSelectedAddress.setText(selectedAddress);
            return;
        }

        geocoderExecutor.execute(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        selectedAddress = addresses.get(0).getAddressLine(0);
                    } else {
                        selectedAddress = String.format(Locale.getDefault(), "%.5f, %.5f", latLng.latitude, latLng.longitude);
                    }
                    tvSelectedAddress.setText(selectedAddress);
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    selectedAddress = String.format(Locale.getDefault(), "%.5f, %.5f", latLng.latitude, latLng.longitude);
                    tvSelectedAddress.setText(selectedAddress);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        geocoderExecutor.shutdownNow();
    }
}

