package com.replate.app.notifications;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;
import com.replate.app.R;

public final class InAppNotificationPopup {

    private InAppNotificationPopup() {}

    public static void show(@NonNull Activity activity,
                            @NonNull InAppNotificationCenter.InAppNotificationMessage message) {
        View root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        Snackbar snackbar = Snackbar.make(root, message.title + "\n" + message.body, Snackbar.LENGTH_LONG);
        snackbar.setAction("View", v -> {
            Intent intent = buildIntent(activity, message);
            if (intent != null) activity.startActivity(intent);
        });

        View view = snackbar.getView();
        int bgColor = backgroundByCategory(activity, message.category);
        int fgColor = activity.getColor(R.color.surface_white);
        view.setBackgroundColor(bgColor);
        snackbar.setTextColor(fgColor);
        snackbar.setActionTextColor(fgColor);
        snackbar.show();
    }

    private static Intent buildIntent(Activity activity, InAppNotificationCenter.InAppNotificationMessage message) {
        String category = NotificationCategories.normalize(message.category);

        if (NotificationCategories.ORDER_PLACED.equals(category)
                || NotificationCategories.ORDER_ACCEPTED.equals(category)
                || NotificationCategories.ORDER_REJECTED.equals(category)
                || NotificationCategories.ORDER_CANCELLED.equals(category)
                || NotificationCategories.PICKUP_READY.equals(category)
                || NotificationCategories.PICKUP_REMINDER.equals(category)
                || NotificationCategories.HOTEL_NEW_ORDER.equals(category)) {
            Intent intent = new Intent(activity, com.replate.app.OrderDetailActivity.class);
            if (message.orderId != null && !message.orderId.trim().isEmpty()) {
                intent.putExtra("orderId", message.orderId.trim());
            }
            return intent;
        }

        Intent intent = new Intent(activity, com.replate.app.MainActivity.class);
        if (NotificationCategories.NEW_DEALS.equals(category)
                || NotificationCategories.HOT_DEALS.equals(category)) {
            intent.putExtra("open_tab", "home");
        } else if (NotificationCategories.PICKUP_REMINDER.equals(category)) {
            intent.putExtra("open_tab", "map");
            if (message.hotelId != null && !message.hotelId.trim().isEmpty()) {
                intent.putExtra("focus_hotel_id", message.hotelId.trim());
            }
        } else {
            intent.putExtra("open_tab", "profile");
        }
        return intent;
    }

    private static int backgroundByCategory(Activity activity, String category) {
        String normalized = NotificationCategories.normalize(category);
        if (NotificationCategories.ORDER_CANCELLED.equals(normalized)
                || NotificationCategories.ORDER_REJECTED.equals(normalized)) {
            return activity.getColor(R.color.error);
        }
        if (NotificationCategories.HOT_DEALS.equals(normalized)
                || NotificationCategories.NEW_DEALS.equals(normalized)
                || NotificationCategories.PICKUP_READY.equals(normalized)) {
            return activity.getColor(R.color.warning);
        }
        return activity.getColor(R.color.primary);
    }
}
