package com.replate.app.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderImpactTest {

    private static Map<String, Object> line(Object quantity) {
        Map<String, Object> line = new HashMap<>();
        if (quantity != null) {
            line.put("quantity", quantity);
        }
        return line;
    }

    @Test
    public void addsUpQuantitiesInAList() {
        List<Object> items = new ArrayList<>();
        items.add(line(2L));
        items.add(line(3));
        assertEquals(5, OrderImpact.mealCount(items));
    }

    @Test
    public void readsItemsStoredAsAMap() {
        Map<String, Object> items = new HashMap<>();
        items.put("0", line(1));
        items.put("1", line(4));
        assertEquals(5, OrderImpact.mealCount(items));
    }

    @Test
    public void aLineWithoutAQuantityCountsAsOne() {
        List<Object> items = new ArrayList<>();
        items.add(line(null));
        items.add(line(0));
        assertEquals(2, OrderImpact.mealCount(items));
    }

    @Test
    public void nothingUsableCountsAsZero() {
        assertEquals(0, OrderImpact.mealCount(null));
        assertEquals(0, OrderImpact.mealCount("not items"));
        List<Object> junk = new ArrayList<>();
        junk.add("x");
        assertEquals(0, OrderImpact.mealCount(junk));
    }
}
