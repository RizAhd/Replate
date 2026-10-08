package com.replate.app.utils;

import static org.junit.Assert.assertEquals;

import com.replate.app.R;

import org.junit.Test;

// Checks that each category name gets the right icon.
public class CategoryIconsTest {

    @Test
    public void knownNamesGetTheirOwnIcon() {
        assertEquals(R.drawable.ic_category_bakery, CategoryIcons.forName("Bakery"));
        assertEquals(R.drawable.ic_category_drinks, CategoryIcons.forName("Fresh Juice"));
        assertEquals(R.drawable.ic_category_groceries, CategoryIcons.forName("Groceries"));
        assertEquals(R.drawable.ic_category_desserts, CategoryIcons.forName("Desserts"));
    }

    @Test
    public void caseDoesNotMatter() {
        assertEquals(R.drawable.ic_category_bakery, CategoryIcons.forName("BAKERY ITEMS"));
    }

    @Test
    public void everythingElseGetsTheMealIcon() {
        assertEquals(R.drawable.ic_category_meals, CategoryIcons.forName("Rice & Curry"));
        assertEquals(R.drawable.ic_category_meals, CategoryIcons.forName("Kottu"));
        assertEquals(R.drawable.ic_category_meals, CategoryIcons.forName(""));
        assertEquals(R.drawable.ic_category_meals, CategoryIcons.forName(null));
    }
}
