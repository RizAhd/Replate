package com.replate.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.adapters.ConversationAdapter;
import com.replate.app.utils.DatabaseErrorLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lists the customer's chats with hotels, newest first, and reopens one in {@link ChatActivity}.
 * Conversations are kept at conversations/{uid}/{conversationId} by ChatActivity.
 */
public class MessagesActivity extends AppCompatActivity {

    private RecyclerView rvConversations;
    private View emptyState;
    private View btnRetry;
    private TextView tvEmptyBody;
    private ProgressBar progressBar;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshMessages;

    private ConversationAdapter adapter;
    private DatabaseReference conversationsRef;
    private ValueEventListener listener;
    private String currentUserId;

    // Sets up the list and back button; closes if not signed in.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentUserId = user.getUid();

        rvConversations = findViewById(R.id.rvConversations);
        emptyState = findViewById(R.id.emptyState);
        btnRetry = findViewById(R.id.btnRetry);
        tvEmptyBody = findViewById(R.id.tvEmptyBody);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshMessages = findViewById(R.id.swipeRefreshMessages);
        swipeRefreshMessages.setColorSchemeResources(R.color.green_primary);
        swipeRefreshMessages.setOnRefreshListener(this::startListening);
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        adapter = new ConversationAdapter("customer");
        adapter.setOnConversationClickListener(this::openConversation);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);

        btnRetry.setOnClickListener(v -> startListening());
    }

    // Start listening for chats when the screen is shown.
    @Override
    protected void onStart() {
        super.onStart();
        if (currentUserId != null) startListening();
    }

    // Stop listening when the screen is hidden.
    @Override
    protected void onStop() {
        super.onStop();
        stopListening();
    }

    // Loads the customer's chats from Firebase and keeps them live.
    private void startListening() {
        stopListening();
        if (!swipeRefreshMessages.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        btnRetry.setVisibility(View.GONE);

        conversationsRef = FirebaseDatabase.getInstance().getReference("conversations").child(currentUserId);
        listener = new ValueEventListener() {
            // Reads the chats, sorts newest first, shows them.
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshMessages.setRefreshing(false);

                List<Map<String, Object>> conversations = new ArrayList<>();
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Object value = child.getValue();
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> conversation = (Map<String, Object>) value;
                        conversations.add(conversation);
                        ids.add(child.getKey());
                    }
                }

                // Newest first: sort an index list so conversations and ids stay paired
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < conversations.size(); i++) order.add(i);
                Collections.sort(order, (a, b) -> Long.compare(
                        timestampOf(conversations.get(b)), timestampOf(conversations.get(a))));
                List<Map<String, Object>> sorted = new ArrayList<>();
                List<String> sortedIds = new ArrayList<>();
                for (int index : order) {
                    sorted.add(conversations.get(index));
                    sortedIds.add(ids.get(index));
                }

                adapter.setConversations(sorted, sortedIds);
                showList(!sorted.isEmpty(), false);
            }

            // Shows the retry state if loading fails.
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                DatabaseErrorLogger.log("MessagesActivity", "load conversations", error);
                progressBar.setVisibility(View.GONE);
                swipeRefreshMessages.setRefreshing(false);
                if (adapter.getItemCount() == 0) {
                    showList(false, true);
                }
            }
        };
        conversationsRef.addValueEventListener(listener);
    }

    // Removes the Firebase listener.
    private void stopListening() {
        if (conversationsRef != null && listener != null) {
            conversationsRef.removeEventListener(listener);
        }
        listener = null;
    }

    // Shows the list, or the empty / retry message.
    private void showList(boolean hasItems, boolean failed) {
        swipeRefreshMessages.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        btnRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        tvEmptyBody.setText(failed ? R.string.messages_load_failed : R.string.messages_empty_subtitle);
    }

    // Gets the last message time, or 0 if missing.
    private static long timestampOf(Map<String, Object> conversation) {
        Object value = conversation.get("lastTimestamp");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    // Opens the chat screen for the tapped conversation.
    private void openConversation(Map<String, Object> conversation, String conversationId) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_HOTEL_ID, stringOf(conversation, "hotelId"));
        intent.putExtra(ChatActivity.EXTRA_HOTEL_NAME, stringOf(conversation, "hotelName"));
        intent.putExtra(ChatActivity.EXTRA_CUSTOMER_ID, currentUserId);
        intent.putExtra(ChatActivity.EXTRA_CUSTOMER_NAME, stringOf(conversation, "customerName"));
        intent.putExtra(ChatActivity.EXTRA_FOOD_ID, stringOf(conversation, "foodId"));
        intent.putExtra(ChatActivity.EXTRA_FOOD_NAME, stringOf(conversation, "foodName"));
        intent.putExtra(ChatActivity.EXTRA_IS_HOTEL_VIEW, false);
        startActivity(intent);
    }

    // Reads a text value from the map, or null.
    private static String stringOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }
}
