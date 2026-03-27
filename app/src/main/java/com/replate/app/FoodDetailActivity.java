package com.replate.app;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.adapters.RelatedFoodAdapter;
import com.replate.app.adapters.ReviewAdapter;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.models.FoodItem;
import com.replate.app.models.Review;
import com.replate.app.utils.TelephonyUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Food Detail Activity - Shows full details of a food item.
 */
public class FoodDetailActivity extends AppCompatActivity {

    private ImageView ivFoodImage, ivBack;
    private TextView tvFoodName, tvHotelName, tvDescription;
    private TextView tvOriginalPrice, tvDiscountedPrice, tvDiscount;
    private TextView tvPickupTime, tvDistance, tvQuantity;
    private Button btnAddToCart, btnMinus, btnPlus, btnCallHotel, btnSmsHotel;

    private TextView tvReviewSummary, tvNoReviews, tvNoRecommendations;
    private RatingBar ratingInput;
    private TextInputEditText etReviewComment;
    private Button btnSubmitReview, btnToggleReviewComposer;
    private View layoutReviewComposer;
    private RecyclerView rvReviews, rvRecommended;

    private FoodItem foodItem;
    private int quantity = 1;
    private DatabaseReference mDatabase;
    private String pendingCallNumber;
    private String currentUserName = "Anonymous";
    private String currentUserId = "";
    private String editingReviewId = null;

    private ReviewAdapter reviewAdapter;
    private RelatedFoodAdapter relatedFoodAdapter;

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
        setContentView(R.layout.activity_food_detail);

        // Get food item from intent
        foodItem = (FoodItem) getIntent().getSerializableExtra("foodItem");
        if (foodItem == null) {
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference();

        initViews();
        setupReviewAndRecommendationLists();
        displayFoodDetails();
        setupClickListeners();

        loadCurrentUserName();
        loadReviews();
        loadRecommendations();
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
        btnSmsHotel = findViewById(R.id.btnSmsHotel);

        tvReviewSummary = findViewById(R.id.tvReviewSummary);
        tvNoReviews = findViewById(R.id.tvNoReviews);
        tvNoRecommendations = findViewById(R.id.tvNoRecommendations);
        ratingInput = findViewById(R.id.ratingInput);
        etReviewComment = findViewById(R.id.etReviewComment);
        btnSubmitReview = findViewById(R.id.btnSubmitReview);
        btnToggleReviewComposer = findViewById(R.id.btnToggleReviewComposer);
        layoutReviewComposer = findViewById(R.id.layoutReviewComposer);
        rvReviews = findViewById(R.id.rvReviews);
        rvRecommended = findViewById(R.id.rvRecommended);
    }

    private void setupReviewAndRecommendationLists() {
        currentUserId = FirebaseAuth.getInstance().getUid() == null ? "" : FirebaseAuth.getInstance().getUid();
        reviewAdapter = new ReviewAdapter(currentUserId, new ReviewAdapter.ReviewActionListener() {
            @Override
            public void onEditReview(Review review) {
                startReviewEdit(review);
            }

            @Override
            public void onDeleteReview(Review review) {
                confirmDeleteReview(review);
            }
        });
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setNestedScrollingEnabled(false);
        rvReviews.setAdapter(reviewAdapter);

        relatedFoodAdapter = new RelatedFoodAdapter(this, this::openRelatedFoodItem);
        rvRecommended.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvRecommended.setNestedScrollingEnabled(false);
        rvRecommended.setAdapter(relatedFoodAdapter);
    }

    private void displayFoodDetails() {
        tvFoodName.setText(foodItem.getFoodName());
        tvHotelName.setText(foodItem.getHotelName());
        tvDescription.setText(foodItem.getDescription() != null && !foodItem.getDescription().trim().isEmpty()
                ? foodItem.getDescription()
                : "Fresh rescue meal from a nearby hotel.");

        tvOriginalPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", foodItem.getOriginalPriceValue()));
        tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        tvDiscountedPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", foodItem.getDiscountedPriceValue()));
        tvDiscount.setText(String.format(Locale.getDefault(), "-%d%%", foodItem.getDiscountPercentValue()));

        tvPickupTime.setText(foodItem.getPickupTimeFormatted());
        tvDistance.setText(foodItem.getDistance() > 0 ? foodItem.getDistanceFormatted() + " away" : "Nearby pickup");

        if (foodItem.getImageUrl() != null && !foodItem.getImageUrl().isEmpty()) {
            Glide.with(this).load(foodItem.getImageUrl()).placeholder(R.drawable.placeholder_food).into(ivFoodImage);
        }

        updateQuantityDisplay();
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());

        if (btnCallHotel != null) btnCallHotel.setOnClickListener(v -> callHotel());
        if (btnSmsHotel != null) btnSmsHotel.setOnClickListener(v -> smsHotel());

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
                Toast.makeText(this, "Maximum available quantity reached", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnToggleReviewComposer != null && layoutReviewComposer != null) {
            btnToggleReviewComposer.setOnClickListener(v -> {
                boolean currentlyVisible = layoutReviewComposer.getVisibility() == android.view.View.VISIBLE;
                layoutReviewComposer.setVisibility(currentlyVisible ? android.view.View.GONE : android.view.View.VISIBLE);
                btnToggleReviewComposer.setText(currentlyVisible ? "Write a review" : "Hide review form");
            });
        }

        btnAddToCart.setOnClickListener(v -> addToCart());
        btnSubmitReview.setOnClickListener(v -> submitReview());
    }

    private void updateQuantityDisplay() {
        tvQuantity.setText(String.valueOf(quantity));
        double total = foodItem.getDiscountedPriceValue() * quantity;
        btnAddToCart.setText(String.format(Locale.getDefault(), "Add to Cart - LKR %.2f", total));
    }

    private void addToCart() {
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

        Toast.makeText(this, quantity + " x " + foodItem.getFoodName() + " added to cart!", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, CartActivity.class));
    }

    private void loadCurrentUserName() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.trim().isEmpty()) return;

        mDatabase.child("users").child(uid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        if (name != null && !name.trim().isEmpty()) {
                            currentUserName = name.trim();
                        }
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                    }
                });
    }

    private void loadReviews() {
        String hotelId = foodItem.getHotelId();
        if (hotelId == null || hotelId.trim().isEmpty()) {
            tvNoReviews.setVisibility(android.view.View.VISIBLE);
            tvReviewSummary.setText("0.0 (0 reviews)");
            return;
        }

        mDatabase.child("reviews")
                .orderByChild("hotelId")
                .equalTo(hotelId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                        List<Review> reviews = new ArrayList<>();
                        float total = 0f;

                        for (DataSnapshot child : snapshot.getChildren()) {
                            Review review = new Review();
                            review.setReviewId(child.getKey());
                            review.setFoodId(getStringValue(child, "foodId"));
                            review.setUserId(getStringValue(child, "userId"));
                            review.setUserName(getStringValue(child, "userName"));
                            review.setComment(getStringValue(child, "comment"));
                            review.setHotelId(getStringValue(child, "hotelId"));
                            review.setHotelName(getStringValue(child, "hotelName"));
                            review.setOrderId(getStringValue(child, "orderId"));
                            review.setRating(child.child("rating").getValue());
                            review.setCreatedAt(child.child("createdAt").getValue());

                            Object verified = child.child("verifiedPurchase").getValue();
                            if (verified == null) {
                                // Backward compatibility with old node naming.
                                verified = child.child("isVerifiedPurchase").getValue();
                            }
                            review.setVerifiedPurchase(verified);

                            reviews.add(review);
                            total += review.getRatingValue();
                        }

                        Collections.sort(reviews, (a, b) -> Long.compare(b.getCreatedAtValue(), a.getCreatedAtValue()));
                        reviewAdapter.submitList(reviews);

                        boolean hasReviews = !reviews.isEmpty();
                        tvNoReviews.setVisibility(hasReviews ? android.view.View.GONE : android.view.View.VISIBLE);

                        float avg = hasReviews ? (total / reviews.size()) : 0f;
                        tvReviewSummary.setText(String.format(Locale.getDefault(), "%.1f (%d reviews)", avg, reviews.size()));
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                        tvNoReviews.setVisibility(android.view.View.VISIBLE);
                    }
                });
    }

    private void submitReview() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "Please login to submit review", Toast.LENGTH_SHORT).show();
            return;
        }

        float rating = ratingInput.getRating();
        if (rating <= 0f) {
            Toast.makeText(this, "Please select a rating", Toast.LENGTH_SHORT).show();
            return;
        }

        String comment = etReviewComment.getText() != null ? etReviewComment.getText().toString().trim() : "";
        btnSubmitReview.setEnabled(false);

        if (editingReviewId != null && !editingReviewId.trim().isEmpty()) {
            updateExistingReview(editingReviewId, rating, comment);
            return;
        }

        createReviewWithPurchaseVerification(uid, rating, comment);
    }

    private void createReviewWithPurchaseVerification(String uid, float rating, String comment) {
        hasCompletedOrderForFood(uid, foodItem.getFoodId(), (verified, orderId) -> {
            DatabaseReference ref = mDatabase.child("reviews").push();
            Review review = new Review();
            review.setReviewId(ref.getKey());
            review.setFoodId(foodItem.getFoodId());
            review.setUserId(uid);
            review.setUserName(currentUserName);
            review.setRating(rating);
            review.setComment(comment);
            review.setCreatedAt(System.currentTimeMillis());
            review.setVerifiedPurchase(verified);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("reviewId", review.getReviewId());
            payload.put("foodId", foodItem.getFoodId() == null ? "" : foodItem.getFoodId());
            payload.put("userId", uid);
            payload.put("userName", currentUserName);
            payload.put("rating", rating);
            payload.put("comment", comment);
            payload.put("createdAt", System.currentTimeMillis());
            payload.put("verifiedPurchase", verified);
            payload.put("hotelId", foodItem.getHotelId() == null ? "" : foodItem.getHotelId());
            payload.put("hotelName", foodItem.getHotelName() == null ? "" : foodItem.getHotelName());
            payload.put("orderId", orderId == null ? "" : orderId);

            ref.setValue(payload)
                    .addOnSuccessListener(unused -> {
                        btnSubmitReview.setEnabled(true);
                        clearReviewInput();
                        updateFoodRatingAggregate(foodItem.getFoodId());
                        Toast.makeText(this, "Review added", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        btnSubmitReview.setEnabled(true);
                        Toast.makeText(this, "Failed to submit review", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void updateExistingReview(String reviewId, float rating, String comment) {
        DatabaseReference ref = mDatabase.child("reviews").child(reviewId);
        ref.child("rating").setValue(rating);
        ref.child("comment").setValue(comment);
        ref.child("updatedAt").setValue(System.currentTimeMillis());
        ref.child("updatedAtServer").setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                .addOnCompleteListener(task -> {
                    btnSubmitReview.setEnabled(true);
                    if (task.isSuccessful()) {
                        clearReviewInput();
                        updateFoodRatingAggregate(foodItem.getFoodId());
                        Toast.makeText(this, "Review updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to update review", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startReviewEdit(Review review) {
        if (review == null) return;
        editingReviewId = review.getReviewId();
        ratingInput.setRating(review.getRatingValue());
        etReviewComment.setText(review.getComment());
        if (layoutReviewComposer != null) layoutReviewComposer.setVisibility(android.view.View.VISIBLE);
        if (btnToggleReviewComposer != null) btnToggleReviewComposer.setText("Hide review form");
        btnSubmitReview.setText("Update Review");
        etReviewComment.requestFocus();
    }

    private void clearReviewInput() {
        editingReviewId = null;
        ratingInput.setRating(0f);
        etReviewComment.setText("");
        btnSubmitReview.setText("Submit Review");
        if (layoutReviewComposer != null) layoutReviewComposer.setVisibility(android.view.View.GONE);
        if (btnToggleReviewComposer != null) btnToggleReviewComposer.setText("Write a review");
    }

    private void confirmDeleteReview(Review review) {
        if (review == null || review.getReviewId() == null || review.getReviewId().trim().isEmpty()) return;

        new AlertDialog.Builder(this)
                .setTitle("Delete review")
                .setMessage("Do you want to remove your review?")
                .setPositiveButton("Delete", (d, w) -> deleteReview(review))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteReview(Review review) {
        if (review == null || review.getReviewId() == null || review.getReviewId().trim().isEmpty()) return;

        mDatabase.child("reviews").child(review.getReviewId()).removeValue()
                .addOnSuccessListener(unused -> {
                    if (review.getReviewId().equals(editingReviewId)) {
                        clearReviewInput();
                    }
                    updateFoodRatingAggregate(foodItem.getFoodId());
                    Toast.makeText(this, "Review deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete review", Toast.LENGTH_SHORT).show());
    }

    private void updateFoodRatingAggregate(String foodId) {
        String hotelId = foodItem.getHotelId();
        if (hotelId == null || hotelId.trim().isEmpty()) return;

        mDatabase.child("reviews")
                .orderByChild("hotelId")
                .equalTo(hotelId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                        int count = 0;
                        float sum = 0f;
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Object ratingValue = child.child("rating").getValue();
                            if (ratingValue == null) continue;
                            try {
                                sum += Float.parseFloat(String.valueOf(ratingValue));
                                count++;
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        float avg = count > 0 ? sum / count : 0f;
                        if (foodId != null && !foodId.trim().isEmpty()) {
                            mDatabase.child("foodItems").child(foodId).child("avgRating").setValue(avg);
                            mDatabase.child("foodItems").child(foodId).child("reviewCount").setValue(count);
                        }
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                    }
                });
    }

    private void hasCompletedOrderForFood(String uid, String foodId, PurchaseCheckCallback callback) {
        mDatabase.child("orders")
                .orderByChild("userId")
                .equalTo(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                        boolean hasPurchase = false;
                        String latestOrderId = "";

                        for (DataSnapshot child : snapshot.getChildren()) {
                            String itemId = child.child("foodItemId").getValue(String.class);
                            String status = child.child("orderStatus").getValue(String.class);
                            if (itemId == null || foodId == null || !itemId.equals(foodId)) continue;
                            if (status == null) continue;

                            String normalized = status.trim().toLowerCase(Locale.US);
                            if ("completed".equals(normalized) || "delivered".equals(normalized)) {
                                hasPurchase = true;
                                latestOrderId = child.getKey() == null ? "" : child.getKey();
                                break;
                            }
                        }

                        callback.onResult(hasPurchase, latestOrderId);
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                        callback.onResult(false, "");
                    }
                });
    }

    private interface PurchaseCheckCallback {
        void onResult(boolean verified, String orderId);
    }

    private void loadRecommendations() {
        mDatabase.child("foodItems").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                List<FoodItem> all = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    FoodItem item = child.getValue(FoodItem.class);
                    if (item == null) continue;
                    item.setFoodId(child.getKey());
                    if (!item.isListable()) continue;
                    if (foodItem.getFoodId() != null && foodItem.getFoodId().equals(item.getFoodId())) continue;
                    all.add(item);
                }

                Collections.sort(all, (a, b) -> Integer.compare(scoreRecommendation(b), scoreRecommendation(a)));
                if (all.size() > 10) {
                    all = new ArrayList<>(all.subList(0, 10));
                }

                relatedFoodAdapter.submitList(all);
                tvNoRecommendations.setVisibility(all.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
            }

            @Override
            public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                tvNoRecommendations.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    private int scoreRecommendation(FoodItem candidate) {
        int score = 0;
        if (sameIgnoreCase(foodItem.getCategory(), candidate.getCategory())) score += 4;
        if (sameIgnoreCase(foodItem.getHotelId(), candidate.getHotelId())) score += 3;
        if (sameIgnoreCase(foodItem.getHotelName(), candidate.getHotelName())) score += 2;

        // Reward similar price bands so recommendations feel relevant.
        double basePrice = foodItem.getDiscountedPriceValue();
        double candidatePrice = candidate.getDiscountedPriceValue();
        if (basePrice > 0 && candidatePrice > 0) {
            double diffRatio = Math.abs(candidatePrice - basePrice) / basePrice;
            if (diffRatio <= 0.15) score += 2;
            else if (diffRatio <= 0.30) score += 1;
        }

        String currentName = foodItem.getFoodName() == null ? "" : foodItem.getFoodName().toLowerCase(Locale.US);
        String candidateName = candidate.getFoodName() == null ? "" : candidate.getFoodName().toLowerCase(Locale.US);
        if (!currentName.isEmpty() && !candidateName.isEmpty()) {
            String[] chunks = currentName.split("\\s+");
            for (String chunk : chunks) {
                if (chunk.length() > 3 && candidateName.contains(chunk)) {
                    score += 1;
                    break;
                }
            }
        }

        score += Math.round((float) candidate.getRatingValue());
        return score;
    }

    private boolean sameIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private void openRelatedFoodItem(FoodItem item) {
        Intent intent = new Intent(this, FoodDetailActivity.class);
        intent.putExtra("foodItem", item);
        startActivity(intent);
    }

    /**
     * Call hotel phone - TELEPHONY REQUIREMENT
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
            String message = "Hi " + (foodItem.getHotelName() != null ? foodItem.getHotelName() : "Hotel")
                    + ", I have a question about " + foodItem.getFoodName() + " on REPLATE.";
            try {
                TelephonyUtils.openSms(this, phone, message);
            } catch (Exception e) {
                Toast.makeText(this, "No SMS app available", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resolveHotelPhone(PhoneCallback callback) {
        String fromItem = TelephonyUtils.normalizePhone(foodItem.getHotelPhone(), "");
        if (!fromItem.isEmpty()) {
            callback.onPhone(fromItem);
            return;
        }

        String hotelId = foodItem.getHotelId();
        if (hotelId == null || hotelId.trim().isEmpty()) {
            callback.onPhone("0112345678");
            return;
        }

        mDatabase.child("hotels").child(hotelId).child("phone")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull DataSnapshot snapshot) {
                        String phone = TelephonyUtils.normalizePhone(snapshot.getValue(String.class), "0112345678");
                        callback.onPhone(phone);
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {
                        callback.onPhone("0112345678");
                    }
                });
    }

    private interface PhoneCallback {
        void onPhone(String phone);
    }

    private String getStringValue(DataSnapshot parent, String key) {
        Object value = parent.child(key).getValue();
        return value == null ? "" : String.valueOf(value).trim();
    }
}
