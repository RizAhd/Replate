package com.replate.app.services;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.replate.app.BuildConfig;
import com.replate.app.CheckoutActivity;
import com.replate.app.R;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.helpers.FoodItemCacheManager;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.FoodItem;
import com.replate.app.notifications.NotificationCategories;
import com.replate.app.utils.NotificationPrefs;
import com.replate.app.utils.PendingPayment;
import com.replate.app.utils.PendingPaymentStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Background worker (every 15 minutes while online): keeps the home screen food cache fresh,
 * reminds the user about a card payment that was never saved as an order, and records activity.
 */
public class OrderSyncWorker extends Worker {

    private static final String TAG = "OrderSyncWorker";
    private static final long FOOD_CACHE_MAX_AGE_MS = 30L * 60 * 1000;
    private static final long FOOD_READ_TIMEOUT_SECONDS = 20;
    private static final int UNSAVED_ORDER_NOTIFICATION_ID = 7301;

    public OrderSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Background sync started");

        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                return Result.success();
            }

            // Update last active timestamp
            Map<String, Object> updates = new HashMap<>();
            updates.put("lastActive", System.currentTimeMillis());
            FirebaseDatabase.getInstance().getReference()
                    .child("users")
                    .child(user.getUid())
                    .updateChildren(updates);

            remindAboutUnsavedPayment(user);

            if (!refreshFoodCacheIfStale()) {
                return Result.retry();
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "User activity synced successfully");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed: " + e.getMessage());
            return Result.retry();
        }
    }

    /** Re-reads the food list when the saved copy is old. Returns false only when the network read failed. */
    private boolean refreshFoodCacheIfStale() {
        try {
            Context ctx = getApplicationContext();
            ReplateDatabaseHelper localDb = ReplateDatabaseHelper.getInstance(ctx);
            long age = System.currentTimeMillis() - localDb.getFoodCacheTimestamp();
            if (age < FOOD_CACHE_MAX_AGE_MS) {
                return true;
            }

            DataSnapshot snapshot = Tasks.await(
                    FirebaseDatabase.getInstance().getReference("foodItems").get(),
                    FOOD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<FoodItem> items = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                try {
                    FoodItem item = child.getValue(FoodItem.class);
                    if (item == null) continue;
                    item.setFoodId(child.getKey());
                    if (item.isListable()) items.add(item);
                } catch (Exception e) {
                    // One malformed listing must not stop the refresh
                    Log.w(TAG, "Skipping malformed food item " + child.getKey(), e);
                }
            }

            if (!items.isEmpty()) {
                localDb.cacheFoodItems(new ArrayList<>(items));
                new FoodItemCacheManager(ctx).saveFoodItems(items);
            }
            return true;
        } catch (ExecutionException | TimeoutException e) {
            Log.w(TAG, "Food refresh failed, will retry", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Food refresh error", e);
            return true;
        }
    }

    /** Posts a quiet reminder to finish an order whose card payment went through but was never saved. */
    private void remindAboutUnsavedPayment(@NonNull FirebaseUser user) {
        try {
            Context ctx = getApplicationContext();
            PendingPayment pending = PendingPaymentStore.load(ctx, user.getUid());
            if (pending == null) return;
            // Checkout closes itself on an empty cart, so there would be nothing to open
            if (CartDatabaseHelper.getInstance(ctx).getAllItems().isEmpty()) return;
            if (!NotificationPrefs.isAllowed(ctx, NotificationCategories.ORDER_PLACED)) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            NotificationManagerCompat manager = NotificationManagerCompat.from(ctx);
            if (!manager.areNotificationsEnabled()) return;

            MyFirebaseMessagingService.createChannel(ctx);

            Intent intent = new Intent(ctx, CheckoutActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openCheckout = PendingIntent.getActivity(ctx, UNSAVED_ORDER_NOTIFICATION_ID, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Same id every time, so a repeat run replaces the reminder instead of stacking another
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, MyFirebaseMessagingService.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_replate)
                    .setContentTitle(ctx.getString(R.string.sync_unsaved_order_title))
                    .setContentText(ctx.getString(R.string.sync_unsaved_order_body))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.sync_unsaved_order_body)))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setContentIntent(openCheckout)
                    .setColor(ContextCompat.getColor(ctx, R.color.primary));
            manager.notify(UNSAVED_ORDER_NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "Could not post the unsaved order reminder", e);
        }
    }
}
