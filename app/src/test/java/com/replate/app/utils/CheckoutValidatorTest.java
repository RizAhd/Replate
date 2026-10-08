package com.replate.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Checks the checkout rules for phone, pickup time and amount.
public class CheckoutValidatorTest {

    @Test
    public void acceptsCommonPhoneFormats() {
        assertTrue(CheckoutValidator.isValidPhone("0771234567"));
        assertTrue(CheckoutValidator.isValidPhone("+94 77 123 4567"));
        assertTrue(CheckoutValidator.isValidPhone("077-123-4567"));
        assertTrue(CheckoutValidator.isValidPhone("(011) 2345678"));
        assertTrue(CheckoutValidator.isValidPhone("  0771234567  "));
    }

    @Test
    public void rejectsBadPhones() {
        assertFalse(CheckoutValidator.isValidPhone(null));
        assertFalse(CheckoutValidator.isValidPhone(""));
        assertFalse(CheckoutValidator.isValidPhone("   "));
        assertFalse(CheckoutValidator.isValidPhone("12345"));          // too short
        assertFalse(CheckoutValidator.isValidPhone("07712345678901234")); // too long
        assertFalse(CheckoutValidator.isValidPhone("077abc4567"));     // letters
        assertFalse(CheckoutValidator.isValidPhone("077+1234567"));    // '+' not at the start
    }

    @Test
    public void detectsPickupTimeInThePast() {
        assertTrue(CheckoutValidator.isPickupTimeInPast("09:00", 10, 30));
        assertTrue(CheckoutValidator.isPickupTimeInPast("10:29", 10, 30));
        assertFalse(CheckoutValidator.isPickupTimeInPast("10:30", 10, 30));
        assertFalse(CheckoutValidator.isPickupTimeInPast("18:45", 10, 30));
    }

    @Test
    public void unparseablePickupTimeIsNotTreatedAsPast() {
        assertFalse(CheckoutValidator.isPickupTimeInPast(null, 10, 30));
        assertFalse(CheckoutValidator.isPickupTimeInPast("", 10, 30));
        assertFalse(CheckoutValidator.isPickupTimeInPast("noon", 10, 30));
        assertFalse(CheckoutValidator.isPickupTimeInPast("25:00", 10, 30));
        assertFalse(CheckoutValidator.isPickupTimeInPast("10:75", 10, 30));
    }

    @Test
    public void payableAmountMustBePositiveAndFinite() {
        assertTrue(CheckoutValidator.isPayableAmount(250.0));
        assertTrue(CheckoutValidator.isPayableAmount(0.01));
        assertFalse(CheckoutValidator.isPayableAmount(0));
        assertFalse(CheckoutValidator.isPayableAmount(-5));
        assertFalse(CheckoutValidator.isPayableAmount(Double.NaN));
        assertFalse(CheckoutValidator.isPayableAmount(Double.POSITIVE_INFINITY));
    }
}
