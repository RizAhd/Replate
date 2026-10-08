package com.replate.app.utils;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * The few product events worth measuring (the buying funnel). Every call is fire-and-forget:
 * analytics must never be able to break a screen, so failures are swallowed.
 */
public final class AppAnalytics {

    private static final String CURRENCY = "LKR";

    private AppAnalytics() {}

    // Records that a dish was added to the cart.
    public static void addToCart(@Nullable Context context, @Nullable String itemId, @Nullable String itemName,
                                 double price, int quantity) {
        Bundle item = new Bundle();
        item.putString(FirebaseAnalytics.Param.ITEM_ID, safe(itemId));
        item.putString(FirebaseAnalytics.Param.ITEM_NAME, safe(itemName));
        item.putDouble(FirebaseAnalytics.Param.PRICE, price);
        item.putLong(FirebaseAnalytics.Param.QUANTITY, Math.max(1, quantity));

        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.CURRENCY, CURRENCY);
        params.putDouble(FirebaseAnalytics.Param.VALUE, price * Math.max(1, quantity));
        params.putParcelableArray(FirebaseAnalytics.Param.ITEMS, new Bundle[]{item});
        log(context, FirebaseAnalytics.Event.ADD_TO_CART, params);
    }

    // Records a finished purchase.
    public static void purchase(@Nullable Context context, @Nullable String orderId, double value,
                                @Nullable String paymentMethod) {
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.TRANSACTION_ID, safe(orderId));
        params.putString(FirebaseAnalytics.Param.CURRENCY, CURRENCY);
        params.putDouble(FirebaseAnalytics.Param.VALUE, value);
        params.putString(FirebaseAnalytics.Param.PAYMENT_TYPE, safe(paymentMethod));
        log(context, FirebaseAnalytics.Event.PURCHASE, params);
    }

    // Records a sign-in and the method used.
    public static void login(@Nullable Context context, String method) {
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.METHOD, safe(method));
        log(context, FirebaseAnalytics.Event.LOGIN, params);
    }

    // Sends one event to analytics.
    private static void log(@Nullable Context context, String event, Bundle params) {
        if (context == null) {
            return;
        }
        try {
            FirebaseAnalytics.getInstance(context.getApplicationContext()).logEvent(event, params);
        } catch (Throwable ignored) {
            // Never let measurement affect the user's flow
        }
    }

    // Turns null text into an empty string.
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}
