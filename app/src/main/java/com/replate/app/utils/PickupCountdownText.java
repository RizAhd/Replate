package com.replate.app.utils;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.replate.app.R;
import com.replate.app.models.Order;

import java.util.TimeZone;

/**
 * The words and colour of the pickup countdown on the order screens ("Pickup starts at 18:00",
 * "Collect within 42 min", "Code expired"). The rule itself is {@link PickupCountdown}.
 */
public final class PickupCountdownText {

    private PickupCountdownText() {}

    /** The countdown for this order right now, or null when it has none (delivery, finished, no expiry). */
    @Nullable
    public static PickupCountdown.Result now(@Nullable Order order) {
        if (order == null) {
            return null;
        }
        return PickupCountdown.of(order.isDelivery(), order.isActive(), order.getPickupTime(),
                order.getPickupExpiresAtValue(), System.currentTimeMillis(), TimeZone.getDefault());
    }

    public static String text(@NonNull Context context, @NonNull PickupCountdown.Result result) {
        switch (result.state) {
            case UPCOMING:
                return context.getString(R.string.pickup_countdown_upcoming,
                        PickupCountdown.clock(result.startsAt, TimeZone.getDefault()));
            case EXPIRED:
                return context.getString(R.string.pickup_countdown_expired);
            default:
                return context.getString(R.string.pickup_countdown_collect, result.minutesLeft);
        }
    }

    /** The usual pickup-chip colour, red under {@link PickupCountdown#URGENT_MINUTES} minutes or once expired. */
    @ColorRes
    public static int color(@NonNull PickupCountdown.Result result) {
        return result.state == PickupCountdown.State.URGENT || result.state == PickupCountdown.State.EXPIRED
                ? R.color.error : R.color.ember_deep;
    }
}
