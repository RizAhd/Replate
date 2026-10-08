package com.replate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.replate.app.notifications.NotificationCategories;

/**
 * The notification switches on the Settings screen. The push service asks here before it
 * shows a heads-up or system notification; the message is still kept in the in-app list.
 */
public final class NotificationPrefs {

    public static final String KEY_ENABLED = "notifications_enabled";
    public static final String KEY_ORDERS = "notif_orders";
    public static final String KEY_DEALS = "notif_deals";
    public static final String KEY_PICKUP = "notif_pickup";

    private NotificationPrefs() {}

    // True when the user's switches allow this type of notification.
    public static boolean isAllowed(Context context, String category) {
        SharedPreferences prefs = context.getSharedPreferences("REPLATE_PREFS", Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, true)) {
            return false;
        }
        switch (NotificationCategories.normalize(category)) {
            case NotificationCategories.ORDER_PLACED:
            case NotificationCategories.ORDER_ACCEPTED:
            case NotificationCategories.ORDER_REJECTED:
            case NotificationCategories.ORDER_CANCELLED:
                return prefs.getBoolean(KEY_ORDERS, true);
            case NotificationCategories.NEW_DEALS:
            case NotificationCategories.HOT_DEALS:
                return prefs.getBoolean(KEY_DEALS, true);
            case NotificationCategories.PICKUP_READY:
            case NotificationCategories.PICKUP_REMINDER:
                return prefs.getBoolean(KEY_PICKUP, true);
            default:
                // Announcements, hotel orders and account messages follow only the master switch
                return true;
        }
    }
}
