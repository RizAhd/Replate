package com.replate.app.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TelephonyUtilsTest {

    @Test
    public void removesSpacesAndDashes() {
        assertEquals("0771234567", TelephonyUtils.normalizePhone("077 123-4567", ""));
    }

    @Test
    public void doubleZeroPrefixBecomesPlus() {
        assertEquals("+94771234567", TelephonyUtils.normalizePhone("0094 77 123 4567", ""));
    }

    @Test
    public void keepsAnExistingPlus() {
        assertEquals("+94771234567", TelephonyUtils.normalizePhone("+94 77 123 4567", ""));
    }

    @Test
    public void emptyInputUsesTheFallback() {
        assertEquals("0112345678", TelephonyUtils.normalizePhone("", "011 234 5678"));
        assertEquals("0112345678", TelephonyUtils.normalizePhone(null, "0112345678"));
    }

    @Test
    public void nothingUsableGivesAnEmptyString() {
        assertEquals("", TelephonyUtils.normalizePhone(null, null));
        assertEquals("", TelephonyUtils.normalizePhone("   ", ""));
    }
}
