package com.replate.app.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// Checks how money amounts are written.
public class MoneyTest {

    @Test
    public void formatsTwoDecimals() {
        assertEquals("LKR 250.00", Money.format(250));
        assertEquals("LKR 199.50", Money.format(199.5));
    }

    @Test
    public void groupsThousands() {
        assertEquals("LKR 1,250.00", Money.format(1250));
        assertEquals("LKR 1,234,567.89", Money.format(1234567.891));
    }

    @Test
    public void roundsHalfUp() {
        assertEquals("LKR 0.13", Money.format(0.125));
        assertEquals("LKR 0.01", Money.format(0.006));
    }

    @Test
    public void badNumbersBecomeZero() {
        assertEquals("LKR 0.00", Money.format(Double.NaN));
        assertEquals("LKR 0.00", Money.format(Double.POSITIVE_INFINITY));
    }

    @Test
    public void wholeRupees() {
        assertEquals("LKR 3,400", Money.formatWhole(3400));
        assertEquals("LKR 0", Money.formatWhole(0));
    }
}
