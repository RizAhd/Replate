package com.replate.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;

import com.replate.app.utils.NetworkUtils;

/**
 * Listens for connectivity broadcasts and reports a change only when the device really goes
 * offline or comes back online (the system can send several broadcasts for one change).
 * <p>
 * Register it from an Activity with {@code new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)}
 * in onStart and unregister it in onStop. Context-registered receivers still get this broadcast
 * on current Android versions even though a manifest-declared one would not.
 */
@SuppressWarnings("deprecation")
public class NetworkChangeReceiver extends BroadcastReceiver {

    public interface Listener {
        void onConnectivityChanged(boolean online);
    }

    private final Listener listener;
    private boolean lastOnline;

    public NetworkChangeReceiver(@NonNull Context context, @NonNull Listener listener) {
        this.listener = listener;
        this.lastOnline = NetworkUtils.isOnline(context);
    }

    // Tells the listener only when online or offline really changes.
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            return;
        }
        boolean online = NetworkUtils.isOnline(context);
        if (online == lastOnline) {
            return;
        }
        lastOnline = online;
        listener.onConnectivityChanged(online);
    }
}
