package com.replate.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.chip.Chip;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.FoodDetailActivity;
import com.replate.app.R;
import com.replate.app.adapters.FoodItemAdapter;
import com.replate.app.models.FoodItem;
import com.replate.app.utils.LocationUtils;

import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.os.Looper;

/**
 * Search Fragment - Search and filter food items
 */
public class SearchFragment extends Fragment implements FoodItemAdapter.OnFoodItemClickListener {

    private EditText etSearch;
    private RecyclerView rvSearchResults;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private TextView tvResultsCount;

    private Chip chipAll, chipNearby, chipPriceLow, chipDiscount, chipRating, chipCategory;

    private DatabaseReference mDatabase;
    private FoodItemAdapter adapter;
    private FusedLocationProviderClient fusedLocationClient;
    private GridLayoutManager gridLayoutManager;

    private final List<FoodItem> allFoodItems = new ArrayList<>();
    private final List<FoodItem> filteredItems = new ArrayList<>();

    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private String currentFilter = "all";
    private double currentLat = 0;
    private double currentLng = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        initViews(view);
        getUserLocation(); // Get location first
        setupSearch();
        setupFilterChips();
        // loadAllFoodItems called inside getUserLocation or strictly if permissions fail

        return view;
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    currentLat = location.getLatitude();
                    currentLng = location.getLongitude();
                }
                loadAllFoodItems();
            });
        } else {
            loadAllFoodItems();
        }
    }

    private void initViews(View view) {
        etSearch = view.findViewById(R.id.etSearch);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        emptyState = view.findViewById(R.id.emptyState);
        progressBar = view.findViewById(R.id.progressBar);
        tvResultsCount = view.findViewById(R.id.tvResultsCount);

        chipAll = view.findViewById(R.id.chipAll);
        chipNearby = view.findViewById(R.id.chipNearby);
        chipPriceLow = view.findViewById(R.id.chipPriceLow);
        chipDiscount = view.findViewById(R.id.chipDiscount);

        int chipRatingId = getResources().getIdentifier("chipRating", "id", requireContext().getPackageName());
        if (chipRatingId != 0) {
            chipRating = view.findViewById(chipRatingId);
        }
        int chipCategoryId = getResources().getIdentifier("chipCategory", "id", requireContext().getPackageName());
        if (chipCategoryId != 0) {
            chipCategory = view.findViewById(chipCategoryId);
        }

        int defaultSpan = getAdaptiveSpanCount();
        gridLayoutManager = new GridLayoutManager(getContext(), defaultSpan);
        rvSearchResults.setLayoutManager(gridLayoutManager);
        rvSearchResults.setHasFixedSize(true);
        rvSearchResults.setItemAnimator(null);
        rvSearchResults.setItemViewCacheSize(12);
        adapter = new FoodItemAdapter(getContext(), filteredItems, this);
        rvSearchResults.setAdapter(adapter);

        rvSearchResults.post(() -> {
            if (!isAdded()) return;
            int adaptiveSpan = getAdaptiveSpanCount();
            if (gridLayoutManager.getSpanCount() != adaptiveSpan) {
                gridLayoutManager.setSpanCount(adaptiveSpan);
            }
        });
    }

    private int getAdaptiveSpanCount() {
        int screenWidthPx = getResources().getDisplayMetrics().widthPixels;
        int minCardWidthPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                170,
                getResources().getDisplayMetrics()
        );
        int span = Math.max(1, screenWidthPx / Math.max(minCardWidthPx, 1));
        return Math.min(span, 3);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips() {
        chipAll.setOnClickListener(v -> {
            currentFilter = "all";
            uncheckAllExcept(chipAll);
            filterItems(etSearch.getText().toString());
        });

        chipNearby.setOnClickListener(v -> {
            currentFilter = "nearby";
            uncheckAllExcept(chipNearby);
            filterItems(etSearch.getText().toString());
        });

        chipPriceLow.setOnClickListener(v -> {
            currentFilter = "price_low";
            uncheckAllExcept(chipPriceLow);
            filterItems(etSearch.getText().toString());
        });

        chipDiscount.setOnClickListener(v -> {
            currentFilter = "discount";
            uncheckAllExcept(chipDiscount);
            filterItems(etSearch.getText().toString());
        });

        if (chipRating != null) {
            chipRating.setOnClickListener(v -> {
                currentFilter = "rating";
                uncheckAllExcept(chipRating);
                filterItems(etSearch.getText().toString());
            });
        }

        if (chipCategory != null) {
            chipCategory.setOnClickListener(v -> {
                currentFilter = "category";
                uncheckAllExcept(chipCategory);
                filterItems(etSearch.getText().toString());
            });
        }
    }

    private void uncheckAllExcept(Chip selected) {
        chipAll.setChecked(chipAll == selected);
        chipNearby.setChecked(chipNearby == selected);
        chipPriceLow.setChecked(chipPriceLow == selected);
        chipDiscount.setChecked(chipDiscount == selected);
        if (chipRating != null) chipRating.setChecked(chipRating == selected);
        if (chipCategory != null) chipCategory.setChecked(chipCategory == selected);
    }

    private void loadAllFoodItems() {
        progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("foodItems").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allFoodItems.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    FoodItem item = itemSnapshot.getValue(FoodItem.class);
                    if (item == null) continue;

                    item.setFoodId(itemSnapshot.getKey());

                    Object quantityRaw = getObjectAny(itemSnapshot, "quantity", "qty", "stock");
                    if (quantityRaw != null) {
                        item.setQuantity(quantityRaw);
                    }

                    String status = getStringAny(itemSnapshot, "status", "itemStatus");
                    if (status != null && !status.trim().isEmpty()) {
                        item.setStatus(status);
                    }

                    if (!item.isListable()) continue;

                    // Calculate Distance
                    if (currentLat != 0 && currentLng != 0) {
                        double dist = LocationUtils.calculateDistance(
                                currentLat, currentLng,
                                item.getLatitudeValue(), item.getLongitudeValue()
                        );
                        item.setDistance(dist);
                    }

                    allFoodItems.add(item);
                }

                progressBar.setVisibility(View.GONE);
                filterItems(etSearch.getText().toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Error loading data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scheduleFilter(String query) {
        if (searchRunnable != null) {
            searchDebounceHandler.removeCallbacks(searchRunnable);
        }
        searchRunnable = () -> filterItems(query);
        searchDebounceHandler.postDelayed(searchRunnable, 160);
    }

    private void filterItems(String query) {
        filteredItems.clear();

        String searchQuery = query == null ? "" : query.toLowerCase().trim();

        for (FoodItem item : allFoodItems) {
            String foodName = safeLower(item.getFoodName());
            String hotelName = safeLower(item.getHotelName());
            String category = safeLower(item.getCategory());
            String description = safeLower(item.getDescription());

            boolean matchesSearch = searchQuery.isEmpty()
                    || foodName.contains(searchQuery)
                    || hotelName.contains(searchQuery)
                    || category.contains(searchQuery)
                    || description.contains(searchQuery);

            if (matchesSearch) {
                filteredItems.add(item);
            }
        }

        switch (currentFilter) {
            case "price_low":
                filteredItems.sort((a, b) ->
                        Double.compare(a.getDiscountedPriceValue(), b.getDiscountedPriceValue()));
                break;
            case "discount":
                filteredItems.sort((a, b) ->
                        Integer.compare(b.getDiscountPercentValue(), a.getDiscountPercentValue()));
                break;
            case "nearby":
                if (currentLat != 0 && currentLng != 0) {
                    filteredItems.sort((a, b) ->
                            Double.compare(a.getDistance(), b.getDistance()));
                }
                break;
            case "rating":
                filteredItems.sort((a, b) ->
                        Double.compare(getRatingForSort(b), getRatingForSort(a)));
                break;
            case "category":
                filteredItems.sort((a, b) ->
                        safeLower(a.getCategory()).compareTo(safeLower(b.getCategory())));
                break;
            default:
                break;
        }

        adapter.updateData(new ArrayList<>(filteredItems));
        updateUI();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private double getRatingForSort(FoodItem item) {
        // Fallback proxy keeps sorting stable even when explicit rating is absent in old data.
        if (item == null) return 0.0;
        double explicit = 0.0;
        try {
            explicit = item.getRatingValue();
        } catch (Exception ignored) {
        }
        if (explicit > 0) return explicit;
        return Math.min(5.0, 3.0 + (item.getDiscountPercentValue() / 100.0) * 2.0);
    }

    private void updateUI() {
        tvResultsCount.setText(filteredItems.size() + " results found");

        if (filteredItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvSearchResults.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvSearchResults.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onFoodItemClick(FoodItem foodItem) {
        Intent intent = new Intent(getContext(), FoodDetailActivity.class);
        intent.putExtra("foodItem", foodItem);
        startActivity(intent);
    }

    @Override
    public void onAddToCartClick(FoodItem foodItem) {
        Toast.makeText(getContext(), foodItem.getFoodName() + " added to cart!",
            Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            searchDebounceHandler.removeCallbacks(searchRunnable);
        }
    }

    private String getStringAny(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Object value = snapshot.child(key).getValue();
            if (value != null) {
                String str = String.valueOf(value).trim();
                if (!str.isEmpty()) return str;
            }
        }
        return null;
    }

    private Object getObjectAny(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Object value = snapshot.child(key).getValue();
            if (value != null) return value;
        }
        return null;
    }
}
