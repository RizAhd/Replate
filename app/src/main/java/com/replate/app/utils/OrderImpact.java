package com.replate.app.utils;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** What an order means in the bigger picture: how many meals it rescues. */
public final class OrderImpact {

    private OrderImpact() {}

    /**
     * Number of dishes in an order. Firebase hands the items back as a list or as a map, and an
     * older line without a quantity counts as one.
     */
    public static int mealCount(@Nullable Object items) {
        Collection<?> lines = null;
        if (items instanceof List) {
            lines = (List<?>) items;
        } else if (items instanceof Map) {
            lines = ((Map<?, ?>) items).values();
        }
        if (lines == null) {
            return 0;
        }
        int total = 0;
        for (Object line : lines) {
            if (!(line instanceof Map)) {
                continue;
            }
            Object quantity = ((Map<?, ?>) line).get("quantity");
            int count = quantity instanceof Number ? ((Number) quantity).intValue() : 1;
            total += Math.max(1, count);
        }
        return total;
    }
}
