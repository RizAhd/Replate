package com.replate.app;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.work.Constraints;
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
import com.replate.app.fragments.CartFragment;
import com.replate.app.fragments.HomeFragment;
import com.replate.app.fragments.MapFragment;
import com.replate.app.fragments.OrdersFragment;
import com.replate.app.fragments.ProfileFragment;
import com.replate.app.receivers.NetworkChangeReceiver;
import com.replate.app.services.OrderSyncWorker;
import com.replate.app.utils.FcmTokenManager;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private FirebaseAuth mAuth;

    private HomeFragment homeFragment;
    private MapFragment mapFragment;
    private CartFragment cartFragment;
    private OrdersFragment ordersFragment;
    private ProfileFragment profileFragment;

    private NetworkChangeReceiver networkReceiver;
    private View mainContent;

    private final com.replate.app.notifications.InAppNotificationCenter.Listener inAppListener = message ->
            runOnUiThread(() -> com.replate.app.notifications.InAppNotificationPopup.show(MainActivity.this, message));

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
        String sessionRole = prefs.getString("role", "customer");
        FcmTokenManager.syncTokenForCurrentUser(this, sessionRole);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        mainContent = findViewById(R.id.mainContent);
        applySystemInsets();
        bindDrawerHeader();

        homeFragment = new HomeFragment();
        mapFragment = new MapFragment();
        cartFragment = new CartFragment();
        ordersFragment = new OrdersFragment();
        profileFragment = new ProfileFragment();

        loadFragment(homeFragment);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) return switchFragment(homeFragment);
            if (itemId == R.id.nav_map) return switchFragment(mapFragment);
            if (itemId == R.id.nav_cart) return switchFragment(cartFragment);
            if (itemId == R.id.nav_orders) return switchFragment(ordersFragment);
            if (itemId == R.id.nav_profile) return switchFragment(profileFragment);
            return false;
        });

        String openTab = getIntent().getStringExtra("open_tab");
        if (openTab == null || openTab.trim().isEmpty()) {
            openTab = resolveOpenTabFromNotificationIntent(getIntent());
        }
        if (bottomNavigation != null) {
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
                bottomNavigation.setSelectedItemId(R.id.nav_cart);
            } else if (id == R.id.drawer_orders) {
                bottomNavigation.setSelectedItemId(R.id.nav_orders);
            } else if (id == R.id.drawer_profile) {
                bottomNavigation.setSelectedItemId(R.id.nav_profile);
            } else if (id == R.id.drawer_search) {
                startActivity(new Intent(this, SearchActivity.class));
            } else if (id == R.id.drawer_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
            } else if (id == R.id.drawer_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.drawer_help) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.drawer_logout) {
                logoutToLogin();
                return true;
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        registerNetworkReceiver();
        FirebaseMessaging.getInstance().subscribeToTopic("all_users");
        scheduleBackgroundSync();
        checkNotificationPermissions();

        handleDeepLinkFromNotification(getIntent());
    }

    private void bindDrawerHeader() {
        if (navigationView == null || mAuth.getCurrentUser() == null) return;

        View header = navigationView.getHeaderView(0);
        if (header == null) return;

        TextView tvDrawerName = header.findViewById(R.id.tvDrawerName);
        TextView tvDrawerEmail = header.findViewById(R.id.tvDrawerEmail);
        ImageView ivDrawerAvatar = header.findViewById(R.id.ivDrawerAvatar);

        if (tvDrawerEmail != null) {
            tvDrawerEmail.setText(mAuth.getCurrentUser().getEmail());
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
                                Glide.with(MainActivity.this)
                                        .load(profileImage)
                                        .placeholder(R.drawable.ic_replate_logo)
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

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    public void switchToTab(int tabId) {
        bottomNavigation.setSelectedItemId(tabId);
    }

    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void registerNetworkReceiver() {
        networkReceiver = new NetworkChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
    }

    private void scheduleBackgroundSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                OrderSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueue(syncRequest);
    }

    private void checkNotificationPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
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
            } catch (Exception ignored) {
            }
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

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (bottomNavigation != null && bottomNavigation.getSelectedItemId() != R.id.nav_home) {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    public void logoutToLogin() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        try {
            FcmTokenManager.clearTokenForCurrentUser();
        } catch (Exception ignored) {
        }

        try {
            mAuth.signOut();
        } catch (Exception ignored) {
        }

        SharedPreferences sessionPrefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        sessionPrefs.edit().clear().apply();

        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
