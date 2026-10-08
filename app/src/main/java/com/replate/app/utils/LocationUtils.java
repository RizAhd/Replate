package com.replate.app.utils;

import android.location.Location;

// Distance helper for locations.
public class LocationUtils {

    /**
     * Calculate distance between two points in km
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if (lat1 == 0 || lon1 == 0 || lat2 == 0 || lon2 == 0) return 99999; // Unknown distance

        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0; // Convert meters to km
    }
}

