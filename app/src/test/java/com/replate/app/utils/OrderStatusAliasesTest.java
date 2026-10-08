package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// Checks that other status names map to the app's own statuses.
public class OrderStatusAliasesTest {

    @Test
    public void aliasesMapToTheAppsOwnStatuses() {
        assertEquals("confirmed", OrderStatusAliases.canonical("accepted"));
        assertEquals("ready", OrderStatusAliases.canonical("ready_for_pickup"));
        assertEquals("cancelled", OrderStatusAliases.canonical("rejected"));
    }

    @Test
    public void ownStatusesAreKeptAndReadLeniently() {
        assertEquals("preparing", OrderStatusAliases.canonical("preparing"));
        assertEquals("ready", OrderStatusAliases.canonical("  Ready "));
        assertEquals("something_new", OrderStatusAliases.canonical("something_new"));
    }

    @Test
    public void missingStatusStaysMissing() {
        assertNull(OrderStatusAliases.canonical(null));
    }
}
