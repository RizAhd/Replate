package com.replate.app.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// Checks how a food item reads prices, stock, status, location and search.
public class FoodItemTest {

    @Test
    public void pricesAcceptNumbersAndNumericStrings() {
        FoodItem item = new FoodItem();
        item.setDiscountedPrice(350.5);
        item.setOriginalPrice("700");

        assertEquals(350.5, item.getDiscountedPriceValue(), 0.0001);
        assertEquals(700.0, item.getOriginalPriceValue(), 0.0001);
    }

    @Test
    public void unreadablePricesBecomeZeroInsteadOfCrashing() {
        FoodItem item = new FoodItem();
        item.setDiscountedPrice("free!");
        item.setOriginalPrice(null);

        assertEquals(0.0, item.getDiscountedPriceValue(), 0.0);
        assertEquals(0.0, item.getOriginalPriceValue(), 0.0);
    }

    @Test
    public void quantityParsesFromNumberOrString() {
        FoodItem item = new FoodItem();
        item.setQuantity(4L);
        assertEquals(4, item.getQuantityValue());

        item.setQuantity("7");
        assertEquals(7, item.getQuantityValue());

        item.setQuantity("many");
        assertEquals(0, item.getQuantityValue());
    }

    @Test
    public void stockRules() {
        FoodItem item = new FoodItem();
        assertTrue("no quantity recorded means unknown, so available", item.hasStock());

        item.setQuantity(0);
        assertFalse(item.hasStock());

        item.setQuantity(2);
        assertTrue(item.hasStock());
    }

    @Test
    public void lowStockOnlyWhenAFewAreLeft() {
        FoodItem item = new FoodItem();
        assertFalse("unknown quantity is not low stock", item.isLowStock());

        item.setQuantity(0);
        assertFalse("sold out is not low stock, it is sold out", item.isLowStock());

        item.setQuantity(1);
        assertTrue(item.isLowStock());
        item.setQuantity(FoodItem.LOW_STOCK_MAX);
        assertTrue(item.isLowStock());
        item.setQuantity(FoodItem.LOW_STOCK_MAX + 1);
        assertFalse(item.isLowStock());
    }

    @Test
    public void discountPercentFallsBackToTheAlternativeField() {
        FoodItem item = new FoodItem();
        assertEquals(0, item.getDiscountPercentValue());

        item.setDiscountPercentage(25);
        assertEquals(25, item.getDiscountPercentValue());

        item.setDiscountPercent("40");
        assertEquals(40, item.getDiscountPercentValue());
    }

    @Test
    public void explicitAvailableStatusWinsOverFlags() {
        FoodItem item = new FoodItem();
        item.setStatus("available");
        item.setIsAvailable(false);

        assertTrue(item.isAvailableValue());
    }

    @Test
    public void soldOutItemsAreNotListable() {
        FoodItem item = new FoodItem();
        item.setStatus("sold_out");
        item.setIsAvailable(false);
        item.setQuantity(0);

        assertFalse(item.isListable());
    }

    @Test
    public void availableItemWithStockIsListable() {
        FoodItem item = new FoodItem();
        item.setStatus("available");
        item.setQuantity(3);

        assertTrue(item.isListable());
    }

    @Test
    public void legacyItemWithoutAnyFlagsIsListable() {
        assertTrue(new FoodItem().isListable());
    }

    @Test
    public void coordinatesFallBackToTheHotelLocation() {
        FoodItem item = new FoodItem();
        assertFalse(item.hasValidCoordinates());

        item.setHotelLatitude(6.9271);
        item.setHotelLongitude("79.8612");

        assertTrue(item.hasValidCoordinates());
        assertEquals(6.9271, item.getLatitudeValue(), 0.0001);
        assertEquals(79.8612, item.getLongitudeValue(), 0.0001);
    }

    @Test
    public void categoryMatchingIgnoresCaseAndTreatsAllAsEverything() {
        FoodItem item = new FoodItem();
        item.setCategory("Bakery");

        assertTrue(item.matchesCategory("bakery"));
        assertTrue(item.matchesCategory("All"));
        assertTrue(item.matchesCategory(""));
        assertTrue(item.matchesCategory(null));
        assertFalse(item.matchesCategory("Drinks"));
    }

    @Test
    public void searchMatchesNameHotelAndDescription() {
        FoodItem item = new FoodItem();
        item.setFoodName("Chicken Rice");
        item.setHotelName("Galle Face Hotel");
        item.setDescription("Spicy and fresh");

        assertTrue(item.matchesQuery("chicken"));
        assertTrue(item.matchesQuery("GALLE"));
        assertTrue(item.matchesQuery("spicy"));
        assertTrue(item.matchesQuery("  ".trim()));
        assertFalse(item.matchesQuery("pizza"));
    }
}
