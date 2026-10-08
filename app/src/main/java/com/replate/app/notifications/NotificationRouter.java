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
        String safeHotelId = hotelId == null ? "" : hotelId.trim();
        String safeDeepLink = deepLink == null ? "" : deepLink.trim();

        if (NotificationCategories.HOTEL_APPROVED.equals(normalizedCategory)
                || normalizedRoute.contains("hotel_dashboard")
                || normalizedRoute.contains("approval")) {
            Intent dashboardIntent = tryHotelDashboardIntent(context);
            return applyCommonExtras(dashboardIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (NotificationCategories.PICKUP_READY.equals(normalizedCategory)
                || NotificationCategories.PICKUP_REMINDER.equals(normalizedCategory)
                || normalizedRoute.contains("map")
                || normalizedRoute.contains("pickup")) {
            Intent mapIntent = new Intent(context, MainActivity.class);
            mapIntent.putExtra("open_tab", "map");
            if (!safeHotelId.isEmpty()) {
                mapIntent.putExtra("focus_hotel_id", safeHotelId);
            }
            if (!safeOrderId.isEmpty()) {
                mapIntent.putExtra("orderId", safeOrderId);
            }
            return applyCommonExtras(mapIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        // A new chat message opens that conversation
        if ("chat".equals(normalizedRoute) && !safeHotelId.isEmpty()) {
            Intent chatIntent = new Intent(context, com.replate.app.ChatActivity.class);
            chatIntent.putExtra(com.replate.app.ChatActivity.EXTRA_HOTEL_ID, safeHotelId);
            return applyCommonExtras(chatIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (isOrderCategory(normalizedCategory) || normalizedRoute.contains("order")) {
            if (NotificationCategories.HOTEL_NEW_ORDER.equals(normalizedCategory)) {
                Intent hotelOrders = tryHotelOrdersIntent(context);
                return applyCommonExtras(hotelOrders, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
            }

            Intent orderIntent = new Intent(context, OrderDetailActivity.class);
            if (!safeOrderId.isEmpty()) {
                orderIntent.putExtra("orderId", safeOrderId);
            }
            return applyCommonExtras(orderIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (NotificationCategories.NEW_DEALS.equals(normalizedCategory)
                || NotificationCategories.HOT_DEALS.equals(normalizedCategory)
                || normalizedRoute.contains("deal")
                || normalizedRoute.contains("home")) {
            Intent homeIntent = new Intent(context, MainActivity.class);
            homeIntent.putExtra("open_tab", "home");
            return applyCommonExtras(homeIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
        }

        if (isSafeDeepLink(safeDeepLink)) {
            try {
                Intent deepLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeDeepLink));
                return applyCommonExtras(deepLinkIntent, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
            } catch (Exception ignored) {
            }
        }

        Intent fallback = new Intent(context, NotificationsActivity.class);
        return applyCommonExtras(fallback, normalizedCategory, normalizedRoute, safeOrderId, safeHotelId, safeDeepLink);
    }

    /**
     * Deep links come from push payloads / database rows, so only open plain web links.
     * Rejects javascript:, file:, content:, intent: and any other scheme.
     */
    public static boolean isSafeDeepLink(String deepLink) {
        if (deepLink == null || deepLink.trim().isEmpty()) {
            return false;
        }
        try {
            Uri uri = Uri.parse(deepLink.trim());
            String scheme = uri.getScheme();
            return "https".equalsIgnoreCase(scheme) && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static Intent applyCommonExtras(Intent intent,
                                            String category,
                                            String route,
                                            String orderId,
                                            String hotelId,
                                            String deepLink) {
        if (intent == null) {
            return new Intent();
        }
        intent.putExtra("category", category == null ? "general" : category);
        intent.putExtra("route", route == null ? "notifications" : route);
        intent.putExtra("orderId", orderId == null ? "" : orderId);
        intent.putExtra("hotelId", hotelId == null ? "" : hotelId);
        intent.putExtra("deepLink", deepLink == null ? "" : deepLink);
        return intent;
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

    private static Intent tryHotelDashboardIntent(Context context) {
        try {
            Class<?> hotelDashboardClass = Class.forName("com.replate.app.hotel.HotelDashboardActivity");
            return new Intent(context, hotelDashboardClass);
        } catch (ClassNotFoundException ignored) {
            Intent fallback = new Intent(context, MainActivity.class);
            fallback.putExtra("open_tab", "profile");
            return fallback;
        }
    }
}
