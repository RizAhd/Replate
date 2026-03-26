package com.replate.app.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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

    private static final String PREFS = "REPLATE_PREFS";
    private static final String PREF_SEARCH_FILTER = "search_preferred_filter";

    private EditText etSearch;
    private RecyclerView rvSearchResults;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private TextView tvResultsCount;
    private TextView tvActiveFilter;
    private View cardFilterAction;

    private Chip chipAll, chipNearby, chipPriceLow, chipDiscount, chipRating, chipCategory;

    private DatabaseReference mDatabase;
    private FoodItemAdapter adapter;
    private FusedLocationProviderClient fusedLocationClient;
    private GridLayoutManager gridLayoutManager;

    private final List<FoodItem> allFoodItems = new ArrayList<>();
    private final List<FoodItem> filteredItems = new ArrayList<>();

    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private String currentFilter = "for_you";
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
        loadSavedFilterPreference();
        setupSearch();
        setupFilterChips();
        setupAdvancedFilterAction();

        // Instant loading first; location can enhance sort afterward.
        loadAllFoodItems();
        getUserLocation();

        return view;
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    currentLat = location.getLatitude();
                    currentLng = location.getLongitude();
                    filterItems(etSearch == null ? "" : etSearch.getText().toString());
                }
            });
        }
    }

    private void initViews(View view) {
        etSearch = view.findViewById(R.id.etSearch);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        emptyState = view.findViewById(R.id.emptyState);
        progressBar = view.findViewById(R.id.progressBar);
        tvResultsCount = view.findViewById(R.id.tvResultsCount);
        tvActiveFilter = view.findViewById(R.id.tvActiveFilter);
        cardFilterAction = view.findViewById(R.id.cardFilterAction);

        chipAll = view.findViewById(R.id.chipAll);
        chipNearby = view.findViewById(R.id.chipNearby);
        chipPriceLow = view.findViewById(R.id.chipPriceLow);
        chipDiscount = view.findViewById(R.id.chipDiscount);
        chipRating = view.findViewById(R.id.chipRating);
        chipCategory = view.findViewById(R.id.chipCategory);

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
            onFilterChangedByUser();
        });

        chipNearby.setOnClickListener(v -> {
            currentFilter = "nearby";
            uncheckAllExcept(chipNearby);
            onFilterChangedByUser();
        });

        chipPriceLow.setOnClickListener(v -> {
            currentFilter = "price_low";
            uncheckAllExcept(chipPriceLow);
            onFilterChangedByUser();
        });

        chipDiscount.setOnClickListener(v -> {
            currentFilter = "discount";
            uncheckAllExcept(chipDiscount);
            onFilterChangedByUser();
        });

        chipRating.setOnClickListener(v -> {
            currentFilter = "rating";
            uncheckAllExcept(chipRating);
            onFilterChangedByUser();
        });

        chipCategory.setOnClickListener(v -> {
            currentFilter = "category";
            uncheckAllExcept(chipCategory);
            onFilterChangedByUser();
        });
    }

    private void setupAdvancedFilterAction() {
        if (cardFilterAction == null) return;
        cardFilterAction.setOnClickListener(this::showAdvancedFilterMenu);
    }

    private void showAdvancedFilterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, getString(R.string.search_filter_for_you));
        menu.getMenu().add(0, 2, 1, getString(R.string.search_filter_nearby));
        menu.getMenu().add(0, 3, 2, getString(R.string.search_filter_low_price));
        menu.getMenu().add(0, 4, 3, getString(R.string.search_filter_discount));
        menu.getMenu().add(0, 5, 4, getString(R.string.search_filter_rating));
        menu.getMenu().add(0, 6, 5, getString(R.string.search_filter_category));

        menu.setOnMenuItemClickListener(this::onAdvancedFilterItemSelected);
        menu.show();
    }

    private boolean onAdvancedFilterItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                currentFilter = "for_you";
                break;
            case 2:
                currentFilter = "nearby";
                break;
            case 3:
                currentFilter = "price_low";
                break;
            case 4:
                currentFilter = "discount";
                break;
            case 5:
                currentFilter = "rating";
                break;
            case 6:
                currentFilter = "category";
                break;
            default:
                return false;
        }

        syncChipStateWithCurrentFilter();
        onFilterChangedByUser();
        return true;
    }

    private void onFilterChangedByUser() {
        saveFilterPreference(currentFilter);
        updateActiveFilterLabel();
        filterItems(etSearch == null ? "" : etSearch.getText().toString());
    }

    private void syncChipStateWithCurrentFilter() {
        if (chipAll == null || chipNearby == null || chipPriceLow == null || chipDiscount == null) return;

        if ("all".equals(currentFilter) || "for_you".equals(currentFilter)) {
            uncheckAllExcept(chipAll);
        } else if ("nearby".equals(currentFilter)) {
            uncheckAllExcept(chipNearby);
        } else if ("price_low".equals(currentFilter)) {
            uncheckAllExcept(chipPriceLow);
        } else if ("discount".equals(currentFilter)) {
            uncheckAllExcept(chipDiscount);
        } else if ("rating".equals(currentFilter) && chipRating != null) {
            uncheckAllExcept(chipRating);
        } else if ("category".equals(currentFilter) && chipCategory != null) {
            uncheckAllExcept(chipCategory);
        }
    }

    private void updateActiveFilterLabel() {
        if (tvActiveFilter == null) return;

        String label;
        switch (currentFilter) {
            case "nearby":
                label = getString(R.string.search_filter_nearby);
                break;
            case "price_low":
                label = getString(R.string.search_filter_low_price);
                break;
            case "discount":
                label = getString(R.string.search_filter_discount);
                break;
            case "rating":
                label = getString(R.string.search_filter_rating);
                break;
            case "category":
                label = getString(R.string.search_filter_category);
                break;
            case "for_you":
                label = getString(R.string.search_filter_for_you);
                break;
            default:
                label = getString(R.string.search_chip_all);
                break;
        }
        tvActiveFilter.setText(getString(R.string.search_active_filter_template, label));
    }

    private void loadSavedFilterPreference() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        currentFilter = prefs.getString(PREF_SEARCH_FILTER, "for_you");
        syncChipStateWithCurrentFilter();
        updateActiveFilterLabel();
    }

    private void saveFilterPreference(String filter) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_SEARCH_FILTER, filter).apply();
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
                filterItems(etSearch == null ? "" : etSearch.getText().toString());
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
        searchDebounceHandler.postDelayed(searchRunnable, 130);
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
            case "for_you":
                if (currentLat != 0 && currentLng != 0) {
                    filteredItems.sort((a, b) -> {
                        double scoreA = a.getDistance() + (a.getDiscountedPriceValue() / 1000.0) - (a.getDiscountPercentValue() * 0.02);
                        double scoreB = b.getDistance() + (b.getDiscountedPriceValue() / 1000.0) - (b.getDiscountPercentValue() * 0.02);
                        return Double.compare(scoreA, scoreB);
                    });
                } else {
                    filteredItems.sort((a, b) ->
                            Integer.compare(b.getDiscountPercentValue(), a.getDiscountPercentValue()));
                }
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
        tvResultsCount.setText(getString(R.string.search_results_count_template, filteredItems.size()));

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
