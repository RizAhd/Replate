package com.replate.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

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

    public static boolean hasCallPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void openDialer(Activity activity, String phone) {
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
        activity.startActivity(intent);
    }

    public static void callNow(Activity activity, String phone) {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
        activity.startActivity(intent);
    }

    public static void openSms(Activity activity, String phone, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(phone)));
        intent.putExtra("sms_body", message);
        activity.startActivity(intent);
    }
}

