package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.AppNotification;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for Notifications RecyclerView
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotifViewHolder> {

    private Context context;
    private List<AppNotification> notifications;
    private OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(AppNotification notification);
        void onNotificationDelete(AppNotification notification);
    }

    public NotificationAdapter(Context context, List<AppNotification> notifications,
                               OnNotificationClickListener listener) {
        this.context = context;
        this.notifications = notifications;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotifViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotifViewHolder holder, int position) {
        AppNotification notif = notifications.get(position);
        holder.bind(notif);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    class NotifViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon, ivDelete;
        TextView tvTitle, tvBody, tvTime;
        View unreadDot;

        NotifViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvBody = itemView.findViewById(R.id.tvBody);
            tvTime = itemView.findViewById(R.id.tvTime);
            unreadDot = itemView.findViewById(R.id.unreadDot);
        }

        void bind(AppNotification notif) {
            tvTitle.setText(notif.getTitle());
            tvBody.setText(notif.getBody());

            long ts = notif.getTimestampValue();
            if (ts > 0) {
                tvTime.setText(formatRelativeTime(ts));
            } else {
                tvTime.setText("Just now");
            }

            unreadDot.setVisibility(notif.isRead() ? View.GONE : View.VISIBLE);
            itemView.setAlpha(notif.isRead() ? 0.78f : 1.0f);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(notif);
                }
            });

            if (ivDelete != null) {
                ivDelete.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onNotificationDelete(notif);
                    }
                });
            }
        }

        private String formatRelativeTime(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            long minutes = diff / (60 * 1000);
            long hours = diff / (60 * 60 * 1000);
            long days = diff / (24 * 60 * 60 * 1000);

            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + (minutes == 1 ? " min ago" : " mins ago");
            if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
            if (days < 7) return days + (days == 1 ? " day ago" : " days ago");

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}

