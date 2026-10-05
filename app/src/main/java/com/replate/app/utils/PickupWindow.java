package com.replate.app.utils;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * A pickup slot is a one-hour window that starts at the time the customer chose. The pickup QR
 * code and OTP stop working when the window closes, so a code cannot be reused later.
 */
public final class PickupWindow {

    /** How long after the chosen time the order can still be collected. */
    public static final int WINDOW_MINUTES = 60;

    private PickupWindow() {}

    /**
     * Epoch millis when the code for a "HH:mm" pickup time chosen today expires, or 0 when the
     * time cannot be read (such an order never expires on its own).
     */
    public static long expiresAt(@Nullable String hhmm, long nowMillis, TimeZone zone) {
        if (hhmm == null) {
            return 0;
        }
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) {
            return 0;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return 0;
            }
            Calendar slot = Calendar.getInstance(zone);
            slot.setTimeInMillis(nowMillis);
            slot.set(Calendar.HOUR_OF_DAY, hour);
            slot.set(Calendar.MINUTE, minute);
            slot.set(Calendar.SECOND, 0);
            slot.set(Calendar.MILLISECOND, 0);
            slot.add(Calendar.MINUTE, WINDOW_MINUTES);
            return slot.getTimeInMillis();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** An expiry of 0 means "no expiry recorded", which is never expired. */
    public static boolean isExpired(long expiresAtMillis, long nowMillis) {
        return expiresAtMillis > 0 && nowMillis > expiresAtMillis;
    }
}
