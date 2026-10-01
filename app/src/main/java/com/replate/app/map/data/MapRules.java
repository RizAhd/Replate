package com.replate.app.map.data;

import androidx.annotation.Nullable;

/**
 * Rules shared by everything on the map that has to agree with everything else: the pin's
 * "N items" count and the bottom card's item list must use the same definition of "available",
 * and a pin must never be placed from garbage coordinates.
 */
public final class MapRules {

    private MapRules() {}

    /** A real place on Earth, and not the (0,0) that missing data usually becomes. */
    public static boolean isValidCoordinate(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)
                || Double.isInfinite(latitude) || Double.isInfinite(longitude)) {
            return false;
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return false;
        }
        return !(latitude == 0 && longitude == 0);
    }

    /** Hotels and listings sometimes carry an empty status; that has always meant "fine". */
    public static boolean isActiveStatus(@Nullable String status) {
        if (status == null) {
            return true;
        }
        String s = status.trim();
        return s.isEmpty()
                || s.equalsIgnoreCase("available")
                || s.equalsIgnoreCase("active")
                || s.equalsIgnoreCase("approved");
    }

    /**
     * Whether a food listing counts as available. A missing quantity is treated as "unknown, so
     * available" (older listings never had one); an explicit 0 or below is sold out.
     */
    public static boolean isListableItem(@Nullable String status, @Nullable Integer quantity) {
        return isActiveStatus(status) && (quantity == null || quantity > 0);
    }

    /** Returns the rating if the hotel has one, otherwise 0 so the screen can say "New". */
    public static float ratingOrZero(double rawRating) {
        if (Double.isNaN(rawRating) || rawRating <= 0 || rawRating > 5) {
            return 0f;
        }
        return (float) rawRating;
    }
}
