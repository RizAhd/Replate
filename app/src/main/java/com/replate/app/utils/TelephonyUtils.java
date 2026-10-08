package com.replate.app.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

/**
 * Telephony utility helpers used across food/order screens.
 */
public final class TelephonyUtils {

    private TelephonyUtils() {
    }

    // Removes spaces and dashes and turns a leading 00 into +.
    public static String normalizePhone(String raw, String fallback) {
        String value = raw == null || raw.isEmpty() ? fallback : raw;
        if (value == null) {
            return "";
        }
        value = value.trim().replace(" ", "").replace("-", "");
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        }
        return value;
    }

    /**
     * Opens the phone app with the number filled in. ACTION_DIAL needs no permission, so this is the
     * fallback when the customer has not granted CALL_PHONE (see {@link CallHelper}).
     */
    public static void openDialer(Activity activity, String phone) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)));
        activity.startActivity(intent);
    }

    /**
     * Places the call straight away with ACTION_CALL. Only use this once CALL_PHONE has been granted
     * (CallHelper asks for it and falls back to the dialer when the customer says no).
     */
    public static void callNow(Activity activity, String phone) {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(phone)));
        activity.startActivity(intent);
    }

    // Opens the messages app with the number and text filled in.
    public static void openSms(Activity activity, String phone, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(phone)));
        intent.putExtra("sms_body", message);
        activity.startActivity(intent);
    }
}

