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
import com.replate.app.utils.Money;
import com.replate.app.models.Order;
import com.replate.app.utils.SafeSnapshot;
import com.replate.app.utils.CallHelper;
import com.replate.app.utils.HotelNavigation;
import com.replate.app.utils.OrderTimeline;
import com.replate.app.utils.TelephonyUtils;
import com.replate.app.utils.ToastUtils;

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
    private Button btnNavigateHotel;
    private Button btnSubmitReview;
    private CardView cardQR;
    private android.widget.RatingBar ratingReview;
    private TextInputEditText etReviewComment;
    private View reviewSection;

    private boolean reviewFormPrepared;
    private CallHelper callHelper;
    private String lastQrData;

    private DatabaseReference mDatabase;
    private Order order;
    private ValueEventListener orderListener;
    private String currentOrderId;

    // Light sensor for QR brightness
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float originalBrightness = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        callHelper = new CallHelper(this);
        setContentView(R.layout.activity_order_detail);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Setup light sensor (REQUIREMENT: Sensors)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        initViews();

        // Load order from intent or Firebase
        String orderId = getIntent().getStringExtra("orderId");
        Order intentOrder = (Order) getIntent().getSerializableExtra("order");

        // Show a passed-in order straight away, then follow it live so the status never goes stale
        if (intentOrder != null) {
            order = intentOrder;
            displayOrderDetails();
            if (orderId == null) {
                orderId = intentOrder.getOrderId();
            }
        }
        if (orderId != null && !orderId.trim().isEmpty()) {
            loadOrderFromFirebase(orderId);
        } else if (intentOrder == null) {
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
        btnNavigateHotel = findViewById(R.id.btnNavigateHotel);
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
        if (btnNavigateHotel != null) {
            btnNavigateHotel.setOnClickListener(v -> navigateToHotel());
        }
        if (btnSubmitReview != null) {
            btnSubmitReview.setOnClickListener(v -> submitReview());
        }
    }

    /**
     * Hands over to Google Maps for turn-by-turn directions to the hotel (app integration). Falls
     * back to any map app, and finally to the in-app route when no maps app is installed.
     */
    private void navigateToHotel() {
        if (order == null || order.getHotelId() == null || order.getHotelId().trim().isEmpty()) {
            ToastUtils.showCustomToast(this, R.string.order_detail_hotel_route_not_available);
            return;
        }

        mDatabase.child("hotels").child(order.getHotelId())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        double lat = readCoordinate(snapshot, "latitude", "lat", "hotelLatitude");
                        double lng = readCoordinate(snapshot, "longitude", "lng", "hotelLongitude", "long");
                        if (HotelNavigation.hasLocation(lat, lng)) {
                            launchMapsNavigation(lat, lng);
                        } else {
                            openPickupRoute();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        openPickupRoute();
                    }
                });
    }

    private void launchMapsNavigation(double lat, double lng) {
        Intent googleMaps = new Intent(Intent.ACTION_VIEW, Uri.parse(HotelNavigation.navigationUri(lat, lng)));
        googleMaps.setPackage("com.google.android.apps.maps");
        try {
            startActivity(googleMaps);
            return;
        } catch (android.content.ActivityNotFoundException ignored) {
            // Google Maps is not installed; try any other map app
        }

        String hotelName = order != null ? order.getHotelName() : null;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(HotelNavigation.geoUri(lat, lng, hotelName))));
        } catch (android.content.ActivityNotFoundException e) {
            ToastUtils.showCustomToast(this, R.string.order_detail_no_maps_app);
            openPickupRoute();
        }
    }

    private static double readCoordinate(DataSnapshot hotel, String... keys) {
        for (String key : keys) {
            Object raw = hotel.child(key).getValue();
            double value = 0;
            if (raw instanceof Number) {
                value = ((Number) raw).doubleValue();
            } else if (raw instanceof String) {
                try {
                    value = Double.parseDouble(((String) raw).trim());
                } catch (NumberFormatException ignored) {
                    // not a number; try the next key
                }
            }
            if (value != 0) {
                return value;
            }
        }
        return 0;
    }

    private void openPickupRoute() {
        if (order == null || order.getHotelId() == null || order.getHotelId().trim().isEmpty()) {
            ToastUtils.showCustomToast(this, R.string.order_detail_hotel_route_not_available);
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("open_tab", "map");
        intent.putExtra("focus_hotel_id", order.getHotelId());
        startActivity(intent);
    }

    private void loadOrderFromFirebase(String orderId) {
        currentOrderId = orderId;

        // Avoid duplicate listeners if this method is called again.
        if (orderListener != null && currentOrderId != null) {
            mDatabase.child("orders").child(currentOrderId).removeEventListener(orderListener);
        }

        orderListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Order loaded = SafeSnapshot.getValue(snapshot, Order.class);
                if (loaded != null) {
                    order = loaded;
                    order.setOrderId(snapshot.getKey());
                    displayOrderDetails();
                } else {
                    ToastUtils.showCustomToast(OrderDetailActivity.this, R.string.order_detail_this_order_could_not_be);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                ToastUtils.showCustomToast(OrderDetailActivity.this, R.string.order_detail_failed_to_load_order);
            }
        };

        mDatabase.child("orders").child(orderId).addValueEventListener(orderListener);
    }

    private void displayOrderDetails() {
        String id = order.getOrderId();
        tvOrderId.setText("Order #" + (id != null ? id.substring(Math.max(0, id.length() - 8)) : "N/A"));

        String status = order.getOrderStatus();
        if (status == null) {
            // Without this the layout's placeholder "Order Confirmed" would stay on screen
            tvOrderStatus.setText(order.getStatusDisplay());
        } else {
            switch (status) {
                case "pending":
                    tvOrderStatus.setText(R.string.order_detail_pending_confirmation);
                    break;
                case "pending_pickup":
                    tvOrderStatus.setText(R.string.order_detail_pending_pickup);
                    break;
                case "pending_delivery":
                    tvOrderStatus.setText(R.string.order_detail_pending_delivery);
                    break;
                case "confirmed":
                    tvOrderStatus.setText(R.string.order_detail_order_confirmed_2);
                    break;
                case "preparing":
                    tvOrderStatus.setText(R.string.order_detail_preparing);
                    break;
                case "ready":
                    tvOrderStatus.setText(R.string.order_detail_ready_for_pickup);
                    break;
                case "out_for_delivery":
                    tvOrderStatus.setText(R.string.order_detail_out_for_delivery);
                    break;
                case "delivered":
                    tvOrderStatus.setText(R.string.order_detail_delivered);
                    break;
                case "completed":
                    tvOrderStatus.setText(R.string.order_detail_order_completed);
                    break;
                case "cancelled":
                    tvOrderStatus.setText("hotel".equals(order.getCancelledBy())
                            ? R.string.order_detail_cancelled_by_hotel
                            : R.string.order_detail_order_cancelled);
                    break;
                default:
                    tvOrderStatus.setText("📋 " + order.getStatusDisplay());
            }
        }

        tvHotelName.setText(order.getHotelName() != null ? order.getHotelName() : "Restaurant");
        tvHotelAddress.setText(order.getHotelAddress() != null && !order.getHotelAddress().trim().isEmpty()
                ? order.getHotelAddress()
                : "Pickup address will be shared by the hotel");
        loadHotelAddressIfMissing();

        // Show delivery address or pickup time depending on order type
        if (order.isDelivery()) {
            String addr = order.getDeliveryAddress();
            if (addr != null && !addr.isEmpty() && !"Pickup at Hotel".equalsIgnoreCase(addr)) {
                tvPickupTime.setText("📍 Delivery to: " + addr);
            } else {
                tvPickupTime.setText(R.string.order_detail_delivery_order);
            }
        } else {
            tvPickupTime.setText("Pickup: " + (order.getPickupTime() != null ? order.getPickupTime() : "N/A"));
        }

        tvTotalAmount.setText(Money.format(order.getTotalAmountValue()));
        tvPaymentMethod.setText(order.getPaymentMethod() != null ? order.getPaymentMethod() : "N/A");
        tvPaymentStatus.setText(order.getPaymentStatus() != null ? order.getPaymentStatus() : "N/A");

        String otp = order.getOtpCode();
        tvOTP.setText(otp != null ? otp : "------");

        // The pickup QR and OTP are only useful while the order can still be collected and its
        // pickup window is open; after that the hotel panel rejects them anyway
        boolean pickupExpired = order.isPickupExpired(System.currentTimeMillis());
        boolean showQr = order.isActive() && !pickupExpired;
        if (cardQR != null) cardQR.setVisibility(showQr ? View.VISIBLE : View.GONE);
        if (showQr) {
            String qrData = order.getQrCode() != null ? order.getQrCode() : order.getOrderId();
            generateQRCode(qrData);
        }
        if (pickupExpired) {
            tvOrderStatus.setText(R.string.order_detail_pickup_expired);
            tvPickupTime.setText(getString(R.string.order_detail_pickup_expired_hint,
                    order.getPickupTime() != null ? order.getPickupTime() : ""));
        }

        // Cancelled orders get a red banner; the route to the hotel makes no sense for delivery orders
        View statusBanner = findViewById(R.id.statusBanner);
        if (statusBanner != null) {
            statusBanner.setBackgroundColor(getColor(order.isCancelled()
                    ? R.color.md_theme_error : R.color.overlay_white_10));
        }
        if (btnOpenPickupRoute != null) {
            btnOpenPickupRoute.setVisibility(order.isDelivery() ? View.GONE : View.VISIBLE);
        }

        showOrderItems();
        buildTimeline();
        updateReviewSection();
    }

    /**
     * A row of dots from "placed" to "collected" or "delivered", filled up to the step the order has
     * reached, so the customer sees progress at a glance instead of reading a status sentence.
     * Cancelled orders have no timeline.
     */
    private void buildTimeline() {
        View card = findViewById(R.id.cardTimeline);
        LinearLayout row = findViewById(R.id.layoutTimeline);
        if (card == null || row == null || order == null) return;

        int current = OrderTimeline.currentStep(order.getOrderStatus());
        if (current < 0) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        row.removeAllViews();

        boolean delivery = order.isDelivery();
        int lineHeight = (int) (3 * getResources().getDisplayMetrics().density);
        int doneColor = getColor(R.color.primary);
        int todoColor = getColor(R.color.divider);

        for (int step = 0; step < OrderTimeline.STEP_COUNT; step++) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            column.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout dotRow = new LinearLayout(this);
            dotRow.setOrientation(LinearLayout.HORIZONTAL);
            dotRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            dotRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // The line on the left is "done" once this step is reached; the line on the right once the next one is
            View left = new View(this);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, lineHeight, 1f));
            left.setBackgroundColor(step <= current ? doneColor : todoColor);
            left.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);

            ImageView dot = new ImageView(this);
            int dotSize = (int) (24 * getResources().getDisplayMetrics().density);
            dot.setLayoutParams(new LinearLayout.LayoutParams(dotSize, dotSize));
            int stateCd;
            if (step < current) {
                dot.setImageResource(R.drawable.timeline_dot_done);
                stateCd = R.string.timeline_step_done_cd;
            } else if (step == current) {
                dot.setImageResource(current == OrderTimeline.STEP_COUNT - 1
                        ? R.drawable.timeline_dot_done : R.drawable.timeline_dot_current);
                stateCd = R.string.timeline_step_current_cd;
            } else {
                dot.setImageResource(R.drawable.timeline_dot_todo);
                stateCd = R.string.timeline_step_todo_cd;
            }
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            View right = new View(this);
            right.setLayoutParams(new LinearLayout.LayoutParams(0, lineHeight, 1f));
            right.setBackgroundColor(step < current ? doneColor : todoColor);
            right.setVisibility(step == OrderTimeline.STEP_COUNT - 1 ? View.INVISIBLE : View.VISIBLE);

            dotRow.addView(left);
            dotRow.addView(dot);
            dotRow.addView(right);

            String label = getString(OrderTimeline.labelFor(step, delivery));
            TextView text = new TextView(this);
            text.setText(label);
            text.setGravity(android.view.Gravity.CENTER);
            text.setMaxLines(2);
            text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_caption));
            text.setTextColor(getColor(step == current ? R.color.primary : R.color.text_secondary));
            if (step == current) {
                text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
            }
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            textParams.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
            text.setLayoutParams(textParams);

            column.setContentDescription(getString(stateCd, label));
            column.addView(dotRow);
            column.addView(text);
            row.addView(column);
        }
    }

    private void showOrderItems() {
        View card = findViewById(R.id.cardOrderItems);
        LinearLayout list = findViewById(R.id.layoutOrderItems);
        if (card == null || list == null) return;
        list.removeAllViews();

        Object rawItems = order.getItems();
        java.util.Collection<?> entries = rawItems instanceof java.util.List
                ? (java.util.List<?>) rawItems
                : rawItems instanceof Map ? ((Map<?, ?>) rawItems).values() : null;
        if (entries == null || entries.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }

        for (Object entry : entries) {
            if (!(entry instanceof Map)) continue;
            Map<?, ?> item = (Map<?, ?>) entry;
            Object nameValue = item.get("foodName");
            String name = nameValue != null && !String.valueOf(nameValue).trim().isEmpty()
                    ? String.valueOf(nameValue) : getString(R.string.order_detail_item_fallback);
            int quantity = Math.max(1, (int) numberOf(item.get("quantity")));
            double price = numberOf(item.get("price"));
            addOrderLine(list, getString(R.string.order_detail_item_line, quantity, name),
                    Money.format(price * quantity), false, 0);
        }

        double subtotal = order.getSubtotalAmountValue();
        double deliveryFee = order.getDeliveryFeeValue();
        double savings = order.getSavingsAmountValue();
        if (subtotal > 0) {
            addOrderLine(list, getString(R.string.order_detail_subtotal), Money.format(subtotal), false, 0);
        }
        if (deliveryFee > 0) {
            addOrderLine(list, getString(R.string.order_detail_delivery_fee), Money.format(deliveryFee), false, 0);
        }
        if (savings > 0) {
            addOrderLine(list, getString(R.string.order_detail_you_saved), "-" + Money.format(savings), false,
                    getColor(R.color.primary));
        }
        addOrderLine(list, getString(R.string.order_detail_order_total),
                Money.format(order.getTotalAmountValue()), true, 0);
        card.setVisibility(View.VISIBLE);
    }

    private void addOrderLine(LinearLayout list, String label, String value, boolean emphasised, int color) {
        View row = getLayoutInflater().inflate(R.layout.item_order_line, list, false);
        TextView tvLabel = row.findViewById(R.id.tvLineLabel);
        TextView tvValue = row.findViewById(R.id.tvLineValue);
        tvLabel.setText(label);
        tvValue.setText(value);
        if (color != 0) {
            tvValue.setTextColor(color);
        }
        if (emphasised) {
            tvLabel.setTypeface(tvLabel.getTypeface(), android.graphics.Typeface.BOLD);
            tvValue.setTypeface(tvValue.getTypeface(), android.graphics.Typeface.BOLD);
        }
        list.addView(row);
    }

    private static double numberOf(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value == null ? 0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateReviewSection() {
        if (reviewSection == null || order == null) return;

        boolean completed = order.isCompleted(); // includes "completed" and "delivered"
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
            tvReviewHint.setText(R.string.order_detail_thanks_for_your_feedback_you);
            tvReviewSummary.setText(String.format(Locale.getDefault(), "Your rating: %.1f / 5", rating));
        } else {
            ratingReview.setIsIndicator(false);
            // Live order updates call this again; only clear the form once so a half-typed review survives
            if (!reviewFormPrepared) {
                ratingReview.setRating(0f);
                etReviewComment.setText("");
                reviewFormPrepared = true;
            }
            etReviewComment.setEnabled(true);
            btnSubmitReview.setEnabled(true);
            btnSubmitReview.setAlpha(1f);
            tvReviewHint.setText(R.string.order_detail_rate_your_pickup_experience_to);
            tvReviewSummary.setText("");
        }
    }

    /**
     * Generate QR Code using ZXing library
     * REQUIREMENT: QR Code generation
     */
    private void generateQRCode(String data) {
        // Live order updates call this on every change; the code only needs rebuilding when its data changes
        if (data == null || data.equals(lastQrData)) return;
        try {
            int size = 400;
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size);

            // One bulk write instead of size*size setPixel calls, which was noticeably slow on the main thread
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int row = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[row + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565);

            ivQRCode.setImageBitmap(bitmap);
            lastQrData = data;
        } catch (Exception e) {
            android.util.Log.w("OrderDetail", "QR generation failed", e);
            ToastUtils.showCustomToast(this, R.string.order_detail_qr_code_generation_failed);
        }
    }

    /**
     * Call hotel using phone dialer
     * REQUIREMENT: Telephony
     */
    private void callHotel() {
        resolveHotelPhone(phone -> {
            // Direct call when CALL_PHONE is granted (asked for here), dialer otherwise
            callHelper.call(phone, () ->
                    ToastUtils.showCustomToast(this, R.string.order_detail_no_phone_app_available));
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
                ToastUtils.showCustomToast(this, R.string.order_detail_no_sms_app_available);
            }
        });
    }

    private void resolveHotelPhone(PhoneCallback callback) {
        // Never fall back to a made-up number: the customer would end up calling a stranger.
        PhoneCallback onlyRealNumber = phone -> {
            if (phone == null || phone.isEmpty()) {
                ToastUtils.showCustomToast(this, R.string.order_detail_this_hotel_hasn_t_shared);
            } else {
                callback.onPhone(phone);
            }
        };

        if (order == null || order.getHotelId() == null || order.getHotelId().trim().isEmpty()) {
            onlyRealNumber.onPhone("");
            return;
        }

        mDatabase.child("hotels").child(order.getHotelId()).child("phone")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String raw = null;
                        try {
                            raw = snapshot.getValue(String.class);
                        } catch (Exception ignored) {
                            // phone stored as a number instead of text; treat as missing
                        }
                        onlyRealNumber.onPhone(TelephonyUtils.normalizePhone(raw, ""));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        onlyRealNumber.onPhone("");
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
            ToastUtils.showCustomToast(this, R.string.order_detail_you_can_review_only_completed);
            return;
        }

        float rating = ratingReview.getRating();
        if (rating <= 0f) {
            ToastUtils.showCustomToast(this, R.string.order_detail_please_add_a_rating);
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
                        com.replate.app.utils.ReviewWriter.saveOrderReview(mDatabase, order, userId, rating, comment);
                    }

                    pushReviewNotificationToHotel(rating, comment);

                    order.setReviewRating(rating);
                    order.setReviewComment(comment);
                    order.setReviewAt(System.currentTimeMillis());
                    order.setHasReview(true);
                    updateReviewSection();

                    ToastUtils.showCustomToast(this, R.string.order_detail_review_submitted_successfully);
                })
                .addOnFailureListener(e -> {
                    btnSubmitReview.setEnabled(true);
                    ToastUtils.showCustomToast(this, R.string.order_success_failed_to_submit_review);
                });
    }

    // Orders do not store the hotel address, so read it from the hotel record when it is missing
    private void loadHotelAddressIfMissing() {
        if (order == null || order.getHotelId() == null || order.getHotelId().trim().isEmpty()) return;
        if (order.getHotelAddress() != null && !order.getHotelAddress().trim().isEmpty()) return;
        mDatabase.child("hotels").child(order.getHotelId()).child("address")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String address = com.replate.app.utils.SafeSnapshot.string(snapshot);
                        if (address != null && !isFinishing() && !isDestroyed()) {
                            tvHotelAddress.setText(address);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // The placeholder text stays
                    }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orderListener != null && currentOrderId != null) {
            mDatabase.child("orders").child(currentOrderId).removeEventListener(orderListener);
        }
    }
}
