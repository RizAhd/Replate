package com.replate.app.map.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapRulesTest {

    @Test
    public void acceptsRealCoordinates() {
        assertTrue(MapRules.isValidCoordinate(6.9271, 79.8612));
        assertTrue(MapRules.isValidCoordinate(-33.86, 151.21));
        assertTrue(MapRules.isValidCoordinate(90, 180));
    }

    @Test
    public void rejectsBadCoordinates() {
        assertFalse(MapRules.isValidCoordinate(0, 0));
        assertFalse(MapRules.isValidCoordinate(91, 10));
        assertFalse(MapRules.isValidCoordinate(10, 181));
        assertFalse(MapRules.isValidCoordinate(-90.5, 0.1));
        assertFalse(MapRules.isValidCoordinate(Double.NaN, 79.8));
        assertFalse(MapRules.isValidCoordinate(6.9, Double.POSITIVE_INFINITY));
    }

    @Test
    public void swappedSriLankaCoordinatesAreStillCaughtWhenOutOfRange() {
        // Longitude 79.86 is not a valid latitude, so a lat/lng swap is rejected only if lng > 90...
        assertTrue(MapRules.isValidCoordinate(79.8612, 6.9271));     // numerically valid, can't detect
        assertFalse(MapRules.isValidCoordinate(151.21, -33.86));      // clearly swapped
    }

    @Test
    public void statusRules() {
        assertTrue(MapRules.isActiveStatus(null));
        assertTrue(MapRules.isActiveStatus(""));
        assertTrue(MapRules.isActiveStatus("  "));
        assertTrue(MapRules.isActiveStatus("Available"));
        assertTrue(MapRules.isActiveStatus("ACTIVE"));
        assertTrue(MapRules.isActiveStatus("approved"));
        assertFalse(MapRules.isActiveStatus("sold_out"));
        assertFalse(MapRules.isActiveStatus("paused"));
        assertFalse(MapRules.isActiveStatus("pending"));
    }

    @Test
    public void listableItemCombinesStatusAndQuantity() {
        assertTrue(MapRules.isListableItem("available", 3));
        assertTrue(MapRules.isListableItem("", null));          // old listing without a quantity
        assertTrue(MapRules.isListableItem(null, null));
        assertFalse(MapRules.isListableItem("available", 0));
        assertFalse(MapRules.isListableItem("available", -1));
        assertFalse(MapRules.isListableItem("sold_out", 5));
    }

    @Test
    public void ratingIsZeroWhenMissingOrNonsense() {
        assertEquals(0f, MapRules.ratingOrZero(0), 0f);
        assertEquals(0f, MapRules.ratingOrZero(-2), 0f);
        assertEquals(0f, MapRules.ratingOrZero(7), 0f);
        assertEquals(0f, MapRules.ratingOrZero(Double.NaN), 0f);
        assertEquals(4.3f, MapRules.ratingOrZero(4.3), 0.0001f);
        assertEquals(5f, MapRules.ratingOrZero(5), 0f);
    }
}
