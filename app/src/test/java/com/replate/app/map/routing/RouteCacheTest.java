package com.replate.app.map.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Collections;

// Checks that saved routes are reused for a while and then expire.
public class RouteCacheTest {

    private static RouteResult route(String distance) {
        return new RouteResult(Collections.emptyList(), distance, "10 mins");
    }

    @Test
    public void returnsStoredRouteWithinTtl() {
        RouteCache cache = new RouteCache(1000);
        RouteResult stored = route("2 km");
        cache.put("k", stored, 0);

        assertSame(stored, cache.get("k", 999));
    }

    @Test
    public void expiresRouteAfterTtl() {
        RouteCache cache = new RouteCache(1000);
        cache.put("k", route("2 km"), 0);

        assertNull(cache.get("k", 1001));
        // and it stays gone
        assertNull(cache.get("k", 0));
    }

    @Test
    public void unknownKeyReturnsNull() {
        assertNull(new RouteCache().get("missing", 0));
    }

    @Test
    public void keyIgnoresSmallGpsDrift() {
        // ~20 m apart, same destination
        String a = RouteCache.key(6.92710, 79.86120, 6.93000, 79.85000);
        String b = RouteCache.key(6.92712, 79.86118, 6.93000, 79.85000);
        assertEquals(a, b);
    }

    @Test
    public void keyDiffersForDifferentDestinations() {
        String a = RouteCache.key(6.9271, 79.8612, 6.9300, 79.8500);
        String b = RouteCache.key(6.9271, 79.8612, 6.9400, 79.8500);
        assertNotEquals(a, b);
    }

    @Test
    public void keyIsNotLocaleDependent() {
        assertNotNull(RouteCache.key(6.9271, 79.8612, 6.93, 79.85));
        // decimal point, never a comma
        assertEquals(-1, RouteCache.key(6.9271, 79.8612, 6.93, 79.85).indexOf("6,9"));
    }
}
