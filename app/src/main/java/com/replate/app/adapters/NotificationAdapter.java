package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.AppNotification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for Notifications RecyclerView with section headers (Today, Yesterday, Earlier).
 */
public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTIFICATION = 1;

    private Context context;
    private List<AppNotification> notifications;
    private List<Object> displayItems = new ArrayList<>();
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
        buildDisplayList();
    }

    /** Rebuild the display list with section headers inserted between groups. */
    public void buildDisplayList() {
        displayItems.clear();
        if (notifications == null || notifications.isEmpty()) return;

        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayStart = todayCal.getTimeInMillis();

        Calendar yesterdayCal = (Calendar) todayCal.clone();
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
        long yesterdayStart = yesterdayCal.getTimeInMillis();

        String currentSection = null;

        for (AppNotification notif : notifications) {
            long ts = notif.getTimestampValue();
            String section;
            if (ts >= todayStart) {
                section = "Today";
            } else if (ts >= yesterdayStart) {
                section = "Yesterday";
            } else {
                section = "Earlier";
            }

            if (!section.equals(currentSection)) {
                displayItems.add(section); // String = header
                currentSection = section;
            }
            displayItems.add(notif);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return displayItems.get(position) instanceof String ? TYPE_HEADER : TYPE_NOTIFICATION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_notification_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotifViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) displayItems.get(position));
        } else if (holder instanceof NotifViewHolder) {
            ((NotifViewHolder) holder).bind((AppNotification) displayItems.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    /** Returns a color res int for the left accent strip based on notification category. */
    private int getCategoryAccentColor(String category) {
        if (category == null) return R.color.green_primary;
        switch (category.toLowerCase(Locale.US)) {
            case "order_placed":
            case "order_accepted":
            case "hotel_new_order":
                return R.color.green_primary;
            case "order_cancelled":
            case "order_rejected":
                return R.color.error;
            case "pickup_ready":
            case "pickup_reminder":
                return R.color.badge_distance;
            case "new_deals":
            case "hot_deals":
                return R.color.orange_accent;
            case "admin_announcement":
            case "admin_broadcast":
                return R.color.primary_dark;
            case "hotel_approved":
            case "welcome":
                return R.color.leaf2;
            default:
                return R.color.green_primary;
        }
    }

    /** Returns the underlying notification at a given adapter position, or null if it's a header. */
    public AppNotification getNotificationAt(int adapterPosition) {
        if (adapterPosition < 0 || adapterPosition >= displayItems.size()) return null;
        Object item = displayItems.get(adapterPosition);
        return item instanceof AppNotification ? (AppNotification) item : null;
    }

    /** Check if position is a header */
    public boolean isHeader(int position) {
        return position >= 0 && position < displayItems.size() && displayItems.get(position) instanceof String;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvSectionHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionHeader = itemView.findViewById(R.id.tvSectionHeader);
        }

        void bind(String sectionTitle) {
            tvSectionHeader.setText(sectionTitle);
        }
    }

    class NotifViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon, ivDelete;
        TextView tvTitle, tvBody, tvTime;
        View unreadDot;
        View viewAccentStrip;

        NotifViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvBody = itemView.findViewById(R.id.tvBody);
            tvTime = itemView.findViewById(R.id.tvTime);
            unreadDot = itemView.findViewById(R.id.unreadDot);
            viewAccentStrip = itemView.findViewById(R.id.viewAccentStrip);
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

            boolean isRead = notif.isRead();
            unreadDot.setVisibility(isRead ? View.GONE : View.VISIBLE);
            itemView.setAlpha(isRead ? 0.78f : 1.0f);

            // Apply category-colored accent strip and icon tint
            int accentColor = ContextCompat.getColor(context, getCategoryAccentColor(notif.getCategory()));
            if (viewAccentStrip != null) {
                viewAccentStrip.setBackgroundColor(accentColor);
            }
            if (ivIcon != null) {
                ivIcon.setColorFilter(accentColor);
            }

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

