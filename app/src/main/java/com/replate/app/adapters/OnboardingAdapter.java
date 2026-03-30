package com.replate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.OnboardingItem;

import java.util.List;

/**
 * Adapter for the Onboarding ViewPager2
 * This displays the 3 onboarding slides with images and text
 */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private List<OnboardingItem> onboardingItems;

    public OnboardingAdapter(List<OnboardingItem> onboardingItems) {
        this.onboardingItems = onboardingItems;
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        holder.bind(onboardingItems.get(position));
    }

    @Override
    public int getItemCount() {
        return onboardingItems.size();
    }

    /**
     * ViewHolder class - holds references to views in each item
     * with entrance animations for a premium feel
     */
    class OnboardingViewHolder extends RecyclerView.ViewHolder {
        private ImageView imageOnboarding;
        private TextView textTitle;
        private TextView textDescription;

        OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            imageOnboarding = itemView.findViewById(R.id.imageOnboarding);
            textTitle = itemView.findViewById(R.id.textTitle);
            textDescription = itemView.findViewById(R.id.textDescription);
        }

        void bind(OnboardingItem onboardingItem) {
            imageOnboarding.setImageResource(onboardingItem.getImage());
            textTitle.setText(onboardingItem.getTitle());
            textDescription.setText(onboardingItem.getDescription());

            // Entrance animation — image scales up with overshoot
            imageOnboarding.setScaleX(0.7f);
            imageOnboarding.setScaleY(0.7f);
            imageOnboarding.setAlpha(0f);
            imageOnboarding.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(500)
                    .setStartDelay(100)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();

            // Title slides up and fades in
            textTitle.setTranslationY(40f);
            textTitle.setAlpha(0f);
            textTitle.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Description slides up and fades in
            textDescription.setTranslationY(30f);
            textDescription.setAlpha(0f);
            textDescription.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay(350)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}

