package com.replate.app.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// Checks the impact estimate: the same factors and numbers as the web panels.
public class ImpactEstimateTest {

    private static final double EPS = 1e-9;

    @Test
    public void theFactorsAreTheDocumentedEstimates() {
        assertEquals(0.4, ImpactEstimate.MEAL_KG, EPS);
        assertEquals(2.5, ImpactEstimate.CO2E_PER_KG, EPS);
    }

    @Test
    public void foodAndCo2eFollowFromTheMeals() {
        ImpactEstimate impact = ImpactEstimate.forMeals(29);
        assertEquals(29, impact.meals);
        assertEquals(11.6, impact.foodKg, EPS);
        assertEquals(29.0, impact.co2eKg, EPS);
    }

    @Test
    public void noMealsIsNothing() {
        ImpactEstimate impact = ImpactEstimate.forMeals(0);
        assertEquals(0, impact.meals);
        assertEquals(0.0, impact.foodKg + impact.co2eKg, EPS);
        assertEquals(0, ImpactEstimate.forMeals(-3).meals);
    }

    @Test
    public void kilogramsShowAtMostOneDecimal() {
        assertEquals("11.6", ImpactEstimate.formatKg(29 * 0.4));
        assertEquals("29", ImpactEstimate.formatKg(29 * 0.4 * 2.5));
        assertEquals("1,160", ImpactEstimate.formatKg(1160));
        assertEquals("0.4", ImpactEstimate.formatKg(0.4));
        assertEquals("0", ImpactEstimate.formatKg(Double.NaN));
    }
}
