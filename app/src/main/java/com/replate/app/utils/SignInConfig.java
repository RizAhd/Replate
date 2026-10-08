package com.replate.app.utils;

/** Tells whether a sign-in key from the resources is a real value or still the template placeholder. */
public final class SignInConfig {

    private SignInConfig() {}

    // True when the value is not a placeholder.
    public static boolean isConfigured(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return !trimmed.isEmpty() && !trimmed.startsWith("YOUR_");
    }
}
