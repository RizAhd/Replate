package com.replate.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.Nullable;

// Checks whether the phone is online.
public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * True when the device has a network that can actually reach the internet. Returns true if the
     * state can't be determined, so a quirky device never blocks the user outright.
     */
    public static boolean isOnline(@Nullable Context context) {
        if (context == null) {
            return true;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true;
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException e) {
            return true;
        }
    }
}
