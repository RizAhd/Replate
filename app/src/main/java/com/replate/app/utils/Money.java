package com.replate.app.utils;

import java.text.DecimalFormat;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * One way to show prices everywhere: "LKR 1,250.00". Always Latin digits and a dot decimal, so a
 * phone set to another language never shows an amount that differs from what PayHere charges.
 */
public final class Money {

    private static final String CURRENCY = "LKR";

    private Money() {}

    // Formats an amount like "LKR 1,250.00".
    public static String format(double amount) {
        // DecimalFormat is not thread-safe, so build one per call; this is cheap next to the UI work
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP); // same as String.format("%.2f"), what PayHere is sent
        double safe = Double.isNaN(amount) || Double.isInfinite(amount) ? 0 : amount;
        return CURRENCY + " " + df.format(safe);
    }

    /** Whole rupees, for compact places such as the profile savings tile. */
    public static String formatWhole(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP);
        double safe = Double.isNaN(amount) || Double.isInfinite(amount) ? 0 : amount;
        return CURRENCY + " " + df.format(safe);
    }
}
