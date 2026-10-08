package com.replate.app;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
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
import com.replate.app.utils.AppAnalytics;
import com.replate.app.utils.Money;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.utils.CheckoutValidator;
import com.replate.app.utils.CartCheck;
import com.replate.app.utils.NetworkUtils;
import com.replate.app.utils.PayHereConfig;
import com.replate.app.utils.PaymentOutcome;
import com.replate.app.utils.PendingPayment;
import com.replate.app.utils.PendingPaymentStore;
import com.replate.app.utils.ToastUtils;

import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.PHResponse;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.Item;
import lk.payhere.androidsdk.model.StatusResponse;

public class CheckoutActivity extends AppCompatActivity {

    private static final String TAG = "PAYHERE_CHECKOUT";
    private static final int PAYHERE_REQUEST = 11001;

    private static final String MERCHANT_ID     = BuildConfig.PAYHERE_MERCHANT_ID;

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

    // One order id / pickup code per checkout attempt, so a retry overwrites the same order
    // instead of creating a duplicate (and charging twice).
    private String pendingOrderId;
    private String pendingOtp;
    // Set once a card payment succeeded but the order isn't saved yet; the next tap must retry
    // saving, never start another payment.
    private String pendingPaymentMethod;
    private String pendingPaymentStatus;
    private boolean orderCompleted;
    private boolean orderWriteInFlight;
    // True while the confirm dialog is on screen, so a second tap cannot open a second one
    private boolean confirmDialogOpen;
    // PayHere's payment number, saved on the order so a payment can be traced
    private long pendingPaymentRef;
    private static final long SUBMIT_TIMEOUT_MS = 25_000L;
    private final android.os.Handler submitTimeoutHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable submitTimeoutRunnable;

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
        registerBackGuard();
        restoreUnsavedPayment();
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

        ivBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
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
                rbCash.setText(R.string.checkout_cash_on_delivery);
            } else {
                cardDeliveryAddress.setVisibility(View.GONE);
                if (cardPickupTime != null) cardPickupTime.setVisibility(View.VISIBLE);
                if (rowDeliveryFee != null) rowDeliveryFee.setVisibility(View.GONE);
                rbCash.setText(R.string.checkout_cash_on_pickup);
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
            ToastUtils.showCustomToast(this, R.string.checkout_cart_is_empty);
            finish();
            return;
        }

        // An order goes to one hotel only (the hotel is taken from the first item), so a cart that mixes
        // hotels would send other hotels' dishes to the wrong kitchen.
        java.util.Set<String> hotelIds = new java.util.HashSet<>();
        for (CartItem item : cartItems) {
            hotelIds.add(item.getHotelId() == null ? "" : item.getHotelId());
        }
        if (hotelIds.size() > 1) {
            btnPlaceOrder.setEnabled(false);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.checkout_mixed_hotels_title)
                    .setMessage(R.string.checkout_mixed_hotels_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.checkout_back_to_cart, (dialog, which) -> finish())
                    .show();
        }

        subtotalAmount = 0;
        totalSavings = 0;
        orderItemsContainer.removeAllViews();

        final int rowGap = (int) (12 * getResources().getDisplayMetrics().density);
        final int lineGap = (int) (4 * getResources().getDisplayMetrics().density);
        for (CartItem item : cartItems) {
            LinearLayout itemRow = new LinearLayout(this);
            itemRow.setOrientation(LinearLayout.VERTICAL);
            itemRow.setPadding(0, 0, 0, rowGap);

            // Theme text styles give these code-built rows the same font and sizes as the rest of the screen
            TextView title = new TextView(this);
            title.setTextAppearance(R.style.TextAppearance_REPLATE_Label);
            title.setText(getString(R.string.order_detail_item_line, item.getQuantity(), item.getFoodName()));
            title.setTextColor(getColor(R.color.text_primary));

            String hotelName = item.getHotelName();
            TextView subtitle = new TextView(this);
            subtitle.setTextAppearance(R.style.TextAppearance_REPLATE_Caption);
            subtitle.setText(getString(R.string.checkout_item_subtitle,
                    hotelName != null && !hotelName.trim().isEmpty() ? hotelName : getString(R.string.hotel_name_fallback),
                    Money.format(item.getSubtotal())));
            subtitle.setPadding(0, lineGap, 0, 0);

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

        tvSubtotal.setText(Money.format(subtotalAmount));
        tvSavings.setText("-" + Money.format(totalSavings));
        if (tvDeliveryFee != null) {
            tvDeliveryFee.setText(Money.format(deliveryFee));
        }
        tvTotal.setText(Money.format(totalAmount));
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
                            Object phoneValue = snapshot.child("phone").getValue();
                            String phone = phoneValue == null ? null : String.valueOf(phoneValue);
                            if (name != null && isEmpty(etCustomerName)) etCustomerName.setText(name);
                            if (phone != null && isEmpty(etCustomerPhone)) etCustomerPhone.setText(phone);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            // Prefill is a convenience only; the customer can still type their details
                            com.replate.app.utils.DatabaseErrorLogger.log(TAG, "prefill user details", error);
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
        // A card payment already went through but saving the order failed: only retry saving.
        // Starting another payment here would charge the customer twice.
        if (pendingPaymentStatus != null) {
            submitOrder(pendingPaymentMethod, pendingPaymentStatus);
            return;
        }
        if (confirmDialogOpen || !btnPlaceOrder.isEnabled()) {
            return;
        }

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
        if (!CheckoutValidator.isValidPhone(customerPhone)) {
            etCustomerPhone.setError("Enter a valid phone number (9-15 digits)");
            return;
        }

        if (!isDelivery && pickupTime.isEmpty()) {
            ToastUtils.showCustomToast(this, R.string.checkout_please_select_pickup_time);
            return;
        }
        Calendar now = Calendar.getInstance();
        if (!isDelivery && CheckoutValidator.isPickupTimeInPast(
                pickupTime, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))) {
            ToastUtils.showCustomToast(this, R.string.checkout_pickup_time_has_already_passed);
            return;
        }

        if (isDelivery && address.isEmpty()) {
            etAddress.setError("Enter delivery address");
            return;
        }

        recalculateTotals();
        boolean payingByCard = rgPayment.getCheckedRadioButtonId() == R.id.rbCard;
        if (payingByCard && !CheckoutValidator.isPayableAmount(totalAmount)) {
            ToastUtils.showCustomToast(this, R.string.checkout_this_order_has_no_payable);
            return;
        }
        confirmDialogOpen = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.checkout_confirm_order)
                .setMessage(getString(R.string.checkout_confirm_message, totalAmount))
                .setPositiveButton(R.string.checkout_yes_place_order, (dialog, which) -> verifyCartThen(() -> {
                    if (rgPayment.getCheckedRadioButtonId() == R.id.rbCard) {
                        startSandboxPayment();
                    } else {
                        submitOrder("Cash", "pending");
                    }
                }))
                .setNegativeButton(R.string.checkout_cancel, null)
                .setOnDismissListener(d -> confirmDialogOpen = false)
                .show();
    }

    /**
     * Reads the live listing of every dish in the cart and only goes on when the price, stock and
     * availability still match, so nobody pays for a dish that sold out or changed price.
     */
    private void verifyCartThen(Runnable proceed) {
        if (!NetworkUtils.isOnline(this)) {
            ToastUtils.showCustomToast(this, R.string.checkout_cart_check_failed);
            return;
        }
        showLoading(true);
        final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        final CartCheck.Line[] lines = new CartCheck.Line[cartItems.size()];
        final int[] remaining = {cartItems.size()};
        final Runnable onTimeout = () -> {
            if (finished.compareAndSet(false, true)) {
                showLoading(false);
                ToastUtils.showCustomToast(this, R.string.checkout_cart_check_failed);
            }
        };
        submitTimeoutHandler.postDelayed(onTimeout, 12_000L);

        for (int i = 0; i < cartItems.size(); i++) {
            final int index = i;
            final CartItem item = cartItems.get(i);
            final String foodId = item.getFoodId();
            if (foodId == null || foodId.trim().isEmpty()) {
                // An old cart line without a dish id cannot be checked; let it through as it was
                lines[index] = new CartCheck.Line(item.getFoodName(), item.getQuantity(), item.getPrice(),
                        new CartCheck.Live(true, item.getQuantity(), item.getPrice()));
                if (--remaining[0] == 0) {
                    finishCartCheck(finished, onTimeout, lines, proceed);
                }
                continue;
            }
            mDatabase.child("foodItems").child(foodId).get().addOnCompleteListener(task -> {
                if (finished.get()) {
                    return;
                }
                if (!task.isSuccessful()) {
                    if (finished.compareAndSet(false, true)) {
                        submitTimeoutHandler.removeCallbacks(onTimeout);
                        showLoading(false);
                        ToastUtils.showCustomToast(this, R.string.checkout_cart_check_failed);
                    }
                    return;
                }
                CartCheck.Live live = null;
                DataSnapshot snap = task.getResult();
                if (snap != null && snap.exists()) {
                    try {
                        com.replate.app.models.FoodItem food = snap.getValue(com.replate.app.models.FoodItem.class);
                        if (food != null) {
                            live = new CartCheck.Live(food.isAvailableValue(), food.getQuantityValue(),
                                    food.getDiscountedPriceValue());
                        }
                    } catch (RuntimeException e) {
                        // A listing we cannot read is not proof that the dish is gone: leave this line unchecked
                        Log.w(TAG, "Could not read listing " + foodId, e);
                        live = new CartCheck.Live(true, item.getQuantity(), item.getPrice());
                    }
                }
                lines[index] = new CartCheck.Line(item.getFoodName(), item.getQuantity(), item.getPrice(), live);
                if (--remaining[0] == 0) {
                    finishCartCheck(finished, onTimeout, lines, proceed);
                }
            });
        }
    }

    private void finishCartCheck(java.util.concurrent.atomic.AtomicBoolean finished, Runnable onTimeout,
                                 CartCheck.Line[] lines, Runnable proceed) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        submitTimeoutHandler.removeCallbacks(onTimeout);
        if (isFinishing() || isDestroyed()) {
            return;
        }
        List<String> problems = CartCheck.problems(Arrays.asList(lines));
        if (problems.isEmpty()) {
            proceed.run();
            return;
        }
        showLoading(false);
        StringBuilder text = new StringBuilder();
        for (String problem : problems) {
            text.append("\u2022 ").append(problem).append('\n');
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.checkout_cart_changed_title)
                .setMessage(getString(R.string.checkout_cart_changed_message, text.toString().trim()))
                .setPositiveButton(R.string.checkout_back_to_cart, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private static boolean isEmpty(TextInputEditText field) {
        return field.getText() == null || field.getText().toString().trim().isEmpty();
    }

    /** One order id and pickup code per checkout; also used as PayHere's order id so the two can be matched. */
    private void ensureOrderIds() {
        if (pendingOrderId == null) {
            pendingOrderId = mDatabase.child("orders").push().getKey();
            pendingOtp = String.format(Locale.US, "%06d", new java.security.SecureRandom().nextInt(1_000_000));
        }
    }

    /** Back is blocked while a card payment has gone through but the order is not saved yet. */
    private void registerBackGuard() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (pendingPaymentStatus == null) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                new AlertDialog.Builder(CheckoutActivity.this)
                        .setTitle(R.string.checkout_leave_unsaved_title)
                        .setMessage(R.string.checkout_leave_unsaved_message)
                        .setPositiveButton(R.string.checkout_retry, (d, w) -> submitOrder(pendingPaymentMethod, pendingPaymentStatus))
                        .setNegativeButton(R.string.checkout_leave, (d, w) -> {
                            setEnabled(false);
                            finish();
                        })
                        .show();
            }
        });
    }

    /** Writes the paid-but-unsaved order to the phone, so closing the app cannot lose it. */
    private void persistUnsavedPayment(String method) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            return;
        }
        ensureOrderIds();
        boolean isDelivery = rgOrderType.getCheckedRadioButtonId() == R.id.rbDelivery;
        PendingPaymentStore.save(this, new PendingPayment(user.getUid(), pendingOrderId, pendingOtp, method,
                pendingPaymentRef, isDelivery,
                String.valueOf(etCustomerName.getText()).trim(), String.valueOf(etCustomerPhone.getText()).trim(),
                isDelivery ? "" : String.valueOf(etPickupTime.getText()).trim(),
                String.valueOf(etAddress.getText()).trim(), selectedDeliveryLat, selectedDeliveryLng));
    }

    /** A payment from an earlier visit that was never saved as an order: fill the form again and offer to finish it. */
    private void restoreUnsavedPayment() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || cartItems == null || cartItems.isEmpty() || pendingPaymentStatus != null) {
            return;
        }
        PendingPayment saved = PendingPaymentStore.load(this, user.getUid());
        if (saved == null) {
            return;
        }
        pendingOrderId = saved.orderId;
        pendingOtp = saved.otp;
        pendingPaymentMethod = saved.method;
        pendingPaymentStatus = "paid";
        pendingPaymentRef = saved.paymentRef;

        rgOrderType.check(saved.delivery ? R.id.rbDelivery : R.id.rbPickup);
        rgPayment.check(R.id.rbCard);
        etCustomerName.setText(saved.customerName);
        etCustomerPhone.setText(saved.customerPhone);
        etPickupTime.setText(saved.pickupTime);
        etAddress.setText(saved.address);
        selectedDeliveryLat = saved.lat;
        selectedDeliveryLng = saved.lng;
        recalculateTotals();
        btnPlaceOrder.setText(R.string.checkout_retry_saving_order);

        new AlertDialog.Builder(this)
                .setTitle(R.string.checkout_payment_received)
                .setMessage(R.string.checkout_unsaved_found)
                .setPositiveButton(R.string.checkout_retry, (d, w) -> submitOrder(pendingPaymentMethod, pendingPaymentStatus))
                .setNegativeButton(R.string.checkout_not_now, null)
                .setCancelable(false)
                .show();
    }

    private void startSandboxPayment() {
        if (!PayHereConfig.isConfigured(MERCHANT_ID)) {
            showLoading(false);
            ToastUtils.showCustomToast(this, R.string.checkout_payment_not_configured_please_contact);
            return;
        }
        // PayHere picks sandbox or live from the merchant id itself; a debug build must stay on the sandbox
        if (!PayHereConfig.mayStartPayment(MERCHANT_ID, BuildConfig.DEBUG)) {
            showLoading(false);
            ToastUtils.showCustomToast(this, R.string.checkout_payment_test_build_only);
            return;
        }

        ensureOrderIds();
        InitRequest req = new InitRequest();
        req.setMerchantId(MERCHANT_ID);
        req.setCurrency("LKR");
        req.setAmount(totalAmount);
        // The same id as the order that will be saved, so a PayHere payment can be matched to its order
        req.setOrderId(pendingOrderId);
        req.setItemsDescription("REPLATE Order");

        String fullName = etCustomerName.getText().toString().trim();
        String[] parts = fullName.split(" ", 2);
        String firstName = parts.length > 0 ? parts[0] : "Customer";
        String lastName = parts.length > 1 ? parts[1] : "User";

        FirebaseUser user = mAuth.getCurrentUser();
        String email = user != null ? user.getEmail() : null;
        if (email == null || email.trim().isEmpty()) {
            // PayHere needs a real email for the receipt; don't invent one
            showLoading(false);
            ToastUtils.showCustomToast(this,
                    R.string.checkout_card_payment_needs_an_email);
            return;
        }

        String typedAddress = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
        String hotelLabel = (cartItems != null && !cartItems.isEmpty() && cartItems.get(0).getHotelName() != null)
                ? cartItems.get(0).getHotelName() : "hotel";
        String billingAddress = !typedAddress.isEmpty() ? typedAddress : "Pickup at " + hotelLabel;

        req.getCustomer().setFirstName(firstName);
        req.getCustomer().setLastName(lastName);
        req.getCustomer().setEmail(email);
        req.getCustomer().setPhone(etCustomerPhone.getText().toString().trim());
        req.getCustomer().getAddress().setAddress(billingAddress);
        req.getCustomer().getAddress().setCity("Colombo");
        req.getCustomer().getAddress().setCountry("Sri Lanka");

        // Do NOT send a fake/unregistered notify URL in sandbox.
        // That can trigger PayHere "unauthorized domain" for merchants that haven't whitelisted it.

        req.getItems().add(new Item(null, "REPLATE Food Order", 1, totalAmount));

        Intent intent = new Intent(this, PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);

        showLoading(true);
        payHereLauncher.launch(intent);
    }

    private void handlePayHereResult(int resultCode, @Nullable Intent data) {
        // Always reset loading/button state first
        showLoading(false);

        Serializable raw = data == null ? null : data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);
        if (!(raw instanceof PHResponse)) {
            // The SDK closed without a result (for example the back button): nothing was charged
            showPaymentCancelledDialog();
            return;
        }
        PHResponse<?> response = (PHResponse<?>) raw;
        int paymentState = PaymentOutcome.UNKNOWN;
        long paymentNo = 0;
        if (response.getData() instanceof StatusResponse) {
            StatusResponse status = (StatusResponse) response.getData();
            paymentState = status.getStatus();
            paymentNo = status.getPaymentNo();
        }
        String responseText = response.toString();

        switch (PaymentOutcome.classify(resultCode == Activity.RESULT_OK, response.getStatus(), paymentState, responseText)) {
            case SUCCESS:
                if (BuildConfig.DEBUG) Log.d(TAG, "Payment success: " + responseText);
                pendingPaymentRef = paymentNo;
                // Written to the phone first: if saving the order fails or the app is closed, the next checkout finishes it
                persistUnsavedPayment("PayHere Online");
                submitOrder("PayHere Online", "paid");
                break;
            case UNAUTHORIZED:
                ToastUtils.showCustomToast(this, R.string.checkout_payhere_blocked_this_request_unauthorized);
                Log.e(TAG, "PayHere unauthorized response: " + responseText);
                break;
            case CANCELLED:
                showPaymentCancelledDialog();
                break;
            default:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.checkout_payment_failed)
                        .setMessage(R.string.checkout_your_payment_could_not_be)
                        .setPositiveButton(R.string.checkout_try_again, (d, w) -> startSandboxPayment())
                        .setNeutralButton(R.string.checkout_pay_with_cash, (d, w) -> {
                            if (rgPayment != null) {
                                rgPayment.check(R.id.rbCash);
                            }
                            updatePlaceOrderButton();
                            ToastUtils.showCustomToast(this, R.string.checkout_switched_to_cash_payment);
                        })
                        .setNegativeButton(R.string.checkout_cancel, null)
                        .show();
                break;
        }
    }

    private void showPaymentCancelledDialog() {
        // Ensure UI is fully reset to prevent broken state
        showLoading(false);

        new AlertDialog.Builder(this)
                .setTitle(R.string.checkout_payment_cancelled)
                .setMessage(R.string.checkout_you_cancelled_the_payment_your)
                .setPositiveButton(R.string.checkout_retry_payment, (dialog, which) -> startSandboxPayment())
                .setNeutralButton(R.string.checkout_pay_with_cash, (dialog, which) -> {
                    // Switch payment method to Cash and auto-submit
                    if (rgPayment != null) {
                        rgPayment.check(R.id.rbCash);
                    }
                    updatePlaceOrderButton();
                    ToastUtils.showCustomToast(this, R.string.checkout_switched_to_cash_payment_tap);
                })
                .setNegativeButton(R.string.checkout_cancel_order, (dialog, which) -> finish())
                .setCancelable(true)
                .show();
    }

    private void submitOrder(String paymentMethod, String paymentStatus) {
        if (orderCompleted || orderWriteInFlight) {
            return;
        }
        if ("paid".equals(paymentStatus)) {
            // Remember that money has been taken, so every retry path knows not to charge again
            pendingPaymentMethod = paymentMethod;
            pendingPaymentStatus = paymentStatus;
        }
        if (mAuth.getCurrentUser() == null) {
            handleSubmitFailure(paymentMethod, paymentStatus,
                    "Your session has expired. Please log in again.");
            return;
        }
        if (!NetworkUtils.isOnline(this)) {
            handleSubmitFailure(paymentMethod, paymentStatus,
                    "You're offline. Connect to the internet and try again.");
            return;
        }

        showLoading(true);

        String customerName = etCustomerName.getText().toString().trim();
        String customerPhone = etCustomerPhone.getText().toString().trim();
        boolean isDelivery = rgOrderType.getCheckedRadioButtonId() == R.id.rbDelivery;
        String orderType = isDelivery ? "Delivery" : "Pickup";
        String pickupTime = isDelivery ? "N/A" : etPickupTime.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        ensureOrderIds();
        final String otpCode = pendingOtp;
        final String orderId = pendingOrderId;

        if (orderId == null) {
            handleSubmitFailure(paymentMethod, paymentStatus, "Couldn't create the order.");
            return;
        }

        List<Map<String, Object>> itemsList = new ArrayList<>();
        String hotelId = "";
        String hotelName = "";

        // The cart does not store a dish's category, but the browsing cache does. Saving it on each
        // order line lets the admin and hotel analytics break revenue down by category.
        Map<String, String> categoryByFood = new HashMap<>();
        for (com.replate.app.models.FoodItem cached
                : com.replate.app.helpers.ReplateDatabaseHelper.getInstance(this).getCachedFoodItems()) {
            if (cached.getFoodId() != null && cached.getCategory() != null && !cached.getCategory().trim().isEmpty()) {
                categoryByFood.put(cached.getFoodId(), cached.getCategory().trim());
            }
        }

        for (CartItem item : cartItems) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("foodId", item.getFoodId());
            itemMap.put("foodName", item.getFoodName());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("price", item.getPrice());
            itemMap.put("imageUrl", item.getImageUrl() != null ? item.getImageUrl() : "");
            String category = categoryByFood.get(item.getFoodId());
            if (category != null) {
                itemMap.put("category", category);
            }
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
        if ("paid".equals(paymentStatus)) {
            // PayHere's payment number, so the payment can be looked up in the PayHere dashboard. If the SDK
            // ever returns none, the time is stored instead (and flagged) so a paid order is never refused.
            if (pendingPaymentRef > 0) {
                orderData.put("paymentRef", pendingPaymentRef);
            } else {
                orderData.put("paymentRef", System.currentTimeMillis());
                orderData.put("paymentRefLocal", true);
            }
        }
        orderData.put("orderType", orderType);
        orderData.put("deliveryAddress", isDelivery ? address : "Pickup at Hotel");
        if (isDelivery) {
            orderData.put("deliveryLatitude", selectedDeliveryLat);
            orderData.put("deliveryLongitude", selectedDeliveryLng);
        }
        orderData.put("pickupTime", pickupTime);
        if (!isDelivery) {
            // The pickup QR and OTP stop working when this window closes
            long expiresAt = com.replate.app.utils.PickupWindow.expiresAt(
                    pickupTime, System.currentTimeMillis(), java.util.TimeZone.getDefault());
            if (expiresAt > 0) {
                orderData.put("pickupExpiresAt", expiresAt);
            }
        }
        orderData.put("qrCode", orderId);
        orderData.put("pickupCode", orderId);
        orderData.put("otpCode", otpCode);
        orderData.put("customerName", customerName);
        orderData.put("customerPhone", customerPhone);
        orderData.put("createdAt", System.currentTimeMillis());
        orderData.put("updatedAt", System.currentTimeMillis());

        final String finalHotelId = hotelId;
        final String finalHotelName = hotelName;

        // If the write never answers (poor signal), stop the spinner and let the user retry.
        // Retrying is safe: it reuses the same order id, so it can't create a second order.
        orderWriteInFlight = true;
        cancelSubmitTimeout();
        submitTimeoutRunnable = () -> {
            if (!isFinishing() && !isDestroyed() && orderWriteInFlight && !orderCompleted) {
                orderWriteInFlight = false;
                handleSubmitFailure(paymentMethod, paymentStatus,
                        "Saving your order is taking longer than expected.");
            }
        };
        submitTimeoutHandler.postDelayed(submitTimeoutRunnable, SUBMIT_TIMEOUT_MS);

        final boolean deliveryOrder = isDelivery;
        mDatabase.child("orders").child(orderId).setValue(orderData)
                .addOnSuccessListener(unused -> onOrderSaved(orderId, otpCode, customerName, finalHotelId, finalHotelName,
                        paymentMethod, deliveryOrder))
                .addOnFailureListener(e -> {
                    cancelSubmitTimeout();
                    orderWriteInFlight = false;
                    if (orderCompleted) {
                        return;
                    }
                    Log.w(TAG, "Order write failed", e);
                    // The first write may have reached the server even though its answer was lost; the retry is then
                    // refused (the server already processed the order). If the order is there and is ours, we are done.
                    mDatabase.child("orders").child(orderId).get().addOnCompleteListener(check -> {
                        FirebaseUser current = mAuth.getCurrentUser();
                        boolean alreadySaved = check.isSuccessful() && check.getResult() != null && check.getResult().exists()
                                && current != null
                                && current.getUid().equals(String.valueOf(check.getResult().child("userId").getValue()));
                        if (alreadySaved) {
                            onOrderSaved(orderId, otpCode, customerName, finalHotelId, finalHotelName, paymentMethod, deliveryOrder);
                        } else {
                            handleSubmitFailure(paymentMethod, paymentStatus, "We couldn't save your order.");
                        }
                    });
                });
    }

    /** The order is stored: clear the cart and the unsaved-payment record, then show the QR/OTP screen. */
    private void onOrderSaved(String orderId, String otpCode, String customerName, String hotelId, String hotelName,
                              String paymentMethod, boolean isDelivery) {
        cancelSubmitTimeout();
        orderWriteInFlight = false;
        if (orderCompleted) {
            return; // a late success after a retry already finished the order
        }
        orderCompleted = true;
        pendingPaymentMethod = null;
        pendingPaymentStatus = null;
        pendingPaymentRef = 0;
        PendingPaymentStore.clear(this);
        showLoading(false);

        // Stock is adjusted by the onOrderCreatedNotify Cloud Function (server side, exactly
        // once); customers are not allowed to write foodItems quantity.
        cartDb.clearCart();
        AppAnalytics.purchase(this, orderId, totalAmount, paymentMethod);
        saveReceipt(orderId, otpCode, customerName, totalAmount);
        playPaymentSound();
        createOrderNotifications(orderId, hotelId, hotelName, customerName, totalAmount);

        com.replate.app.notifications.InAppNotificationCenter.publish(
                new com.replate.app.notifications.InAppNotificationCenter.InAppNotificationMessage(
                        "Order placed successfully",
                        isDelivery
                                ? "Your delivery order has been placed and is being processed."
                                : "Your order has been placed and is waiting for pickup.",
                        com.replate.app.notifications.NotificationCategories.ORDER_PLACED,
                        "order",
                        orderId,
                        hotelId,
                        "",
                        null
                )
        );

        Intent intent = new Intent(this, OrderSuccessActivity.class);
        intent.putExtra("orderId", orderId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();

        ToastUtils.showCustomToast(this, R.string.checkout_order_placed_successfully);
    }

    private void cancelSubmitTimeout() {
        if (submitTimeoutRunnable != null) {
            submitTimeoutHandler.removeCallbacks(submitTimeoutRunnable);
            submitTimeoutRunnable = null;
        }
    }

    /**
     * Cash orders just show the problem. After a successful card payment the money is already
     * taken, so the dialog only offers to retry saving, never to pay again.
     */
    private void handleSubmitFailure(String paymentMethod, String paymentStatus, String reason) {
        showLoading(false);
        if ("paid".equals(paymentStatus)) {
            btnPlaceOrder.setText(R.string.checkout_retry_saving_order);
            if (isFinishing() || isDestroyed()) {
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.checkout_payment_received)
                    .setMessage(reason + "\n\nYour card payment went through. Tap Retry to save your order. "
                            + "You will not be charged again.")
                    .setPositiveButton(R.string.checkout_retry, (d, w) -> submitOrder(paymentMethod, paymentStatus))
                    .setNegativeButton(R.string.checkout_not_now, null)
                    .setCancelable(false)
                    .show();
        } else {
            ToastUtils.showCustomToast(this, reason + " Please try again.");
        }
    }

    @Override
    protected void onDestroy() {
        cancelSubmitTimeout();
        super.onDestroy();
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
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to save receipt", e);
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
            // Non-critical: sound playback failure doesn't affect order
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        btnPlaceOrder.setEnabled(!show);
        btnPlaceOrder.setAlpha(show ? 0.65f : 1f);
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
