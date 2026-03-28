package com.replate.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.adapters.NotificationAdapter;
import com.replate.app.models.AppNotification;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Notifications Activity - Shows push notification history
 * Requirement: Notifications
 */
public class NotificationsActivity extends AppCompatActivity
        implements NotificationAdapter.OnNotificationClickListener {

    private RecyclerView rvNotifications;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private TextView tvMarkAllRead;

    private DatabaseReference mDatabase;
    private NotificationAdapter adapter;
    private List<AppNotification> notifications = new ArrayList<>();
    private String launchOrderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        launchOrderId = getIntent().getStringExtra("orderId");

        initViews();
        loadNotifications();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        rvNotifications = findViewById(R.id.rvNotifications);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);
        tvMarkAllRead = findViewById(R.id.tvMarkAllRead);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this, notifications, this);
        rvNotifications.setAdapter(adapter);

        ivBack.setOnClickListener(v -> finish());
        tvMarkAllRead.setOnClickListener(v -> markAllAsRead());
    }

    private void loadNotifications() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("notifications").child(userId)
                .addValueEventListener(new ValueEventListener() {
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

                        adapter.notifyDataSetChanged();
                        progressBar.setVisibility(View.GONE);
                        updateUI();

                        if (launchOrderId != null && !launchOrderId.trim().isEmpty()) {
                            openOrderFromPayload(launchOrderId.trim());
                            launchOrderId = null;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                    }
                });
    }

    private void updateUI() {
        if (notifications.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            tvMarkAllRead.setVisibility(View.GONE);
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);

            boolean hasUnread = false;
            for (AppNotification notif : notifications) {
                if (!notif.isRead()) {
                    hasUnread = true;
                    break;
                }
            }
            tvMarkAllRead.setVisibility(hasUnread ? View.VISIBLE : View.INVISIBLE);
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
}
