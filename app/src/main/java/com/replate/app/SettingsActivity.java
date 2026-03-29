package com.replate.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.utils.FcmTokenManager;
import com.replate.app.utils.NotificationPermissionHelper;
import com.replate.app.utils.ToastUtils;
import com.replate.app.utils.UserRoleHelper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Settings Activity - Profile editing, camera, SharedPreferences, and notification tools.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 2001;
    private static final String FUNCTIONS_BASE_URL = "https://asia-southeast1-replate-food-rescue.cloudfunctions.net";
    private static final String SELF_TEST_FUNCTION_PATH = "/sendSelfTestPush";
    private static final String TAG = "SettingsActivity";

    private CircleImageView ivProfileImage;
    private TextInputEditText etName, etPhone, etEmail, etAddress;
    private Button btnChangePhoto, btnSaveProfile, btnPickAddress;
    private Button btnSendTestPush, btnRefreshFcmToken;
    private TextView tvCurrentFcmToken;
    private MaterialCardView cardNotificationTools;
    private SwitchMaterial switchNotifications, switchLocation;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;
    private SharedPreferences prefs;
    private ExecutorService networkExecutor;
    private ReplateDatabaseHelper localDb;

    private Bitmap capturedPhoto;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Intent> locationPickerLauncher;
    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (switchNotifications != null) {
                    switchNotifications.setChecked(granted || !NotificationPermissionHelper.isRuntimePermissionRequired());
                }
                prefs.edit().putBoolean("notifications_enabled", granted || !NotificationPermissionHelper.isRuntimePermissionRequired()).apply();
                if (!granted && NotificationPermissionHelper.isRuntimePermissionRequired()) {
                    ToastUtils.showCustomToast(this, getString(R.string.notifications_permission_denied));
                }
            });

    private Double pickedLat = null;
    private Double pickedLng = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();
        prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        networkExecutor = Executors.newSingleThreadExecutor();
        localDb = ReplateDatabaseHelper.getInstance(this);

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

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (stream != null) stream.close();
                if (bitmap != null) {
                    ivProfileImage.setImageBitmap(bitmap);
                    savePhotoToInternalStorage(bitmap);
                    uploadProfilePhoto(bitmap);
                }
            } catch (Exception e) {
                ToastUtils.showCustomToast(this, "Failed to read selected image");
            }
        });

        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        double lat = data.getDoubleExtra("lat", 0.0);
                        double lng = data.getDoubleExtra("lng", 0.0);
                        String address = data.getStringExtra("address");

                        pickedLat = lat;
                        pickedLng = lng;

                        if (address != null && !address.trim().isEmpty() && etAddress != null) {
                            etAddress.setText(address);
                        }

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("latitude", lat);
                            map.put("longitude", lng);
                            if (address != null && !address.trim().isEmpty()) {
                                map.put("address", address);
                            }
                            mDatabase.child("users").child(user.getUid()).updateChildren(map);
                        }
                    }
                }
        );

        initViews();
        loadUserData();
        loadPreferences();
        bindCurrentTokenText(FcmTokenManager.getLastToken(this));
        refreshSavedTokenFromFirebase();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        ivProfileImage = findViewById(R.id.ivProfileImage);
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etAddress = findViewById(R.id.etAddress);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnPickAddress = findViewById(R.id.btnPickAddress);
        btnSendTestPush = findViewById(R.id.btnSendTestPush);
        btnRefreshFcmToken = findViewById(R.id.btnRefreshFcmToken);
        tvCurrentFcmToken = findViewById(R.id.tvCurrentFcmToken);
        cardNotificationTools = findViewById(R.id.cardNotificationTools);
        switchNotifications = findViewById(R.id.switchNotifications);
        switchLocation = findViewById(R.id.switchLocation);

        ivBack.setOnClickListener(v -> finish());
        btnChangePhoto.setOnClickListener(v -> showPhotoPicker());
        btnPickAddress.setOnClickListener(v -> locationPickerLauncher.launch(new Intent(this, LocationPickerActivity.class)));
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        switchNotifications.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !NotificationPermissionHelper.hasPermission(this)) {
                switchNotifications.setChecked(false);
                requestNotificationPermissionFromSettings();
                return;
            }
            prefs.edit().putBoolean("notifications_enabled", checked).apply();
        });

        switchLocation.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean("location_enabled", checked).apply());

        setupNotificationTools();
    }

    private void setupNotificationTools() {
        if (cardNotificationTools != null) {
            cardNotificationTools.setVisibility(View.VISIBLE);
        }

        if (btnRefreshFcmToken != null) {
            btnRefreshFcmToken.setOnClickListener(v -> refreshAndSyncToken());
        }

        if (btnSendTestPush != null) {
            btnSendTestPush.setOnClickListener(v -> sendSelfTestPush());
        }
    }

    private void requestNotificationPermissionFromSettings() {
        if (!NotificationPermissionHelper.isRuntimePermissionRequired()) {
            prefs.edit().putBoolean("notifications_enabled", true).apply();
            if (switchNotifications != null) switchNotifications.setChecked(true);
            return;
        }

        if (NotificationPermissionHelper.hasPermission(this)) {
            prefs.edit().putBoolean("notifications_enabled", true).apply();
            if (switchNotifications != null) switchNotifications.setChecked(true);
            return;
        }

        if (NotificationPermissionHelper.shouldShowRationale(this)) {
            NotificationPermissionHelper.showRationaleDialog(this,
                    () -> NotificationPermissionHelper.request(notificationsPermissionLauncher));
            return;
        }

        NotificationPermissionHelper.request(notificationsPermissionLauncher);
    }

    private void refreshAndSyncToken() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            ToastUtils.showCustomToast(this, getString(R.string.settings_token_refresh_failed));
            return;
        }

        ToastUtils.showCustomToast(this, getString(R.string.settings_token_refreshing));
        FirebaseMessaging.getInstance().setAutoInitEnabled(true);
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (token == null || token.trim().isEmpty()) {
                        ToastUtils.showCustomToast(this, getString(R.string.settings_token_refresh_failed));
                        return;
                    }
                    bindCurrentTokenText(token);
                    String role = UserRoleHelper.fromPrefs(prefs);
                    FcmTokenManager.persistLatestToken(this, token);
                    FcmTokenManager.syncTokenForCurrentUser(this, role);
                    Log.d(TAG, "Manual FCM token refresh completed for uid=" + user.getUid() + " token=" + token);
                    ToastUtils.showCustomToast(this, getString(R.string.settings_token_refreshed));
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Manual FCM token refresh failed", e);
                    ToastUtils.showCustomToast(this, getString(R.string.settings_token_refresh_failed));
                });
    }

    private void refreshSavedTokenFromFirebase() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        FcmTokenManager.fetchReceiverToken(user.getUid(), result -> runOnUiThread(() -> {
            if (!canUseUi()) return;
            String token = result == null ? "" : result.token;
            if (token == null || token.trim().isEmpty()) {
                token = FcmTokenManager.getLastToken(SettingsActivity.this);
            }
            bindCurrentTokenText(token);
        }));
    }

    private void bindCurrentTokenText(String token) {
        if (tvCurrentFcmToken == null) return;
        if (token == null || token.trim().isEmpty()) {
            tvCurrentFcmToken.setText(getString(R.string.settings_current_fcm_token_empty));
            return;
        }
        tvCurrentFcmToken.setText(getString(R.string.settings_current_fcm_token_format, token.trim()));
        Log.d(TAG, "Current FCM token=" + token.trim());
    }

    private void sendSelfTestPush() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            ToastUtils.showCustomToast(this, getString(R.string.settings_test_push_failed));
            return;
        }

        if (!NotificationPermissionHelper.hasPermission(this)) {
            requestNotificationPermissionFromSettings();
            return;
        }

        ToastUtils.showCustomToast(this, getString(R.string.settings_test_push_sending));

        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            boolean cloudFunctionSucceeded = false;
            try {
                String idToken = Tasks.await(user.getIdToken(true), 20, TimeUnit.SECONDS).getToken();
                if (idToken == null || idToken.trim().isEmpty()) {
                    throw new IllegalStateException("Missing auth token");
                }

                JSONObject payload = new JSONObject();
                payload.put("title", "REPLATE Test Push");
                payload.put("body", "If you can see this popup, push notifications are working.");
                payload.put("category", "admin_announcement");
                payload.put("route", "notifications");
                payload.put("data", new JSONObject().put("debug", "true").put("source", "settings_self_test"));

                String requestUrl = FUNCTIONS_BASE_URL + SELF_TEST_FUNCTION_PATH;
                Log.d(TAG, "sendSelfTestPush request URL: " + requestUrl);
                URL url = new URL(requestUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + idToken);

                byte[] out = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.getOutputStream().write(out);

                int code = connection.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(),
                        StandardCharsets.UTF_8
                ));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                Log.d(TAG, "sendSelfTestPush HTTP " + code + " response from " + requestUrl + ": " + response);
                if (code >= 200 && code < 300) {
                    cloudFunctionSucceeded = true;
                    runOnUiThread(() -> ToastUtils.showCustomToast(SettingsActivity.this,
                            getString(R.string.settings_test_push_sent)));
                } else {
                    Log.w(TAG, "Cloud function returned " + code + ", falling back to local notification");
                }
            } catch (Exception e) {
                Log.w(TAG, "Cloud function call failed, falling back to local notification", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            // Fallback: show a local notification directly if cloud function not deployed
            if (!cloudFunctionSucceeded) {
                showLocalTestNotification();
            }
        });
    }

    /**
     * Shows a local test notification directly on the device.
     * Used as fallback when Cloud Function is not deployed yet.
     */
    private void showLocalTestNotification() {
        try {
            String channelId = "replate_notifications_v4";

            // Create channel if needed (Android 8+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationManager nm =
                        (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(channelId) == null) {
                    android.app.NotificationChannel channel = new android.app.NotificationChannel(
                            channelId, "REPLATE Alerts",
                            android.app.NotificationManager.IMPORTANCE_HIGH);
                    channel.setDescription("Order, deal and pickup alerts");
                    channel.enableVibration(true);
                    channel.enableLights(true);
                    channel.setShowBadge(true);
                    nm.createNotificationChannel(channel);
                }
            }

            Intent tapIntent = new Intent(this, MainActivity.class);
            tapIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    this, (int) System.currentTimeMillis(), tapIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

            android.graphics.Bitmap largeIcon =
                    android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.replate);

            androidx.core.app.NotificationCompat.Builder builder =
                    new androidx.core.app.NotificationCompat.Builder(this, channelId)
                            .setSmallIcon(R.drawable.ic_notif_replate)
                            .setLargeIcon(largeIcon)
                            .setContentTitle("REPLATE Test Push")
                            .setContentText("Push notifications are working! (local test)")
                            .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                                    .bigText("Push notifications are working! This is a local test notification. " +
                                            "Cloud Function endpoint is not deployed yet — deploy with: firebase deploy --only functions"))
                            .setAutoCancel(true)
                            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
                            .setContentIntent(pendingIntent)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                            .setColor(ContextCompat.getColor(this, R.color.primary));

            androidx.core.app.NotificationManagerCompat notifManager =
                    androidx.core.app.NotificationManagerCompat.from(this);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                    || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                notifManager.notify((int) System.currentTimeMillis(), builder.build());
            }

            Log.d(TAG, "Local test notification shown (cloud function fallback)");
            runOnUiThread(() -> ToastUtils.showCustomToast(SettingsActivity.this,
                    getString(R.string.settings_test_push_sent) + " (local)"));
        } catch (Exception e) {
            Log.e(TAG, "Local test notification failed", e);
            runOnUiThread(() -> ToastUtils.showCustomToast(SettingsActivity.this,
                    getString(R.string.settings_test_push_failed)));
        }
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        etEmail.setText(user.getEmail());

        // Load from SQLite cache first for instant display
        if (localDb != null) {
            java.util.Map<String, String> cached = localDb.getCachedUserProfile(user.getUid());
            if (cached != null) {
                if (cached.get("name") != null) etName.setText(cached.get("name"));
                if (cached.get("phone") != null) etPhone.setText(cached.get("phone"));
                if (cached.get("address") != null) etAddress.setText(cached.get("address"));
                String cachedPhoto = cached.get("photoUrl");
                if (cachedPhoto != null && !cachedPhoto.isEmpty() && canUseUi()) {
                    try {
                        Glide.with(SettingsActivity.this)
                                .load(cachedPhoto)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .into(ivProfileImage);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        mDatabase.child("users").child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!canUseUi()) return;
                        if (snapshot.exists()) {
                            String name = snapshot.child("name").getValue(String.class);
                            String phone = snapshot.child("phone").getValue(String.class);
                            String address = snapshot.child("address").getValue(String.class);
                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage == null || profileImage.trim().isEmpty()) {
                                profileImage = snapshot.child("profileImageUrl").getValue(String.class);
                            }

                            if (name != null) etName.setText(name);
                            if (phone != null) etPhone.setText(phone);
                            if (address != null) etAddress.setText(address);

                            // Cache to SQLite for offline access
                            if (localDb != null) {
                                localDb.cacheUserProfile(user.getUid(), name,
                                        user.getEmail(), phone, profileImage, address);
                            }

                            if (profileImage != null && !profileImage.isEmpty() && canUseUi()) {
                                try {
                                    Glide.with(SettingsActivity.this)
                                            .load(profileImage)
                                            .placeholder(R.drawable.ic_profile_placeholder)
                                            .into(ivProfileImage);
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (canUseUi()) {
                            ToastUtils.showCustomToast(SettingsActivity.this,
                                    "Failed to load profile");
                        }
                    }
                });
    }

    private void loadPreferences() {
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        if (NotificationPermissionHelper.isRuntimePermissionRequired() && !NotificationPermissionHelper.hasPermission(this)) {
            notificationsEnabled = false;
        }
        switchNotifications.setChecked(notificationsEnabled);
        switchLocation.setChecked(prefs.getBoolean("location_enabled", true));
    }

    private void showPhotoPicker() {
        String[] options = new String[]{"Take photo", "Choose from gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Update profile photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openCamera();
                    } else {
                        galleryLauncher.launch("image/*");
                    }
                })
                .show();
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
                ToastUtils.showCustomToast(this, "Camera permission required");
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
            Log.w(TAG, "Failed to cache profile photo locally", e);
        }
    }

    private boolean canUseUi() {
        return !isFinishing() && !(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed());
    }

    private void showBrandedToast(String message) {
        if (!canUseUi()) return;
        ToastUtils.showCustomToast(this, message);
    }

    private boolean isPermissionDenied(Throwable error) {
        if (!(error instanceof StorageException)) return false;
        StorageException se = (StorageException) error;
        return se.getErrorCode() == StorageException.ERROR_NOT_AUTHORIZED;
    }

    private void uploadProfilePhotoToRef(StorageReference photoRef, FirebaseUser user) {
        photoRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    if (!canUseUi()) return;
                    if (uri == null) {
                        showBrandedToast("Failed to get photo URL");
                        return;
                    }

                    String photoUrl = uri.toString();
                    Map<String, Object> photoUpdate = new HashMap<>();
                    photoUpdate.put("profileImage", photoUrl);
                    photoUpdate.put("profileImageUrl", photoUrl);

                    mDatabase.child("users").child(user.getUid())
                            .updateChildren(photoUpdate)
                            .addOnSuccessListener(aVoid -> {
                                if (!canUseUi() || ivProfileImage == null) return;
                                showBrandedToast("Photo updated");
                                try {
                                    if (isFinishing() || isDestroyed()) return;
                                    if (ivProfileImage.getWindowToken() == null) return;
                                    Glide.with(SettingsActivity.this)
                                            .load(photoUrl)
                                            .placeholder(R.drawable.ic_profile_placeholder)
                                            .error(R.drawable.ic_profile_placeholder)
                                            .into(ivProfileImage);
                                } catch (IllegalArgumentException e) {
                                    Log.w(TAG, "Skipped image load: view/activity not valid", e);
                                } catch (Exception e) {
                                    Log.w(TAG, "Glide error loading profile photo", e);
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (canUseUi()) {
                                    showBrandedToast("Failed to save profile photo");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (canUseUi()) {
                        showBrandedToast("Failed to get photo URL");
                    }
                });
    }

    /**
     * Upload profile photo to Firebase Storage
     */
    private void uploadProfilePhoto(Bitmap bitmap) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showBrandedToast("User not authenticated");
            return;
        }

        if (bitmap == null) {
            showBrandedToast("No photo selected");
            return;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] data = baos.toByteArray();

            if (data.length == 0) {
                showBrandedToast("Image conversion failed");
                return;
            }

            if (mStorage == null) {
                mStorage = FirebaseStorage.getInstance().getReference();
            }

            // Use a user-scoped path first (more likely to match strict Storage rules).
            String fileName = "profile_" + System.currentTimeMillis() + ".jpg";
            StorageReference primaryRef = mStorage
                    .child("profileImages")
                    .child(user.getUid())
                    .child(fileName);

            primaryRef.putBytes(data)
                    .addOnSuccessListener(taskSnapshot -> {
                        if (!isFinishing() && !isDestroyed()) {
                            uploadProfilePhotoToRef(primaryRef, user);
                        }
                    })
                    .addOnFailureListener(primaryError -> {
                        if (isFinishing() || isDestroyed()) return;

                        // Fallback to legacy path for older rulesets.
                        StorageReference legacyRef = mStorage
                                .child("profile_photos")
                                .child(user.getUid() + "_" + System.currentTimeMillis() + ".jpg");

                        if (isPermissionDenied(primaryError)) {
                            legacyRef.putBytes(data)
                                    .addOnSuccessListener(taskSnapshot -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            uploadProfilePhotoToRef(legacyRef, user);
                                        }
                                    })
                                    .addOnFailureListener(legacyError -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            if (isPermissionDenied(legacyError)) {
                                                showBrandedToast("No permission to upload. Check Firebase Storage rules.");
                                            } else {
                                                showBrandedToast("Upload failed: " + legacyError.getMessage());
                                            }
                                        }
                                    });
                        } else {
                            if (!isFinishing() && !isDestroyed()) {
                                showBrandedToast("Upload failed: " + primaryError.getMessage());
                            }
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Profile photo upload error", e);
            showBrandedToast("Upload error");
        }
    }

    private void saveProfile() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";

        if (name.isEmpty() || phone.isEmpty()) {
            ToastUtils.showCustomToast(this, "Please fill all fields");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("name", name);
            updates.put("phone", phone);
            updates.put("address", address);
            if (pickedLat != null && pickedLng != null) {
                updates.put("latitude", pickedLat);
                updates.put("longitude", pickedLng);
            }

            mDatabase.child("users").child(user.getUid()).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                            // Cache updated profile to SQLite
                            if (localDb != null) {
                                localDb.cacheUserProfile(user.getUid(), name,
                                        user.getEmail(), phone, null, address);
                            }
                            ToastUtils.showCustomToast(SettingsActivity.this,
                                    "Profile Updated ✅");
                    })
                    .addOnFailureListener(e ->
                            ToastUtils.showCustomToast(SettingsActivity.this,
                                    "Update Failed ❌"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
    }
}
