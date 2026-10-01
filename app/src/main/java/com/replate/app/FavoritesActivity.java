package com.replate.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.adapters.FoodItemAdapter;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.models.FoodItem;
import com.replate.app.utils.DatabaseErrorLogger;
import com.replate.app.utils.SafeSnapshot;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays locally saved favorite food items.
 */
public class FavoritesActivity extends AppCompatActivity implements FoodItemAdapter.OnFoodItemClickListener {

    private ImageView ivBack;
    private TextView tvFavoritesCount;
    private RecyclerView rvFavorites;
    private LinearLayout emptyState;
    private ProgressBar progressBar;

    private final List<FoodItem> favoriteItems = new ArrayList<>();
    private FoodItemAdapter adapter;
    private ReplateDatabaseHelper localDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        localDb = ReplateDatabaseHelper.getInstance(this);
        initViews();
        setupRecyclerView();
        setupClickListeners();
        setupSwipeToRemove();
        loadFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvFavoritesCount = findViewById(R.id.tvFavoritesCount);
        rvFavorites = findViewById(R.id.rvFavorites);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);
        findViewById(R.id.btnFindDishes).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));
    }

    private void setupRecyclerView() {
        rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        rvFavorites.setHasFixedSize(true);
        rvFavorites.setItemAnimator(null);
        adapter = new FoodItemAdapter(this, favoriteItems, this);
        rvFavorites.setAdapter(adapter);
    }

    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
    }

    private void setupSwipeToRemove() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                removeFavoriteAt(position);
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvFavorites);
    }

    private void loadFavorites() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        List<FoodItem> savedFavorites = localDb == null ? new ArrayList<>() : localDb.getAllFavorites();
        favoriteItems.clear();
        favoriteItems.addAll(savedFavorites);
        adapter.updateData(new ArrayList<>(favoriteItems));

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        updateUi();
        refreshFromServer();
    }

    /**
     * Favorites are saved copies, so a price, stock level or listing can change after saving.
     * Pull each dish's current state and mark dishes that are gone as unavailable.
     */
    private void refreshFromServer() {
        for (FoodItem saved : new ArrayList<>(favoriteItems)) {
            final String foodId = saved.getFoodId();
            if (foodId == null || foodId.trim().isEmpty()) continue;

            FirebaseDatabase.getInstance().getReference("foodItems").child(foodId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (isFinishing() || isDestroyed()) return;
                            int index = indexOfFavorite(foodId);
                            if (index < 0) return;

                            FoodItem fresh = snapshot.exists() ? SafeSnapshot.getValue(snapshot, FoodItem.class) : null;
                            if (fresh == null) {
                                // Removed by the hotel: keep it listed, but it can't be ordered
                                favoriteItems.get(index).setStatus("unavailable");
                            } else {
                                fresh.setFoodId(foodId);
                                // Distance comes from the phone's location, not from Firebase
                                fresh.setDistance(favoriteItems.get(index).getDistance());
                                favoriteItems.set(index, fresh);
                            }
                            adapter.updateData(new ArrayList<>(favoriteItems));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            // Offline or denied: the saved copy stays on screen
                            DatabaseErrorLogger.log("FavoritesActivity", "refresh favorite", error);
                        }
                    });
        }
    }

    private int indexOfFavorite(String foodId) {
        for (int i = 0; i < favoriteItems.size(); i++) {
            if (foodId.equals(favoriteItems.get(i).getFoodId())) return i;
        }
        return -1;
    }

    private void updateUi() {
        int count = favoriteItems.size();
        if (tvFavoritesCount != null) {
            tvFavoritesCount.setText(getString(
                    R.string.favorites_saved_count,
                    count,
                    count == 1 ? getString(R.string.favorites_item_singular) : getString(R.string.favorites_item_plural)
            ));
        }

        boolean isEmpty = favoriteItems.isEmpty();
        rvFavorites.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void removeFavoriteAt(int position) {
        if (position < 0 || position >= favoriteItems.size()) {
            adapter.updateData(new ArrayList<>(favoriteItems));
            return;
        }

        FoodItem removedItem = favoriteItems.get(position);
        String removedFoodId = removedItem.getFoodId();
        if (removedFoodId == null || removedFoodId.trim().isEmpty()) {
            adapter.updateData(new ArrayList<>(favoriteItems));
            return;
        }

        favoriteItems.remove(position);
        if (localDb != null) {
            localDb.removeFavorite(removedFoodId);
        }
        adapter.updateData(new ArrayList<>(favoriteItems));
        updateUi();

        View anchor = rvFavorites != null ? rvFavorites : findViewById(android.R.id.content);
        Snackbar.make(anchor, getString(R.string.favorites_removed_message), Snackbar.LENGTH_LONG)
                .setAction(R.string.favorites_undo, v -> {
                    if (localDb != null) {
                        localDb.addFavorite(removedItem);
                    }
                    int insertAt = Math.min(position, favoriteItems.size());
                    favoriteItems.add(insertAt, removedItem);
                    adapter.updateData(new ArrayList<>(favoriteItems));
                    updateUi();
                })
                .show();
    }

    @Override
    public void onFoodItemClick(FoodItem foodItem) {
        Intent intent = new Intent(this, FoodDetailActivity.class);
        intent.putExtra("foodItem", foodItem);
        startActivity(intent);
    }

    @Override
    public void onAddToCartClick(FoodItem foodItem) {
        if (foodItem == null) {
            return;
        }
        if (!foodItem.isAvailableValue() || !foodItem.hasStock()) {
            ToastUtils.showCustomToast(this, R.string.food_detail_sold_out);
            return;
        }

        CartDatabaseHelper cartDb = CartDatabaseHelper.getInstance(this);
        // The cart sums repeated adds, so don't go past the stock that is left
        if (foodItem.getQuantity() != null
                && cartDb.getQuantityInCart(foodItem.getFoodId()) >= foodItem.getQuantityValue()) {
            ToastUtils.showCustomToast(this,
                    getString(R.string.food_detail_cart_limit_reached, foodItem.getQuantityValue()));
            return;
        }
        CartItem cartItem = new CartItem(
                foodItem.getFoodId(),
                foodItem.getFoodName() != null ? foodItem.getFoodName() : getString(R.string.cart_item_fallback),
                foodItem.getHotelName() != null ? foodItem.getHotelName() : getString(R.string.food_detail_default_hotel),
                foodItem.getHotelId() != null ? foodItem.getHotelId() : "",
                foodItem.getImageUrl() != null ? foodItem.getImageUrl() : "",
                foodItem.getDiscountedPriceValue(),
                foodItem.getOriginalPriceValue(),
                1,
                foodItem.getDiscountPercentValue()
        );
        cartDb.addItem(cartItem);

        ToastUtils.showCustomToast(this,
                getString(R.string.food_detail_added_to_cart_toast, 1, cartItem.getFoodName()));
    }
}
