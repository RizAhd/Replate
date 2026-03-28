package com.replate.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.replate.app.utils.ToastUtils;

/**
 * BroadcastReceiver for network connectivity changes
 * Requirement: Receivers and Integration
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    private OnNetworkChangeListener listener;

    public interface OnNetworkChangeListener {
        void onNetworkChanged(boolean isConnected);
    }

    public void setOnNetworkChangeListener(OnNetworkChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isConnected = isNetworkAvailable(context);

        if (listener != null) {
            listener.onNetworkChanged(isConnected);
        }

        if (!isConnected) {
            ToastUtils.showCustomToast(context, "📡 No internet connection. Some features may not work.");
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
}
