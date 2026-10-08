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
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

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
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.utils.AccountActions;
import com.replate.app.utils.FcmTokenManager;
import com.replate.app.utils.NotificationPermissionHelper;
import com.replate.app.utils.NotificationPrefs;
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
    private static final String TAG = "SettingsActivity";
    private static final String KEY_NOTIF_PERMISSION_ASKED = "notif_permission_asked";
    private static final int MAX_PROFILE_PHOTO_PX = 1024;
    private static final String CAMERA_FILE_NAME = "profile_capture.jpg";

    private CircleImageView ivProfileImage;
    private TextInputEditText etName, etPhone, etEmail, etAddress;
    private Button btnChangePhoto, btnSaveProfile, btnPickAddress;
    private SwitchMaterial switchNotifications, switchLocation;
    private SwitchMaterial switchNotifyOrders, switchNotifyDeals, switchNotifyPickup;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;
    private SharedPreferences prefs;
    private ExecutorService networkExecutor;
    private ReplateDatabaseHelper localDb;

    private ActivityResultLauncher<Uri> cameraLauncher;
    private File cameraFile;
    // True while the code (not the user) moves the master switch, so the listener ignores it
    private boolean bindingNotificationSwitch;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Intent> locationPickerLauncher;
    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                prefs.edit().putBoolean(KEY_NOTIF_PERMISSION_ASKED, true).apply();
                if (granted) {
                    prefs.edit().putBoolean(NotificationPrefs.KEY_ENABLED, true).apply();
                } else {
                    ToastUtils.showCustomToast(this, getString(R.string.notifications_permission_denied));
                }
                // A refusal is not saved, the switch just shows the real state
                refreshNotificationSwitch();
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

        // The camera writes a full-size photo into our cache file through a FileProvider
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
            File photoFile = cameraFile != null ? cameraFile : new File(getCacheDir(), CAMERA_FILE_NAME);
            try {
                if (saved && photoFile.exists()) {
                    Bitmap bitmap = decodeScaledBitmap(Uri.fromFile(photoFile), MAX_PROFILE_PHOTO_PX);
                    if (bitmap != null) {
                        ivProfileImage.setImageBitmap(bitmap);
                        savePhotoToInternalStorage(bitmap);
                        uploadProfilePhoto(bitmap);
                    } else {
                        ToastUtils.showCustomToast(this, R.string.settings_failed_to_read_selected_image);
                    }
                }
            } catch (Exception | OutOfMemoryError e) {
                ToastUtils.showCustomToast(this, R.string.settings_failed_to_read_selected_image);
            } finally {
                // The temp photo is only needed until it has been read
                if (photoFile.exists() && !photoFile.delete()) {
                    Log.w(TAG, "Could not delete the temporary camera photo");
                }
                cameraFile = null;
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            try {
                Bitmap bitmap = decodeScaledBitmap(uri, MAX_PROFILE_PHOTO_PX);
                if (bitmap != null) {
                    ivProfileImage.setImageBitmap(bitmap);
                    savePhotoToInternalStorage(bitmap);
                    uploadProfilePhoto(bitmap);
                } else {
                    ToastUtils.showCustomToast(this, R.string.settings_failed_to_read_selected_image);
                }
            } catch (Exception | OutOfMemoryError e) {
                ToastUtils.showCustomToast(this, R.string.settings_failed_to_read_selected_image);
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
    }

    /** Drops disposable data (images, food lists, search history) but keeps the cart, favorites and orders. */
    private void clearCaches() {
        final android.content.Context app = getApplicationContext();
        networkExecutor.execute(() -> {
            try {
                Glide.get(app).clearDiskCache();
            } catch (Exception e) {
                Log.w(TAG, "Could not clear image cache", e);
            }
            try {
                ReplateDatabaseHelper db = ReplateDatabaseHelper.getInstance(app);
                db.clearFoodCache();
                db.clearSearchHistory();
                new com.replate.app.helpers.FoodItemCacheManager(app).clearCache();
            } catch (Exception e) {
                Log.w(TAG, "Could not clear local caches", e);
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    Glide.get(app).clearMemory();
                    ToastUtils.showCustomToast(this, getString(R.string.settings_cache_cleared));
                }
            });
        });
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        // Always show the real build version instead of a hard-coded number that drifts
        android.widget.TextView tvAppVersion = findViewById(R.id.tvAppVersion);
        if (tvAppVersion != null) {
            tvAppVersion.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        }
        ivProfileImage = findViewById(R.id.ivProfileImage);
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etAddress = findViewById(R.id.etAddress);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnPickAddress = findViewById(R.id.btnPickAddress);
        switchNotifications = findViewById(R.id.switchNotifications);
        switchLocation = findViewById(R.id.switchLocation);

        ivBack.setOnClickListener(v -> finish());
        findViewById(R.id.rowPrivacy).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.settings_privacy_title)
                .setMessage(R.string.settings_privacy_body)
                .setPositiveButton(android.R.string.ok, null)
                .show());
        findViewById(R.id.rowClearCache).setOnClickListener(v -> clearCaches());
        btnChangePhoto.setOnClickListener(v -> showPhotoPicker());
        btnPickAddress.setOnClickListener(v -> locationPickerLauncher.launch(new Intent(this, LocationPickerActivity.class)));
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        switchNotifications.setOnCheckedChangeListener((btn, checked) -> {
            if (bindingNotificationSwitch) return;
            if (checked && !osAllowsNotifications()) {
                showNotificationSwitch(false);
                requestNotificationPermissionFromSettings();
                return;
            }
            prefs.edit().putBoolean(NotificationPrefs.KEY_ENABLED, checked).apply();
            updateNotifyCategoryState(checked);
        });

        // Per-category switches; the push service reads these through NotificationPrefs
        switchNotifyOrders = findViewById(R.id.switchNotifyOrders);
        switchNotifyDeals = findViewById(R.id.switchNotifyDeals);
        switchNotifyPickup = findViewById(R.id.switchNotifyPickup);
        switchNotifyOrders.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(NotificationPrefs.KEY_ORDERS, checked).apply());
        switchNotifyDeals.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(NotificationPrefs.KEY_DEALS, checked).apply());
        switchNotifyPickup.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(NotificationPrefs.KEY_PICKUP, checked).apply());
        findViewById(R.id.rowSystemNotifications).setOnClickListener(v -> openSystemNotificationSettings());

        // Location access is a system permission an app can't revoke itself, so this switch shows the
        // real state and sends the user to the phone's app settings to change it.
        switchLocation.setOnClickListener(v -> {
            refreshLocationSwitch();
            ToastUtils.showCustomToast(this, R.string.settings_location_off_hint);
            openAppDetailsSettings();
        });

        findViewById(R.id.rowHelp).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_body)
                .setPositiveButton(R.string.help_dismiss, null)
                .show());
        findViewById(R.id.rowContact).setOnClickListener(v -> contactSupport());
        findViewById(R.id.rowRate).setOnClickListener(v -> rateApp());
        findViewById(R.id.rowShare).setOnClickListener(v -> shareApp());
        findViewById(R.id.rowLicenses).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.settings_licenses_title)
                .setMessage(R.string.settings_licenses_body)
                .setPositiveButton(android.R.string.ok, null)
                .show());
        findViewById(R.id.rowLogout).setOnClickListener(v -> AccountActions.confirmLogout(this));
        findViewById(R.id.rowDeleteAccount).setOnClickListener(v -> AccountActions.confirmDelete(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have changed location or notification access in the phone settings
        refreshLocationSwitch();
        if (switchNotifications != null) refreshNotificationSwitch();
    }

    private void refreshLocationSwitch() {
        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        switchLocation.setChecked(granted);
    }

    private void updateNotifyCategoryState(boolean masterEnabled) {
        for (SwitchMaterial s : new SwitchMaterial[]{switchNotifyOrders, switchNotifyDeals, switchNotifyPickup}) {
            if (s != null) s.setEnabled(masterEnabled);
        }
    }

    private void startSafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            ToastUtils.showCustomToast(this, R.string.settings_no_app_available);
        }
    }

    private void openAppDetailsSettings() {
        startSafely(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private void openSystemNotificationSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startSafely(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } else {
            openAppDetailsSettings();
        }
    }

    private void contactSupport() {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.settings_support_email)});
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_contact_subject, BuildConfig.VERSION_NAME));
        startSafely(intent);
    }

    private void rateApp() {
        Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()));
        try {
            startActivity(store);
        } catch (android.content.ActivityNotFoundException e) {
            startSafely(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.settings_share_text));
        startSafely(Intent.createChooser(send, getString(R.string.settings_share_title)));
    }

    /** True when the phone itself lets this app post notifications. */
    private boolean osAllowsNotifications() {
        return NotificationPermissionHelper.hasPermission(this)
                && NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    /** Moves the master switch without saving anything. */
    private void showNotificationSwitch(boolean on) {
        bindingNotificationSwitch = true;
        try {
            switchNotifications.setChecked(on);
        } finally {
            bindingNotificationSwitch = false;
        }
        updateNotifyCategoryState(on);
    }

    /** Shows the saved choice only while the phone also allows notifications. */
    private void refreshNotificationSwitch() {
        showNotificationSwitch(prefs.getBoolean(NotificationPrefs.KEY_ENABLED, true) && osAllowsNotifications());
    }

    private void requestNotificationPermissionFromSettings() {
        if (osAllowsNotifications()) {
            prefs.edit().putBoolean(NotificationPrefs.KEY_ENABLED, true).apply();
            showNotificationSwitch(true);
            return;
        }

        // Ask in-app first; once the system stops showing the dialog, send the user to the app settings
        boolean canAsk = NotificationPermissionHelper.isRuntimePermissionRequired()
                && !NotificationPermissionHelper.hasPermission(this)
                && (NotificationPermissionHelper.shouldShowRationale(this)
                || !prefs.getBoolean(KEY_NOTIF_PERMISSION_ASKED, false));
        if (!canAsk) {
            openSystemNotificationSettings();
            return;
        }

        if (NotificationPermissionHelper.shouldShowRationale(this)) {
            NotificationPermissionHelper.showRationaleDialog(this,
                    () -> NotificationPermissionHelper.request(notificationsPermissionLauncher));
            return;
        }

        NotificationPermissionHelper.request(notificationsPermissionLauncher);
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
                            String phone = com.replate.app.utils.SafeSnapshot.string(snapshot.child("phone"));
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
                                    R.string.settings_failed_to_load_profile);
                        }
                    }
                });
    }

    private void loadPreferences() {
        // Reflect the phone's state without overwriting the saved choice
        refreshNotificationSwitch();
        switchNotifyOrders.setChecked(prefs.getBoolean(NotificationPrefs.KEY_ORDERS, true));
        switchNotifyDeals.setChecked(prefs.getBoolean(NotificationPrefs.KEY_DEALS, true));
        switchNotifyPickup.setChecked(prefs.getBoolean(NotificationPrefs.KEY_PICKUP, true));
        refreshLocationSwitch();
    }

    private void showPhotoPicker() {
        String[] options = new String[]{"Take photo", "Choose from gallery"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_update_profile_photo)
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

        try {
            cameraFile = new File(getCacheDir(), CAMERA_FILE_NAME);
            Uri photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraFile);
            cameraLauncher.launch(photoUri);
        } catch (android.content.ActivityNotFoundException | IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "No camera app could take the photo", e);
            ToastUtils.showCustomToast(this, R.string.settings_no_camera_app);
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
                ToastUtils.showCustomToast(this, R.string.settings_camera_permission_required);
            }
        }
    }

    /**
     * Save photo to internal storage
     * REQUIREMENT: Internal Storage
     */
    /**
     * Decodes an image without loading the full-size bitmap into memory first:
     * reads the dimensions, picks a power-of-two sample size, then decodes once.
     */
    private Bitmap decodeScaledBitmap(android.net.Uri uri, int maxDimension) throws java.io.IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) return null;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sampleSize = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;
            return BitmapFactory.decodeStream(stream, null, options);
        }
    }

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

            // Declare the type so Storage rules can require real images
            StorageMetadata jpegMetadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();

            primaryRef.putBytes(data, jpegMetadata)
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
                            legacyRef.putBytes(data, jpegMetadata)
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
            ToastUtils.showCustomToast(this, R.string.settings_please_fill_all_fields);
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
                                    R.string.settings_profile_updated);
                    })
                    .addOnFailureListener(e ->
                            ToastUtils.showCustomToast(SettingsActivity.this,
                                    R.string.settings_update_failed));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
    }
}
