package com.replate.app.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// Checks the order number rule shared with the web panels.
public class OrderNumberTest {

    @Test
    public void lastEightCharactersInUpperCase() {
        assertEquals("#B2C3D4E5", OrderNumber.format("ORD-a1b2c3d4e5", null));
    }

    @Test
    public void shortIdsAreKeptWhole() {
        assertEquals("#AB12", OrderNumber.format("ab12", "-Nkey"));
    }

    @Test
    public void fallsBackToTheFirebaseKey() {
        assertEquals("#XYZ98765", OrderNumber.format(null, "-NabcXyZ98765"));
        assertEquals("#XYZ98765", OrderNumber.format("  ", "-NabcXyZ98765"));
    }

    @Test
    public void nothingWithoutAnId() {
        assertEquals("", OrderNumber.format(null, null));
    }
}
