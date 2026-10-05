package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HotelNavigationTest {

    @Test
    public void navigationUriUsesDotsAndSixDecimals() {
        assertEquals("google.navigation:q=6.927079,79.861244",
                HotelNavigation.navigationUri(6.927079, 79.861244));
    }

    @Test
    public void geoUriLabelsThePinWithTheHotelName() {
        assertEquals("geo:6.927079,79.861244?q=6.927079,79.861244(Ocean%20View)",
                HotelNavigation.geoUri(6.927079, 79.861244, "Ocean View"));
    }

    @Test
    public void geoUriWithoutNameIsJustThePin() {
        assertEquals("geo:6.900000,79.800000?q=6.900000,79.800000",
                HotelNavigation.geoUri(6.9, 79.8, "  "));
    }

    @Test
    public void bracketsInANameCannotBreakTheLink() {
        assertEquals("geo:1.000000,2.000000?q=1.000000,2.000000(Cafe%20Bar)",
                HotelNavigation.geoUri(1, 2, "Cafe (Bar)"));
    }

    @Test
    public void unsetOrOutOfRangeLocationsAreRejected() {
        assertFalse(HotelNavigation.hasLocation(0, 0));
        assertFalse(HotelNavigation.hasLocation(91, 10));
        assertFalse(HotelNavigation.hasLocation(10, 181));
        assertTrue(HotelNavigation.hasLocation(6.9, 79.8));
    }
}
