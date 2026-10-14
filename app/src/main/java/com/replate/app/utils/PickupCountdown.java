package com.replate.app.utils;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time left to collect an open pickup order, the same rule as the web panels
 * ({@code ReplateSchema.pickupCountdown} in web-panels/js/data-schema.js):
 * <ul>
 *   <li>before the slot opens: "Pickup starts at 18:00" ({@link State#UPCOMING})</li>
 *   <li>during the slot: "Collect within 42 min" ({@link State#COLLECT}, or {@link State#URGENT}
 *       under {@link #URGENT_MINUTES} minutes)</li>
 *   <li>after the code stopped working: "Code expired" ({@link State#EXPIRED})</li>
 * </ul>
 * Deliveries, finished or cancelled orders and orders without a recorded expiry get no countdown.
 * Pure Java, so it is unit tested; the screens only pick the words and the colour.
 */
public final class PickupCountdown {

    /** Under this many minutes left, the countdown turns to the warning colour. */
    public static final int URGENT_MINUTES = 15;

    private static final long MINUTE = 60_000L;
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");

    public enum State { UPCOMING, COLLECT, URGENT, EXPIRED }

    /** What to show right now. */
    public static final class Result {
        public final State state;
        /** Whole minutes left, rounded up (COLLECT and URGENT); 0 otherwise. */
        public final int minutesLeft;
        /** When the slot opens (epoch millis). */
        public final long startsAt;
        /** When the code stops working (epoch millis). */
        public final long expiresAt;

        Result(State state, int minutesLeft, long startsAt, long expiresAt) {
            this.state = state;
            this.minutesLeft = minutesLeft;
            this.startsAt = startsAt;
            this.expiresAt = expiresAt;
        }
    }

    private PickupCountdown() {}

    /**
     * The countdown for one order, or null when it has none.
     *
     * @param isDelivery  delivery orders are never collected, so they have no countdown
     * @param isOpen      false for completed, delivered and cancelled orders
     * @param pickupTime  the time the customer chose ("18:30", or "18:00 - 19:00" in older orders)
     * @param expiresAt   pickupExpiresAt, 0 when the order has none
     */
    @Nullable
    public static Result of(boolean isDelivery, boolean isOpen, @Nullable String pickupTime,
                            long expiresAt, long nowMillis, TimeZone zone) {
        if (isDelivery || !isOpen || expiresAt <= 0) {
            return null;
        }
        long startsAt = startsAt(pickupTime, expiresAt, zone);
        if (nowMillis > expiresAt) {
            return new Result(State.EXPIRED, 0, startsAt, expiresAt);
        }
        if (nowMillis < startsAt) {
            return new Result(State.UPCOMING, 0, startsAt, expiresAt);
        }
        int minutesLeft = (int) Math.max(1, (expiresAt - nowMillis + MINUTE - 1) / MINUTE);
        State state = minutesLeft < URGENT_MINUTES ? State.URGENT : State.COLLECT;
        return new Result(state, minutesLeft, startsAt, expiresAt);
    }

    /**
     * When the slot opens: the first "HH:mm" in the pickup time, on the slot's own day, or one hour
     * before the code expires when the time cannot be read.
     */
    public static long startsAt(@Nullable String pickupTime, long expiresAt, TimeZone zone) {
        long fallback = expiresAt - PickupWindow.WINDOW_MINUTES * MINUTE;
        if (pickupTime == null) {
            return fallback;
        }
        Matcher match = CLOCK.matcher(pickupTime);
        if (!match.find()) {
            return fallback;
        }
        int hour = Integer.parseInt(match.group(1));
        int minute = Integer.parseInt(match.group(2));
        if (hour > 23 || minute > 59) {
            return fallback;
        }
        Calendar start = Calendar.getInstance(zone);
        start.setTimeInMillis(fallback);
        start.set(Calendar.HOUR_OF_DAY, hour);
        start.set(Calendar.MINUTE, minute);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start.getTimeInMillis() <= expiresAt ? start.getTimeInMillis() : fallback;
    }

    /** "18:00" (24-hour) in the given zone. */
    public static String clock(long millis, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(millis);
        return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /**
     * How long until the shown text changes, so a screen can refresh just then: never more often than
     * once a second, never later than a minute; -1 once the code has expired (nothing changes after that).
     */
    public static long millisUntilChange(@Nullable Result result, long nowMillis) {
        if (result == null || result.state == State.EXPIRED) {
            return -1;
        }
        long wait;
        if (result.state == State.UPCOMING) {
            wait = result.startsAt - nowMillis;
        } else {
            // The minute count drops when the time left reaches the next whole minute below it
            wait = (result.expiresAt - nowMillis) - (result.minutesLeft - 1) * MINUTE + 1;
        }
        return Math.max(1_000L, Math.min(MINUTE, wait));
    }
}
