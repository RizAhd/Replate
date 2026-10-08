package com.replate.app.utils;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Pickup rules for a dish: a hotel sets "from - to" for each dish, and an order must be collected inside
 * that window. Pickup is same-day only; a window that runs past midnight counts until 23:59.
 * Times are minutes since midnight. Pure Java, so it is unit tested.
 */
public final class PickupSlot {

    /** Extra minutes after the window closes during which an already booked slot can still be collected. */
    public static final int GRACE_MINUTES = 15;
    public static final int DAY_MINUTES = 24 * 60;

    private PickupSlot() {}

    /** The allowed pickup times (start inclusive, end exclusive). {@link #UNLIMITED} means no limit. */
    public static final class Window {
        public final int start;
        public final int end;

        public Window(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public boolean isEmpty() {
            return start >= end;
        }

        public boolean isUnlimited() {
            return start == 0 && end == DAY_MINUTES;
        }

        /** "18:00 - 20:00" for messages. */
        public String label() {
            return format(start) + " - " + format(end >= DAY_MINUTES ? DAY_MINUTES - 1 : end);
        }
    }

    public static final Window UNLIMITED = new Window(0, DAY_MINUTES);

    /** "HH:mm" to minutes since midnight, or -1 when it cannot be read. */
    public static int parse(@Nullable String hhmm) {
        if (hhmm == null) return -1;
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) return -1;
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String format(int minutes) {
        int m = Math.max(0, Math.min(minutes, DAY_MINUTES - 1));
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    /** One dish's window; missing or unreadable times mean no limit. */
    public static Window windowOf(@Nullable String startHhmm, @Nullable String endHhmm) {
        int start = parse(startHhmm);
        int end = parse(endHhmm);
        if (start < 0 && end < 0) return UNLIMITED;
        if (start < 0) start = 0;
        // No end, or an end at/before the start (runs past midnight): open until the end of the day
        if (end < 0 || end <= start) end = DAY_MINUTES;
        return new Window(start, end);
    }

    /** The times that suit every dish in the cart (the overlap of their windows). */
    public static Window overlap(List<Window> windows) {
        int start = 0;
        int end = DAY_MINUTES;
        for (Window w : windows) {
            start = Math.max(start, w.start);
            end = Math.min(end, w.end);
        }
        return new Window(start, end);
    }

    /** True when the window has already closed for today. */
    public static boolean isClosed(Window window, int nowMinutes) {
        return !window.isUnlimited() && nowMinutes >= window.end;
    }

    /** The earliest time that can be chosen right now (not in the past, not before the window opens). */
    public static int earliest(Window window, int nowMinutes) {
        return Math.max(window.start, nowMinutes);
    }

    public enum Verdict { OK, IN_THE_PAST, BEFORE_WINDOW, AFTER_WINDOW, NO_COMMON_WINDOW, CLOSED_TODAY }

    /** Checks a chosen time ("HH:mm") against the window and the clock. */
    public static Verdict check(@Nullable String chosenHhmm, Window window, int nowMinutes) {
        int chosen = parse(chosenHhmm);
        if (chosen < 0) return Verdict.OK; // empty/unreadable input is the screen's own "required" check
        if (window.isEmpty()) return Verdict.NO_COMMON_WINDOW;
        if (isClosed(window, nowMinutes)) return Verdict.CLOSED_TODAY;
        if (chosen < nowMinutes) return Verdict.IN_THE_PAST;
        if (chosen < window.start) return Verdict.BEFORE_WINDOW;
        if (chosen >= window.end) return Verdict.AFTER_WINDOW;
        return Verdict.OK;
    }

    /**
     * Epoch millis when a slot booked at {@code chosenHhmm} stops being collectable: one hour after the chosen
     * time, but never later than the dish window's end plus a short grace. 0 when the time cannot be read.
     */
    public static long expiresAt(@Nullable String chosenHhmm, Window window, long nowMillis, java.util.TimeZone zone) {
        long slotEnd = PickupWindow.expiresAt(chosenHhmm, nowMillis, zone);
        if (slotEnd == 0 || window == null || window.isUnlimited()) return slotEnd;
        long midnight = PickupWindow.expiresAt("00:00", nowMillis, zone) - PickupWindow.WINDOW_MINUTES * 60_000L;
        long windowEnd = midnight + (window.end + GRACE_MINUTES) * 60_000L;
        return Math.min(slotEnd, windowEnd);
    }
}
