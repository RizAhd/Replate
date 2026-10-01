package com.replate.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.replate.app.models.Order;
import com.replate.app.utils.SafeSnapshot;
import com.replate.app.utils.ToastUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Order Success Screen — shown after successful checkout.
 * Displays QR code prominently, listens for real-time status changes,
 * shows completion overlay when scanned, then reveals review section.
 */
public class OrderSuccessActivity extends AppCompatActivity {

    private static final String TAG = "OrderSuccess";

    // Views
    private ImageView ivQRCode;
    private TextView tvOTP, tvOrderId, tvHotelName, tvTotal;
    private TextView tvTitle, tvSubtitle, tvStatus, btnSkip;
    private CircularProgressIndicator statusSpinner;
    private View cardQR, reviewSection, completionOverlay, statusRow;
    private View completionCircle, headerSection;
    private RatingBar ratingReview;
    private TextInputEditText etReviewComment;
    private MaterialButton btnSubmitReview, btnViewDetails;

    // Data
    private DatabaseReference mDatabase;
    private String orderId;
    private Order order;
    private ValueEventListener statusListener;
    private boolean completionShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_success);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        orderId = getIntent().getStringExtra("orderId");

        if (orderId == null || orderId.isEmpty()) {
            finish();
            return;
        }

        initViews();
        loadOrder();
        attachStatusListener();
        playEntranceAnimation();
        playOrderPlacedFeedback();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToOrders();
            }
        });
    }

    private void initViews() {
        ivQRCode = findViewById(R.id.ivQRCode);
        tvOTP = findViewById(R.id.tvOTP);
        tvOrderId = findViewById(R.id.tvOrderId);
        tvHotelName = findViewById(R.id.tvHotelName);
        tvTotal = findViewById(R.id.tvTotal);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvStatus = findViewById(R.id.tvStatus);
        btnSkip = findViewById(R.id.btnSkip);
        statusSpinner = findViewById(R.id.statusSpinner);
        cardQR = findViewById(R.id.cardQR);
        reviewSection = findViewById(R.id.reviewSection);
        completionOverlay = findViewById(R.id.completionOverlay);
        completionCircle = findViewById(R.id.completionCircle);
        headerSection = findViewById(R.id.headerSection);
        statusRow = findViewById(R.id.statusRow);
        ratingReview = findViewById(R.id.ratingReview);
        etReviewComment = findViewById(R.id.etReviewComment);
        btnSubmitReview = findViewById(R.id.btnSubmitReview);
        btnViewDetails = findViewById(R.id.btnViewDetails);

        // Skip → go to orders tab
        btnSkip.setOnClickListener(v -> navigateToOrders());

        // View details → open OrderDetailActivity
        btnViewDetails.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra("orderId", orderId);
            startActivity(intent);
            finish();
        });

        // Submit review
        btnSubmitReview.setOnClickListener(v -> submitReview());
    }

    private void loadOrder() {
        mDatabase.child("orders").child(orderId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Order loaded = SafeSnapshot.getValue(snapshot, Order.class);
                        if (loaded != null) {
                            order = loaded;
                            order.setOrderId(snapshot.getKey());
                            displayOrder();
                        } else {
                            ToastUtils.showCustomToast(OrderSuccessActivity.this, "Could not load your order details. Check My Orders.");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load order", error.toException());
                    }
                });
    }

    private void displayOrder() {
        // QR Code
        String qrData = order.getQrCode() != null ? order.getQrCode() : orderId;
        generateQRCode(qrData);

        // OTP
        String otp = order.getOtpCode();
        tvOTP.setText(otp != null ? otp : "------");

        // Summary
        String id = order.getOrderId();
        tvOrderId.setText("#" + (id != null ? id.substring(Math.max(0, id.length() - 8)) : "N/A"));
        tvHotelName.setText(order.getHotelName() != null ? order.getHotelName() : "Restaurant");
        tvTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", order.getTotalAmountValue()));

        // If already completed (user returns to screen), show review directly
        if (order.isCompleted()) {
            showCompletedState(false);
        }
    }

    /**
     * Real-time listener on orderStatus — when hotel scans QR and marks
     * completed, this fires instantly and shows the success overlay.
     */
    private void attachStatusListener() {
        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if (("completed".equals(status) || "delivered".equals(status)) && !completionShown) {
                    completionShown = true;
                    // Reload full order to get latest data
                    mDatabase.child("orders").child(orderId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snap) {
                                    Order reloaded = SafeSnapshot.getValue(snap, Order.class);
                                    if (reloaded != null) {
                                        order = reloaded;
                                        order.setOrderId(snap.getKey());
                                    }
                                    showCompletedState(true);
                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError e) {
                                    showCompletedState(true);
                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Status listener cancelled", error.toException());
            }
        };

        mDatabase.child("orders").child(orderId).child("orderStatus")
                .addValueEventListener(statusListener);
    }

    /**
     * Transition to completed state:
     * 1. Flash full-screen "Pickup Confirmed!" overlay for 2s
     * 2. Then hide overlay, update header, hide QR, show review section
     */
    private void showCompletedState(boolean animate) {
        if (animate) {
            // Show overlay with scale animation
            completionOverlay.setVisibility(View.VISIBLE);
            completionOverlay.setAlpha(0f);
            completionOverlay.animate().alpha(1f).setDuration(300).start();

            if (completionCircle != null) {
                completionCircle.setScaleX(0f);
                completionCircle.setScaleY(0f);
                completionCircle.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(500)
                        .setInterpolator(new OvershootInterpolator())
                        .start();
            }

            // After 2 seconds, dismiss overlay and reveal review
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                completionOverlay.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    completionOverlay.setVisibility(View.GONE);
                    transitionToReviewState();
                }).start();
            }, 2000);
        } else {
            // No animation — direct state
            transitionToReviewState();
        }
    }

    private void transitionToReviewState() {
        // Update header based on order type
        boolean isDelivery = order != null && order.isDelivery();
        tvTitle.setText(isDelivery ? "Delivery Complete!" : "Pickup Complete!");
        tvSubtitle.setText("Thanks for using REPLATE");

        // Hide QR card (no longer needed)
        cardQR.setVisibility(View.GONE);

        // Update status row
        statusSpinner.setVisibility(View.GONE);
        tvStatus.setText(isDelivery ? "📬 Order delivered" : "✅ Order completed");
        tvStatus.setTextColor(getColor(R.color.green_primary));

        // Show review section (if not already reviewed)
        if (order != null && !order.hasReview()) {
            reviewSection.setVisibility(View.VISIBLE);
            reviewSection.setAlpha(0f);
            reviewSection.animate().alpha(1f).setDuration(400).start();
        }

        // Change skip text
        btnSkip.setText("Done");

        // Change button text
        btnViewDetails.setText("View Order Details");
    }

    private void generateQRCode(String data) {
        try {
            int size = 512;
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size);

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            ivQRCode.setImageBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "QR generation failed", e);
        }
    }

    private void playEntranceAnimation() {
        View circle = findViewById(R.id.successCircle);
        View check = findViewById(R.id.ivCheckmark);

        if (circle != null) {
            circle.setScaleX(0f);
            circle.setScaleY(0f);
            circle.animate().scaleX(1f).scaleY(1f)
                    .setDuration(500)
                    .setInterpolator(new OvershootInterpolator())
                    .start();
        }
        if (check != null) {
            check.setAlpha(0f);
            check.animate().alpha(1f).setStartDelay(300).setDuration(400).start();
        }
        if (tvTitle != null) {
            tvTitle.setAlpha(0f);
            tvTitle.animate().alpha(1f).setStartDelay(400).setDuration(400).start();
        }
        if (tvSubtitle != null) {
            tvSubtitle.setAlpha(0f);
            tvSubtitle.animate().alpha(1f).setStartDelay(500).setDuration(400).start();
        }
        if (cardQR != null) {
            cardQR.setAlpha(0f);
            cardQR.animate().alpha(1f).setStartDelay(600).setDuration(500).start();
        }
    }

    private void submitReview() {
        if (order == null || orderId == null) return;

        float rating = ratingReview.getRating();
        if (rating <= 0f) {
            ToastUtils.showCustomToast(this, "Please add a rating");
            return;
        }

        String comment = etReviewComment.getText() != null
                ? etReviewComment.getText().toString().trim() : "";

        btnSubmitReview.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("reviewRating", rating);
        updates.put("reviewComment", comment);
        updates.put("reviewAt", System.currentTimeMillis());
        updates.put("hasReview", true);

        mDatabase.child("orders").child(orderId).updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    // Also save to reviews node
                    String userId = FirebaseAuth.getInstance().getUid();
                    if (userId != null) {
                        Map<String, Object> reviewData = new HashMap<>();
                        reviewData.put("orderId", orderId);
                        reviewData.put("userId", userId);
                        reviewData.put("hotelId", order.getHotelId() != null ? order.getHotelId() : "");
                        reviewData.put("hotelName", order.getHotelName() != null ? order.getHotelName() : "");
                        reviewData.put("rating", rating);
                        reviewData.put("comment", comment);
                        reviewData.put("createdAt", System.currentTimeMillis());
                        mDatabase.child("reviews").child(orderId).setValue(reviewData);
                    }

                    // Send notification to hotel
                    pushReviewNotificationToHotel(rating, comment);

                    ToastUtils.showCustomToast(this, "Review submitted!");

                    // Redirect to orders after short delay
                    new Handler(Looper.getMainLooper()).postDelayed(this::navigateToOrders, 800);
                })
                .addOnFailureListener(e -> {
                    btnSubmitReview.setEnabled(true);
                    ToastUtils.showCustomToast(this, "Failed to submit review");
                });
    }

    private void pushReviewNotificationToHotel(float rating, String comment) {
        if (order == null || order.getHotelId() == null || order.getHotelId().trim().isEmpty()) return;

        mDatabase.child("hotels").child(order.getHotelId()).child("userId")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String hotelUserId = snapshot.getValue(String.class);
                        if (hotelUserId == null || hotelUserId.trim().isEmpty()) return;

                        Map<String, Object> notif = new HashMap<>();
                        notif.put("title", "New customer review");
                        notif.put("body", String.format(Locale.getDefault(),
                                "You received %.1f-star feedback", rating));
                        notif.put("type", "review_received");
                        notif.put("orderId", orderId);
                        notif.put("hotelId", order.getHotelId());
                        notif.put("reviewComment", comment != null ? comment : "");
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("read", false);

                        mDatabase.child("notifications").child(hotelUserId).push().setValue(notif);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    /**
     * Play a success bell/notification sound + vibration pattern when order is placed.
     */
    private void playOrderPlacedFeedback() {
        // 1. Play notification bell sound
        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            MediaPlayer mp = MediaPlayer.create(this, soundUri);
            if (mp != null) {
                mp.setOnCompletionListener(MediaPlayer::release);
                mp.start();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not play order success sound", e);
        }

        // 2. Vibrate with a celebratory pattern: short-pause-long
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 100, 80, 200, 80, 300}; // off-on-off-on-off-on
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not vibrate on order success", e);
        }
    }

    private void navigateToOrders() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_tab", "orders");
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove real-time listener
        if (statusListener != null) {
            mDatabase.child("orders").child(orderId).child("orderStatus")
                    .removeEventListener(statusListener);
        }
    }
}

