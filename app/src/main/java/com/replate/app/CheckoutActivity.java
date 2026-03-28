package com.replate.app;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.utils.ToastUtils;

import java.io.FileOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import lk.payhere.androidsdk.PHConfigs;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.Item;

public class CheckoutActivity extends AppCompatActivity {

    private static final String TAG = "PAYHERE_CHECKOUT";
    private static final int PAYHERE_REQUEST = 11001;

    // ── Secrets loaded from local.properties via BuildConfig ────────────────
    private static final String MERCHANT_ID     = BuildConfig.PAYHERE_MERCHANT_ID;
    private static final String MERCHANT_SECRET = BuildConfig.PAYHERE_MERCHANT_SECRET;

    private static final double DELIVERY_FEE_LKR = 250.0;

    private LinearLayout orderItemsContainer;
    private TextView tvSubtotal, tvSavings, tvTotal, tvDeliveryFee;
    private TextInputEditText etPickupTime, etCustomerName, etCustomerPhone, etAddress;
    private RadioGroup rgPayment, rgOrderType;
    private View cardDeliveryAddress;
    private View rowDeliveryFee;
    private CardView cardPickupTime;
    private Button btnPlaceOrder;
    private ProgressBar progressBar;
    private android.widget.RadioButton rbCash;
    private com.google.android.material.button.MaterialButton btnPickAddressFromMap;

    private ActivityResultLauncher<Intent> mapPickerLauncher;
    private ActivityResultLauncher<Intent> payHereLauncher;

    private CartDatabaseHelper cartDb;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private List<CartItem> cartItems;
    private double subtotalAmount = 0;
    private double totalAmount = 0;
    private double totalSavings = 0;
    private double deliveryFee = 0;
    private double selectedDeliveryLat = 0;
    private double selectedDeliveryLng = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        cartDb = CartDatabaseHelper.getInstance(this);
        mDatabase = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        initViews();
        initResultLaunchers();
        loadOrderSummary();
        loadUserData();
    }

    private void initResultLaunchers() {
        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String address = result.getData().getStringExtra("address");
                        selectedDeliveryLat = result.getData().getDoubleExtra("lat", 0);
                        selectedDeliveryLng = result.getData().getDoubleExtra("lng", 0);
                        if (address != null && !address.trim().isEmpty()) {
                            etAddress.setText(address);
                        }
                    }
                }
        );

        payHereLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handlePayHereResult(result.getResultCode(), result.getData())
        );
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        orderItemsContainer = findViewById(R.id.orderItemsContainer);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvSavings = findViewById(R.id.tvSavings);
        tvTotal = findViewById(R.id.tvTotal);
        etPickupTime = findViewById(R.id.etPickupTime);
        etCustomerName = findViewById(R.id.etCustomerName);
        etCustomerPhone = findViewById(R.id.etCustomerPhone);

        etAddress = findViewById(R.id.etAddress);
        rgOrderType = findViewById(R.id.rgOrderType);
        cardDeliveryAddress = findViewById(R.id.cardDeliveryAddress);
        cardPickupTime = findViewById(R.id.cardPickupTime);
        rbCash = findViewById(R.id.rbCash);

        rgPayment = findViewById(R.id.rgPayment);
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
        progressBar = findViewById(R.id.progressBar);
        btnPickAddressFromMap = findViewById(R.id.btnPickAddressFromMap);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        rowDeliveryFee = findViewById(R.id.rowDeliveryFee);

        ivBack.setOnClickListener(v -> finish());
        etPickupTime.setOnClickListener(v -> showTimePicker());
        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        if (btnPickAddressFromMap != null) {
            btnPickAddressFromMap.setOnClickListener(v -> {
                Intent i = new Intent(this, LocationPickerActivity.class);
                mapPickerLauncher.launch(i);
            });
        }

        rgOrderType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isDelivery = checkedId == R.id.rbDelivery;
            if (isDelivery) {
                cardDeliveryAddress.setVisibility(View.VISIBLE);
                if (cardPickupTime != null) cardPickupTime.setVisibility(View.GONE);
                if (rowDeliveryFee != null) rowDeliveryFee.setVisibility(View.VISIBLE);
                etPickupTime.setText("");
                rbCash.setText("Cash on Delivery");
            } else {
                cardDeliveryAddress.setVisibility(View.GONE);
                if (cardPickupTime != null) cardPickupTime.setVisibility(View.VISIBLE);
                if (rowDeliveryFee != null) rowDeliveryFee.setVisibility(View.GONE);
                rbCash.setText("Cash on Pickup");
                selectedDeliveryLat = 0;
                selectedDeliveryLng = 0;
            }
            recalculateTotals();
            updatePlaceOrderButton();
        });

        rgPayment.setOnCheckedChangeListener((group, checkedId) -> updatePlaceOrderButton());
        recalculateTotals();
        updatePlaceOrderButton();
    }

    private void loadOrderSummary() {
        cartItems = cartDb.getAllItems();

        if (cartItems.isEmpty()) {
            ToastUtils.showCustomToast(this, "Cart is empty");
            finish();
            return;
        }

        subtotalAmount = 0;
        totalSavings = 0;
        orderItemsContainer.removeAllViews();

        for (CartItem item : cartItems) {
            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.VERTICAL);
            itemRow.setPadding(0, 0, 0, 16);

            TextView title = new TextView(this);
            title.setText(item.getQuantity() + " × " + item.getFoodName());
            title.setTextSize(15f);
            title.setTextColor(getColor(R.color.text_primary));
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

            TextView subtitle = new TextView(this);
            subtitle.setText(item.getHotelName() + " • LKR " + String.format(Locale.getDefault(), "%.2f", item.getSubtotal()));
            subtitle.setTextSize(13f);
            subtitle.setTextColor(getColor(R.color.text_secondary));
            subtitle.setPadding(0, 4, 0, 0);

            itemRow.addView(title);
            itemRow.addView(subtitle);
            orderItemsContainer.addView(itemRow);

            subtotalAmount += item.getSubtotal();
            totalSavings += (item.getOriginalPrice() - item.getPrice()) * item.getQuantity();
        }

        recalculateTotals();
    }

    private void recalculateTotals() {
        boolean isDelivery = rgOrderType != null && rgOrderType.getCheckedRadioButtonId() == R.id.rbDelivery;
        deliveryFee = isDelivery ? DELIVERY_FEE_LKR : 0;
        totalAmount = subtotalAmount + deliveryFee;

        tvSubtotal.setText(String.format(Locale.getDefault(), "LKR %.2f", subtotalAmount));
        tvSavings.setText(String.format(Locale.getDefault(), "-LKR %.2f", totalSavings));
        if (tvDeliveryFee != null) {
            tvDeliveryFee.setText(String.format(Locale.getDefault(), "LKR %.2f", deliveryFee));
        }
        tvTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", totalAmount));
    }

    private void updatePlaceOrderButton() {
        boolean isDelivery = rgOrderType != null && rgOrderType.getCheckedRadioButtonId() == R.id.rbDelivery;
        boolean isCard = rgPayment != null && rgPayment.getCheckedRadioButtonId() == R.id.rbCard;

        if (isCard) {
            btnPlaceOrder.setText(isDelivery ? "Continue to PayHere" : "Pay & confirm order");
        } else {
            btnPlaceOrder.setText(isDelivery ? "Place delivery order" : "Place pickup order");
        }
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            mDatabase.child("users").child(user.getUid())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String name = snapshot.child("name").getValue(String.class);
                            String phone = snapshot.child("phone").getValue(String.class);
                            if (name != null) etCustomerName.setText(name);
                            if (phone != null) etCustomerPhone.setText(phone);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                        }
                    });
        }
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            etPickupTime.setText(time);
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
    }

    private void placeOrder() {
        String customerName = etCustomerName.getText().toString().trim();
        String customerPhone = etCustomerPhone.getText().toString().trim();
        String pickupTime = etPickupTime.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        boolean isDelivery = rgOrderType.getCheckedRadioButtonId() == R.id.rbDelivery;

        if (customerName.isEmpty()) {
            etCustomerName.setError("Enter your name");
            return;
        }
        if (customerPhone.isEmpty()) {
            etCustomerPhone.setError("Enter phone number");
            return;
        }

        if (!isDelivery && pickupTime.isEmpty()) {
            ToastUtils.showCustomToast(this, "Please select pickup time");
            return;
        }

        if (isDelivery && address.isEmpty()) {
            etAddress.setError("Enter delivery address");
            return;
        }

        recalculateTotals();
        new AlertDialog.Builder(this)
                .setTitle("Confirm Order")
                .setMessage(getString(R.string.checkout_confirm_message, totalAmount))
                .setPositiveButton("Yes, Place Order", (dialog, which) -> {
                    if (rgPayment.getCheckedRadioButtonId() == R.id.rbCard) {
                        startSandboxPayment();
                    } else {
                        submitOrder("Cash", "pending");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startSandboxPayment() {
        InitRequest req = new InitRequest();
        req.setMerchantId(MERCHANT_ID);
        applyMerchantSecretIfSupported(req);
        req.setCurrency("LKR");
        req.setAmount(totalAmount);
        req.setOrderId("RPL-" + System.currentTimeMillis());
        req.setItemsDescription("REPLATE Order");

        String fullName = etCustomerName.getText().toString().trim();
        String[] parts = fullName.split(" ", 2);
        String firstName = parts.length > 0 ? parts[0] : "Customer";
        String lastName = parts.length > 1 ? parts[1] : "User";

        FirebaseUser user = mAuth.getCurrentUser();
        String email = (user != null && user.getEmail() != null) ? user.getEmail() : "test@example.com";

        req.getCustomer().setFirstName(firstName);
        req.getCustomer().setLastName(lastName);
        req.getCustomer().setEmail(email);
        req.getCustomer().setPhone(etCustomerPhone.getText().toString().trim());
        req.getCustomer().getAddress().setAddress(etAddress.getText() != null && etAddress.getText().length() > 0 ? etAddress.getText().toString() : "Colombo");
        req.getCustomer().getAddress().setCity("Colombo");
        req.getCustomer().getAddress().setCountry("Sri Lanka");

        // Do NOT send a fake/unregistered notify URL in sandbox.
        // That can trigger PayHere "unauthorized domain" for merchants that haven't whitelisted it.

        req.getItems().add(new Item(null, "REPLATE Food Order", 1, totalAmount));

        Intent intent = new Intent(this, PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);

        PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL);
        payHereLauncher.launch(intent);
    }

    private void applyMerchantSecretIfSupported(InitRequest req) {
        try {
            Method m = req.getClass().getMethod("setMerchantSecret", String.class);
            m.invoke(req, MERCHANT_SECRET);
            Log.d(TAG, "Applied merchant secret to InitRequest via SDK method");
        } catch (Exception ignore) {
            // SDK version may not expose merchant secret setter; sandbox can still proceed.
        }
    }

    private void handlePayHereResult(int resultCode, @Nullable Intent data) {
        if (data == null || !data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
            if (resultCode == Activity.RESULT_CANCELED) {
                ToastUtils.showCustomToast(this, "Payment canceled");
            }
            return;
        }

        Serializable response = data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);
        String responseText = response != null ? response.toString() : "No response payload";
        String lower = responseText.toLowerCase(Locale.US);

        if (lower.contains("unauthorized domain") || lower.contains("unauthorized")) {
            ToastUtils.showCustomToast(this,
                    "PayHere blocked this request (unauthorized domain). Use sandbox merchant + approved URLs.");
            Log.e(TAG, "PayHere unauthorized response: " + responseText);
            return;
        }

        boolean success = (resultCode == Activity.RESULT_OK);
        if (response != null) {
            try {
                Method isSuccess = response.getClass().getMethod("isSuccess");
                Object v = isSuccess.invoke(response);
                if (v instanceof Boolean) {
                    success = (Boolean) v;
                }
            } catch (Exception ignore) {
                // Keep resultCode fallback.
            }
        }

        if (success) {
            Log.d(TAG, "Payment success: " + responseText);
            submitOrder("PayHere Online", "paid");
        } else if (resultCode == Activity.RESULT_CANCELED) {
            ToastUtils.showCustomToast(this, "Payment canceled: " + responseText);
        } else {
            ToastUtils.showCustomToast(this, "Payment failed: " + responseText);
        }
    }

    private void submitOrder(String paymentMethod, String paymentStatus) {
        showLoading(true);

        String customerName = etCustomerName.getText().toString().trim();
        String customerPhone = etCustomerPhone.getText().toString().trim();
        boolean isDelivery = rgOrderType.getCheckedRadioButtonId() == R.id.rbDelivery;
        String orderType = isDelivery ? "Delivery" : "Pickup";
        String pickupTime = isDelivery ? "N/A" : etPickupTime.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        String otpCode = String.format(Locale.US, "%06d", new Random().nextInt(999999));
        String orderId = mDatabase.child("orders").push().getKey();

        if (orderId == null) {
            showLoading(false);
            return;
        }

        List<Map<String, Object>> itemsList = new ArrayList<>();
        String hotelId = "";
        String hotelName = "";

        for (CartItem item : cartItems) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("foodId", item.getFoodId());
            itemMap.put("foodName", item.getFoodName());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("price", item.getPrice());
            itemMap.put("imageUrl", item.getImageUrl() != null ? item.getImageUrl() : "");
            itemsList.add(itemMap);

            if (hotelId.isEmpty()) {
                hotelId = item.getHotelId() != null ? item.getHotelId() : "";
                hotelName = item.getHotelName();
            }
        }

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", orderId);
        orderData.put("userId", mAuth.getCurrentUser().getUid());
        orderData.put("hotelId", hotelId);
        orderData.put("hotelName", hotelName);
        orderData.put("items", itemsList);
        orderData.put("totalAmount", totalAmount);
        orderData.put("subtotalAmount", subtotalAmount);
        orderData.put("deliveryFee", deliveryFee);
        orderData.put("savingsAmount", totalSavings);
        orderData.put("originalTotal", subtotalAmount + totalSavings + deliveryFee);
        orderData.put("orderStatus", isDelivery ? "pending_delivery" : "pending_pickup");
        orderData.put("paymentStatus", paymentStatus);
        orderData.put("paymentMethod", paymentMethod);
        orderData.put("orderType", orderType);
        orderData.put("deliveryAddress", isDelivery ? address : "Pickup at Hotel");
        if (isDelivery) {
            orderData.put("deliveryLatitude", selectedDeliveryLat);
            orderData.put("deliveryLongitude", selectedDeliveryLng);
        }
        orderData.put("pickupTime", pickupTime);
        orderData.put("qrCode", orderId);
        orderData.put("pickupCode", orderId);
        orderData.put("otpCode", otpCode);
        orderData.put("customerName", customerName);
        orderData.put("customerPhone", customerPhone);
        orderData.put("createdAt", System.currentTimeMillis());
        orderData.put("updatedAt", System.currentTimeMillis());

        final String finalHotelId = hotelId;
        final String finalHotelName = hotelName;

        mDatabase.child("orders").child(orderId).setValue(orderData)
                .addOnSuccessListener(unused -> {
                    showLoading(false);

                    // Decrement food quantity & auto-deactivate if sold out
                    for (CartItem ci : cartItems) {
                        if (ci.getFoodId() != null && !ci.getFoodId().trim().isEmpty()) {
                            decreaseFoodQuantity(ci.getFoodId(), ci.getQuantity());
                        }
                    }

                    cartDb.clearCart();
                    saveReceipt(orderId, otpCode, customerName, totalAmount);
                    playPaymentSound();
                    createOrderNotifications(orderId, finalHotelId, finalHotelName, customerName, totalAmount);

                    com.replate.app.notifications.InAppNotificationCenter.publish(
                            new com.replate.app.notifications.InAppNotificationCenter.InAppNotificationMessage(
                                    "Order placed successfully",
                                    "Your order has been placed and is waiting for pickup.",
                                    com.replate.app.notifications.NotificationCategories.ORDER_PLACED,
                                    "order",
                                    orderId,
                                    finalHotelId,
                                    "",
                                    null
                            )
                    );

                    Intent intent = new Intent(this, OrderSuccessActivity.class);
                    intent.putExtra("orderId", orderId);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();

                    ToastUtils.showCustomToast(this, "Order placed successfully");
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, "Failed to place order: " + e.getMessage());
                });
    }

    private void saveReceipt(String orderId, String otp, String name, double total) {
        try {
            String receipt = "======== REPLATE ORDER RECEIPT ========\n"
                    + "Order ID: " + orderId + "\n"
                    + "Customer: " + name + "\n"
                    + "Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()) + "\n"
                    + "Total: LKR " + String.format(Locale.US, "%.2f", total) + "\n"
                    + "OTP: " + otp + "\n"
                    + "======================================\n";

            FileOutputStream fos = openFileOutput("receipt_" + orderId + ".txt", MODE_PRIVATE);
            fos.write(receipt.getBytes());
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playPaymentSound() {
        try {
            MediaPlayer mp = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            if (mp != null) {
                mp.setOnCompletionListener(MediaPlayer::release);
                mp.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnPlaceOrder.setEnabled(!show);
        btnPlaceOrder.setAlpha(show ? 0.65f : 1f);
    }

    /**
     * Decrease food item quantity in Firebase after successful order.
     * Auto-marks item as sold_out when quantity reaches 0 to prevent overselling.
     */
    private void decreaseFoodQuantity(String foodId, int orderedQty) {
        mDatabase.child("foodItems").child(foodId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Object qtyObj = snapshot.child("quantity").getValue();
                        int currentQty = 0;
                        if (qtyObj instanceof Number) {
                            currentQty = ((Number) qtyObj).intValue();
                        } else if (qtyObj != null) {
                            try { currentQty = Integer.parseInt(String.valueOf(qtyObj)); } catch (Exception ignored) {}
                        }
                        int newQty = Math.max(0, currentQty - orderedQty);
                        mDatabase.child("foodItems").child(foodId).child("quantity").setValue(newQty);
                        if (newQty <= 0) {
                            mDatabase.child("foodItems").child(foodId).child("status").setValue("sold_out");
                            mDatabase.child("foodItems").child(foodId).child("isAvailable").setValue(false);
                            Log.d(TAG, "Food sold out, auto-deactivated: " + foodId);
                        } else {
                            Log.d(TAG, "Food quantity updated: " + foodId + " -> " + newQty);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "Failed to decrease food quantity for " + foodId, error.toException());
                    }
                });
    }

    private void createOrderNotifications(String orderId,
                                          String hotelId,
                                          String hotelName,
                                          String customerName,
                                          double amount) {
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        long now = System.currentTimeMillis();
        String amountText = String.format(Locale.US, "LKR %.2f", amount);

        Map<String, Object> customerNotif = new HashMap<>();
        customerNotif.put("title", "Order confirmed");
        customerNotif.put("body", "Your order at " + (hotelName != null ? hotelName : "hotel") + " is placed. Total " + amountText);
        customerNotif.put("type", "order_confirmed");
        customerNotif.put("category", com.replate.app.notifications.NotificationCategories.ORDER_PLACED);
        customerNotif.put("route", "order");
        customerNotif.put("orderId", orderId);
        customerNotif.put("hotelId", hotelId != null ? hotelId : "");
        customerNotif.put("read", false);
        customerNotif.put("timestamp", now);

        mDatabase.child("notifications").child(currentUserId).push().setValue(customerNotif);

        if (hotelId != null && !hotelId.trim().isEmpty()) {
            mDatabase.child("hotels").child(hotelId).child("userId")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String hotelUserId = snapshot.getValue(String.class);
                            if (hotelUserId == null || hotelUserId.trim().isEmpty()) return;

                            Map<String, Object> hotelNotif = new HashMap<>();
                            hotelNotif.put("title", "New pickup order");
                            hotelNotif.put("body", (customerName != null ? customerName : "A customer") + " placed an order. " + amountText);
                            hotelNotif.put("type", "order_received");
                            hotelNotif.put("category", com.replate.app.notifications.NotificationCategories.HOTEL_NEW_ORDER);
                            hotelNotif.put("route", "order");
                            hotelNotif.put("orderId", orderId);
                            hotelNotif.put("hotelId", hotelId);
                            hotelNotif.put("read", false);
                            hotelNotif.put("timestamp", now);

                            mDatabase.child("notifications").child(hotelUserId).push().setValue(hotelNotif);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG, "Failed to resolve hotel user for notification", error.toException());
                        }
                    });
        }
    }
}
