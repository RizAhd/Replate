package com.replate.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.replate.app.R;

import org.junit.Test;

public class OrderTimelineTest {

    @Test
    public void everyPendingStateIsJustPlaced() {
        assertEquals(0, OrderTimeline.currentStep("pending"));
        assertEquals(0, OrderTimeline.currentStep("pending_pickup"));
        assertEquals(0, OrderTimeline.currentStep("pending_delivery"));
        assertEquals(0, OrderTimeline.currentStep(null));
        assertEquals(0, OrderTimeline.currentStep("something_new"));
    }

    @Test
    public void statusesMoveAlongTheTimeline() {
        assertEquals(1, OrderTimeline.currentStep("confirmed"));
        assertEquals(2, OrderTimeline.currentStep("preparing"));
        assertEquals(3, OrderTimeline.currentStep("ready"));
        assertEquals(3, OrderTimeline.currentStep("out_for_delivery"));
        assertEquals(4, OrderTimeline.currentStep("completed"));
        assertEquals(4, OrderTimeline.currentStep("delivered"));
    }

    @Test
    public void statusIsReadLeniently() {
        assertEquals(2, OrderTimeline.currentStep("  Preparing "));
    }

    @Test
    public void cancelledOrdersHaveNoTimeline() {
        assertEquals(-1, OrderTimeline.currentStep("cancelled"));
    }

    @Test
    public void pickupAndDeliveryDifferOnlyAtTheEnd() {
        for (int step = 0; step < 3; step++) {
            assertEquals(OrderTimeline.labelFor(step, false), OrderTimeline.labelFor(step, true));
        }
        assertEquals(R.string.timeline_ready, OrderTimeline.labelFor(3, false));
        assertEquals(R.string.timeline_on_the_way, OrderTimeline.labelFor(3, true));
        assertNotEquals(OrderTimeline.labelFor(4, false), OrderTimeline.labelFor(4, true));
    }

    @Test
    public void outOfRangeStepsAreClamped() {
        assertEquals(OrderTimeline.labelFor(0, false), OrderTimeline.labelFor(-3, false));
        assertEquals(OrderTimeline.labelFor(4, false), OrderTimeline.labelFor(99, false));
    }
}
