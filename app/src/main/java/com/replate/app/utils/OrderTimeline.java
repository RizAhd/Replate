package com.replate.app.utils;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.replate.app.R;

/**
 * The steps an order goes through, as the customer sees them on the order screen. Pickup and delivery
 * orders share the first three steps and differ at the end.
 */
public final class OrderTimeline {

    public static final int STEP_COUNT = 5;

    private static final int[] PICKUP_LABELS = {
            R.string.timeline_placed, R.string.timeline_confirmed, R.string.timeline_preparing,
            R.string.timeline_ready, R.string.timeline_collected};

    private static final int[] DELIVERY_LABELS = {
            R.string.timeline_placed, R.string.timeline_confirmed, R.string.timeline_preparing,
            R.string.timeline_on_the_way, R.string.timeline_delivered};

    private OrderTimeline() {}

    // Gives the text for one step of the order timeline.
    @StringRes
    public static int labelFor(int step, boolean delivery) {
        int[] labels = delivery ? DELIVERY_LABELS : PICKUP_LABELS;
        return labels[Math.max(0, Math.min(step, STEP_COUNT - 1))];
    }

    /**
     * Index of the step the order has reached (0 = placed, 4 = collected or delivered), or -1 when
     * the order was cancelled and a timeline would be misleading.
     */
    public static int currentStep(@Nullable String orderStatus) {
        if (orderStatus == null) {
            return 0;
        }
        switch (OrderStatusAliases.canonical(orderStatus)) {
            case "cancelled":
                return -1;
            case "confirmed":
                return 1;
            case "preparing":
                return 2;
            case "ready":
            case "out_for_delivery":
                return 3;
            case "completed":
            case "delivered":
                return 4;
            default:
                // pending, pending_pickup, pending_delivery and anything unknown count as just placed
                return 0;
        }
    }
}
