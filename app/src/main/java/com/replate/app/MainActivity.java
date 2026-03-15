package com.replate.app;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

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

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNavigation = findViewById(R.id.bottomNavigation);
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
                mAuth.signOut();
                SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
                prefs.edit().clear().apply();
                Intent i = new Intent(this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        registerNetworkReceiver();
        FirebaseMessaging.getInstance().subscribeToTopic("all_users");
        scheduleBackgroundSync();
        checkNotificationPermissions();
    }

    private void bindDrawerHeader() {
        if (navigationView == null || mAuth.getCurrentUser() == null) return;

        View header = navigationView.getHeaderView(0);
        if (header == null) return;

        TextView tvDrawerName = header.findViewById(R.id.tvDrawerName);
        TextView tvDrawerEmail = header.findViewById(R.id.tvDrawerEmail);

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
}