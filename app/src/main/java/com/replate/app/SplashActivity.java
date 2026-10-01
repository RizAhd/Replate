package com.replate.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable proceedTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must run before super.onCreate: swaps the system splash theme for Theme.REPLATE.PostSplash
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        final Intent launchIntent = getIntent();

        View ivEmoji = findViewById(R.id.ivEmoji);
        View appNameView = findViewById(R.id.appName);
        View taglineView = findViewById(R.id.tagline);
        View glowRing = findViewById(R.id.glowRing);

        // Fade-in glow ring
        if (glowRing != null) {
            glowRing.setAlpha(0f);
            glowRing.setScaleX(0.6f);
            glowRing.setScaleY(0.6f);
            glowRing.animate().alpha(0.18f).scaleX(1f).scaleY(1f)
                    .setStartDelay(200).setDuration(800).start();
        }

        // Fade-in title and tagline
        if (appNameView != null) {
            appNameView.setAlpha(0f);
            appNameView.animate().alpha(1f).setStartDelay(350).setDuration(500).start();
        }
        if (taglineView != null) {
            taglineView.setAlpha(0f);
            taglineView.animate().alpha(1f).setStartDelay(550).setDuration(500).start();
        }

        if (ivEmoji != null) {
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
        }

        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        boolean returningUser = prefs.getString("userId", null) != null;
        // The system splash has already covered the cold start, so returning users only need a beat
        long splashDelayMs = returningUser ? 400L : 1500L;

        proceedTask = () -> {
            if (!isFinishing() && !isDestroyed()) {
                proceedToNextScreen(launchIntent);
            }
        };
        handler.postDelayed(proceedTask, splashDelayMs);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void proceedToNextScreen(Intent launchIntent) {
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        String savedUserId = prefs.getString("userId", null);
        String savedRole = prefs.getString("role", null);
        if (savedRole == null || savedRole.trim().isEmpty()) {
            savedRole = prefs.getString("userType", "customer");
        }

        // A saved id without a live Firebase session (signed out elsewhere, account removed) means login
        if (savedUserId != null && FirebaseAuth.getInstance().getCurrentUser() == null) {
            savedUserId = null;
        }

        Intent nextIntent;
        if (savedUserId != null) {
            // Auto-login based on role
            if ("hotel".equals(savedRole)) {
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
            // Show onboarding for first-time users; OnboardingActivity will
            // skip to LoginActivity automatically if onboarding was already seen.
            boolean onboardingSeen = prefs.getBoolean("onboarding_seen", false);
            if (onboardingSeen) {
                nextIntent = new Intent(this, LoginActivity.class);
            } else {
                nextIntent = new Intent(this, OnboardingActivity.class);
            }
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