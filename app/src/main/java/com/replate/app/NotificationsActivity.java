package com.replate.app;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.adapters.NotificationAdapter;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.AppNotification;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Notifications Activity - Shows push notification history
 * Requirement: Notifications
 */
public class NotificationsActivity extends AppCompatActivity
        implements NotificationAdapter.OnNotificationClickListener {

    private RecyclerView rvNotifications;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private TextView tvMarkAllRead, tvDeleteAll, tvUnreadCount;

    private DatabaseReference mDatabase;
    private NotificationAdapter adapter;
    private List<AppNotification> notifications = new ArrayList<>();
    private String launchOrderId;
    private ReplateDatabaseHelper localDb;
    private ValueEventListener notificationsListener;
    private DatabaseReference notificationsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        launchOrderId = getIntent().getStringExtra("orderId");
        localDb = ReplateDatabaseHelper.getInstance(this);

        initViews();
        loadNotifications();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        rvNotifications = findViewById(R.id.rvNotifications);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);
        tvMarkAllRead = findViewById(R.id.tvMarkAllRead);
        tvDeleteAll = findViewById(R.id.tvDeleteAll);
        tvUnreadCount = findViewById(R.id.tvUnreadCount);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this, notifications, this);
        rvNotifications.setAdapter(adapter);

        // Slide-in animation for items
        try {
            LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(
                    this, R.anim.layout_animation_slide_from_bottom);
            rvNotifications.setLayoutAnimation(animation);
        } catch (Exception ignored) {
            // Animation resource may not exist yet; skip gracefully
        }

        // Swipe-to-delete
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
                // Don't allow swiping on header items
                if (adapter.isHeader(viewHolder.getAdapterPosition())) {
                    return 0;
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                AppNotification notif = adapter.getNotificationAt(position);
                if (notif != null) {
                    deleteNotificationWithUndo(notif);
                } else {
                    adapter.buildDisplayList();
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View itemView = viewHolder.itemView;
                    Paint paint = new Paint();
                    paint.setColor(ContextCompat.getColor(NotificationsActivity.this, R.color.error));
                    paint.setAntiAlias(true);

                    float cornerRadius = 16 * getResources().getDisplayMetrics().density;
                    RectF background = new RectF(
                            itemView.getRight() + dX,
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom());
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

                    // Draw delete icon/text
                    Paint textPaint = new Paint();
                    textPaint.setColor(0xFFFFFFFF);
                    textPaint.setTextSize(14 * getResources().getDisplayMetrics().density);
                    textPaint.setAntiAlias(true);
                    float textY = itemView.getTop() + (itemView.getHeight() + textPaint.getTextSize()) / 2f;
                    float textX = itemView.getRight() - 24 * getResources().getDisplayMetrics().density
                            - textPaint.measureText("Delete");
                    if (textX > itemView.getRight() + dX + 8 * getResources().getDisplayMetrics().density) {
                        c.drawText("Delete", textX, textY, textPaint);
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvNotifications);

        ivBack.setOnClickListener(v -> finish());
        tvMarkAllRead.setOnClickListener(v -> markAllAsRead());
        if (tvDeleteAll != null) {
            tvDeleteAll.setOnClickListener(v -> confirmDeleteAll());
        }
    }

    private void deleteNotificationWithUndo(AppNotification notification) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null || notification.getNotificationId() == null) return;

        // Remove from local list immediately for smooth UX
        notifications.remove(notification);
        adapter.buildDisplayList();
        adapter.notifyDataSetChanged();
        updateUI();

        Snackbar snackbar = Snackbar.make(rvNotifications, "Notification deleted", Snackbar.LENGTH_LONG);
        snackbar.setAction("Undo", v -> {
            // Re-add to list and refresh
            notifications.add(notification);
            Collections.sort(notifications, (a, b) ->
                    Long.compare(b.getTimestampValue(), a.getTimestampValue()));
            adapter.buildDisplayList();
            adapter.notifyDataSetChanged();
            updateUI();
        });
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    // Only delete from Firebase if undo was NOT clicked
                    mDatabase.child("notifications").child(userId)
                            .child(notification.getNotificationId())
                            .removeValue();
                }
            }
        });
        snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.green_light));
        snackbar.show();
    }

    private void loadNotifications() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        progressBar.setVisibility(View.VISIBLE);

        notificationsRef = mDatabase.child("notifications").child(userId);

        // Load cached notifications immediately for instant display
        if (localDb != null) {
            List<AppNotification> cached = localDb.getCachedNotifications();
            if (!cached.isEmpty() && notifications.isEmpty()) {
                notifications.clear();
                notifications.addAll(cached);
                adapter.buildDisplayList();
                adapter.notifyDataSetChanged();
                updateUI();
            }
        }

        notificationsRef
                .addValueEventListener(notificationsListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        notifications.clear();

                        for (DataSnapshot notifSnapshot : snapshot.getChildren()) {
                            AppNotification notif = notifSnapshot.getValue(AppNotification.class);
                            if (notif != null) {
                                notif.setNotificationId(notifSnapshot.getKey());
                                notifications.add(notif);
                            }
                        }

                        // Sort by timestamp (newest first)
                        Collections.sort(notifications, (a, b) ->
                                Long.compare(b.getTimestampValue(), a.getTimestampValue()));

                        // Cache to SQLite for offline access
                        if (localDb != null && !notifications.isEmpty()) {
                            localDb.cacheNotifications(notifications);
                        }

                        adapter.buildDisplayList();
                        adapter.notifyDataSetChanged();
                        progressBar.setVisibility(View.GONE);

                        // Run layout animation on first load
                        if (rvNotifications.getLayoutAnimation() != null) {
                            rvNotifications.scheduleLayoutAnimation();
                        }

                        updateUI();

                        if (launchOrderId != null && !launchOrderId.trim().isEmpty()) {
                            openOrderFromPayload(launchOrderId.trim());
                            launchOrderId = null;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        // Fallback: load from SQLite cache if Firebase fails
                        if (localDb != null && notifications.isEmpty()) {
                            List<AppNotification> cached = localDb.getCachedNotifications();
                            if (!cached.isEmpty()) {
                                notifications.clear();
                                notifications.addAll(cached);
                                adapter.buildDisplayList();
                                adapter.notifyDataSetChanged();
                                updateUI();
                            }
                        }
                    }
                });
    }

    private void updateUI() {
        if (notifications.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            tvMarkAllRead.setVisibility(View.GONE);
            if (tvDeleteAll != null) tvDeleteAll.setVisibility(View.GONE);
            if (tvUnreadCount != null) tvUnreadCount.setVisibility(View.GONE);
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            if (tvDeleteAll != null) tvDeleteAll.setVisibility(View.VISIBLE);

            int unreadCount = 0;
            for (AppNotification notif : notifications) {
                if (!notif.isRead()) unreadCount++;
            }

            boolean hasUnread = unreadCount > 0;
            tvMarkAllRead.setVisibility(hasUnread ? View.VISIBLE : View.GONE);

            if (tvUnreadCount != null) {
                if (hasUnread) {
                    tvUnreadCount.setVisibility(View.VISIBLE);
                    tvUnreadCount.setText(String.valueOf(unreadCount));
                } else {
                    tvUnreadCount.setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public void onNotificationClick(AppNotification notification) {
        // Mark as read
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null && notification.getNotificationId() != null) {
            mDatabase.child("notifications").child(userId)
                    .child(notification.getNotificationId())
                    .child("read").setValue(true);
        }

        navigateFromNotification(notification);
    }

    private void navigateFromNotification(AppNotification notification) {
        if (notification == null) return;

        Intent intent = com.replate.app.notifications.NotificationRouter.buildIntent(
                this,
                com.replate.app.notifications.NotificationCategories.normalize(value(notification.getCategory())),
                value(notification.getRoute()),
                value(notification.getOrderId()),
                value(notification.getHotelId()),
                value(notification.getDeepLink()),
                null
        );

        if (intent != null) {
            startActivity(intent);
        }
    }

    private String normalizeCategory(String rawCategory) {
        return com.replate.app.notifications.NotificationCategories.normalize(rawCategory);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private void openOrderFromPayload(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            Intent fallback = new Intent(this, MainActivity.class);
            fallback.putExtra("open_tab", "orders");
            startActivity(fallback);
            return;
        }

        Intent intent = new Intent(this, OrderDetailActivity.class);
        intent.putExtra("orderId", orderId);
        startActivity(intent);
    }

    private void markAllAsRead() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        for (AppNotification notif : notifications) {
            if (!notif.isRead() && notif.getNotificationId() != null) {
                mDatabase.child("notifications").child(userId)
                        .child(notif.getNotificationId())
                        .child("read").setValue(true);
            }
        }
        ToastUtils.showCustomToast(this, "All notifications marked as read");
    }

    @Override
    public void onNotificationDelete(AppNotification notification) {
        if (notification == null || notification.getNotificationId() == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Delete notification")
                .setMessage("Remove this notification?")
                .setPositiveButton("Delete", (d, w) -> deleteSingleNotification(notification))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSingleNotification(AppNotification notification) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null || notification.getNotificationId() == null) return;

        mDatabase.child("notifications").child(userId)
                .child(notification.getNotificationId())
                .removeValue()
                .addOnSuccessListener(unused -> {
                    ToastUtils.showCustomToast(this, "Notification deleted");
                })
                .addOnFailureListener(e -> {
                    ToastUtils.showCustomToast(this, "Failed to delete notification");
                });
    }

    private void confirmDeleteAll() {
        if (notifications.isEmpty()) {
            ToastUtils.showCustomToast(this, "No notifications to delete");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete all notifications")
                .setMessage("Are you sure you want to delete all " + notifications.size() + " notifications? This cannot be undone.")
                .setPositiveButton("Delete All", (d, w) -> deleteAllNotifications())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAllNotifications() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        mDatabase.child("notifications").child(userId)
                .removeValue()
                .addOnSuccessListener(unused -> {
                    // Also clear SQLite cache
                    if (localDb != null) {
                        localDb.clearNotificationCache();
                    }
                    ToastUtils.showCustomToast(this, "All notifications deleted");
                })
                .addOnFailureListener(e -> {
                    ToastUtils.showCustomToast(this, "Failed to delete notifications");
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsRef != null && notificationsListener != null) {
            notificationsRef.removeEventListener(notificationsListener);
        }
    }
}
