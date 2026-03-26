package com.replate.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.replate.app.models.Order;
import com.replate.app.utils.TelephonyUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Order Detail Activity - Shows order info, QR code, OTP, light sensor
 * Requirements: QR Code (ZXing), Sensors (Light), Telephony
 */
public class OrderDetailActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvOrderStatus, tvOrderId, tvOTP;
    private TextView tvHotelName, tvHotelAddress, tvPickupTime;
    private TextView tvTotalAmount, tvPaymentMethod, tvPaymentStatus;
    private TextView tvReviewHint, tvReviewSummary;
    private ImageView ivQRCode;
    private Button btnCallHotel;
    private Button btnSmsHotel;
    private Button btnOpenPickupRoute;
    private Button btnSubmitReview;
    private CardView cardQR;
    private android.widget.RatingBar ratingReview;
    private TextInputEditText etReviewComment;
    private View reviewSection;

    private DatabaseReference mDatabase;
    private Order order;

    // Light sensor for QR brightness
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float originalBrightness = -1;
    private String pendingCallNumber;

    private final ActivityResultLauncher<String> callPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingCallNumber != null && !pendingCallNumber.isEmpty()) {
                    TelephonyUtils.callNow(this, pendingCallNumber);
                } else if (!granted) {
                    Toast.makeText(this, "Call permission denied. Opening dialer instead.", Toast.LENGTH_SHORT).show();
                    if (pendingCallNumber != null && !pendingCallNumber.isEmpty()) {
                        TelephonyUtils.openDialer(this, pendingCallNumber);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Setup light sensor (REQUIREMENT: Sensors)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        initViews();

        // Load order from intent or Firebase
        String orderId = getIntent().getStringExtra("orderId");
        Order intentOrder = (Order) getIntent().getSerializableExtra("order");

        if (orderId != null) {
            loadOrderFromFirebase(orderId);
        } else if (intentOrder != null) {
            order = intentOrder;
            displayOrderDetails();
        } else {
            finish();
        }
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        tvOrderStatus = findViewById(R.id.tvOrderStatus);
        tvOrderId = findViewById(R.id.tvOrderId);
        tvOTP = findViewById(R.id.tvOTP);
        tvHotelName = findViewById(R.id.tvHotelName);
        tvHotelAddress = findViewById(R.id.tvHotelAddress);
        tvPickupTime = findViewById(R.id.tvPickupTime);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod);
        tvPaymentStatus = findViewById(R.id.tvPaymentStatus);
        ivQRCode = findViewById(R.id.ivQRCode);
        btnCallHotel = findViewById(R.id.btnCallHotel);
        btnSmsHotel = findViewById(R.id.btnSmsHotel);
        btnOpenPickupRoute = findViewById(R.id.btnOpenPickupRoute);
        cardQR = findViewById(R.id.cardQR);
        reviewSection = findViewById(R.id.reviewSection);
        ratingReview = findViewById(R.id.ratingReview);
        etReviewComment = findViewById(R.id.etReviewComment);
        btnSubmitReview = findViewById(R.id.btnSubmitReview);
        tvReviewHint = findViewById(R.id.tvReviewHint);
        tvReviewSummary = findViewById(R.id.tvReviewSummary);

        ivBack.setOnClickListener(v -> finish());

        // TELEPHONY REQUIREMENT
        btnCallHotel.setOnClickListener(v -> callHotel());
        if (btnSmsHotel != null) {
            btnSmsHotel.setOnClickListener(v -> smsHotel());
        }
        if (btnOpenPickupRoute != null) {
            btnOpenPickupRoute.setOnClickListener(v -> openPickupRoute());
        }
        if (btnSubmitReview != null) {
            btnSubmitReview.setOnClickListener(v -> submitReview());
        }
    }

    private void openPickupRoute() {
        if (order == null || order.getHotelId() == null || order.getHotelId().trim().isEmpty()) {
            Toast.makeText(this, "Hotel route not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_tab", "map");
        intent.putExtra("focus_hotel_id", order.getHotelId());
        startActivity(intent);
    }

    private void loadOrderFromFirebase(String orderId) {
        mDatabase.child("orders").child(orderId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        order = snapshot.getValue(Order.class);
                        if (order != null) {
                            order.setOrderId(snapshot.getKey());
                            displayOrderDetails();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(OrderDetailActivity.this, "Failed to load order",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void displayOrderDetails() {
        String id = order.getOrderId();
        tvOrderId.setText("Order #" + (id != null ? id.substring(Math.max(0, id.length() - 8)) : "N/A"));

        String status = order.getOrderStatus();
        if (status != null) {
            switch (status) {
                case "pending":
                    tvOrderStatus.setText("⏳ Pending Confirmation");
                    break;
                case "pending_pickup":
                    tvOrderStatus.setText("🛍️ Pending Pickup");
                    break;
                case "confirmed":
                    tvOrderStatus.setText("✅ Order Confirmed");
                    break;
                case "ready":
                    tvOrderStatus.setText("🍽️ Ready for Pickup");
                    break;
                case "completed":
                    tvOrderStatus.setText("🎉 Order Completed");
                    break;
                case "cancelled":
                    tvOrderStatus.setText("❌ Order Cancelled");
                    break;
                default:
                    tvOrderStatus.setText("📋 " + order.getStatusDisplay());
            }
        }

        tvHotelName.setText(order.getHotelName() != null ? order.getHotelName() : "Restaurant");
        tvHotelAddress.setText(order.getHotelAddress() != null && !order.getHotelAddress().trim().isEmpty()
                ? order.getHotelAddress()
                : "Pickup address will be shared by the hotel");
        tvPickupTime.setText("Pickup: " + (order.getPickupTime() != null ? order.getPickupTime() : "N/A"));

        tvTotalAmount.setText(String.format(Locale.getDefault(), "LKR %.2f", order.getTotalAmountValue()));
        tvPaymentMethod.setText(order.getPaymentMethod() != null ? order.getPaymentMethod() : "N/A");
        tvPaymentStatus.setText(order.getPaymentStatus() != null ? order.getPaymentStatus() : "N/A");

        String otp = order.getOtpCode();
        tvOTP.setText(otp != null ? otp : "------");

        String qrData = order.getQrCode() != null ? order.getQrCode() : order.getOrderId();
        generateQRCode(qrData);
        updateReviewSection();
    }

    private void updateReviewSection() {
        if (reviewSection == null || order == null) return;

        boolean completed = order.isCompleted();
        boolean hasReview = order.hasReview();

        if (!completed) {
            reviewSection.setVisibility(View.GONE);
            return;
        }

        reviewSection.setVisibility(View.VISIBLE);

        if (hasReview) {
            float rating = order.getReviewRatingValue();
            ratingReview.setRating(rating);
            ratingReview.setIsIndicator(true);
            if (order.getReviewComment() != null && !order.getReviewComment().trim().isEmpty()) {
                etReviewComment.setText(order.getReviewComment());
            }
            etReviewComment.setEnabled(false);
            btnSubmitReview.setEnabled(false);
            btnSubmitReview.setAlpha(0.6f);
            tvReviewHint.setText("Thanks for your feedback! You already reviewed this order.");
            tvReviewSummary.setText(String.format(Locale.getDefault(), "Your rating: %.1f / 5", rating));
        } else {
            ratingReview.setIsIndicator(false);
            ratingReview.setRating(0f);
            etReviewComment.setEnabled(true);
            etReviewComment.setText("");
            btnSubmitReview.setEnabled(true);
            btnSubmitReview.setAlpha(1f);
            tvReviewHint.setText("Rate your pickup experience to help other customers.");
            tvReviewSummary.setText("");
        }
    }

    /**
     * Generate QR Code using ZXing library
     * REQUIREMENT: QR Code generation
     */
    private void generateQRCode(String data) {
        try {
            int size = 400;
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
            e.printStackTrace();
            Toast.makeText(this, "QR Code generation failed", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Call hotel using phone dialer
     * REQUIREMENT: Telephony
     */
    private void callHotel() {
        resolveHotelPhone(phone -> {
            pendingCallNumber = phone;
            new AlertDialog.Builder(this)
                    .setTitle("Contact hotel")
                    .setItems(new CharSequence[]{"Open Dialer", "Call Now"}, (dialog, which) -> {
                        if (which == 0) {
                            TelephonyUtils.openDialer(this, phone);
                        } else {
                            if (TelephonyUtils.hasCallPermission(this)) {
                                TelephonyUtils.callNow(this, phone);
                            } else {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
                            }
                        }
                    })
                    .show();
        });
    }

    private void smsHotel() {
        resolveHotelPhone(phone -> {
            String message = "Hi " + (order != null && order.getHotelName() != null ? order.getHotelName() : "Hotel")
                    + ", I am contacting about my order "
                    + (order != null && order.getOrderId() != null ? order.getOrderId() : "")
                    + " in REPLATE.";
            try {
                TelephonyUtils.openSms(this, phone, message);
            } catch (Exception e) {
                Toast.makeText(this, "No SMS app available", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resolveHotelPhone(PhoneCallback callback) {
        if (order == null || order.getHotelId() == null || order.getHotelId().trim().isEmpty()) {
            callback.onPhone("0112345678");
            return;
        }

        mDatabase.child("hotels").child(order.getHotelId()).child("phone")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String phone = TelephonyUtils.normalizePhone(snapshot.getValue(String.class), "0112345678");
                        callback.onPhone(phone);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onPhone("0112345678");
                    }
                });
    }

    private interface PhoneCallback {
        void onPhone(String phone);
    }

    /**
     * Light sensor callback - adjust brightness for QR scanning
     * REQUIREMENT: Sensors Controlling
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];

            // If ambient light is low (< 50 lux), increase screen brightness
            WindowManager.LayoutParams params = getWindow().getAttributes();
            if (lux < 50) {
                params.screenBrightness = 1.0f; // Max brightness for QR display
            } else {
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            }
            getWindow().setAttributes(params);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        // Reset brightness
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(params);
    }

    private void submitReview() {
        if (order == null || order.getOrderId() == null) return;
        if (!order.isCompleted()) {
            Toast.makeText(this, "You can review only completed orders", Toast.LENGTH_SHORT).show();
            return;
        }

        float rating = ratingReview.getRating();
        if (rating <= 0f) {
            Toast.makeText(this, "Please add a rating", Toast.LENGTH_SHORT).show();
            return;
        }

        String comment = etReviewComment.getText() != null
                ? etReviewComment.getText().toString().trim()
                : "";

        btnSubmitReview.setEnabled(false);

        Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("reviewRating", rating);
        updates.put("reviewComment", comment);
        updates.put("reviewAt", System.currentTimeMillis());
        updates.put("hasReview", true);

        mDatabase.child("orders").child(order.getOrderId()).updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    String userId = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                    if (userId != null) {
                        Map<String, Object> reviewData = new java.util.HashMap<>();
                        reviewData.put("orderId", order.getOrderId());
                        reviewData.put("userId", userId);
                        reviewData.put("hotelId", order.getHotelId() != null ? order.getHotelId() : "");
                        reviewData.put("hotelName", order.getHotelName() != null ? order.getHotelName() : "");
                        reviewData.put("rating", rating);
                        reviewData.put("comment", comment);
                        reviewData.put("createdAt", System.currentTimeMillis());
                        mDatabase.child("reviews").child(order.getOrderId()).setValue(reviewData);
                    }

                    pushReviewNotificationToHotel(rating, comment);

                    order.setReviewRating(rating);
                    order.setReviewComment(comment);
                    order.setReviewAt(System.currentTimeMillis());
                    order.setHasReview(true);
                    updateReviewSection();

                    Toast.makeText(this, "Review submitted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    btnSubmitReview.setEnabled(true);
                    Toast.makeText(this, "Failed to save review: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

                        Map<String, Object> notif = new java.util.HashMap<>();
                        notif.put("title", "New customer review");
                        notif.put("body", String.format(Locale.getDefault(), "You received %.1f-star feedback", rating));
                        notif.put("type", "review_received");
                        notif.put("orderId", order.getOrderId() != null ? order.getOrderId() : "");
                        notif.put("hotelId", order.getHotelId());
                        notif.put("reviewComment", comment != null ? comment : "");
                        notif.put("timestamp", System.currentTimeMillis());
                        notif.put("read", false);

                        mDatabase.child("notifications").child(hotelUserId).push().setValue(notif);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }
}
