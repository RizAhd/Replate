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
