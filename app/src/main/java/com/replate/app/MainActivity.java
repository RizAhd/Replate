package com.replate.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.provider.Settings;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.replate.app.fragments.HomeFragment;
import com.replate.app.fragments.MapFragment;
import com.replate.app.fragments.OrdersFragment;
import com.replate.app.fragments.ProfileFragment;
import com.replate.app.services.OrderSyncWorker;
import com.replate.app.utils.FcmTokenManager;
import com.replate.app.utils.LocalDataCleaner;
import com.replate.app.receivers.NetworkChangeReceiver;
import com.replate.app.utils.ScrollToTop;
import com.replate.app.utils.NotificationPermissionHelper;
import com.replate.app.utils.ToastUtils;
import com.replate.app.utils.UserRoleHelper;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final float SHAKE_THRESHOLD = 12.0f;
    private static final long SHAKE_COOLDOWN_MS = 2000;

    private BottomNavigationView bottomNavigation;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private FirebaseAuth mAuth;

    private HomeFragment homeFragment;
    private MapFragment mapFragment;
    private OrdersFragment ordersFragment;
    private ProfileFragment profileFragment;

    private NetworkChangeReceiver networkReceiver;
    private View mainContent;

    // Accelerometer shake detection
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;

    private final com.replate.app.notifications.InAppNotificationCenter.Listener inAppListener = message ->
            runOnUiThread(() -> com.replate.app.notifications.InAppNotificationPopup.show(MainActivity.this, message));

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
                boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                if (fineGranted || coarseGranted) {
                    prefs.edit().putBoolean("pending_location_permission_prompt", false).apply();
                    return;
                }

                boolean showRationaleFine = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
                boolean showRationaleCoarse = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
                boolean permanentlyDenied = !showRationaleFine && !showRationaleCoarse;

                if (permanentlyDenied) {
                    showLocationSettingsDialog();
                } else {
                    showLocationRationaleDialog();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        String sessionRole = UserRoleHelper.fromPrefs(prefs);
        FcmTokenManager.syncTokenForCurrentUser(this, sessionRole);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        mainContent = findViewById(R.id.mainContent);
        applySystemInsets();
        bindDrawerHeader();

        // After a rotation the FragmentManager restores every tab fragment that was created; pick the
        // restored instances up again instead of creating duplicates.
        if (savedInstanceState != null) {
            restoreFragmentReferences();
        }
        // Only the first launch loads Home (and applies a requested tab / notification deep link
        // below). Other tabs are created lazily when first opened.
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            loadFragment(homeFragment);
        }

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) return switchFragment(getHomeFragment());
            if (itemId == R.id.nav_map) return switchFragment(getMapFragment());
            if (itemId == R.id.nav_cart) {
                startActivity(new Intent(this, CartActivity.class));
                return false;
            }
            if (itemId == R.id.nav_orders) return switchFragment(getOrdersFragment());
            if (itemId == R.id.nav_profile) return switchFragment(getProfileFragment());
            return false;
        });

        // Tapping the tab you're already on scrolls it back to the top
        bottomNavigation.setOnItemReselectedListener(item ->
                ScrollToTop.apply(activeFragment != null ? activeFragment.getView() : null));

        String openTab = getIntent().getStringExtra("open_tab");
        if (openTab == null || openTab.trim().isEmpty()) {
            openTab = resolveOpenTabFromNotificationIntent(getIntent());
        }
        if (bottomNavigation != null && savedInstanceState == null) {
            if ("map".equalsIgnoreCase(openTab)) {
                bottomNavigation.setSelectedItemId(R.id.nav_map);
            } else if ("orders".equalsIgnoreCase(openTab)) {
                bottomNavigation.setSelectedItemId(R.id.nav_orders);
            } else if ("profile".equalsIgnoreCase(openTab)) {
                bottomNavigation.setSelectedItemId(R.id.nav_profile);
            } else if ("cart".equalsIgnoreCase(openTab)) {
                bottomNavigation.setSelectedItemId(R.id.nav_cart);
            } else if ("home".equalsIgnoreCase(openTab)) {
                bottomNavigation.setSelectedItemId(R.id.nav_home);
            }
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.drawer_home) {
                bottomNavigation.setSelectedItemId(R.id.nav_home);
            } else if (id == R.id.drawer_map) {
                bottomNavigation.setSelectedItemId(R.id.nav_map);
            } else if (id == R.id.drawer_cart) {
                startActivity(new Intent(this, CartActivity.class));
            } else if (id == R.id.drawer_orders) {
                bottomNavigation.setSelectedItemId(R.id.nav_orders);
            } else if (id == R.id.drawer_profile) {
                bottomNavigation.setSelectedItemId(R.id.nav_profile);
            } else if (id == R.id.drawer_search) {
                startActivity(new Intent(this, SearchActivity.class));
            } else if (id == R.id.drawer_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
            } else if (id == R.id.drawer_messages) {
                startActivity(new Intent(this, MessagesActivity.class));
            } else if (id == R.id.drawer_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.drawer_help) {
                showHelpDialog();
            } else if (id == R.id.drawer_logout) {
                logoutToLogin();
                return true;
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // Only on first launch: after a rotation this would reopen the order screen again
        if (savedInstanceState == null) {
            handleDeepLinkFromNotification(getIntent());
        }

        // Setup accelerometer for shake-to-refresh
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // Modern back navigation via OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else if (bottomNavigation != null && bottomNavigation.getSelectedItemId() != R.id.nav_home) {
                    bottomNavigation.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Defer non-critical operations to after first frame for faster startup
        mainContent.post(() -> {
            startNetworkMonitor();
            FirebaseMessaging.getInstance().subscribeToTopic("all_users");
            scheduleBackgroundSync();
            checkNotificationPermissions();
        });

        // Request location permission after auth with a small delay for better UX
        // This ensures the UI is fully loaded before showing permission dialog
        mainContent.postDelayed(this::maybeRequestLocationAfterAuth, 500);
    }

    private void bindDrawerHeader() {
        if (navigationView == null || mAuth.getCurrentUser() == null) return;

        View header = navigationView.getHeaderView(0);
        if (header == null) return;

        TextView tvDrawerName = header.findViewById(R.id.tvDrawerName);
        TextView tvDrawerEmail = header.findViewById(R.id.tvDrawerEmail);
        ImageView ivDrawerAvatar = header.findViewById(R.id.ivDrawerAvatar);

        // Phone and social sign-ins can have no email; keep the tagline then instead of blanking the line
        if (tvDrawerEmail != null) {
            String email = mAuth.getCurrentUser().getEmail();
            String phone = mAuth.getCurrentUser().getPhoneNumber();
            if (email != null && !email.trim().isEmpty()) {
                tvDrawerEmail.setText(email);
            } else if (phone != null && !phone.trim().isEmpty()) {
                tvDrawerEmail.setText(phone);
            }
        }

        FirebaseDatabase.getInstance().getReference("users")
                .child(mAuth.getCurrentUser().getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (tvDrawerName != null) {
                            String name = snapshot.child("name").getValue(String.class);
                            tvDrawerName.setText(name != null && !name.trim().isEmpty() ? name : getString(R.string.drawer_welcome_back));
                        }

                        if (ivDrawerAvatar != null) {
                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage == null || profileImage.trim().isEmpty()) {
                                profileImage = snapshot.child("profileImageUrl").getValue(String.class);
                            }
                            if (profileImage != null && !profileImage.trim().isEmpty()) {
                                // The logo sits inside a padded white circle; a real photo should fill the circle
                                ivDrawerAvatar.setPadding(0, 0, 0, 0);
                                Glide.with(MainActivity.this)
                                        .load(profileImage)
                                        .circleCrop()
                                        .placeholder(R.drawable.ic_replate_logo)
                                        .error(R.drawable.ic_replate_logo)
                                        .into(ivDrawerAvatar);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (tvDrawerName != null) {
                            tvDrawerName.setText(getString(R.string.drawer_welcome_back));
                        }
                    }
                });
    }

    private boolean switchFragment(Fragment fragment) {
        loadFragment(fragment);
        return true;
    }

    private HomeFragment getHomeFragment() {
        if (homeFragment == null) homeFragment = new HomeFragment();
        return homeFragment;
    }

    private MapFragment getMapFragment() {
        if (mapFragment == null) mapFragment = new MapFragment();
        return mapFragment;
    }

    private OrdersFragment getOrdersFragment() {
        if (ordersFragment == null) ordersFragment = new OrdersFragment();
        return ordersFragment;
    }

    private ProfileFragment getProfileFragment() {
        if (profileFragment == null) profileFragment = new ProfileFragment();
        return profileFragment;
    }

    /** The tab fragment currently on screen. */
    private Fragment activeFragment;

    /** Re-link the tab fields to the fragments the FragmentManager restored (after rotation). */
    private void restoreFragmentReferences() {
        for (Fragment restored : getSupportFragmentManager().getFragments()) {
            if (restored instanceof HomeFragment) {
                homeFragment = (HomeFragment) restored;
            } else if (restored instanceof MapFragment) {
                mapFragment = (MapFragment) restored;
            } else if (restored instanceof OrdersFragment) {
                ordersFragment = (OrdersFragment) restored;
            } else if (restored instanceof ProfileFragment) {
                profileFragment = (ProfileFragment) restored;
            } else {
                continue;
            }
            if (!restored.isHidden()) {
                activeFragment = restored;
            }
        }
    }

    /**
     * Shows a tab. Tabs are added once and then hidden/shown, so the map (and every other tab) keeps
     * its state and isn't rebuilt on each visit, which is what made the map feel slow to open.
     * Hidden tabs are capped at STARTED so they pause (location updates, banner timer, sensors).
     */
    private void loadFragment(Fragment fragment) {
        if (fragment == activeFragment && fragment.isAdded() && !fragment.isHidden()) {
            return;
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setReorderingAllowed(true);
        transaction.setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );
        if (activeFragment != null && activeFragment != fragment && activeFragment.isAdded()) {
            transaction.hide(activeFragment);
            transaction.setMaxLifecycle(activeFragment, androidx.lifecycle.Lifecycle.State.STARTED);
        }
        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.fragmentContainer, fragment);
        }
        transaction.setMaxLifecycle(fragment, androidx.lifecycle.Lifecycle.State.RESUMED);
        transaction.commit();
        activeFragment = fragment;
    }

    public void switchToTab(int tabId) {
        bottomNavigation.setSelectedItemId(tabId);
    }

    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    /** BroadcastReceiver for connectivity changes (online/offline toast); unregistered in onDestroy. */
    @SuppressWarnings("deprecation")
    private void startNetworkMonitor() {
        if (networkReceiver != null) {
            return;
        }
        networkReceiver = new NetworkChangeReceiver(this, online -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            ToastUtils.showCustomToast(this, online
                    ? R.string.network_back_online
                    : R.string.network_offline);
        });
        ContextCompat.registerReceiver(this, networkReceiver,
                new android.content.IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void scheduleBackgroundSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                OrderSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "replate_order_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );
    }

    private void checkNotificationPermissions() {
        if (!NotificationPermissionHelper.isRuntimePermissionRequired()
                || NotificationPermissionHelper.hasPermission(this)) {
            return;
        }

        if (NotificationPermissionHelper.shouldShowRationale(this)) {
            NotificationPermissionHelper.showRationaleDialog(this,
                    () -> NotificationPermissionHelper.request(this, 101));
            return;
        }

        NotificationPermissionHelper.request(this, 101);
    }

    private void applySystemInsets() {
        if (drawerLayout == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            WindowInsetsCompat systemBars = insets;
            int top = systemBars.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottom = systemBars.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            if (mainContent != null) {
                mainContent.setPadding(
                        mainContent.getPaddingLeft(),
                        0,
                        mainContent.getPaddingRight(),
                        0
                );
            }

            if (bottomNavigation != null) {
                bottomNavigation.setPadding(
                        bottomNavigation.getPaddingLeft(),
                        bottomNavigation.getPaddingTop(),
                        bottomNavigation.getPaddingRight(),
                        bottom + getResources().getDimensionPixelSize(R.dimen.space_0_5)
                );
            }

            View header = navigationView != null ? navigationView.getHeaderView(0) : null;
            if (header != null) {
                int horizontal = getResources().getDimensionPixelSize(R.dimen.space_2_5);
                int baseTop = getResources().getDimensionPixelSize(R.dimen.space_2_5);
                int baseBottom = getResources().getDimensionPixelSize(R.dimen.space_2_5);
                header.setPadding(horizontal, baseTop + top, horizontal, baseBottom);
            }

            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkReceiver != null) {
            try {
                unregisterReceiver(networkReceiver);
            } catch (IllegalArgumentException ignored) {
                // already unregistered
            }
            networkReceiver = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        com.replate.app.notifications.InAppNotificationCenter.register(inAppListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        com.replate.app.notifications.InAppNotificationCenter.unregister(inAppListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    // ─── Accelerometer shake-to-refresh ─────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Calculate acceleration magnitude (minus gravity)
        double acceleration = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

        if (acceleration > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                lastShakeTime = now;
                onShakeDetected();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    private void onShakeDetected() {
        // Short vibration feedback
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(100,
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(100);
                }
            }
        } catch (Exception ignored) {}

        com.replate.app.utils.ToastUtils.showCustomToast(this, R.string.main_refreshing);
        refreshCurrentFragment();
    }

    private void refreshCurrentFragment() {
        Fragment current = activeFragment;
        if (current instanceof HomeFragment) {
            ((HomeFragment) current).refreshData();
        } else if (current != null) {
            // Detach then attach in separate transactions to force view recreation
            final Fragment frag = current;
            getSupportFragmentManager().beginTransaction().detach(frag).commitNow();
            getSupportFragmentManager().beginTransaction().attach(frag).commitNow();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String openTab = resolveOpenTabFromNotificationIntent(intent);
        if (openTab == null) return;

        if ("map".equalsIgnoreCase(openTab)) {
            bottomNavigation.setSelectedItemId(R.id.nav_map);
        } else if ("orders".equalsIgnoreCase(openTab)) {
            bottomNavigation.setSelectedItemId(R.id.nav_orders);
        } else if ("profile".equalsIgnoreCase(openTab)) {
            bottomNavigation.setSelectedItemId(R.id.nav_profile);
        } else if ("cart".equalsIgnoreCase(openTab)) {
            bottomNavigation.setSelectedItemId(R.id.nav_cart);
        } else {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        // The map tab now survives between visits, so a hotel to focus has to be handed to it directly
        String focusHotelId = intent.getStringExtra("focus_hotel_id");
        if (focusHotelId != null && !focusHotelId.trim().isEmpty() && mapFragment != null && mapFragment.isAdded()) {
            mapFragment.focusHotel(focusHotelId);
            intent.removeExtra("focus_hotel_id");
        }

        handleDeepLinkFromNotification(intent);
    }

    private void handleDeepLinkFromNotification(Intent intent) {
        if (intent == null) return;

        String orderId = intent.getStringExtra("orderId");
        String route = intent.getStringExtra("route");
        String category = intent.getStringExtra("category");

        if (orderId != null && !orderId.trim().isEmpty()) {
            String safeRoute = route == null ? "" : route.toLowerCase(java.util.Locale.US);
            String safeCategory = category == null ? "" : category.toLowerCase(java.util.Locale.US);
            if (safeRoute.contains("order") || safeCategory.contains("order") || safeCategory.contains("pickup")) {
                Intent detail = new Intent(this, OrderDetailActivity.class);
                detail.putExtra("orderId", orderId.trim());
                startActivity(detail);
            }
        }
    }

    private String resolveOpenTabFromNotificationIntent(Intent intent) {
        if (intent == null) return null;

        if (intent.hasExtra("open_tab")) {
            String openTab = intent.getStringExtra("open_tab");
            return openTab == null ? null : openTab;
        }

        String route = intent.getStringExtra("route");
        String category = intent.getStringExtra("category");
        if (route == null) route = "";
        if (category == null) category = "";

        route = route.toLowerCase(java.util.Locale.US);
        category = category.toLowerCase(java.util.Locale.US);

        if (route.contains("map") || category.contains("pickup")) return "map";
        if (route.contains("order") || category.contains("order")) return "orders";
        if (route.contains("deal") || category.contains("deal") || category.contains("hot")) return "home";
        return null;
    }

    // Back navigation handled via OnBackPressedDispatcher in onCreate

    private void showHelpDialog() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_body)
                .setPositiveButton(R.string.help_dismiss, null)
                .show();
    }

    public void logoutToLogin() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        try {
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        } catch (IllegalStateException ignored) {
            // Drawer may already be destroyed
        }

        try {
            FcmTokenManager.clearTokenForCurrentUser();
        } catch (Exception ignored) {
            // Best-effort token cleanup; user still logs out
        }

        try {
            mAuth.signOut();
        } catch (Exception ignored) {
            // Auth signout failure is non-blocking
        }

        // Remove cart, favorites, cached orders (with pickup codes), chat and notifications
        LocalDataCleaner.clearAll(this);

        SharedPreferences sessionPrefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        // Preserve device-level onboarding flag across logouts
        boolean onboardingSeen = sessionPrefs.getBoolean("onboarding_seen", false);
        sessionPrefs.edit().clear().apply();
        if (onboardingSeen) {
            sessionPrefs.edit().putBoolean("onboarding_seen", true).apply();
        }

        Intent i = new Intent(this, SplashActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void maybeRequestLocationAfterAuth() {
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        boolean shouldPrompt = prefs.getBoolean("pending_location_permission_prompt", false);
        if (!shouldPrompt) {
            return;
        }

        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (hasFine || hasCoarse) {
            prefs.edit().putBoolean("pending_location_permission_prompt", false).apply();
            return;
        }

        boolean showRationaleFine = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean showRationaleCoarse = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (showRationaleFine || showRationaleCoarse) {
            showLocationRationaleDialog();
            return;
        }

        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void showLocationRationaleDialog() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.main_enable_live_location)
                .setMessage(R.string.main_we_use_your_current_location)
                .setPositiveButton(R.string.main_continue, (d, w) ->
                        locationPermissionLauncher.launch(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }))
                .setNegativeButton(R.string.main_not_now, null)
                .show();
    }

    private void showLocationSettingsDialog() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.main_location_permission_needed)
                .setMessage(R.string.main_location_access_is_permanently_denied)
                .setPositiveButton(R.string.main_open_settings, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.main_not_now, null)
                .show();
    }
}
