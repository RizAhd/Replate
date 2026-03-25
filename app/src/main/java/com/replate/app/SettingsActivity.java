package com.replate.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Settings Activity - Profile editing, camera, SharedPreferences
 * Requirements: Multimedia (Camera), Data Storage (SharedPreferences, Internal Storage)
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 2001;

    private CircleImageView ivProfileImage;
    private TextInputEditText etName, etPhone, etEmail, etAddress; // Added etAddress
    private Button btnChangePhoto, btnSaveProfile, btnPickAddress; // Added btnPickAddress
    private SwitchMaterial switchNotifications, switchLocation;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;
    private SharedPreferences prefs;

    private Bitmap capturedPhoto;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();
        prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getExtras() != null) {
                        capturedPhoto = (Bitmap) result.getData().getExtras().get("data");
                        if (capturedPhoto != null) {
                            ivProfileImage.setImageBitmap(capturedPhoto);
                            savePhotoToInternalStorage(capturedPhoto);
                            uploadProfilePhoto(capturedPhoto);
                        }
                    }
                }
        );

        initViews();
        loadUserData();
        loadPreferences();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        ivProfileImage = findViewById(R.id.ivProfileImage);
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etAddress = findViewById(R.id.etAddress); // Initialize
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnPickAddress = findViewById(R.id.btnPickAddress); // Initialize
        switchNotifications = findViewById(R.id.switchNotifications);
        switchLocation = findViewById(R.id.switchLocation);

        ivBack.setOnClickListener(v -> finish());

        // CAMERA - MULTIMEDIA REQUIREMENT
        btnChangePhoto.setOnClickListener(v -> openCamera());

        btnPickAddress.setOnClickListener(v -> {
            // Open Map to pick address (For now, user can just type or we open map)
            // Ideally, we start SearchActivity or a new PickerActivity
            Toast.makeText(this, "Opening Map...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, com.replate.app.SearchActivity.class); // Reusing Search for demo
            intent.putExtra("picker_mode", true);
            startActivity(intent);
        });

        btnSaveProfile.setOnClickListener(v -> saveProfile());

        // Save preferences on toggle (SHARED PREFERENCES REQUIREMENT)
        switchNotifications.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean("notifications_enabled", checked).apply());

        switchLocation.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean("location_enabled", checked).apply());
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        etEmail.setText(user.getEmail());

        mDatabase.child("users").child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String name = snapshot.child("name").getValue(String.class);
                            String phone = snapshot.child("phone").getValue(String.class);
                            String address = snapshot.child("address").getValue(String.class); // Load address
                            String profileImage = snapshot.child("profileImage").getValue(String.class);

                            if (name != null) etName.setText(name);
                            if (phone != null) etPhone.setText(phone);
                            if (address != null) etAddress.setText(address); // Set address

                            // Load image with Glide...
                            if (profileImage != null && !profileImage.isEmpty()) {
                                Glide.with(SettingsActivity.this)
                                        .load(profileImage)
                                        .placeholder(R.drawable.ic_profile_placeholder)
                                        .into(ivProfileImage);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(SettingsActivity.this,
                                "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadPreferences() {
        switchNotifications.setChecked(prefs.getBoolean("notifications_enabled", true));
        switchLocation.setChecked(prefs.getBoolean("location_enabled", true));
    }

    /**
     * Open camera to take profile photo
     * REQUIREMENT: Multimedia - Camera
     */
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(cameraIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Save photo to internal storage
     * REQUIREMENT: Internal Storage
     */
    private void savePhotoToInternalStorage(Bitmap bitmap) {
        try {
            File file = new File(getFilesDir(), "profile_photo.jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Upload profile photo to Firebase Storage
     */
    private void uploadProfilePhoto(Bitmap bitmap) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        StorageReference photoRef = mStorage.child("profile_photos/" + user.getUid() + ".jpg");
        photoRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot ->
                        photoRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            mDatabase.child("users").child(user.getUid())
                                    .child("profileImage").setValue(uri.toString());
                            Toast.makeText(this, "Photo updated!", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim(); // Get address

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("phone", phone);
            updates.put("address", address); // Save address

            mDatabase.child("users").child(user.getUid()).updateChildren(updates)
                    .addOnSuccessListener(aVoid ->
                            Toast.makeText(SettingsActivity.this,
                                    "Profile Updated ✅", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(SettingsActivity.this,
                                    "Update Failed ❌", Toast.LENGTH_SHORT).show());
        }
    }
}
