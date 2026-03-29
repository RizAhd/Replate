package com.replate.app.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.replate.app.BuildConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Background worker that periodically syncs user activity data.
 */
public class OrderSyncWorker extends Worker {

    private static final String TAG = "OrderSyncWorker";

    public OrderSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Background sync started");

        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                // Update last active timestamp
                Map<String, Object> updates = new HashMap<>();
                updates.put("lastActive", System.currentTimeMillis());

                FirebaseDatabase.getInstance().getReference()
                        .child("users")
                        .child(user.getUid())
                        .updateChildren(updates);

                if (BuildConfig.DEBUG) Log.d(TAG, "User activity synced successfully");
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed: " + e.getMessage());
            return Result.retry();
        }
    }
}

