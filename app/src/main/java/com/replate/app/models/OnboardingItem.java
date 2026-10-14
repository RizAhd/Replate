package com.replate.app.models;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/**
 * One onboarding slide: a photo, a short title and one sentence, plus which piece of real app UI
 * is drawn over the photo as an example (a meal card, a pickup chip or a pickup code).
 */
public class OnboardingItem {

    /** Example app UI shown over the photo. */
    public enum Sample { MEAL_CARD, PICKUP_CHIP, PICKUP_CODE }

    @DrawableRes private final int photo;
    @StringRes private final int photoDescription;
    @StringRes private final int title;
    @StringRes private final int description;
    private final Sample sample;

    public OnboardingItem(@DrawableRes int photo, @StringRes int photoDescription,
                          @StringRes int title, @StringRes int description, Sample sample) {
        this.photo = photo;
        this.photoDescription = photoDescription;
        this.title = title;
        this.description = description;
        this.sample = sample;
    }

    @DrawableRes
    public int getPhoto() {
        return photo;
    }

    @StringRes
    public int getPhotoDescription() {
        return photoDescription;
    }

    @StringRes
    public int getTitle() {
        return title;
    }

    @StringRes
    public int getDescription() {
        return description;
    }

    public Sample getSample() {
        return sample;
    }
}
