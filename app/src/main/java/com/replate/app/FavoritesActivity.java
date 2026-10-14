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
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.adapters.FavoriteHotelAdapter;
import com.replate.app.adapters.FoodItemAdapter;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.models.FavoriteHotel;
import com.replate.app.models.FoodItem;
import com.replate.app.utils.DatabaseErrorLogger;
import com.replate.app.utils.SafeSnapshot;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Displays the locally saved favorite dishes and favorite hotels (two tabs).
 */
public class FavoritesActivity extends AppCompatActivity implements FoodItemAdapter.OnFoodItemClickListener {

    private ImageView ivBack;
    private TextView tvFavoritesCount;
    private RecyclerView rvFavorites;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private RecyclerView rvFavoriteHotels;
    private TextView tvEmptyTitle, tvEmptySubtitle;
    private com.google.android.material.button.MaterialButton btnEmptyAction;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshFavorites;
    private android.view.View errorBanner;
    // Server checks still running, and whether any of them failed
    private int pendingRefreshes;
    private boolean refreshFailed;

    private final List<FoodItem> favoriteItems = new ArrayList<>();
    private final List<FavoriteHotel> favoriteHotels = new ArrayList<>();
    private boolean showingHotels;
    private FavoriteHotelAdapter hotelAdapter;
    private FoodItemAdapter adapter;
    private ReplateDatabaseHelper localDb;

    // Sets up the screen and loads the saved favourites.
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

    // Reload favourites each time the screen comes back.
    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    // Finds the views and sets up the dishes / hotels tabs.
    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvFavoritesCount = findViewById(R.id.tvFavoritesCount);
        rvFavorites = findViewById(R.id.rvFavorites);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);
        rvFavoriteHotels = findViewById(R.id.rvFavoriteHotels);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        btnEmptyAction = findViewById(R.id.btnFindDishes);
        errorBanner = findViewById(R.id.errorBanner);
        swipeRefreshFavorites = findViewById(R.id.swipeRefreshFavorites);
        swipeRefreshFavorites.setColorSchemeResources(R.color.green_primary);
        swipeRefreshFavorites.setOnRefreshListener(this::loadFavorites);
        // Two lists share the pull-to-refresh: only pull when the visible one is scrolled to the top
        swipeRefreshFavorites.setOnChildScrollUpCallback((parent, child) ->
                (showingHotels ? rvFavoriteHotels : rvFavorites).canScrollVertically(-1));
        btnEmptyAction.setOnClickListener(v -> {
            if (showingHotels) {
                Intent map = new Intent(this, MainActivity.class);
                map.putExtra("open_tab", "map");
                startActivity(map);
            } else {
                startActivity(new Intent(this, SearchActivity.class));
            }
        });

        TabLayout tabs = findViewById(R.id.tabLayoutFavorites);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showingHotels = tab.getPosition() == 1;
                updateUi();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // Sets up the dish list and the hotel list.
    private void setupRecyclerView() {
        rvFavorites.setLayoutManager(new LinearLayoutManager(this));
        rvFavorites.setHasFixedSize(true);
        rvFavorites.setItemAnimator(null);
        adapter = new FoodItemAdapter(this, favoriteItems, this);
        rvFavorites.setAdapter(adapter);

        rvFavoriteHotels.setLayoutManager(new LinearLayoutManager(this));
        hotelAdapter = new FavoriteHotelAdapter(new FavoriteHotelAdapter.Listener() {
            // Opens a search showing only that hotel's dishes.
            @Override
            public void onViewDeals(FavoriteHotel hotel) {
                // Search matches hotel names, so this lists just that hotel's dishes
                Intent intent = new Intent(FavoritesActivity.this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_QUERY, hotel.getName());
                startActivity(intent);
            }

            @Override
            public void onRemove(FavoriteHotel hotel) {
                removeFavoriteHotel(hotel);
            }
        });
        rvFavoriteHotels.setAdapter(hotelAdapter);
    }

    // Back button closes the screen.
    private void setupClickListeners() {
        ivBack.setOnClickListener(v -> finish());
    }

    // Swiping a dish left or right removes it.
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

    // Reads saved dishes and hotels, then refreshes the screen.
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
        favoriteHotels.clear();
        if (localDb != null) {
            favoriteHotels.addAll(localDb.getFavoriteHotels());
        }
        hotelAdapter.submit(new ArrayList<>(favoriteHotels));

        updateUi();
        refreshFromServer();
    }

    /**
     * Favorites are saved copies, so a price, stock level or listing can change after saving.
     * Pull each dish's current state and mark dishes that are gone as unavailable.
     */
    private void refreshFromServer() {
        pendingRefreshes = 0;
        refreshFailed = false;
        com.replate.app.utils.ErrorStates.hide(errorBanner);
        for (FoodItem saved : new ArrayList<>(favoriteItems)) {
            final String foodId = saved.getFoodId();
            if (foodId == null || foodId.trim().isEmpty()) continue;
            pendingRefreshes++;

            FirebaseDatabase.getInstance().getReference("foodItems").child(foodId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (isFinishing() || isDestroyed()) return;
                            onRefreshDone(false);
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
                            if (isFinishing() || isDestroyed()) return;
                            onRefreshDone(true);
                        }
                    });
        }
        if (pendingRefreshes == 0) {
            swipeRefreshFavorites.setRefreshing(false);
        }
    }

    // Counts down the server checks; when all are back, stops the spinner and says if any failed.
    private void onRefreshDone(boolean failed) {
        refreshFailed |= failed;
        if (--pendingRefreshes > 0) return;
        swipeRefreshFavorites.setRefreshing(false);
        if (refreshFailed) {
            com.replate.app.utils.ErrorStates.show(errorBanner, R.string.favorites_refresh_failed, this::refreshFromServer);
        }
    }

    // Finds a dish's place in the list, or -1.
    private int indexOfFavorite(String foodId) {
        for (int i = 0; i < favoriteItems.size(); i++) {
            if (foodId.equals(favoriteItems.get(i).getFoodId())) return i;
        }
        return -1;
    }

    // Shows the count, the right list, or the empty message.
    private void updateUi() {
        int count = showingHotels ? favoriteHotels.size() : favoriteItems.size();
        if (tvFavoritesCount != null) {
            tvFavoritesCount.setText(showingHotels
                    ? getString(R.string.favorites_hotels_saved_count, count,
                            count == 1 ? getString(R.string.favorites_hotel_singular)
                                    : getString(R.string.favorites_hotel_plural))
                    : getString(R.string.favorites_saved_count, count,
                            count == 1 ? getString(R.string.favorites_item_singular)
                                    : getString(R.string.favorites_item_plural)));
        }

        boolean isEmpty = count == 0;
        swipeRefreshFavorites.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        rvFavorites.setVisibility(!showingHotels && !isEmpty ? View.VISIBLE : View.GONE);
        rvFavoriteHotels.setVisibility(showingHotels && !isEmpty ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        tvEmptyTitle.setText(showingHotels ? R.string.favorites_hotels_empty_title : R.string.favorites_empty_title);
        tvEmptySubtitle.setText(showingHotels ? R.string.favorites_hotels_empty_subtitle : R.string.favorites_empty_subtitle);
        btnEmptyAction.setText(showingHotels ? R.string.favorites_hotels_empty_cta : R.string.favorites_empty_cta);
    }

    // Removes a hotel, with an Undo option.
    private void removeFavoriteHotel(FavoriteHotel hotel) {
        int position = favoriteHotels.indexOf(hotel);
        if (position < 0) return;

        favoriteHotels.remove(position);
        if (localDb != null) {
            localDb.removeFavoriteHotel(hotel.getHotelId());
        }
        hotelAdapter.submit(new ArrayList<>(favoriteHotels));
        updateUi();

        Snackbar.make(rvFavoriteHotels, getString(R.string.favorite_hotel_removed), Snackbar.LENGTH_LONG)
                .setAction(R.string.favorites_undo, v -> {
                    if (localDb != null) {
                        localDb.addFavoriteHotel(hotel.getHotelId(), hotel.getName(), hotel.getAddress());
                    }
                    favoriteHotels.add(Math.min(position, favoriteHotels.size()), hotel);
                    hotelAdapter.submit(new ArrayList<>(favoriteHotels));
                    updateUi();
                })
                .show();
    }

    // Removes a dish, with an Undo option.
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

    // Opens the dish detail screen.
    @Override
    public void onFoodItemClick(FoodItem foodItem) {
        Intent intent = new Intent(this, FoodDetailActivity.class);
        intent.putExtra("foodItem", foodItem);
        startActivity(intent);
    }

    // Adds one portion to the cart if it is in stock.
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
