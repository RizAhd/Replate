package com.replate.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.models.FoodItem;

import java.util.Locale;

/**
 * Food Detail Activity - Shows full details of a food item
 */
public class FoodDetailActivity extends AppCompatActivity {

    private ImageView ivFoodImage, ivBack;
    private TextView tvFoodName, tvHotelName, tvDescription;
    private TextView tvOriginalPrice, tvDiscountedPrice, tvDiscount;
    private TextView tvPickupTime, tvDistance, tvQuantity;
    private Button btnAddToCart, btnMinus, btnPlus, btnCallHotel;

    private FoodItem foodItem;
    private int quantity = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_food_detail);

        // Get food item from intent
        foodItem = (FoodItem) getIntent().getSerializableExtra("foodItem");
        if (foodItem == null) {
            finish();
            return;
        }

        initViews();
        displayFoodDetails();
        setupClickListeners();
    }

    private void initViews() {
        ivFoodImage = findViewById(R.id.ivFoodImage);
        ivBack = findViewById(R.id.ivBack);
        tvFoodName = findViewById(R.id.tvFoodName);
        tvHotelName = findViewById(R.id.tvHotelName);
        tvDescription = findViewById(R.id.tvDescription);
        tvOriginalPrice = findViewById(R.id.tvOriginalPrice);
        tvDiscountedPrice = findViewById(R.id.tvDiscountedPrice);
        tvDiscount = findViewById(R.id.tvDiscount);
        tvPickupTime = findViewById(R.id.tvPickupTime);
        tvDistance = findViewById(R.id.tvDistance);
        tvQuantity = findViewById(R.id.tvQuantity);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        btnCallHotel = findViewById(R.id.btnCallHotel);
    }

    private void displayFoodDetails() {
        tvFoodName.setText(foodItem.getFoodName());
        tvHotelName.setText(foodItem.getHotelName());
        tvDescription.setText(foodItem.getDescription() != null && !foodItem.getDescription().trim().isEmpty()
                ? foodItem.getDescription()
                : "Fresh rescue meal from a nearby hotel.");

        tvOriginalPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", foodItem.getOriginalPriceValue()));
        tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() |
            android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        tvDiscountedPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", foodItem.getDiscountedPriceValue()));
        tvDiscount.setText(String.format(Locale.getDefault(), "-%d%%", foodItem.getDiscountPercentValue()));

        tvPickupTime.setText(foodItem.getPickupTimeFormatted());

        if (foodItem.getDistance() > 0) {
            tvDistance.setText(foodItem.getDistanceFormatted() + " away");
        } else {
            tvDistance.setText("Nearby pickup");
        }

        // Load image
        if (foodItem.getImageUrl() != null && !foodItem.getImageUrl().isEmpty()) {
            Glide.with(this)
                .load(foodItem.getImageUrl())
                .placeholder(R.drawable.placeholder_food)
                .into(ivFoodImage);
        }

        updateQuantityDisplay();
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
        if (btnCallHotel != null) {
            btnCallHotel.setOnClickListener(v -> callHotel());
        }

        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updateQuantityDisplay();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (quantity < foodItem.getQuantityValue()) {
                quantity++;
                updateQuantityDisplay();
            } else {
                Toast.makeText(this, "Maximum available quantity reached",
                    Toast.LENGTH_SHORT).show();
            }
        });

        btnAddToCart.setOnClickListener(v -> addToCart());
    }

    private void updateQuantityDisplay() {
        tvQuantity.setText(String.valueOf(quantity));
        double total = foodItem.getDiscountedPriceValue() * quantity;
        btnAddToCart.setText(String.format(Locale.getDefault(), "Add to Cart - LKR %.2f", total));
    }

    private void addToCart() {
        // Add to SQLite cart (REQUIREMENT: SQLite Data Storage)
        CartDatabaseHelper cartDb = CartDatabaseHelper.getInstance(this);
        CartItem cartItem = new CartItem(
                foodItem.getFoodId(),
                foodItem.getFoodName(),
                foodItem.getHotelName(),
                foodItem.getHotelId(),
                foodItem.getImageUrl(),
                foodItem.getDiscountedPriceValue(),
                foodItem.getOriginalPriceValue(),
                quantity,
                foodItem.getDiscountPercentValue()
        );
        cartDb.addItem(cartItem);

        Toast.makeText(this, quantity + " x " + foodItem.getFoodName() + " added to cart!",
            Toast.LENGTH_SHORT).show();

        // Go to cart
        Intent intent = new Intent(this, CartActivity.class);
        startActivity(intent);
    }

    /**
     * Call hotel phone - TELEPHONY REQUIREMENT
     */
    private void callHotel() {
        String phone = foodItem.getHotelPhone();
        if (phone == null || phone.isEmpty()) phone = "0112345678";
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
        startActivity(intent);
    }
}

