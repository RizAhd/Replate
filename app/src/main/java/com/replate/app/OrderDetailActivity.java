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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.replate.app.models.Order;

import java.util.Locale;

/**
 * Order Detail Activity - Shows order info, QR code, OTP, light sensor
 * Requirements: QR Code (ZXing), Sensors (Light), Telephony
 */
public class OrderDetailActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvOrderStatus, tvOrderId, tvOTP;
    private TextView tvHotelName, tvHotelAddress, tvPickupTime;
    private TextView tvTotalAmount, tvPaymentMethod, tvPaymentStatus;
    private ImageView ivQRCode;
    private Button btnCallHotel;
    private Button btnOpenPickupRoute;
    private CardView cardQR;

    private DatabaseReference mDatabase;
    private Order order;

    // Light sensor for QR brightness
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float originalBrightness = -1;

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
        btnOpenPickupRoute = findViewById(R.id.btnOpenPickupRoute);
        cardQR = findViewById(R.id.cardQR);

        ivBack.setOnClickListener(v -> finish());

        // TELEPHONY REQUIREMENT
        btnCallHotel.setOnClickListener(v -> callHotel());
        if (btnOpenPickupRoute != null) {
            btnOpenPickupRoute.setOnClickListener(v -> openPickupRoute());
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
        // Get hotel phone from Firebase, fallback to default
        if (order != null && order.getHotelId() != null) {
            mDatabase.child("hotels").child(order.getHotelId()).child("phone")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String phone = snapshot.getValue(String.class);
                            if (phone == null || phone.isEmpty()) phone = "0112345678";
                            dialPhone(phone);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            dialPhone("0112345678");
                        }
                    });
        } else {
            dialPhone("0112345678");
        }
    }

    private void dialPhone(String phone) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open dialer", Toast.LENGTH_SHORT).show();
        }
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
}
