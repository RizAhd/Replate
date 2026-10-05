package com.replate.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import androidx.annotation.NonNull;

/**
 * Fires when the user switches location (GPS) on or off from the quick settings or the phone
 * settings, so the Map screen can update its status banner at once instead of on the next resume.
 */
public class GpsStateReceiver extends BroadcastReceiver {

    private final Runnable onChanged;

    public GpsStateReceiver(@NonNull Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && LocationManager.PROVIDERS_CHANGED_ACTION.equals(intent.getAction())) {
            onChanged.run();
        }
    }
}
