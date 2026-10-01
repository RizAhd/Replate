package com.replate.app;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Start-up router. The system splash (dark green, white mark) covers the cold start. Returning users
 * are sent straight to their home screen with no extra screen at all. First-time users get this
 * screen, which starts as an exact continuation of the system splash (same colour, same logo size and
 * position) and then fades the wordmark and tagline in.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long FIRST_RUN_HOLD_MS = 1400L;
    private static final long FIRST_RUN_HOLD_REDUCED_MS = 600L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must run before super.onCreate: swaps the system splash theme for Theme.REPLATE.PostSplash
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        final Intent launchIntent = getIntent();
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        boolean returningUser = prefs.getString("userId", null) != null;

        if (returningUser) {
            // No second splash: the system splash simply hands over to the next screen
            proceedToNextScreen(launchIntent);
            return;
        }

        setContentView(R.layout.activity_splash);
        boolean animate = animationsEnabled();
        playIntro(animate);

        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                proceedToNextScreen(launchIntent);
            }
        }, animate ? FIRST_RUN_HOLD_MS : FIRST_RUN_HOLD_REDUCED_MS);
    }

    private boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    /** Wordmark and tagline rise in under the (already visible) logo; the glow and loader fade in. */
    private void playIntro(boolean animate) {
        View glow = findViewById(R.id.glowRing);
        View appName = findViewById(R.id.appName);
        View tagline = findViewById(R.id.tagline);
        View progress = findViewById(R.id.progressBar);

        if (!animate) {
            appName.setAlpha(1f);
            tagline.setAlpha(1f);
            glow.setAlpha(0.35f);
            progress.setAlpha(1f);
            return;
        }

        float rise = getResources().getDisplayMetrics().density * 12f;
        appName.setTranslationY(rise);
        tagline.setTranslationY(rise);

        glow.animate().alpha(0.35f).setDuration(900).setStartDelay(100).start();
        appName.animate().alpha(1f).translationY(0f)
                .setInterpolator(new DecelerateInterpolator())
                .setStartDelay(150).setDuration(550).start();
        tagline.animate().alpha(1f).translationY(0f)
                .setInterpolator(new DecelerateInterpolator())
                .setStartDelay(300).setDuration(550).start();
        progress.animate().alpha(1f).setStartDelay(600).setDuration(400).start();
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
        // Cross-fade: dark green into the next screen instead of the default side-slide
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
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
