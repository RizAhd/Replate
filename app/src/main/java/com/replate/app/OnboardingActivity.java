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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth;
import com.replate.app.adapters.OnboardingAdapter;
import com.replate.app.models.OnboardingItem;
import com.replate.app.views.PillPageIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * Onboarding Activity - Shows 3 slides introducing the app.
 * Displayed only once for first-time users (new registration or first app launch).
 * Each slide is a real photo with an example of the app on it; the photo drifts a little slower
 * than the text while swiping.
 */
public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPagerOnboarding;
    private OnboardingAdapter onboardingAdapter;
    private PillPageIndicator pageIndicator;
    private Button buttonNext;
    private TextView textSkip;

    /** True when we were launched right after the user registered / signed in. */
    private boolean isPostAuth = false;

    // Shows the slides, or skips them if already seen.
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
        pageIndicator = findViewById(R.id.pageIndicator);
        buttonNext = findViewById(R.id.buttonNext);
        textSkip = findViewById(R.id.textSkip);

        // Setup onboarding items
        setupOnboardingItems();

        viewPagerOnboarding.setAdapter(onboardingAdapter);
        pageIndicator.attachTo(viewPagerOnboarding);
        // Photo parallax only when the phone allows animations
        if (animationsEnabled()) {
            viewPagerOnboarding.setPageTransformer(new PhotoParallaxTransformer());
        }

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
            // Changes the button text and hides Skip on the last page.
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
                viewPagerOnboarding.setCurrentItem(viewPagerOnboarding.getCurrentItem() + 1, animationsEnabled());
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

    // Checks that the phone allows animations.
    private boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    /**
     * The 3 slides: what REPLATE is, how pickup works, and what you show at the counter
     */
    private void setupOnboardingItems() {
        List<OnboardingItem> onboardingItems = new ArrayList<>();
        onboardingItems.add(new OnboardingItem(R.drawable.onboarding_1, R.string.onboarding_slide1_photo_cd,
                R.string.onboarding_slide1_title, R.string.onboarding_slide1_body,
                OnboardingItem.Sample.MEAL_CARD));
        onboardingItems.add(new OnboardingItem(R.drawable.onboarding_2, R.string.onboarding_slide2_photo_cd,
                R.string.onboarding_slide2_title, R.string.onboarding_slide2_body,
                OnboardingItem.Sample.PICKUP_CHIP));
        onboardingItems.add(new OnboardingItem(R.drawable.onboarding_3, R.string.onboarding_slide3_photo_cd,
                R.string.onboarding_slide3_title, R.string.onboarding_slide3_body,
                OnboardingItem.Sample.PICKUP_CODE));
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
            // This app is customers only (hotels and admins use the web panels)
            intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    /**
     * Moves only the photo, against the swipe at 0.4 of the page width, so it trails the text a
     * little. The page edge clips it, which reads as depth without any scaling or fading.
     */
    private static class PhotoParallaxTransformer implements ViewPager2.PageTransformer {
        private static final float PARALLAX = 0.4f;

        // Offsets the photo of a page that is partly on screen.
        @Override
        public void transformPage(@NonNull View page, float position) {
            View photo = page.findViewById(R.id.imageOnboarding);
            if (photo == null) {
                return;
            }
            if (position <= -1f || position >= 1f) {
                photo.setTranslationX(0f);
            } else {
                // Positions run the other way in a right-to-left layout
                float against = page.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 1f : -1f;
                photo.setTranslationX(against * position * page.getWidth() * PARALLAX);
            }
        }
    }
}