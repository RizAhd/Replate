package com.replate.app.utils;

/**
 * How much a customer saved on one order. Shared by the Profile totals and its savings chart so
 * both always agree.
 */
public final class Savings {

    private Savings() {}

    /**
     * Prefers the saving recorded at checkout. Older orders did not store it, so fall back to the
     * original price minus what was paid.
     */
    public static double forOrder(double savingsAmount, double originalTotal, double totalAmount) {
        if (savingsAmount > 0) {
            return savingsAmount;
        }
        if (originalTotal > 0 && totalAmount > 0 && originalTotal > totalAmount) {
            return originalTotal - totalAmount;
        }
        return 0;
    }
}
