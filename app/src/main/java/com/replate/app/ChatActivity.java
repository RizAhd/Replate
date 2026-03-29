package com.replate.app;

import android.Manifest;
import android.content.Intent;
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
import com.replate.app.utils.TelephonyUtils;
import com.replate.app.utils.ToastUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Chat Activity for real-time messaging between customers and hotels.
 * Uses Firebase Realtime Database for message sync and SQLite for offline caching.
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

    // Firebase
    private DatabaseReference messagesRef;
    private DatabaseReference conversationsRef;
    private ChildEventListener messagesListener;

    // Local
    private ChatAdapter chatAdapter;
    private ChatDatabaseHelper chatDb;

    private String pendingCallNumber;
    private final ActivityResultLauncher<String> callPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingCallNumber != null) {
                    TelephonyUtils.callNow(this, pendingCallNumber);
                } else if (!granted && pendingCallNumber != null) {
                    TelephonyUtils.openDialer(this, pendingCallNumber);
                }
            });

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
            ToastUtils.showCustomToast(this, "Invalid chat session");
            finish();
            return;
        }

        // Get current user
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            ToastUtils.showCustomToast(this, "Please login to chat");
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

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

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

        // Call button
        btnCall.setOnClickListener(v -> makeCall());
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
                btnSend.setEnabled(s != null && s.toString().trim().length() > 0);
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
                        Log.w(TAG, "Failed to load user name", error.toException());
                    }
                });
    }

    private void loadMessages() {
        progressBar.setVisibility(View.VISIBLE);

        // First load from local SQLite cache
        List<ChatMessage> cachedMessages = chatDb.getMessages(conversationId);
        if (!cachedMessages.isEmpty()) {
            chatAdapter.setMessages(cachedMessages);
            updateEmptyState();
            scrollToBottom();
        }

        // Then sync from Firebase
        messagesRef.orderByChild("timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);

                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                    if (msg != null) {
                        msg.setMessageId(msgSnap.getKey());
                        chatDb.saveMessage(msg);
                        chatAdapter.addMessage(msg);
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
    }

    private void listenForNewMessages() {
        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage msg = snapshot.getValue(ChatMessage.class);
                if (msg != null) {
                    msg.setMessageId(snapshot.getKey());
                    chatDb.saveMessage(msg);
                    chatAdapter.addMessage(msg);
                    updateEmptyState();
                    scrollToBottom();

                    // Mark as read if we're the receiver
                    if (!msg.isSentByMe(currentUserId) && !msg.isRead()) {
                        markAsRead(snapshot.getKey());
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                ChatMessage msg = snapshot.getValue(ChatMessage.class);
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

    private void sendMessage() {
        String messageText = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (messageText.isEmpty()) return;

        etMessage.setText("");

        // Create message
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

        // Save locally first (optimistic UI)
        chatDb.savePendingMessage(message);
        chatAdapter.addMessage(message);
        updateEmptyState();
        scrollToBottom();

        // Push to Firebase
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
                    ToastUtils.showCustomToast(this, "Failed to send message");
                });
    }

    private void updateConversationMeta(String lastMessage, long timestamp) {
        // Update conversation summary for both parties
        Map<String, Object> convData = new HashMap<>();
        convData.put("hotelId", hotelId);
        convData.put("hotelName", hotelName);
        convData.put("customerId", customerId != null ? customerId : currentUserId);
        convData.put("customerName", customerName);
        // Include customer email for hotel reference
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null && !isHotelView) {
            convData.put("customerEmail", user.getEmail());
        }
        convData.put("lastMessage", lastMessage);
        convData.put("lastTimestamp", timestamp);
        convData.put("lastSenderId", currentUserId);
        if (foodId != null) convData.put("foodId", foodId);
        if (foodName != null) convData.put("foodName", foodName);

        // Save under hotel's conversations
        conversationsRef.child(hotelId).child(conversationId).setValue(convData);

        // Save under customer's conversations
        String custId = customerId != null ? customerId : currentUserId;
        conversationsRef.child(custId).child(conversationId).setValue(convData);
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
                                ToastUtils.showCustomToast(ChatActivity.this, "Phone number not available");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            ToastUtils.showCustomToast(ChatActivity.this, "Failed to get phone number");
                        }
                    });
        } else {
            initiateCall(phone);
        }
    }

    private void initiateCall(String phone) {
        pendingCallNumber = phone;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            TelephonyUtils.callNow(this, phone);
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
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

