package com.replate.app;

import android.app.Application;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;

/**
 * App-wide setup that has to happen before any screen touches Firebase.
 */
public class ReplateApplication extends Application {

    private static final String TAG = "ReplateApplication";

    // Runs once at app start: theme, notification channel, Firebase setup.
    @Override
    public void onCreate() {
        super.onCreate();

        // The app is designed for the light theme only; say so explicitly so a phone in dark mode
        // doesn't half-switch some screens.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Create the notification channel now: a push that arrives while the app is closed is shown by the
        // system, and without the channel it would land in "Miscellaneous"
        com.replate.app.services.MyFirebaseMessagingService.createChannel(this);

        try {
            // Keep a local copy of the data Firebase has already delivered, so screens still show
            // content offline and writes made while the connection drops are sent when it returns.
            // Must be called before the first use of FirebaseDatabase, and only once.
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            database.setPersistenceEnabled(true);

            // Small, rarely changing lists: always keep them fresh and available offline
            database.getReference("banners").keepSynced(true);
        } catch (DatabaseException e) {
            // Already initialised elsewhere; the app still works, just without this extra
            Log.w(TAG, "Could not enable Firebase offline persistence", e);
        }
    }
}
