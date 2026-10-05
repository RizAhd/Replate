package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class PickupWindowTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static long at(int hour, int minute) {
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(2026, Calendar.OCTOBER, 5, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void windowClosesOneHourAfterTheChosenTime() {
        long now = at(9, 0);
        assertEquals(at(19, 0), PickupWindow.expiresAt("18:00", now, UTC));
    }

    @Test
    public void windowCanRunPastMidnight() {
        long now = at(9, 0);
        assertEquals(at(0, 30) + 24L * 60 * 60 * 1000, PickupWindow.expiresAt("23:30", now, UTC));
    }

    @Test
    public void unreadableTimesNeverExpire() {
        assertEquals(0, PickupWindow.expiresAt(null, at(9, 0), UTC));
        assertEquals(0, PickupWindow.expiresAt("N/A", at(9, 0), UTC));
        assertEquals(0, PickupWindow.expiresAt("25:00", at(9, 0), UTC));
        assertEquals(0, PickupWindow.expiresAt("12:75", at(9, 0), UTC));
    }

    @Test
    public void isExpiredOnlyAfterTheDeadline() {
        long deadline = at(19, 0);
        assertFalse(PickupWindow.isExpired(deadline, at(18, 59)));
        assertFalse(PickupWindow.isExpired(deadline, deadline));
        assertTrue(PickupWindow.isExpired(deadline, deadline + 1));
    }

    @Test
    public void noRecordedExpiryIsNeverExpired() {
        assertFalse(PickupWindow.isExpired(0, at(23, 59)));
    }
}
