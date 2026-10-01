package com.replate.app.utils;

import androidx.annotation.Nullable;

/**
 * Input checks for the checkout screen, kept free of Android classes so they can be unit-tested.
 */
public final class CheckoutValidator {

    private CheckoutValidator() {}

    /**
     * Accepts local and international numbers: digits with optional leading '+', spaces, dashes
     * and brackets, 9 to 15 digits in total (e.g. 0771234567, +94 77 123 4567).
     */
    public static boolean isValidPhone(@Nullable String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        int digits = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
            } else if (c == '+' && i == 0) {
                continue;
            } else if (c == ' ' || c == '-' || c == '(' || c == ')') {
                continue;
            } else {
                return false;
            }
        }
        return digits >= 9 && digits <= 15;
    }

    /**
     * True when a "HH:mm" pickup time is earlier than the current time today. A value that can't
     * be parsed returns false; the screen's "required" check handles empty input.
     */
    public static boolean isPickupTimeInPast(@Nullable String hhmm, int nowHour, int nowMinute) {
        if (hhmm == null) {
            return false;
        }
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) {
            return false;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return false;
            }
            return hour * 60 + minute < nowHour * 60 + nowMinute;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** A card payment needs a real, positive, finite amount. */
    public static boolean isPayableAmount(double amount) {
        return !Double.isNaN(amount) && !Double.isInfinite(amount) && amount > 0;
    }
}
