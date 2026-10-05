package com.replate.app.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Telephony utility helpers used across food/order screens.
 */
public final class TelephonyUtils {

    private TelephonyUtils() {
    }

    public static String normalizePhone(String raw, String fallback) {
        String value = TextUtils.isEmpty(raw) ? fallback : raw;
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
     * Opens the phone app with the number filled in. ACTION_DIAL needs no permission, so the app
     * does not request CALL_PHONE; the customer taps the call button themselves.
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

    public static void openSms(Activity activity, String phone, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(phone)));
        intent.putExtra("sms_body", message);
        activity.startActivity(intent);
    }
}

