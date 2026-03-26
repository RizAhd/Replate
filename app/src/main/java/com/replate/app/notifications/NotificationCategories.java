package com.replate.app.notifications;

import java.util.Locale;

public final class NotificationCategories {
    public static final String WELCOME = "welcome";
    public static final String NEW_DEALS = "new_deals";
    public static final String HOT_DEALS = "hot_deals";
    public static final String ORDER_PLACED = "order_placed";
    public static final String ORDER_ACCEPTED = "order_accepted";
    public static final String ORDER_REJECTED = "order_rejected";
    public static final String ORDER_CANCELLED = "order_cancelled";
    public static final String PICKUP_READY = "pickup_ready";
    public static final String PICKUP_REMINDER = "pickup_reminder";
    public static final String ADMIN_ANNOUNCEMENT = "admin_announcement";
    public static final String ADMIN_BROADCAST = "admin_broadcast";
    public static final String HOTEL_NEW_ORDER = "hotel_new_order";
    public static final String PROFILE_UPDATED = "profile_updated";
    public static final String LOGIN_SUCCESS = "login_success";
    public static final String GENERAL = "general";

    private NotificationCategories() {}

    public static String normalize(String raw) {
        String category = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (category.isEmpty()) return GENERAL;

        switch (category) {
            case WELCOME:
            case NEW_DEALS:
            case HOT_DEALS:
            case ORDER_PLACED:
            case ORDER_ACCEPTED:
            case ORDER_REJECTED:
            case ORDER_CANCELLED:
            case PICKUP_READY:
            case PICKUP_REMINDER:
            case ADMIN_ANNOUNCEMENT:
            case ADMIN_BROADCAST:
            case HOTEL_NEW_ORDER:
            case PROFILE_UPDATED:
            case LOGIN_SUCCESS:
                return category;
            default:
                if (category.contains("deal")) return category.contains("hot") ? HOT_DEALS : NEW_DEALS;
                if (category.contains("reject")) return ORDER_REJECTED;
                if (category.contains("cancel")) return ORDER_CANCELLED;
                if (category.contains("accept")) return ORDER_ACCEPTED;
                if (category.contains("pickup") && category.contains("remind")) return PICKUP_REMINDER;
                if (category.contains("pickup") || category.contains("ready")) return PICKUP_READY;
                if (category.contains("new_order") || category.contains("hotel")) return HOTEL_NEW_ORDER;
                if (category.contains("place") || category.contains("create") || category.contains("order")) return ORDER_PLACED;
                if (category.contains("announce") || category.contains("broadcast") || category.contains("admin")) {
                    return ADMIN_ANNOUNCEMENT;
                }
                if (category.contains("profile")) return PROFILE_UPDATED;
                if (category.contains("login")) return LOGIN_SUCCESS;
                if (category.contains("welcome")) return WELCOME;
                return GENERAL;
        }
    }
}

