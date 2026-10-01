package com.replate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.replate.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Adapter for conversation list (shows recent chats)
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Map<String, Object> conversation, String conversationId);
    }

    private final List<Map<String, Object>> conversations = new ArrayList<>();
    private final List<String> conversationIds = new ArrayList<>();
    private final String currentUserType; // "customer" or "hotel"
    private OnConversationClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

    public ConversationAdapter(String currentUserType) {
        this.currentUserType = currentUserType;
    }

    public void setOnConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> conv = conversations.get(position);
        String convId = conversationIds.get(position);

        // Show the other party's name
        String name;
        if ("hotel".equals(currentUserType)) {
            name = getString(conv, "customerName", "Customer");
        } else {
            name = getString(conv, "hotelName", "Hotel");
        }
        holder.tvName.setText(name);

        // Last message preview
        String lastMessage = getString(conv, "lastMessage", "");
        holder.tvLastMessage.setText(lastMessage);

        // Timestamp
        Object tsObj = conv.get("lastTimestamp");
        if (tsObj instanceof Number) {
            long ts = ((Number) tsObj).longValue();

            if (android.text.format.DateUtils.isToday(ts)) {
                // Today - show time
                holder.tvTime.setText(timeFormat.format(new Date(ts)));
            } else {
                // Older - show date
                holder.tvTime.setText(dateFormat.format(new Date(ts)));
            }
        } else {
            holder.tvTime.setText("");
        }

        // Avatar placeholder
        holder.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversationClick(conv, convId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    public void setConversations(List<Map<String, Object>> newConversations, List<String> newIds) {
        conversations.clear();
        conversationIds.clear();
        conversations.addAll(newConversations);
        conversationIds.addAll(newIds);
        notifyDataSetChanged();
    }

    private String getString(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : fallback;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvName, tvLastMessage, tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}

