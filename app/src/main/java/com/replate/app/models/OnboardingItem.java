package com.replate.app.models;

/**
 * Model class for Onboarding slides
 * Holds the data for each onboarding screen
 */
public class OnboardingItem {
    private int image;      // Resource ID for the image
    private String title;   // Title text
    private String description;  // Description text

    // Constructor
    public OnboardingItem(int image, String title, String description) {
        this.image = image;
        this.title = title;
        this.description = description;
    }

    // Getters
    public int getImage() {
        return image;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    // Setters
    public void setImage(int image) {
        this.image = image;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

