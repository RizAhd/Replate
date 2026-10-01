package com.replate.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.adapters.ChatAdapter;
import com.replate.app.helpers.ChatDatabaseHelper;
import com.replate.app.models.ChatMessage;
import com.replate.app.utils.SafeSnapshot;
import com.replate.app.utils.TelephonyUtils;
import com.replate.app.utils.ToastUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Chat Activity for real-time messaging between customers and hotels.
 * Uses Firebase Realtime Database for real-time sync and SQLite for local
 * chat history persistence so messages are never lost.
 */
public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    // Intent extras
    public static final String EXTRA_HOTEL_ID = "hotel_id";
    public static final String EXTRA_HOTEL_NAME = "hotel_name";
    public static final String EXTRA_HOTEL_IMAGE = "hotel_image";
    public static final String EXTRA_HOTEL_PHONE = "hotel_phone";
    public static final String EXTRA_CUSTOMER_ID = "customer_id";
    public static final String EXTRA_CUSTOMER_NAME = "customer_name";
    public static final String EXTRA_IS_HOTEL_VIEW = "is_hotel_view";
    public static final String EXTRA_FOOD_ID = "food_id";
    public static final String EXTRA_FOOD_NAME = "food_name";

    // Views
    private Toolbar toolbar;
    private CircleImageView ivAvatar;
    private TextView tvChatName, tvChatStatus;
    private ImageButton btnCall;
    private RecyclerView rvMessages;
    private TextInputEditText etMessage;
    private FloatingActionButton btnSend;
    private View layoutEmpty;
    private ProgressBar progressBar;

    // Data
    private String hotelId, hotelName, hotelImage, hotelPhone;
    private String customerId, customerName;
    private String foodId, foodName;
    private String conversationId;
    private String currentUserId;
    private String currentUserName;
    private String currentUserType; // "customer" or "hotel"
    private boolean isHotelView;

    // Tracks message IDs already loaded from SQLite so Firebase doesn't duplicate them
    private final Set<String> loadedMessageIds = new HashSet<>();

    // Firebase
    private DatabaseReference messagesRef;
    private DatabaseReference conversationsRef;
    private ChildEventListener messagesListener;

    // Local SQLite cache
    private ChatAdapter chatAdapter;
    private ChatDatabaseHelper chatDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get intent data
        hotelId = getIntent().getStringExtra(EXTRA_HOTEL_ID);
        hotelName = getIntent().getStringExtra(EXTRA_HOTEL_NAME);
        hotelImage = getIntent().getStringExtra(EXTRA_HOTEL_IMAGE);
        hotelPhone = getIntent().getStringExtra(EXTRA_HOTEL_PHONE);
        customerId = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);
        customerName = getIntent().getStringExtra(EXTRA_CUSTOMER_NAME);
        isHotelView = getIntent().getBooleanExtra(EXTRA_IS_HOTEL_VIEW, false);
        foodId = getIntent().getStringExtra(EXTRA_FOOD_ID);
        foodName = getIntent().getStringExtra(EXTRA_FOOD_NAME);

        // Validate required data
        if (hotelId == null || hotelId.isEmpty()) {
            ToastUtils.showCustomToast(this, R.string.chat_invalid_chat_session);
            finish();
            return;
        }

        // Get current user
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            ToastUtils.showCustomToast(this, R.string.chat_please_login_to_chat);
            finish();
            return;
        }
        currentUserId = firebaseUser.getUid();

        // Determine customer info
        if (isHotelView) {
            // Hotel viewing customer's chat
            currentUserType = "hotel";
            currentUserName = hotelName != null ? hotelName : "Hotel";
        } else {
            // Customer chatting with hotel
            currentUserType = "customer";
            customerId = currentUserId;
            customerName = firebaseUser.getDisplayName();
            if (customerName == null || customerName.isEmpty()) {
                customerName = "Customer";
            }
        }

        // Generate conversation ID
        conversationId = ChatMessage.generateConversationId(hotelId, customerId != null ? customerId : currentUserId);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupInputListeners();
        loadCurrentUserName();
        loadMessages();
        listenForNewMessages();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvChatName = findViewById(R.id.tvChatName);
        tvChatStatus = findViewById(R.id.tvChatStatus);
        btnCall = findViewById(R.id.btnCall);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        progressBar = findViewById(R.id.progressBar);

        chatDb = ChatDatabaseHelper.getInstance(this);

        // Firebase references
        messagesRef = FirebaseDatabase.getInstance().getReference("chats")
                .child(conversationId).child("messages");
        conversationsRef = FirebaseDatabase.getInstance().getReference("conversations");
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Set chat partner info
        if (isHotelView) {
            // Hotel sees customer info
            tvChatName.setText(customerName != null ? customerName : "Customer");
            tvChatStatus.setText(foodName != null && !foodName.isEmpty() ? "Re: " + foodName : "Customer");
        } else {
            // Customer sees hotel info
            tvChatName.setText(hotelName != null ? hotelName : "Hotel");
            tvChatStatus.setText(foodName != null && !foodName.isEmpty() ? "Re: " + foodName : "Hotel");

            if (hotelImage != null && !hotelImage.isEmpty()) {
                Glide.with(this)
                        .load(hotelImage)
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .into(ivAvatar);
            }
        }

        // The call button dials the hotel's phone, which makes no sense when the hotel is the one chatting
        if (isHotelView) {
            btnCall.setVisibility(View.GONE);
        } else {
            btnCall.setOnClickListener(v -> makeCall());
        }
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(chatAdapter);
    }

    private void setupInputListeners() {
        // Text change listener to enable/disable send button
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSend.setEnabled(s != null && !s.toString().trim().isEmpty());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSend.setEnabled(false);
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void loadCurrentUserName() {
        // Load user's actual name from database
        FirebaseDatabase.getInstance().getReference("users")
                .child(currentUserId)
                .child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String name = snapshot.getValue(String.class);
                            if (name != null && !name.isEmpty()) {
                                currentUserName = name;
                                if (!isHotelView) {
                                    customerName = name;
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to load user name", error.toException());
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────
    //  MESSAGE LOADING — SQLite first, then Firebase sync
    // ─────────────────────────────────────────────────────────────

    /**
     * Load chat history.  Strategy:
     * 1. Instantly show messages from SQLite (offline-first, no spinner delay).
     * 2. Sync any newer messages from Firebase and save them to SQLite.
     * 3. Retry any locally-queued messages that were never pushed.
     */
    private void loadMessages() {
        progressBar.setVisibility(View.VISIBLE);

        // Offline, the server may never answer: stop the spinner after a while and show what we have
        progressBar.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && progressBar.getVisibility() == View.VISIBLE) {
                progressBar.setVisibility(View.GONE);
                updateEmptyState();
            }
        }, 8000);

        // ── Step 1: Load from SQLite (instant, no network needed) ──
        List<ChatMessage> cachedMessages = chatDb.getMessages(conversationId);
        if (!cachedMessages.isEmpty()) {
            for (ChatMessage cached : cachedMessages) {
                if (cached.getMessageId() != null) {
                    loadedMessageIds.add(cached.getMessageId());
                }
            }
            chatAdapter.setMessages(cachedMessages);
            updateEmptyState();
            scrollToBottom();
            progressBar.setVisibility(View.GONE);
        }

        // ── Step 2: Sync from Firebase (fills gaps & catches new messages) ──
        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    ChatMessage msg = SafeSnapshot.getValue(msgSnap, ChatMessage.class);
                    if (msg != null) {
                        msg.setMessageId(msgSnap.getKey());

                        // Save every Firebase message to SQLite for future offline use
                        chatDb.saveMessage(msg);

                        // Only add to the adapter if not already shown from cache
                        if (!loadedMessageIds.contains(msg.getMessageId())) {
                            loadedMessageIds.add(msg.getMessageId());
                            chatAdapter.addMessage(msg);
                        }
                    }
                }

                updateEmptyState();
                scrollToBottom();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load messages", error.toException());
            }
        });

        // ── Step 3: Retry any pending (un-synced) messages ──
        retryPendingMessages();
    }

    /**
     * Push any messages that were saved locally but never confirmed by Firebase.
     */
    private void retryPendingMessages() {
        List<ChatMessage> pending = chatDb.getPendingMessages();
        for (ChatMessage msg : pending) {
            if (msg.getConversationId() == null ||
                !msg.getConversationId().equals(conversationId)) continue;

            Map<String, Object> data = new HashMap<>();
            data.put("conversationId", msg.getConversationId());
            data.put("senderId", msg.getSenderId());
            data.put("senderName", msg.getSenderName());
            data.put("senderType", msg.getSenderType());
            data.put("receiverId", msg.getReceiverId());
            data.put("message", msg.getMessage());
            data.put("timestamp", msg.getTimestamp());
            data.put("read", msg.isRead());
            data.put("hotelId", msg.getHotelId());
            data.put("hotelName", msg.getHotelName());
            data.put("customerId", msg.getCustomerId());
            data.put("customerName", msg.getCustomerName());
            if (msg.getFoodId() != null) data.put("foodId", msg.getFoodId());
            if (msg.getFoodName() != null) data.put("foodName", msg.getFoodName());

            String mid = msg.getMessageId();
            messagesRef.child(mid).setValue(data)
                    .addOnSuccessListener(v -> {
                        chatDb.markSynced(mid);
                        Log.d(TAG, "Retried pending message synced: " + mid);
                    })
                    .addOnFailureListener(e ->
                        Log.w(TAG, "Retry failed for message: " + mid, e));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  REAL-TIME LISTENER — new messages from Firebase
    // ─────────────────────────────────────────────────────────────

    private void listenForNewMessages() {
        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage msg = SafeSnapshot.getValue(snapshot, ChatMessage.class);
                if (msg != null) {
                    msg.setMessageId(snapshot.getKey());

                    // Always persist to SQLite
                    chatDb.saveMessage(msg);

                    // Only add to UI if not already displayed
                    if (!loadedMessageIds.contains(msg.getMessageId())) {
                        loadedMessageIds.add(msg.getMessageId());
                        chatAdapter.addMessage(msg);
                        updateEmptyState();
                        scrollToBottom();
                    }

                    // Mark as read if we're the receiver
                    if (!msg.isSentByMe(currentUserId) && !msg.isRead()) {
                        markAsRead(snapshot.getKey());
                        chatDb.markMessageRead(snapshot.getKey());
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage msg = SafeSnapshot.getValue(snapshot, ChatMessage.class);
                if (msg != null) {
                    msg.setMessageId(snapshot.getKey());
                    chatDb.saveMessage(msg);
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Messages listener cancelled", error.toException());
            }
        };

        messagesRef.orderByChild("timestamp").addChildEventListener(messagesListener);
    }

    // ─────────────────────────────────────────────────────────────
    //  SEND MESSAGE — save to SQLite immediately, push to Firebase
    // ─────────────────────────────────────────────────────────────

    private void sendMessage() {
        String messageText = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (messageText.isEmpty()) return;

        etMessage.setText("");

        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setConversationId(conversationId);
        message.setSenderId(currentUserId);
        message.setSenderName(currentUserName != null ? currentUserName : "User");
        message.setSenderType(currentUserType);
        message.setReceiverId(isHotelView ? customerId : hotelId);
        message.setMessage(messageText);
        message.setTimestamp(timestamp);
        message.setRead(false);
        message.setHotelId(hotelId);
        message.setHotelName(hotelName);
        message.setCustomerId(customerId != null ? customerId : currentUserId);
        message.setCustomerName(customerName);
        message.setFoodId(foodId);
        message.setFoodName(foodName);

        // ── Save to SQLite first (optimistic UI — instant display) ──
        chatDb.savePendingMessage(message);
        loadedMessageIds.add(messageId);
        chatAdapter.addMessage(message);
        updateEmptyState();
        scrollToBottom();

        // ── Push to Firebase ──
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("conversationId", conversationId);
        messageData.put("senderId", currentUserId);
        messageData.put("senderName", message.getSenderName());
        messageData.put("senderType", currentUserType);
        messageData.put("receiverId", message.getReceiverId());
        messageData.put("message", messageText);
        messageData.put("timestamp", timestamp);
        messageData.put("read", false);
        messageData.put("hotelId", hotelId);
        messageData.put("hotelName", hotelName);
        messageData.put("customerId", message.getCustomerId());
        messageData.put("customerName", customerName);
        if (foodId != null) messageData.put("foodId", foodId);
        if (foodName != null) messageData.put("foodName", foodName);

        messagesRef.child(messageId).setValue(messageData)
                .addOnSuccessListener(v -> {
                    chatDb.markSynced(messageId);
                    updateConversationMeta(messageText, timestamp);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send message", e);
                    ToastUtils.showCustomToast(this, R.string.chat_message_saved_locally_will_retry);
                });
    }

    // ─────────────────────────────────────────────────────────────
    //  CONVERSATION METADATA — keeps Firebase & SQLite in sync
    // ─────────────────────────────────────────────────────────────

    private void updateConversationMeta(String lastMessage, long timestamp) {
        Map<String, Object> convData = new HashMap<>();
        convData.put("hotelId", hotelId);
        convData.put("hotelName", hotelName);
        convData.put("customerId", customerId != null ? customerId : currentUserId);
        convData.put("customerName", customerName);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = (user != null && user.getEmail() != null && !isHotelView) ? user.getEmail() : null;
        if (email != null) convData.put("customerEmail", email);

        convData.put("lastMessage", lastMessage);
        convData.put("lastTimestamp", timestamp);
        convData.put("lastSenderId", currentUserId);
        if (foodId != null) convData.put("foodId", foodId);
        if (foodName != null) convData.put("foodName", foodName);

        // Push to Firebase
        conversationsRef.child(hotelId).child(conversationId).setValue(convData);
        String custId = customerId != null ? customerId : currentUserId;
        conversationsRef.child(custId).child(conversationId).setValue(convData);

        // Also save to local SQLite
        chatDb.saveConversation(conversationId, hotelId, hotelName,
                custId, customerName, email,
                foodId, foodName, lastMessage, timestamp);
    }

    private void markAsRead(String messageId) {
        if (messageId == null) return;
        messagesRef.child(messageId).child("read").setValue(true);
    }

    private void updateEmptyState() {
        boolean isEmpty = chatAdapter.getItemCount() == 0;
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvMessages.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            rvMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  PHONE CALL
    // ─────────────────────────────────────────────────────────────

    private void makeCall() {
        String phone = hotelPhone;
        if (phone == null || phone.isEmpty()) {
            // Try to fetch from database
            FirebaseDatabase.getInstance().getReference("hotels")
                    .child(hotelId).child("phone")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String ph = snapshot.getValue(String.class);
                            if (ph != null && !ph.isEmpty()) {
                                initiateCall(ph);
                            } else {
                                ToastUtils.showCustomToast(ChatActivity.this, R.string.chat_phone_number_not_available);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            ToastUtils.showCustomToast(ChatActivity.this, R.string.chat_failed_to_get_phone_number);
                        }
                    });
        } else {
            initiateCall(phone);
        }
    }

    private void initiateCall(String phone) {
        // The dialer needs no CALL_PHONE permission; the customer taps once to place the call.
        try {
            TelephonyUtils.openDialer(this, phone);
        } catch (android.content.ActivityNotFoundException e) {
            ToastUtils.showCustomToast(this, R.string.chat_no_phone_app_available);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null && messagesRef != null) {
            messagesRef.removeEventListener(messagesListener);
        }
    }
}

