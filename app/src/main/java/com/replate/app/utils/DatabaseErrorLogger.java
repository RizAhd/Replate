package com.replate.app.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.database.DatabaseError;

/**
 * One place to record Realtime Database listener failures that the UI otherwise ignores.
 * "Permission denied" is also sent to Crashlytics as a non-fatal issue, because after the security
 * rules change it is the clearest sign that a screen is asking for data the rules do not allow.
 */
public final class DatabaseErrorLogger {

    private DatabaseErrorLogger() {}

    // Writes the database error to the log.
    public static void log(@NonNull String tag, @NonNull String where, @NonNull DatabaseError error) {
        Log.w(tag, where + " cancelled: " + error.getMessage() + " (code " + error.getCode() + ")");
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.log(tag + ": " + where + " -> " + error.getMessage());
            if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                crashlytics.recordException(error.toException());
            }
        } catch (Throwable ignored) {
            // Crash reporting must never become a reason for the app to fail
        }
    }
}
