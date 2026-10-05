package com.replate.app.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Builds the links that hand the customer over to Google Maps for turn-by-turn directions to a
 * hotel. Plain strings so they can be unit tested without Android.
 */
public final class HotelNavigation {

    private HotelNavigation() {}

    /** Opens Google Maps navigation straight to the coordinates. */
    public static String navigationUri(double latitude, double longitude) {
        return String.format(Locale.US, "google.navigation:q=%.6f,%.6f", latitude, longitude);
    }

    /** Opens any installed map app on a pin, labelled with the hotel name when there is one. */
    public static String geoUri(double latitude, double longitude, String label) {
        String pin = String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
        String name = label == null ? "" : label.trim().replace("(", "").replace(")", "");
        return name.isEmpty()
                ? "geo:" + pin + "?q=" + pin
                : "geo:" + pin + "?q=" + pin + "(" + encode(name) + ")";
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    /** Valid, non-zero coordinates; (0, 0) is how an unset hotel location shows up. */
    public static boolean hasLocation(double latitude, double longitude) {
        boolean inRange = latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
        return inRange && !(latitude == 0 && longitude == 0);
    }
}
