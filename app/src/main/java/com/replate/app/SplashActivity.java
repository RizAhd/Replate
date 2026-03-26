package com.replate.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SplashActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final Intent launchIntent = getIntent();

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> proceedToNextScreen(launchIntent)
        );

        View ivEmoji = findViewById(R.id.ivEmoji);

        // Scale Animation 0 -> 1 with Overshoot
        ivEmoji.setScaleX(0f);
        ivEmoji.setScaleY(0f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ivEmoji, "scaleX", 0f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ivEmoji, "scaleY", 0f, 1f);

        scaleX.setDuration(600);
        scaleY.setDuration(600);

        scaleX.setInterpolator(new OvershootInterpolator());
        scaleY.setInterpolator(new OvershootInterpolator());

        scaleX.start();
        scaleY.start();

        // 2.5s Delay
        new Handler().postDelayed(() -> handleStartupPermissionsAndNavigate(launchIntent), 2500);
    }

    private void handleStartupPermissionsAndNavigate(Intent launchIntent) {
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        boolean askedBefore = prefs.getBoolean("location_permission_asked", false);

        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse && !askedBefore) {
            prefs.edit().putBoolean("location_permission_asked", true).apply();
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        proceedToNextScreen(launchIntent);
    }

    private void proceedToNextScreen(Intent launchIntent) {
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        String savedUserId = prefs.getString("userId", null);
        String savedRole = prefs.getString("role", null);
        if (savedRole == null || savedRole.trim().isEmpty()) {
            savedRole = prefs.getString("userType", "customer");
        }

        Intent nextIntent;
        if (savedUserId != null) {
            // Auto-login based on role
            if ("hotel".equals(savedRole)) {
                // Check if we have hotel dashboard activity class
                try {
                    Class<?> hotelClass = Class.forName("com.replate.app.hotel.HotelDashboardActivity");
                    nextIntent = new Intent(this, hotelClass);
                } catch (ClassNotFoundException e) {
                    nextIntent = new Intent(this, MainActivity.class);
                }
            } else if ("admin".equals(savedRole)) {
                try {
                    Class<?> adminClass = Class.forName("com.replate.app.admin.AdminDashboardActivity");
                    nextIntent = new Intent(this, adminClass);
                } catch (ClassNotFoundException e) {
                    nextIntent = new Intent(this, MainActivity.class);
                }
            } else {
                // Customer
                nextIntent = new Intent(this, MainActivity.class);
            }
        } else {
            nextIntent = new Intent(this, LoginActivity.class);
        }

        copyNotificationExtras(launchIntent, nextIntent);
        startActivity(nextIntent);
        finish();
    }

    private void copyNotificationExtras(Intent source, Intent target) {
        if (source == null || target == null) return;
        if (source.getExtras() == null) return;

        String[] keys = new String[]{
                "title", "body", "category", "type", "route",
                "orderId", "hotelId", "deepLink", "open_tab"
        };

        for (String key : keys) {
            if (!source.hasExtra(key)) continue;
            Object value = source.getExtras().get(key);
            if (value instanceof String) {
                target.putExtra(key, (String) value);
            }
        }
    }
}