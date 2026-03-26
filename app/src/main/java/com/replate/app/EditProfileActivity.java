package com.replate.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class EditProfileActivity extends AppCompatActivity {

    private TextInputEditText etName, etPhone, etAddress;
    private Button btnSave, btnPickLocation;
    private CircleImageView ivProfileImage;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ActivityResultLauncher<Intent> locationPickerLauncher;

    private Double pickedLat = null;
    private Double pickedLng = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePickedLocation(result.getData());
                    }
                }
        );

        initViews();
        loadUserData();
        setupListeners();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        btnSave = findViewById(R.id.btnSave);
        btnPickLocation = findViewById(R.id.btnPickLocation);
        ivProfileImage = findViewById(R.id.ivProfileImage);
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            mDatabase.child("users").child(user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String name = snapshot.child("name").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);
                        String address = snapshot.child("address").getValue(String.class);

                        if (name != null) etName.setText(name);
                        if (phone != null) etPhone.setText(phone);
                        if (address != null) etAddress.setText(address);

                        // Handle profile image if implementing storage
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show());
        }
    }

    private void setupListeners() {
        if (btnPickLocation != null) {
            btnPickLocation.setOnClickListener(v -> {
                Intent intent = new Intent(EditProfileActivity.this, LocationPickerActivity.class);
                locationPickerLauncher.launch(intent);
            });
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveProfile());
        }
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Name required");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("address", address);
        if (pickedLat != null && pickedLng != null) {
            updates.put("latitude", pickedLat);
            updates.put("longitude", pickedLng);
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            mDatabase.child("users").child(user.getUid()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("title", "Profile updated");
                    notif.put("body", "Your profile changes were saved successfully.");
                    notif.put("type", "profile_update");
                    notif.put("category", com.replate.app.notifications.NotificationCategories.PROFILE_UPDATED);
                    notif.put("route", "profile");
                    notif.put("timestamp", System.currentTimeMillis());
                    notif.put("read", false);
                    mDatabase.child("notifications").child(user.getUid()).push().setValue(notif);

                    com.replate.app.notifications.InAppNotificationCenter.publish(
                            new com.replate.app.notifications.InAppNotificationCenter.InAppNotificationMessage(
                                    "Profile updated",
                                    "Your profile changes were saved successfully.",
                                    com.replate.app.notifications.NotificationCategories.PROFILE_UPDATED,
                                    "profile",
                                    "",
                                    "",
                                    "",
                                    null
                            )
                    );

                    Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
        }
    }

    private void handlePickedLocation(Intent data) {
        // LocationPickerActivity returns lat/lng keys.
        double lat = data.getDoubleExtra("lat", 0.0);
        double lng = data.getDoubleExtra("lng", 0.0);
        String address = data.getStringExtra("address");

        pickedLat = lat;
        pickedLng = lng;

        Map<String, Object> locUpdates = new HashMap<>();
        locUpdates.put("latitude", lat);
        locUpdates.put("longitude", lng);
        if (address != null && !address.isEmpty()) {
            locUpdates.put("address", address);
            etAddress.setText(address);
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            mDatabase.child("users").child(user.getUid()).updateChildren(locUpdates);
        }
    }
}
