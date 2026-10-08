package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

import org.junit.Test;

/** The dish pickup window rules: what time a customer may choose and when the code stops working. */
public class PickupSlotTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static long at(int hour, int minute) {
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(2026, Calendar.OCTOBER, 5, hour, minute, 0);
        return c.getTimeInMillis();
    }

    private static final PickupSlot.Window EVENING = PickupSlot.windowOf("18:00", "20:00");

    @Test
    public void timesAreParsedAndFormatted() {
        assertEquals(18 * 60 + 5, PickupSlot.parse("18:05"));
        assertEquals(-1, PickupSlot.parse(null));
        assertEquals(-1, PickupSlot.parse("N/A"));
        assertEquals(-1, PickupSlot.parse("24:00"));
        assertEquals(-1, PickupSlot.parse("12:60"));
        assertEquals("07:05", PickupSlot.format(7 * 60 + 5));
    }

    @Test
    public void aDishWindowIsReadFromItsTimes() {
        assertEquals(18 * 60, EVENING.start);
        assertEquals(20 * 60, EVENING.end);
        assertEquals("18:00 - 20:00", EVENING.label());
    }

    @Test
    public void missingTimesMeanNoLimit() {
        assertTrue(PickupSlot.windowOf(null, null).isUnlimited());
        assertTrue(PickupSlot.windowOf("", "").isUnlimited());
        assertTrue(PickupSlot.windowOf("xx", "yy").isUnlimited());
    }

    @Test
    public void aWindowThatRunsPastMidnightLastsUntilTheEndOfToday() {
        PickupSlot.Window night = PickupSlot.windowOf("22:00", "02:00");
        assertEquals(22 * 60, night.start);
        assertEquals(PickupSlot.DAY_MINUTES, night.end);
        assertEquals(PickupSlot.Verdict.OK, PickupSlot.check("23:30", night, 21 * 60));
    }

    @Test
    public void onlyTheStartOrOnlyTheEndStillLimitsTheDay() {
        PickupSlot.Window fromNoon = PickupSlot.windowOf("12:00", null);
        assertEquals(PickupSlot.Verdict.BEFORE_WINDOW, PickupSlot.check("11:00", fromNoon, 9 * 60));
        PickupSlot.Window untilNoon = PickupSlot.windowOf(null, "12:00");
        assertEquals(PickupSlot.Verdict.AFTER_WINDOW, PickupSlot.check("13:00", untilNoon, 9 * 60));
    }

    @Test
    public void aTimeInsideTheWindowIsAccepted() {
        assertEquals(PickupSlot.Verdict.OK, PickupSlot.check("18:00", EVENING, 9 * 60));
        assertEquals(PickupSlot.Verdict.OK, PickupSlot.check("19:59", EVENING, 9 * 60));
    }

    @Test
    public void timesOutsideTheWindowAreRefused() {
        assertEquals(PickupSlot.Verdict.BEFORE_WINDOW, PickupSlot.check("09:00", EVENING, 8 * 60));
        assertEquals(PickupSlot.Verdict.AFTER_WINDOW, PickupSlot.check("20:00", EVENING, 9 * 60));
        assertEquals(PickupSlot.Verdict.AFTER_WINDOW, PickupSlot.check("23:00", EVENING, 9 * 60));
    }

    @Test
    public void aTimeInThePastIsRefusedEvenInsideTheWindow() {
        assertEquals(PickupSlot.Verdict.IN_THE_PAST, PickupSlot.check("18:10", EVENING, 18 * 60 + 30));
    }

    @Test
    public void afterTheWindowClosesNothingCanBeChosenToday() {
        assertEquals(PickupSlot.Verdict.CLOSED_TODAY, PickupSlot.check("19:00", EVENING, 20 * 60 + 5));
        assertTrue(PickupSlot.isClosed(EVENING, 20 * 60));
        assertFalse(PickupSlot.isClosed(EVENING, 19 * 60 + 59));
        assertFalse(PickupSlot.isClosed(PickupSlot.UNLIMITED, 23 * 60 + 59));
    }

    @Test
    public void theEarliestChoiceIsNowOrWhenTheWindowOpens() {
        assertEquals(18 * 60, PickupSlot.earliest(EVENING, 9 * 60));
        assertEquals(18 * 60 + 40, PickupSlot.earliest(EVENING, 18 * 60 + 40));
    }

    @Test
    public void severalDishesNeedTheOverlapOfTheirWindows() {
        PickupSlot.Window a = PickupSlot.windowOf("17:00", "21:00");
        PickupSlot.Window b = PickupSlot.windowOf("18:30", "22:00");
        PickupSlot.Window both = PickupSlot.overlap(Arrays.asList(a, b));
        assertEquals("18:30 - 21:00", both.label());
    }

    @Test
    public void dishesWithNoCommonTimeCannotBeOrderedTogether() {
        PickupSlot.Window morning = PickupSlot.windowOf("08:00", "10:00");
        PickupSlot.Window both = PickupSlot.overlap(Arrays.asList(morning, EVENING));
        assertTrue(both.isEmpty());
        assertEquals(PickupSlot.Verdict.NO_COMMON_WINDOW, PickupSlot.check("18:30", both, 7 * 60));
    }

    @Test
    public void noDishesMeansNoLimit() {
        assertTrue(PickupSlot.overlap(Collections.<PickupSlot.Window>emptyList()).isUnlimited());
    }

    @Test
    public void garbageInputIsLeftToTheRequiredCheck() {
        assertEquals(PickupSlot.Verdict.OK, PickupSlot.check("", EVENING, 9 * 60));
        assertEquals(PickupSlot.Verdict.OK, PickupSlot.check(null, EVENING, 9 * 60));
    }

    @Test
    public void theCodeStopsOneHourAfterTheChosenTimeInsideTheWindow() {
        assertEquals(at(19, 0), PickupSlot.expiresAt("18:00", EVENING, at(9, 0), UTC));
    }

    @Test
    public void theCodeNeverOutlivesTheWindowByMoreThanTheGrace() {
        // chosen 19:50 would run to 20:50, but the window closes at 20:00 (+15 min grace)
        assertEquals(at(20, 15), PickupSlot.expiresAt("19:50", EVENING, at(9, 0), UTC));
    }

    @Test
    public void withoutALimitTheOldOneHourRuleApplies() {
        assertEquals(at(19, 0), PickupSlot.expiresAt("18:00", PickupSlot.UNLIMITED, at(9, 0), UTC));
        assertEquals(at(19, 0), PickupSlot.expiresAt("18:00", null, at(9, 0), UTC));
    }

    @Test
    public void anUnreadableChosenTimeNeverExpires() {
        assertEquals(0, PickupSlot.expiresAt("N/A", EVENING, at(9, 0), UTC));
    }

    @Test
    public void theCartWindowIsTheOverlapOfTheLiveDishWindows() {
        CartCheck.Line rice = new CartCheck.Line("Rice", 1, 100,
                new CartCheck.Live(true, 5, 100, PickupSlot.windowOf("17:00", "21:00")));
        CartCheck.Line soup = new CartCheck.Line("Soup", 1, 100,
                new CartCheck.Live(true, 5, 100, PickupSlot.windowOf("18:30", "22:00")));
        CartCheck.Line gone = new CartCheck.Line("Gone", 1, 100, null);
        assertEquals("18:30 - 21:00", CartCheck.pickupWindow(Arrays.asList(rice, soup, gone)).label());
    }

    @Test
    public void aLiveDishWithoutTimesDoesNotLimitTheCart() {
        CartCheck.Line open = new CartCheck.Line("Cake", 1, 100, new CartCheck.Live(true, 5, 100));
        assertTrue(CartCheck.pickupWindow(Collections.singletonList(open)).isUnlimited());
    }
}
