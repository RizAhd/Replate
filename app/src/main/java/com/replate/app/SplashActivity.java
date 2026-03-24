package com.replate.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView ivEmoji = findViewById(R.id.ivEmoji);

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
        new Handler().postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
            String savedUserId = prefs.getString("userId", null);
            String savedRole = prefs.getString("role", null);
            if (savedRole == null || savedRole.trim().isEmpty()) {
                savedRole = prefs.getString("userType", "customer");
            }

            if (savedUserId != null) {
                // Auto-login based on role
                if ("hotel".equals(savedRole)) {
                    // Check if we have hotel dashboard activity class
                    try {
                        Class<?> hotelClass = Class.forName("com.replate.app.hotel.HotelDashboardActivity");
                        startActivity(new Intent(this, hotelClass));
                    } catch (ClassNotFoundException e) {
                        // Fallback if not yet implemented
                        startActivity(new Intent(this, MainActivity.class));
                    }
                } else if ("admin".equals(savedRole)) {
                    try {
                        Class<?> adminClass = Class.forName("com.replate.app.admin.AdminDashboardActivity");
                        startActivity(new Intent(this, adminClass));
                    } catch (ClassNotFoundException e) {
                        startActivity(new Intent(this, MainActivity.class));
                    }
                } else {
                    // Customer
                    startActivity(new Intent(this, MainActivity.class));
                }
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 2500);
    }
}