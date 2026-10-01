package com.replate.app.map.routing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived in-memory cache of fetched routes. Each Directions request is a billable API
 * call, so asking for the same trip again within a few minutes reuses the earlier answer.
 */
public final class RouteCache {

    static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_ENTRIES = 30;

    private static final class Entry {
        final RouteResult result;
        final long storedAt;

        Entry(RouteResult result, long storedAt) {
            this.result = result;
            this.storedAt = storedAt;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlMs;

    public RouteCache() {
        this(DEFAULT_TTL_MS);
    }

    RouteCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /**
     * Origin is rounded to 3 decimals (about 110 m) so small GPS drift still hits the cache;
     * the destination (a hotel) is matched more precisely.
     */
    @NonNull
    public static String key(double originLat, double originLng, double destLat, double destLng) {
        return String.format(Locale.US, "%.3f,%.3f>%.5f,%.5f", originLat, originLng, destLat, destLng);
    }

    @Nullable
    public RouteResult get(@NonNull String key, long now) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (now - entry.storedAt > ttlMs) {
            entries.remove(key);
            return null;
        }
        return entry.result;
    }

    public void put(@NonNull String key, @NonNull RouteResult result, long now) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        entries.put(key, new Entry(result, now));
    }
}
