package com.replate.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Reports when the device goes offline or comes back online, using the modern
 * ConnectivityManager callback (the old CONNECTIVITY_ACTION broadcast is deprecated and not
 * delivered reliably). Listener calls always arrive on the main thread, once per change.
 */
public final class NetworkMonitor {

    public interface Listener {
        void onConnectivityChanged(boolean online);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private ConnectivityManager.NetworkCallback callback;
    @Nullable
    private Listener listener;
    private boolean lastOnline = true;

    public NetworkMonitor(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void start(@NonNull Listener newListener) {
        stop();
        listener = newListener;
        lastOnline = NetworkUtils.isOnline(appContext);

        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }

        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                publish(true);
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Another network (for example mobile data) may already have taken over
                publish(NetworkUtils.isOnline(appContext));
            }

            @Override
            public void onUnavailable() {
                publish(false);
            }
        };

        try {
            cm.registerDefaultNetworkCallback(callback);
        } catch (RuntimeException e) {
            // Some devices throw when too many callbacks are registered; monitoring is optional
            callback = null;
        }
    }

    public void stop() {
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (RuntimeException ignored) {
                // already unregistered
            }
        }
        callback = null;
        listener = null;
    }

    private void publish(boolean online) {
        mainHandler.post(() -> {
            Listener current = listener;
            if (current == null || online == lastOnline) {
                return;
            }
            lastOnline = online;
            current.onConnectivityChanged(online);
        });
    }
}
