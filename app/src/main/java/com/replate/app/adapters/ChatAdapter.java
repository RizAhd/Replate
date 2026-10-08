package com.replate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for chat messages RecyclerView.
 * Shows sent messages on right, received on left.
 * Includes date separators between different days.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final String currentUserId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public ChatAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    // Tells sent messages from received ones.
    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        return msg.isSentByMe(currentUserId) ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SENT) {
            View view = inflater.inflate(R.layout.item_chat_message_sent, parent, false);
            return new SentMessageHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_message_received, parent, false);
            return new ReceivedMessageHolder(view);
        }
    }

    // Fills a message bubble and its date separator.
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        String time = timeFormat.format(new Date(msg.getTimestamp()));

        if (holder instanceof SentMessageHolder) {
            SentMessageHolder sentHolder = (SentMessageHolder) holder;
            sentHolder.tvMessage.setText(msg.getMessage());
            sentHolder.tvTime.setText(time);
        } else if (holder instanceof ReceivedMessageHolder) {
            ReceivedMessageHolder receivedHolder = (ReceivedMessageHolder) holder;
            receivedHolder.tvMessage.setText(msg.getMessage());
            receivedHolder.tvTime.setText(time);

            // Show sender name only for first message in a group from same sender
            boolean showName = true;
            if (position > 0) {
                ChatMessage prev = messages.get(position - 1);
                if (prev.getSenderId() != null && prev.getSenderId().equals(msg.getSenderId())
                        && !prev.isSentByMe(currentUserId)) {
                    showName = false;
                }
            }
            if (showName && msg.getSenderName() != null && !msg.getSenderName().isEmpty()) {
                receivedHolder.tvSenderName.setText(msg.getSenderName());
                receivedHolder.tvSenderName.setVisibility(View.VISIBLE);
            } else {
                receivedHolder.tvSenderName.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // Replaces all messages in the list.
    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    // Adds a new message, or updates it if it is already there.
    public void addMessage(ChatMessage message) {
        // Check if already exists (by messageId)
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getMessageId() != null &&
                messages.get(i).getMessageId().equals(message.getMessageId())) {
                messages.set(i, message);
                notifyItemChanged(i);
                return;
            }
        }
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    // Removes every message.
    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    // ViewHolder for sent messages (right side, green bubble)
    static class SentMessageHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;

        SentMessageHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }

    // ViewHolder for received messages (left side, gray bubble)
    static class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvSenderName;

        ReceivedMessageHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
        }
    }
}

