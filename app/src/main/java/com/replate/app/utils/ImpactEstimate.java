package com.replate.app.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * What a customer's rescued meals add up to. These are estimates, not measurements, and the same
 * numbers as the web panels ({@code ReplateSchema.impactFromOrders} in web-panels/js/data-schema.js):
 * meals = dishes in completed and delivered orders, food = meals x {@link #MEAL_KG},
 * CO2e = food x {@link #CO2E_PER_KG}. docs/DESIGN.md says where the two factors come from.
 */
public final class ImpactEstimate {

    /** About how much food one rescued meal is, in kg. */
    public static final double MEAL_KG = 0.4;
    /** About how much CO2e is avoided for each kg of food not wasted, in kg. */
    public static final double CO2E_PER_KG = 2.5;

    public final int meals;
    public final double foodKg;
    public final double co2eKg;

    private ImpactEstimate(int meals) {
        this.meals = Math.max(0, meals);
        this.foodKg = this.meals * MEAL_KG;
        this.co2eKg = this.foodKg * CO2E_PER_KG;
    }

    public static ImpactEstimate forMeals(int meals) {
        return new ImpactEstimate(meals);
    }

    /** "11.6", "29", "1,160": at most one decimal, Latin digits (the unit is added by the screen). */
    public static String formatKg(double kg) {
        DecimalFormat df = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP);
        double safe = Double.isNaN(kg) || Double.isInfinite(kg) || kg < 0 ? 0 : kg;
        return df.format(safe);
    }
}
