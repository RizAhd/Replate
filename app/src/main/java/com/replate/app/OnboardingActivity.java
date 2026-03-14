package com.replate.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.replate.app.adapters.OnboardingAdapter;
import com.replate.app.models.OnboardingItem;

import java.util.ArrayList;
import java.util.List;

import me.relex.circleindicator.CircleIndicator3;

/**
 * Onboarding Activity - Shows 3 slides introducing the app
 * Uses ViewPager2 for smooth sliding between pages
 */
public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPagerOnboarding;
    private OnboardingAdapter onboardingAdapter;
    private CircleIndicator3 circleIndicator;
    private Button buttonNext;
    private TextView textSkip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user has seen onboarding before
        if (hasSeenOnboarding()) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        // Initialize views
        viewPagerOnboarding = findViewById(R.id.viewPagerOnboarding);
        circleIndicator = findViewById(R.id.circleIndicator);
        buttonNext = findViewById(R.id.buttonNext);
        textSkip = findViewById(R.id.textSkip);

        // Setup onboarding items
        setupOnboardingItems();

        // Setup ViewPager
        viewPagerOnboarding.setAdapter(onboardingAdapter);
        circleIndicator.setViewPager(viewPagerOnboarding);

        // Handle page changes
        viewPagerOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // Change button text on last page
                if (position == onboardingAdapter.getItemCount() - 1) {
                    buttonNext.setText("Get Started");
                } else {
                    buttonNext.setText("Next");
                }
            }
        });

        // Next button click
        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (viewPagerOnboarding.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                    // Go to next page
                    viewPagerOnboarding.setCurrentItem(viewPagerOnboarding.getCurrentItem() + 1);
                } else {
                    // Last page - go to login
                    saveOnboardingSeen();
                    goToLogin();
                }
            }
        });

        // Skip button click
        textSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveOnboardingSeen();
                goToLogin();
            }
        });
    }

    /**
     * Setup the 3 onboarding slides with images and text
     */
    private void setupOnboardingItems() {
        List<OnboardingItem> onboardingItems = new ArrayList<>();

        // Slide 1 - Discover Deals
        OnboardingItem item1 = new OnboardingItem(
            R.drawable.ic_onboarding_discover,
            "Discover Amazing Deals",
            "Find surplus food from hotels and restaurants at up to 50% off. Great meals at great prices!"
        );

        // Slide 2 - Find Nearby
        OnboardingItem item2 = new OnboardingItem(
            R.drawable.ic_onboarding_nearby,
            "Find Food Near You",
            "Use GPS to discover delicious deals nearby. Pick up fresh food from restaurants in your area."
        );

        // Slide 3 - Save Food, Save Planet
        OnboardingItem item3 = new OnboardingItem(
            R.drawable.ic_onboarding_save,
            "Save Food, Save Planet",
            "Join the food rescue movement! Every meal you save helps reduce food waste and protect our environment."
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
     * Go to Login/Register screen
     */
    private void goToLogin() {
        Intent intent = new Intent(OnboardingActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}