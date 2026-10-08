package com.replate.app.utils;

import java.util.Locale;

/**
 * Other apps and panels write a few status names the app does not use itself. This maps them to
 * the app's own: accepted is confirmed, ready_for_pickup is ready and rejected is cancelled,
 * so such orders still land in the right Orders tab.
 */
public final class OrderStatusAliases {

    private OrderStatusAliases() {}

    /** The status in the app's own spelling (trimmed, lower case), or null when there is none. */
    public static String canonical(String status) {
        if (status == null) return null;
        String s = status.trim().toLowerCase(Locale.US);
        switch (s) {
            case "accepted":
                return "confirmed";
            case "ready_for_pickup":
                return "ready";
            case "rejected":
                return "cancelled";
            default:
                return s;
        }
    }
}
