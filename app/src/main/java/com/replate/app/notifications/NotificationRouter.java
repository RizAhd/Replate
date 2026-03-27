package com.replate.app.notifications;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.replate.app.MainActivity;
import com.replate.app.NotificationsActivity;
import com.replate.app.OrderDetailActivity;

import java.util.Locale;
import java.util.Map;

public final class NotificationRouter {

    private NotificationRouter() {}

    public static Intent buildIntent(Context context,
                                     String category,
                                     String route,
                                     String orderId,
                                     String hotelId,
                                     String deepLink,
                                     Map<String, String> data) {
        String normalizedCategory = NotificationCategories.normalize(category);
        String normalizedRoute = route == null ? "notifications" : route.trim().toLowerCase(Locale.US);
        String safeOrderId = orderId == null ? "" : orderId.trim();

        if (NotificationCategories.PICKUP_READY.equals(normalizedCategory)
                || NotificationCategories.PICKUP_REMINDER.equals(normalizedCategory)
                || normalizedRoute.contains("map")
                || normalizedRoute.contains("pickup")) {
            Intent mapIntent = new Intent(context, MainActivity.class);
            mapIntent.putExtra("open_tab", "map");
            if (hotelId != null && !hotelId.trim().isEmpty()) {
                mapIntent.putExtra("focus_hotel_id", hotelId.trim());
            }
            if (!safeOrderId.isEmpty()) {
                mapIntent.putExtra("orderId", safeOrderId);
            }
            return mapIntent;
        }

        if (isOrderCategory(normalizedCategory) || normalizedRoute.contains("order")) {
            if (NotificationCategories.HOTEL_NEW_ORDER.equals(normalizedCategory)) {
                Intent hotelOrders = tryHotelOrdersIntent(context);
                if (hotelOrders != null) return hotelOrders;
            }

            Intent orderIntent = new Intent(context, OrderDetailActivity.class);
            if (!safeOrderId.isEmpty()) {
                orderIntent.putExtra("orderId", safeOrderId);
            }
            return orderIntent;
        }

        if (NotificationCategories.NEW_DEALS.equals(normalizedCategory)
                || NotificationCategories.HOT_DEALS.equals(normalizedCategory)
                || normalizedRoute.contains("deal")
                || normalizedRoute.contains("home")) {
            Intent homeIntent = new Intent(context, MainActivity.class);
            homeIntent.putExtra("open_tab", "home");
            return homeIntent;
        }

        if (deepLink != null && !deepLink.trim().isEmpty()) {
            try {
                return new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
            } catch (Exception ignored) {
            }
        }

        return new Intent(context, NotificationsActivity.class);
    }

    private static boolean isOrderCategory(String category) {
        return NotificationCategories.ORDER_PLACED.equals(category)
                || NotificationCategories.ORDER_ACCEPTED.equals(category)
                || NotificationCategories.ORDER_REJECTED.equals(category)
                || NotificationCategories.ORDER_CANCELLED.equals(category)
                || NotificationCategories.PICKUP_READY.equals(category)
                || NotificationCategories.PICKUP_REMINDER.equals(category)
                || NotificationCategories.HOTEL_NEW_ORDER.equals(category);
    }

    private static Intent tryHotelOrdersIntent(Context context) {
        try {
            Class<?> hotelOrdersClass = Class.forName("com.replate.app.hotel.HotelOrdersActivity");
            return new Intent(context, hotelOrdersClass);
        } catch (ClassNotFoundException ignored) {
            Intent fallback = new Intent(context, MainActivity.class);
            fallback.putExtra("open_tab", "orders");
            return fallback;
        }
    }
}

