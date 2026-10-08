package com.replate.app.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// Checks how much money an order saved the customer.
public class SavingsTest {

    @Test
    public void usesTheSavingRecordedAtCheckout() {
        assertEquals(120.0, Savings.forOrder(120.0, 900.0, 700.0), 0.001);
    }

    @Test
    public void fallsBackToOriginalMinusPaidForOldOrders() {
        assertEquals(200.0, Savings.forOrder(0.0, 900.0, 700.0), 0.001);
    }

    @Test
    public void neverReturnsANegativeSaving() {
        assertEquals(0.0, Savings.forOrder(0.0, 500.0, 750.0), 0.001);
    }

    @Test
    public void missingValuesGiveZero() {
        assertEquals(0.0, Savings.forOrder(0.0, 0.0, 0.0), 0.001);
    }
}
