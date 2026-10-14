package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

// Checks the "Collect within 42 min" / "Pickup starts at 18:00" / "Code expired" rule (same cases as the web tests).
public class PickupCountdownTest {

    private static final TimeZone COLOMBO = TimeZone.getTimeZone("Asia/Colombo");

    // 14 October 2026 at hh:mm in Colombo
    private static long clock(int hour, int minute) {
        Calendar c = Calendar.getInstance(COLOMBO);
        c.clear();
        c.set(2026, Calendar.OCTOBER, 14, hour, minute, 0);
        return c.getTimeInMillis();
    }

    private static PickupCountdown.Result open(String pickupTime, long expiresAt, long now) {
        return PickupCountdown.of(false, true, pickupTime, expiresAt, now, COLOMBO);
    }

    @Test
    public void beforeTheSlotItSaysWhenPickupStarts() {
        PickupCountdown.Result r = open("18:00", clock(19, 0), clock(17, 30));
        assertEquals(PickupCountdown.State.UPCOMING, r.state);
        assertEquals("18:00", PickupCountdown.clock(r.startsAt, COLOMBO));
    }

    @Test
    public void duringTheSlotItCountsWholeMinutesRoundedUp() {
        PickupCountdown.Result r = open("18:00", clock(19, 0), clock(18, 18));
        assertEquals(PickupCountdown.State.COLLECT, r.state);
        assertEquals(42, r.minutesLeft);
        assertEquals(42, open("18:00", clock(19, 0), clock(18, 18) + 30_000).minutesLeft);
    }

    @Test
    public void underFifteenMinutesItIsUrgent() {
        assertEquals(PickupCountdown.State.COLLECT, open("18:00", clock(19, 0), clock(18, 45)).state);
        assertEquals(PickupCountdown.State.URGENT, open("18:00", clock(19, 0), clock(18, 46)).state);
        PickupCountdown.Result late = open("18:00", clock(19, 0), clock(18, 50));
        assertEquals(PickupCountdown.State.URGENT, late.state);
        assertEquals(10, late.minutesLeft);
    }

    @Test
    public void theLastMomentStillCountsThenTheCodeExpires() {
        assertEquals(1, open("18:00", clock(19, 0), clock(19, 0)).minutesLeft);
        assertEquals(PickupCountdown.State.EXPIRED, open("18:00", clock(19, 0), clock(19, 0) + 1).state);
    }

    @Test
    public void olderOrdersWithARangeUseTheFirstTime() {
        assertEquals(PickupCountdown.State.UPCOMING, open("18:00 - 19:00", clock(19, 0), clock(17, 0)).state);
        assertEquals(30, open("18:00 - 19:00", clock(19, 0), clock(18, 30)).minutesLeft);
    }

    @Test
    public void anUnreadableTimeStartsOneHourBeforeExpiry() {
        PickupCountdown.Result before = open("soon", clock(19, 0), clock(17, 59));
        assertEquals(PickupCountdown.State.UPCOMING, before.state);
        assertEquals("18:00", PickupCountdown.clock(before.startsAt, COLOMBO));
        assertEquals(50, open(null, clock(19, 0), clock(18, 10)).minutesLeft);
        assertEquals(clock(18, 0), PickupCountdown.startsAt("25:00", clock(19, 0), COLOMBO));
    }

    @Test
    public void aDishWindowThatEndsEarlierShortensTheSlot() {
        PickupCountdown.Result before = open("19:00", clock(19, 45), clock(18, 50));
        assertEquals(PickupCountdown.State.UPCOMING, before.state);
        assertEquals("19:00", PickupCountdown.clock(before.startsAt, COLOMBO));
        assertEquals(40, open("19:00", clock(19, 45), clock(19, 5)).minutesLeft);
    }

    @Test
    public void aLateSlotRunsPastMidnight() {
        long afterMidnight = clock(19, 0) + (5 * 60 + 30) * 60_000L; // 00:30 on the 15th
        assertEquals(PickupCountdown.State.UPCOMING, open("23:30", afterMidnight, clock(23, 0)).state);
        assertEquals(40, open("23:30", afterMidnight, clock(23, 50)).minutesLeft);
    }

    @Test
    public void noCountdownForDeliveriesFinishedOrdersOrWithoutAnExpiry() {
        assertNull(PickupCountdown.of(true, true, "18:00", clock(19, 0), clock(18, 0), COLOMBO));
        assertNull(PickupCountdown.of(false, false, "18:00", clock(19, 0), clock(18, 0), COLOMBO));
        assertNull(PickupCountdown.of(false, true, "18:00", 0, clock(18, 0), COLOMBO));
    }

    @Test
    public void refreshesWhenTheMinuteCountDrops() {
        long expires = clock(19, 0);
        long now = clock(18, 18) + 30_000; // 41.5 min left, shown as 42
        PickupCountdown.Result r = open("18:00", expires, now);
        assertEquals(30_001, PickupCountdown.millisUntilChange(r, now));
        // Exactly 42 min left: the next change is a full minute away
        assertEquals(60_000, PickupCountdown.millisUntilChange(open("18:00", expires, clock(18, 18)), clock(18, 18)));
        // Waiting for the slot to open: at most a minute
        assertEquals(60_000, PickupCountdown.millisUntilChange(open("18:00", expires, clock(17, 0)), clock(17, 0)));
        // Nothing changes after the code expired
        assertEquals(-1, PickupCountdown.millisUntilChange(open("18:00", expires, expires + 1), expires + 1));
        assertEquals(-1, PickupCountdown.millisUntilChange(null, now));
    }
}
