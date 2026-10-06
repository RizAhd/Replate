package com.replate.app.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares what the customer put in the cart with what the hotel is offering right now, so a stale
 * price or a sold-out dish is caught before any money is taken.
 */
public final class CartCheck {

    private CartCheck() {}

    /** One cart line against the live listing. {@code live == null} means the dish no longer exists. */
    public static final class Line {
        final String name;
        final int wantedQuantity;
        final double cartPrice;
        final Live live;

        public Line(String name, int wantedQuantity, double cartPrice, Live live) {
            this.name = name;
            this.wantedQuantity = wantedQuantity;
            this.cartPrice = cartPrice;
            this.live = live;
        }
    }

    public static final class Live {
        final boolean available;
        final int quantity;
        final double price;

        public Live(boolean available, int quantity, double price) {
            this.available = available;
            this.quantity = quantity;
            this.price = price;
        }
    }

    /** Plain sentences, one per problem; empty when the cart can be paid as it is. */
    public static List<String> problems(List<Line> lines) {
        List<String> out = new ArrayList<>();
        for (Line line : lines) {
            String name = line.name == null || line.name.trim().isEmpty() ? "A dish" : line.name.trim();
            if (line.live == null || !line.live.available || line.live.quantity <= 0) {
                out.add(name + " is no longer available.");
            } else if (line.live.quantity < line.wantedQuantity) {
                out.add("Only " + line.live.quantity + " of " + name + " left (you have " + line.wantedQuantity + ").");
            } else if (Math.abs(line.live.price - line.cartPrice) > 0.009) {
                out.add(String.format(Locale.US, "The price of %s changed to LKR %.2f.", name, line.live.price));
            }
        }
        return out;
    }
}
