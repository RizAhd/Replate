package com.replate.app.models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CartItemTest {

    private static CartItem item(double price, int quantity) {
        return new CartItem("f1", "Rice", "Hotel", "h1", "", price, price * 2, quantity, 50);
    }

    @Test
    public void subtotalIsPriceTimesQuantity() {
        assertEquals(750.0, item(250.0, 3).getSubtotal(), 0.0001);
    }

    @Test
    public void singleItemSubtotalIsItsPrice() {
        assertEquals(199.5, item(199.5, 1).getSubtotal(), 0.0001);
    }

    @Test
    public void zeroQuantityGivesZero() {
        assertEquals(0.0, item(250.0, 0).getSubtotal(), 0.0001);
    }
}
