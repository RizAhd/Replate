package com.replate.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.Collections;
import java.util.List;

/**
 * Search Fragment - Search and filter food items
 */
public class SearchFragment extends Fragment implements FoodItemAdapter.OnFoodItemClickListener {

    private EditText etSearch;
    private RecyclerView rvSearchResults;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private TextView tvResultsCount;

    private Chip chipAll, chipNearby, chipPriceLow, chipDiscount;

    private DatabaseReference mDatabase;
    private FoodItemAdapter adapter;
    private FusedLocationProviderClient fusedLocationClient;

    private List<FoodItem> allFoodItems = new ArrayList<>();
    private List<FoodItem> filteredItems = new ArrayList<>();

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

        rvSearchResults.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new FoodItemAdapter(getContext(), filteredItems, this);
        rvSearchResults.setAdapter(adapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterItems(s.toString());
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
    }

    private void uncheckAllExcept(Chip selected) {
        chipAll.setChecked(chipAll == selected);
        chipNearby.setChecked(chipNearby == selected);
        chipPriceLow.setChecked(chipPriceLow == selected);
        chipDiscount.setChecked(chipDiscount == selected);
    }

    private void loadAllFoodItems() {
        progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("foodItems").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allFoodItems.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    FoodItem item = itemSnapshot.getValue(FoodItem.class);
                    if (item != null && item.isAvailableValue()) {
                        item.setFoodId(itemSnapshot.getKey());

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

    private void filterItems(String query) {
        filteredItems.clear();

        String searchQuery = query.toLowerCase().trim();

        for (FoodItem item : allFoodItems) {
            boolean matchesSearch = searchQuery.isEmpty() ||
                item.getFoodName().toLowerCase().contains(searchQuery) ||
                item.getHotelName().toLowerCase().contains(searchQuery) ||
                (item.getCategory() != null && item.getCategory().toLowerCase().contains(searchQuery));

            if (matchesSearch) {
                filteredItems.add(item);
            }
        }

        // Apply sorting based on filter
        switch (currentFilter) {
            case "price_low":
                Collections.sort(filteredItems, (a, b) ->
                    Double.compare(a.getDiscountedPriceValue(), b.getDiscountedPriceValue()));
                break;
            case "discount":
                Collections.sort(filteredItems, (a, b) ->
                    Integer.compare(b.getDiscountPercentValue(), a.getDiscountPercentValue()));
                break;
            case "nearby":
                // Sort by distance (FoodItem must have distance set)
                Collections.sort(filteredItems, (a, b) ->
                    Double.compare(a.getDistance(), b.getDistance()));
                break;
        }

        adapter.notifyDataSetChanged();
        updateUI();
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
}

