package com.replate.app.utils;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The order number people see: "#" and the last 8 characters of the order id (the Firebase key
 * when the id is missing), upper case. The web panels use the same rule
 * ({@code ReplateSchema.shortOrderId}), so a customer and a hotel read out the same number.
 */
public final class OrderNumber {

    private static final int LENGTH = 8;

    private OrderNumber() {}

    /** "#A1B2C3D4", or "" when there is no id at all. */
    public static String format(@Nullable String orderId, @Nullable String firebaseKey) {
        String id = orderId != null && !orderId.trim().isEmpty() ? orderId.trim()
                : firebaseKey != null ? firebaseKey.trim() : "";
        if (id.isEmpty()) {
            return "";
        }
        String tail = id.length() > LENGTH ? id.substring(id.length() - LENGTH) : id;
        return "#" + tail.toUpperCase(Locale.ROOT);
    }
}
