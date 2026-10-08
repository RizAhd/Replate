package com.replate.app.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;

/**
 * Wraps DataSnapshot.getValue(Class) so one malformed record (for example a number written
 * into a String field by the web panel) is skipped instead of throwing DatabaseException
 * out of a listener and crashing the app.
 */
public final class SafeSnapshot {

    private static final String TAG = "SafeSnapshot";

    private SafeSnapshot() {}

    /** Text from any scalar (a phone number saved as a number is still read as text); null when empty or not a scalar. */
    @Nullable
    public static String string(@Nullable DataSnapshot snapshot) {
        return stringOf(snapshot == null ? null : snapshot.getValue());
    }

    // Turns any plain value into text, or null when empty.
    @Nullable
    public static String stringOf(@Nullable Object value) {
        if (value == null || value instanceof java.util.Map || value instanceof java.util.List) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** True/false from a real boolean or the words "true"/"false"; {@code fallback} for anything else. */
    public static boolean booleanOf(@Nullable Object value, boolean fallback) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String t = ((String) value).trim();
            if (t.equalsIgnoreCase("true")) return true;
            if (t.equalsIgnoreCase("false")) return false;
        }
        return fallback;
    }

    // Reads a record as the given class, or null if it is malformed.
    @Nullable
    public static <T> T getValue(@Nullable DataSnapshot snapshot, Class<T> type) {
        if (snapshot == null) {
            return null;
        }
        try {
            return snapshot.getValue(type);
        } catch (Exception e) {
            Log.w(TAG, "Skipping malformed " + type.getSimpleName() + " at " + snapshot.getKey(), e);
            return null;
        }
    }
}
