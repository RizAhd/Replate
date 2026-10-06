package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** The payment decisions that stand between a customer's card and a saved order. */
public class PaymentSafetyTest {

    // ---- PayHereConfig -------------------------------------------------------------------------

    @Test
    public void merchantIdPrefixPicksSandboxOrLive() {
        assertTrue(PayHereConfig.isSandbox("1234567"));
        assertTrue(PayHereConfig.isLive("2234567"));
        assertFalse(PayHereConfig.isSandbox("2234567"));
        assertFalse(PayHereConfig.isSandbox(""));
        assertFalse(PayHereConfig.isSandbox(null));
        assertFalse(PayHereConfig.isConfigured("  "));
    }

    @Test
    public void debugBuildCanOnlyUseTheSandbox() {
        assertTrue(PayHereConfig.mayStartPayment("1234567", true));
        assertFalse("a debug build must never charge a real card", PayHereConfig.mayStartPayment("2234567", true));
        assertTrue(PayHereConfig.mayStartPayment("2234567", false));
        assertFalse(PayHereConfig.mayStartPayment("", false));
        assertFalse(PayHereConfig.mayStartPayment(null, true));
    }

    // ---- PaymentOutcome ------------------------------------------------------------------------

    @Test
    public void onlyAConfirmedSuccessCountsAsPaid() {
        assertEquals(PaymentOutcome.Kind.SUCCESS, PaymentOutcome.classify(true, 1, 2, "ok"));
        // result code OK but the payment itself failed or is on hold: not paid
        assertEquals(PaymentOutcome.Kind.FAILED, PaymentOutcome.classify(true, -5, -2, "failed"));
        assertEquals(PaymentOutcome.Kind.FAILED, PaymentOutcome.classify(true, -5, 3, "hold"));
        // SDK says success but the payment status is not "success": still not paid
        assertEquals(PaymentOutcome.Kind.FAILED, PaymentOutcome.classify(true, 1, 0, "init"));
        assertEquals(PaymentOutcome.Kind.FAILED, PaymentOutcome.classify(true, 1, PaymentOutcome.UNKNOWN, ""));
        // a result code that is not OK can never be paid
        assertEquals(PaymentOutcome.Kind.FAILED, PaymentOutcome.classify(false, 1, 2, ""));
    }

    @Test
    public void cancelAndUnauthorizedAreRecognised() {
        assertEquals(PaymentOutcome.Kind.CANCELLED, PaymentOutcome.classify(false, -6, PaymentOutcome.UNKNOWN, ""));
        assertEquals(PaymentOutcome.Kind.CANCELLED, PaymentOutcome.classify(true, -5, 0, "User canceled the request"));
        assertEquals(PaymentOutcome.Kind.UNAUTHORIZED,
                PaymentOutcome.classify(true, -5, 0, "Unauthorized domain"));
    }

    // ---- CartCheck -----------------------------------------------------------------------------

    @Test
    public void aCartThatMatchesTheListingHasNoProblems() {
        List<CartCheck.Line> lines = Collections.singletonList(
                new CartCheck.Line("Rice", 2, 250.0, new CartCheck.Live(true, 5, 250.0)));
        assertTrue(CartCheck.problems(lines).isEmpty());
    }

    @Test
    public void soldOutDeletedLowStockAndPriceChangesAreAllReported() {
        List<CartCheck.Line> lines = Arrays.asList(
                new CartCheck.Line("Soup", 1, 100, new CartCheck.Live(false, 5, 100)),
                new CartCheck.Line("Cake", 1, 100, null),
                new CartCheck.Line("Rice", 4, 100, new CartCheck.Live(true, 2, 100)),
                new CartCheck.Line("Curry", 1, 100, new CartCheck.Live(true, 9, 120.5)),
                new CartCheck.Line("Bread", 1, 100, new CartCheck.Live(true, 0, 100)));
        List<String> problems = CartCheck.problems(lines);
        assertEquals(5, problems.size());
        assertEquals("Soup is no longer available.", problems.get(0));
        assertEquals("Cake is no longer available.", problems.get(1));
        assertEquals("Only 2 of Rice left (you have 4).", problems.get(2));
        assertEquals("The price of Curry changed to LKR 120.50.", problems.get(3));
        assertEquals("Bread is no longer available.", problems.get(4));
    }

    @Test
    public void aTinyPriceRoundingDifferenceIsNotAProblem() {
        List<CartCheck.Line> lines = Collections.singletonList(
                new CartCheck.Line("Rice", 1, 99.999, new CartCheck.Live(true, 5, 100.0)));
        assertTrue(CartCheck.problems(lines).isEmpty());
    }

    // ---- PendingPayment ------------------------------------------------------------------------

    @Test
    public void anUnsavedPaymentSurvivesBeingWrittenAndReadBack() {
        PendingPayment original = new PendingPayment("uid1", "-Nabc", "012345", "PayHere Online", 320005678L,
                true, "Nimal | Perera", "0771234567", "N/A", "12, Main St | Colombo 03 & more", 6.9271, 79.8612);
        PendingPayment copy = PendingPayment.decode(original.encode());
        assertEquals("uid1", copy.userId);
        assertEquals("-Nabc", copy.orderId);
        assertEquals("012345", copy.otp);
        assertEquals(320005678L, copy.paymentRef);
        assertTrue(copy.delivery);
        assertEquals("Nimal | Perera", copy.customerName);
        assertEquals("12, Main St | Colombo 03 & more", copy.address);
        assertEquals(6.9271, copy.lat, 0.00001);
        assertEquals(79.8612, copy.lng, 0.00001);
    }

    @Test
    public void damagedRecordsAreIgnoredInsteadOfCrashing() {
        assertNull(PendingPayment.decode(null));
        assertNull(PendingPayment.decode(""));
        assertNull(PendingPayment.decode("v2|a|b"));
        assertNull(PendingPayment.decode("v1|uid|order|otp|m|notanumber|0|n|p|t|a|0.0|0.0"));
        assertNull("a record without an order id is useless",
                PendingPayment.decode(new PendingPayment("uid", "", "1", "m", 1, false, "n", "p", "t", "a", 0, 0).encode()));
    }

    @Test
    public void otpWithLeadingZerosIsKept() {
        PendingPayment copy = PendingPayment.decode(
                new PendingPayment("u", "o", "000123", "m", 1, false, "n", "p", "t", "a", 0, 0).encode());
        assertEquals("000123", copy.otp);
    }
}
