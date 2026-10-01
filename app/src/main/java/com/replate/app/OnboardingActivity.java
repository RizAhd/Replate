package com.replate.app;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth;
import com.replate.app.adapters.OnboardingAdapter;
import com.replate.app.models.OnboardingItem;

import java.util.ArrayList;
import java.util.List;

import me.relex.circleindicator.CircleIndicator3;

/**
 * Onboarding Activity - Shows 3 slides introducing the app.
 * Displayed only once for first-time users (new registration or first app launch).
 * Uses ViewPager2 with page-transform animations for a premium feel.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPagerOnboarding;
    private OnboardingAdapter onboardingAdapter;
    private CircleIndicator3 circleIndicator;
    private Button buttonNext;
    private TextView textSkip;

    /** True when we were launched right after the user registered / signed in. */
    private boolean isPostAuth = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If the user already completed onboarding, skip to the next screen
        if (hasSeenOnboarding()) {
            proceedAfterOnboarding();
            return;
        }

        // Check if launched after authentication (first-time login/register)
        isPostAuth = getIntent().getBooleanExtra("post_auth", false);

        setContentView(R.layout.activity_onboarding);

        // Initialize views
        viewPagerOnboarding = findViewById(R.id.viewPagerOnboarding);
        circleIndicator = findViewById(R.id.circleIndicator);
        buttonNext = findViewById(R.id.buttonNext);
        textSkip = findViewById(R.id.textSkip);

        // Setup onboarding items
        setupOnboardingItems();

        // Setup ViewPager with page transformer for depth effect
        viewPagerOnboarding.setAdapter(onboardingAdapter);
        circleIndicator.setViewPager(viewPagerOnboarding);
        viewPagerOnboarding.setPageTransformer(new DepthPageTransformer());

        // Bottom card rises into place (skipped when the user turned animations off)
        View bottomLayout = findViewById(R.id.bottomLayout);
        if (bottomLayout != null && animationsEnabled()) {
            bottomLayout.setTranslationY(getResources().getDisplayMetrics().density * 32f);
            bottomLayout.setAlpha(0f);
            bottomLayout.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(450)
                    .setStartDelay(100)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        // Handle page changes
        viewPagerOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                boolean isLast = position == onboardingAdapter.getItemCount() - 1;
                buttonNext.setText(isLast ? R.string.onboarding_get_started : R.string.onboarding_next);
                // Skip is pointless on the last page: hide it and stop it taking taps
                textSkip.setEnabled(!isLast);
                textSkip.animate().alpha(isLast ? 0f : 1f).setDuration(200).start();
            }
        });

        // Next button click
        buttonNext.setOnClickListener(v -> {
            if (viewPagerOnboarding.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                viewPagerOnboarding.setCurrentItem(viewPagerOnboarding.getCurrentItem() + 1);
            } else {
                // Last page - mark onboarding as seen and proceed
                saveOnboardingSeen();
                proceedAfterOnboarding();
            }
        });

        // Skip button click
        textSkip.setOnClickListener(v -> {
            saveOnboardingSeen();
            proceedAfterOnboarding();
        });
    }

    private boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    /**
     * Setup the 3 onboarding slides with images and text
     */
    private void setupOnboardingItems() {
        List<OnboardingItem> onboardingItems = new ArrayList<>();

        // Slide 1 - Discover Deals
        OnboardingItem item1 = new OnboardingItem(
            R.drawable.ic_onboarding_discover,
            getString(R.string.onboarding_slide1_title),
            getString(R.string.onboarding_slide1_body)
        );

        // Slide 2 - Find Nearby
        OnboardingItem item2 = new OnboardingItem(
            R.drawable.ic_onboarding_nearby,
            getString(R.string.onboarding_slide2_title),
            getString(R.string.onboarding_slide2_body)
        );

        // Slide 3 - Save Food, Save Planet
        OnboardingItem item3 = new OnboardingItem(
            R.drawable.ic_onboarding_save,
            getString(R.string.onboarding_slide3_title),
            getString(R.string.onboarding_slide3_body)
        );

        onboardingItems.add(item1);
        onboardingItems.add(item2);
        onboardingItems.add(item3);

        onboardingAdapter = new OnboardingAdapter(onboardingItems);
    }

    /**
     * Check if user has already seen onboarding
     */
    private boolean hasSeenOnboarding() {
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        return prefs.getBoolean("onboarding_seen", false);
    }

    /**
     * Save that user has seen onboarding
     */
    private void saveOnboardingSeen() {
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarding_seen", true).apply();
    }

    /**
     * Navigate to the appropriate screen after onboarding:
     * - If user is already authenticated (post-registration), go to MainActivity.
     * - Otherwise go to LoginActivity.
     */
    private void proceedAfterOnboarding() {
        Intent intent;
        // If user is already signed in (came here right after first-time registration/social login)
        if (isPostAuth || FirebaseAuth.getInstance().getCurrentUser() != null) {
            SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
            String role = prefs.getString("role", "customer");
            if ("hotel".equals(role)) {
                try {
                    Class<?> hotelClass = Class.forName("com.replate.app.hotel.HotelDashboardActivity");
                    intent = new Intent(this, hotelClass);
                } catch (ClassNotFoundException e) {
                    intent = new Intent(this, MainActivity.class);
                }
            } else if ("admin".equals(role)) {
                try {
                    Class<?> adminClass = Class.forName("com.replate.app.admin.AdminDashboardActivity");
                    intent = new Intent(this, adminClass);
                } catch (ClassNotFoundException e) {
                    intent = new Intent(this, MainActivity.class);
                }
            } else {
                intent = new Intent(this, MainActivity.class);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /**
     * Depth page transformer for a premium swipe effect.
     */
    private static class DepthPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.85f;
        private static final float MIN_ALPHA = 0.5f;

        @Override
        public void transformPage(View page, float position) {
            int pageWidth = page.getWidth();
            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                // Use default slide transition on the left page
                page.setAlpha(1f);
                page.setTranslationX(0f);
                page.setScaleX(1f);
                page.setScaleY(1f);
            } else if (position <= 1) {
                // Fade the page out
                page.setAlpha(MIN_ALPHA + (1 - MIN_ALPHA) * (1 - position));
                // Counteract the default slide transition
                page.setTranslationX(pageWidth * -position);
                // Scale the page down
                float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            } else {
                page.setAlpha(0f);
            }
        }
    }
}