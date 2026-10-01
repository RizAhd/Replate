package com.replate.app.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.FoodDetailActivity;
import com.replate.app.R;
import com.replate.app.adapters.FoodItemAdapter;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.helpers.FoodItemCacheManager;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.models.FoodItem;
import com.replate.app.utils.LocationUtils;
import com.replate.app.utils.SafeSnapshot;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import android.os.Handler;
import android.os.Looper;

/**
 * Search Fragment - Search and filter food items
 */
public class SearchFragment extends Fragment implements FoodItemAdapter.OnFoodItemClickListener {

    private static final String PREFS = "REPLATE_PREFS";
    private static final String PREF_SEARCH_FILTER = "search_preferred_filter";
    private static final String PREF_SEARCH_CATEGORY = "search_preferred_category";
    private static final String PREF_SEARCH_AVAILABLE_ONLY = "search_available_only";
    private static final String EXTRA_INITIAL_FILTER = "initial_filter";
    private static final String EXTRA_FROM_CART_EMPTY = "from_cart_empty";

    private EditText etSearch;
    private RecyclerView rvSearchResults;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private TextView tvResultsCount;
    private TextView tvActiveFilter;
    private View cardFilterAction;
    private View loadingOverlaySearch;
    private View recentSearchesSection;
    private ChipGroup chipGroupRecentSearches;
    private TextView tvClearHistory;

    private DatabaseReference mDatabase;
    private FoodItemAdapter adapter;
    private FusedLocationProviderClient fusedLocationClient;
    private GridLayoutManager gridLayoutManager;
    private FoodItemCacheManager cacheManager;
    private ReplateDatabaseHelper localDb;

    private final List<FoodItem> allFoodItems = new ArrayList<>();
    private final List<FoodItem> filteredItems = new ArrayList<>();

    private final Handler searchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private String currentFilter = "for_you";
    private String selectedCategory = "All";
    private boolean availableOnly = false;
    private double currentLat = 0;
    private double currentLng = 0;

    private ValueEventListener foodItemsListener;
    private DatabaseReference foodItemsRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        cacheManager = new FoodItemCacheManager(requireContext());
        localDb = ReplateDatabaseHelper.getInstance(requireContext());

        initViews(view);
        loadSavedFilterPreference();
        applyLaunchOverrides();
        setupSearch();
        setupFilterChips();
        setupAdvancedFilterAction();

        // Cache-first rendering for instant feel.
        loadCachedSearchItems();

        // Instant loading first; location can enhance sort afterward.
        loadAllFoodItems();
        getUserLocation();

        return view;
    }

    private void loadCachedSearchItems() {
        // Try SQLite cache first, then SharedPreferences fallback
        List<FoodItem> cachedItems = null;

        if (localDb != null && localDb.isFoodCacheFresh(5 * 60 * 1000)) {
            cachedItems = localDb.getCachedFoodItems();
        }

        if ((cachedItems == null || cachedItems.isEmpty()) && cacheManager != null) {
            cachedItems = cacheManager.getCachedFoodItems();
        }

        if (cachedItems == null || cachedItems.isEmpty()) return;

        allFoodItems.clear();
        allFoodItems.addAll(cachedItems);
        filterItems(etSearch == null ? "" : etSearch.getText().toString());
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        setLoadingState(false);
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
                    refreshDistancesFromCurrentLocation();
                    filterItems(etSearch == null ? "" : etSearch.getText().toString());
                }
            });
        }
    }

    private void refreshDistancesFromCurrentLocation() {
        if (currentLat == 0 || currentLng == 0 || allFoodItems.isEmpty()) {
            return;
        }

        for (FoodItem item : allFoodItems) {
            if (item == null) continue;
            if (!item.hasValidCoordinates()) {
                item.setDistance(0);
                continue;
            }

            double dist = LocationUtils.calculateDistance(
                    currentLat,
                    currentLng,
                    item.getLatitudeValue(),
                    item.getLongitudeValue()
            );
            item.setDistance(dist);
        }
    }

    private void initViews(View view) {
        etSearch = view.findViewById(R.id.etSearch);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        emptyState = view.findViewById(R.id.emptyState);
        progressBar = view.findViewById(R.id.progressBar);
        loadingOverlaySearch = view.findViewById(R.id.loadingOverlaySearch);
        tvResultsCount = view.findViewById(R.id.tvResultsCount);
        tvActiveFilter = view.findViewById(R.id.tvActiveFilter);
        cardFilterAction = view.findViewById(R.id.cardFilterAction);
        recentSearchesSection = view.findViewById(R.id.recentSearchesSection);
        chipGroupRecentSearches = view.findViewById(R.id.chipGroupRecentSearches);
        tvClearHistory = view.findViewById(R.id.tvClearHistory);

        // ...removed chip references...

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
        // Show recent searches when search field is focused and empty
        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etSearch.getText().toString().trim().isEmpty()) {
                showRecentSearches();
            }
        });

        // Clear history button
        if (tvClearHistory != null) {
            tvClearHistory.setOnClickListener(v -> {
                if (localDb != null) {
                    localDb.clearSearchHistory();
                    hideRecentSearches();
                }
            });
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s == null ? "" : s.toString().trim();
                if (text.isEmpty()) {
                    showRecentSearches();
                } else {
                    hideRecentSearches();
                }
                scheduleFilter(text);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Show history on initial load if search is empty
        if (etSearch.getText().toString().trim().isEmpty()) {
            showRecentSearches();
        }
    }

    private void showRecentSearches() {
        if (localDb == null || recentSearchesSection == null || chipGroupRecentSearches == null) return;

        List<String> recent = localDb.getRecentSearches(8);
        if (recent.isEmpty()) {
            hideRecentSearches();
            return;
        }

        chipGroupRecentSearches.removeAllViews();
        for (String query : recent) {
            Chip chip = new Chip(requireContext());
            chip.setText(query);
            chip.setChipBackgroundColorResource(R.color.green_pale);
            chip.setTextColor(getResources().getColor(R.color.text_primary, null));
            chip.setCloseIconVisible(true);
            chip.setCloseIconResource(R.drawable.ic_close);
            chip.setCloseIconTintResource(R.color.text_secondary);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            chip.setChipCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()));

            // Click to fill search
            chip.setOnClickListener(v -> {
                etSearch.setText(query);
                etSearch.setSelection(query.length());
                hideRecentSearches();
            });

            // Close to delete from history
            chip.setOnCloseIconClickListener(v -> {
                localDb.deleteSearchQuery(query);
                chipGroupRecentSearches.removeView(chip);
                if (chipGroupRecentSearches.getChildCount() == 0) {
                    hideRecentSearches();
                }
            });

            chipGroupRecentSearches.addView(chip);
        }

        recentSearchesSection.setVisibility(View.VISIBLE);
    }

    private void hideRecentSearches() {
        if (recentSearchesSection != null) {
            recentSearchesSection.setVisibility(View.GONE);
        }
    }

    private void setupFilterChips() {
        // Filter chips are now handled via bottom sheet filter dialog
        // This method is kept for compatibility
    }

    private void setupAdvancedFilterAction() {
        if (cardFilterAction == null) return;
        cardFilterAction.setOnClickListener(this::showAdvancedFilterMenu);
    }

    private void showAdvancedFilterMenu(View anchor) {
        if (!isAdded() || getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_search_filters, null);
        dialog.setContentView(sheetView);

        RadioGroup rgSort = sheetView.findViewById(R.id.rgSort);
        Spinner spCategory = sheetView.findViewById(R.id.spCategory);
        MaterialCheckBox cbAvailableOnly = sheetView.findViewById(R.id.cbAvailableOnly);
        MaterialButton btnApply = sheetView.findViewById(R.id.btnApplyFilters);
        MaterialButton btnReset = sheetView.findViewById(R.id.btnResetFilters);

        // Set current sort selection
        switch (currentFilter) {
            case "nearby":    rgSort.check(R.id.rbNearby);    break;
            case "price_low": rgSort.check(R.id.rbPriceLow);  break;
            case "price_high": rgSort.check(R.id.rbPriceHigh); break;
            case "discount":  rgSort.check(R.id.rbDiscount);  break;
            case "rating":    rgSort.check(R.id.rbRating);    break;
            default:          rgSort.check(R.id.rbForYou);    break;
        }

        // Build category list from loaded items
        Set<String> categorySet = new LinkedHashSet<>();
        categorySet.add("All");
        for (FoodItem item : allFoodItems) {
            String cat = item.getCategory();
            if (cat != null && !cat.trim().isEmpty()) {
                categorySet.add(cat.trim());
            }
        }
        List<String> categoryList = new ArrayList<>(categorySet);
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, categoryList);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(catAdapter);

        int catIndex = categoryList.indexOf(selectedCategory);
        spCategory.setSelection(catIndex >= 0 ? catIndex : 0);

        // Available only checkbox
        cbAvailableOnly.setChecked(availableOnly);

        // Track pending spinner selection without closing dialog
        java.util.concurrent.atomic.AtomicReference<String> pendingCategory =
                new java.util.concurrent.atomic.AtomicReference<>(selectedCategory);
        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                pendingCategory.set(categoryList.get(position));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnApply.setOnClickListener(v -> {
            // Read sort selection
            int checkedId = rgSort.getCheckedRadioButtonId();
            if (checkedId == R.id.rbNearby)        currentFilter = "nearby";
            else if (checkedId == R.id.rbPriceLow)  currentFilter = "price_low";
            else if (checkedId == R.id.rbPriceHigh) currentFilter = "price_high";
            else if (checkedId == R.id.rbDiscount)  currentFilter = "discount";
            else if (checkedId == R.id.rbRating)    currentFilter = "rating";
            else                                     currentFilter = "for_you";

            selectedCategory = pendingCategory.get();
            availableOnly = cbAvailableOnly.isChecked();

            dialog.dismiss();
            onFilterChangedByUser();
        });

        btnReset.setOnClickListener(v -> {
            rgSort.check(R.id.rbForYou);
            spCategory.setSelection(0);
            cbAvailableOnly.setChecked(false);
            pendingCategory.set("All");
        });

        dialog.show();
    }

    private void onFilterChangedByUser() {
        saveFilterPreference(currentFilter);
        updateActiveFilterLabel();
        filterItems(etSearch == null ? "" : etSearch.getText().toString());
    }

    private void syncChipStateWithCurrentFilter() {
        // Filter chips are now handled via bottom sheet filter dialog
        // This method is kept for compatibility but does nothing
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
            case "price_high":
                label = getString(R.string.search_filter_high_price);
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

        if (!"All".equalsIgnoreCase(selectedCategory)) {
            label = label + " · " + selectedCategory;
        }
        if (availableOnly) {
            label = label + " · " + getString(R.string.search_filter_available_only);
        }
        tvActiveFilter.setText(getString(R.string.search_active_filter_template, label));
    }

    private void loadSavedFilterPreference() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        currentFilter = prefs.getString(PREF_SEARCH_FILTER, "for_you");
        selectedCategory = prefs.getString(PREF_SEARCH_CATEGORY, "All");
        availableOnly = prefs.getBoolean(PREF_SEARCH_AVAILABLE_ONLY, false);
        syncChipStateWithCurrentFilter();
        updateActiveFilterLabel();
    }

    private void saveFilterPreference(String filter) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_SEARCH_FILTER, filter)
                .putString(PREF_SEARCH_CATEGORY, selectedCategory)
                .putBoolean(PREF_SEARCH_AVAILABLE_ONLY, availableOnly)
                .apply();
    }

    private void applyLaunchOverrides() {
        if (!isAdded() || getActivity() == null) return;
        Intent intent = getActivity().getIntent();
        if (intent == null) return;

        String launchFilter = intent.getStringExtra(EXTRA_INITIAL_FILTER);
        if (launchFilter != null && !launchFilter.trim().isEmpty()) {
            String normalized = launchFilter.trim().toLowerCase();
            if ("nearby".equals(normalized)
                    || "price_low".equals(normalized)
                    || "price_high".equals(normalized)
                    || "discount".equals(normalized)
                    || "rating".equals(normalized)
                    || "for_you".equals(normalized)) {
                currentFilter = normalized;
                saveFilterPreference(currentFilter);
                updateActiveFilterLabel();
            }
            intent.removeExtra(EXTRA_INITIAL_FILTER);
        }

        if (intent.getBooleanExtra(EXTRA_FROM_CART_EMPTY, false)) {
            intent.removeExtra(EXTRA_FROM_CART_EMPTY);
        }
    }


    private void loadAllFoodItems() {
        if (allFoodItems.isEmpty()) {
            setLoadingState(true);
        }

        if (foodItemsRef != null && foodItemsListener != null) {
            foodItemsRef.removeEventListener(foodItemsListener);
        }

        foodItemsRef = mDatabase.child("foodItems");
        foodItemsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                allFoodItems.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    FoodItem item = SafeSnapshot.getValue(itemSnapshot, FoodItem.class);
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

                    if (currentLat != 0 && currentLng != 0 && item.hasValidCoordinates()) {
                        double dist = LocationUtils.calculateDistance(
                                currentLat, currentLng,
                                item.getLatitudeValue(), item.getLongitudeValue()
                        );
                        item.setDistance(dist);
                    } else {
                        item.setDistance(0);
                    }

                    allFoodItems.add(item);
                }

                if (!allFoodItems.isEmpty()) {
                    if (localDb != null) {
                        localDb.cacheFoodItems(new ArrayList<>(allFoodItems));
                    }
                    if (cacheManager != null) {
                        cacheManager.saveFoodItems(new ArrayList<>(allFoodItems));
                    }
                }

                setLoadingState(false);
                filterItems(etSearch == null ? "" : etSearch.getText().toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                setLoadingState(false);
                if (allFoodItems.isEmpty()) {
                    ToastUtils.showCustomToast(getContext(), R.string.search_error_loading_data);
                }
            }
        };

        foodItemsRef.addValueEventListener(foodItemsListener);
    }

    private void setLoadingState(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (loadingOverlaySearch != null) {
            loadingOverlaySearch.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void scheduleFilter(String query) {
        if (searchRunnable != null) {
            searchDebounceHandler.removeCallbacks(searchRunnable);
        }
        searchRunnable = () -> {
            filterItems(query);
            // Save meaningful queries (3+ chars) to SQLite search history
            if (localDb != null && query != null && query.trim().length() >= 3) {
                localDb.saveSearchQuery(query.trim());
            }
        };
        // Reduced debounce time for instant search results (80ms instead of 130ms)
        searchDebounceHandler.postDelayed(searchRunnable, 80);
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

            if (!matchesSearch) continue;

            // Category filter
            if (!item.matchesCategory(selectedCategory)) continue;

            // Available only filter
            if (availableOnly && !item.isAvailableValue()) continue;

            filteredItems.add(item);
        }

        switch (currentFilter) {
            case "price_low":
                filteredItems.sort((a, b) ->
                        Double.compare(a.getDiscountedPriceValue(), b.getDiscountedPriceValue()));
                break;
            case "price_high":
                filteredItems.sort((a, b) ->
                        Double.compare(b.getDiscountedPriceValue(), a.getDiscountedPriceValue()));
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
        if (item == null) {
            return 0.0;
        }
        double explicit = 0.0;
        try {
            explicit = item.getRatingValue();
        } catch (Exception ignored) {
        }
        if (explicit > 0) {
            return explicit;
        }
        return Math.min(5.0, 3.0 + (item.getDiscountPercentValue() / 100.0) * 2.0);
    }

    private void updateUI() {
        if (tvResultsCount != null) {
            tvResultsCount.setText(getString(R.string.search_results_count_template, filteredItems.size()));
        }

        if (filteredItems.isEmpty()) {
            if (emptyState != null) {
                emptyState.setVisibility(View.VISIBLE);
            }
            if (rvSearchResults != null) {
                rvSearchResults.setVisibility(View.GONE);
            }
        } else {
            if (emptyState != null) {
                emptyState.setVisibility(View.GONE);
            }
            if (rvSearchResults != null) {
                rvSearchResults.setVisibility(View.VISIBLE);
            }
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
        if (foodItem == null || foodItem.getFoodId() == null || getContext() == null) {
            ToastUtils.showCustomToast(getContext(), R.string.search_couldn_t_add_this_item);
            return;
        }

        CartItem cartItem = new CartItem(
                foodItem.getFoodId(),
                foodItem.getFoodName(),
                foodItem.getHotelName(),
                foodItem.getHotelId(),
                foodItem.getImageUrl(),
                foodItem.getDiscountedPriceValue(),
                foodItem.getOriginalPriceValue(),
                1,
                foodItem.getDiscountPercentValue()
        );
        CartDatabaseHelper.getInstance(requireContext()).addItem(cartItem);
        ToastUtils.showCustomToast(getContext(), foodItem.getFoodName() + " added to cart!");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            searchDebounceHandler.removeCallbacks(searchRunnable);
        }
        if (foodItemsRef != null && foodItemsListener != null) {
            foodItemsRef.removeEventListener(foodItemsListener);
        }
    }

    private String getStringAny(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Object value = snapshot.child(key).getValue();
            if (value != null) {
                String str = String.valueOf(value).trim();
                if (!str.isEmpty()) {
                    return str;
                }
            }
        }
        return null;
    }

    private Object getObjectAny(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Object value = snapshot.child(key).getValue();
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
