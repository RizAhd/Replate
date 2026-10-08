package com.replate.app.utils;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.replate.app.R;

import java.util.Locale;

/**
 * Categories are managed by the admin (Categories page), so the app only knows their names. This
 * picks one of the bundled icons that fits the name, so a new category still looks right.
 */
public final class CategoryIcons {

    private CategoryIcons() {}

    // Picks an icon that fits the category name.
    @DrawableRes
    public static int forName(@Nullable String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (containsAny(n, "bakery", "bread", "pastry", "bun", "short eat")) return R.drawable.ic_category_bakery;
        if (containsAny(n, "drink", "juice", "beverage", "tea", "coffee")) return R.drawable.ic_category_drinks;
        if (containsAny(n, "grocer", "fruit", "vegetable", "produce")) return R.drawable.ic_category_groceries;
        if (containsAny(n, "dessert", "cake", "sweet", "ice cream")) return R.drawable.ic_category_desserts;
        return R.drawable.ic_category_meals;
    }

    // True when the text has any of the words.
    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
