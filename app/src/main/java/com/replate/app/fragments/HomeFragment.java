package com.replate.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.CartActivity;
import com.replate.app.FoodDetailActivity;
import com.replate.app.MainActivity;
import com.replate.app.NotificationsActivity;
import com.replate.app.R;
import com.replate.app.SearchActivity;
import com.replate.app.adapters.BannerAdapter;
import com.replate.app.adapters.CategoryAdapter;
import com.replate.app.adapters.FoodItemAdapter;
import com.replate.app.models.Category;
import com.replate.app.models.FoodItem;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.utils.ShakeDetector;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

/**
 * Home Fragment - Main dashboard showing food deals
 */
public class HomeFragment extends Fragment implements
        FoodItemAdapter.OnFoodItemClickListener,
        CategoryAdapter.OnCategoryClickListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    // Views
    private View ivMenu;
    private TextView tvCurrentLocation;
    private View ivNotification;
    private androidx.appcompat.widget.SearchView searchView;
    private ViewPager2 bannerViewPager;
    private RecyclerView rvCategories, rvHotDeals, rvFoodItems;
    private me.relex.circleindicator.CircleIndicator3 bannerIndicator;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0;
    private double currentLongitude = 0;

    // Adapters
    private CategoryAdapter categoryAdapter;
    private FoodItemAdapter foodItemAdapter;
    private FoodItemAdapter hotDealsAdapter;
    private BannerAdapter bannerAdapter;

    // Data
    private List<Category> categories = new ArrayList<>();
    private List<FoodItem> foodItems = new ArrayList<>();
    private List<FoodItem> allFoodItems = new ArrayList<>(); // Unfiltered list
    private List<FoodItem> hotDeals = new ArrayList<>();
    private String selectedCategory = "All";

    // Shake to refresh (REQUIREMENT: Sensors)
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Initialize views
        ivMenu = view.findViewById(R.id.ivMenu);
        tvCurrentLocation = view.findViewById(R.id.tvCurrentLocation);
        ivNotification = view.findViewById(R.id.ivNotification);
        searchView = view.findViewById(R.id.searchView);
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        bannerIndicator = view.findViewById(R.id.bannerIndicator);
        rvCategories = view.findViewById(R.id.rvCategories);
        rvHotDeals = view.findViewById(R.id.rvHotDeals);
        rvFoodItems = view.findViewById(R.id.rvDeals);
        progressBar = view.findViewById(R.id.progressBar);

        rvCategories.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        rvHotDeals.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvFoodItems.setLayoutManager(new LinearLayoutManager(getContext()));

        categoryAdapter = new CategoryAdapter(getContext(), categories, this);
        rvCategories.setAdapter(categoryAdapter);

        foodItemAdapter = new FoodItemAdapter(getContext(), foodItems, this);
        rvFoodItems.setAdapter(foodItemAdapter);

        hotDealsAdapter = new FoodItemAdapter(getContext(), hotDeals, this);
        rvHotDeals.setAdapter(hotDealsAdapter);

        // Setup banner
        setupBanner();

        // Setup greeting (Removed)
        // setupGreeting();

        // Setup categories
        setupCategories();

        // Setup click listeners
        setupClickListeners();

        // Get user location and load food items
        getUserLocation();

        // Setup shake-to-refresh (REQUIREMENT: Sensors - Accelerometer)
        sensorManager = (SensorManager) requireActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();
        shakeDetector.setOnShakeListener(count -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "🔄 Refreshing nearby deals...", Toast.LENGTH_SHORT).show();
                    getUserLocation();
                });
            }
        });

        return view;
    }

    private void setupCategories() {
        categories.clear();
        categories.add(new Category("all", "All", R.drawable.ic_category_all));
        categories.add(new Category("meals", "Meals", R.drawable.ic_category_meals));
        categories.add(new Category("bakery", "Bakery", R.drawable.ic_category_bakery));
        categories.add(new Category("drinks", "Drinks", R.drawable.ic_category_drinks));
        categories.add(new Category("groceries", "Groceries", R.drawable.ic_category_groceries));
        categories.add(new Category("desserts", "Desserts", R.drawable.ic_category_desserts));

        categoryAdapter.notifyDataSetChanged();
    }

    private void setupBanner() {
        List<Integer> bannerImages = new ArrayList<>();
        bannerImages.add(R.drawable.banner_1);
        bannerImages.add(R.drawable.banner_2);
        bannerImages.add(R.drawable.banner_3);

        bannerAdapter = new BannerAdapter(getContext(), bannerImages);
        bannerViewPager.setAdapter(bannerAdapter);

        // Auto-scroll banner
        bannerViewPager.setOffscreenPageLimit(3);
    }

    private void setupClickListeners() {
        // Menu click - Open sidebar
        if (ivMenu != null) {
            ivMenu.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openDrawer();
                }
            });
        }

        // Notification click
        ivNotification.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), NotificationsActivity.class);
            startActivity(intent);
        });

        // Search bar click
        if (searchView != null) {
            searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    Intent intent = new Intent(getContext(), SearchActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    private void getUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(location -> {
                if (location != null) {
                    currentLatitude = location.getLatitude();
                    currentLongitude = location.getLongitude();
                }
                // Load food items after getting location (or without it)
                loadFoodItems();
            })
            .addOnFailureListener(e -> {
                // Load food items even if location fails
                loadFoodItems();
            });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation();
            } else {
                // Load food items without location
                loadFoodItems();
            }
        }
    }

    private void loadFoodItems() {
        showLoading(true);

        mDatabase.child("foodItems").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allFoodItems.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    FoodItem item = itemSnapshot.getValue(FoodItem.class);
                    if (item == null) continue;

                    item.setFoodId(itemSnapshot.getKey());

                    // Fix schema variants from Firebase nodes used by web panels.
                    if (item.getImageUrl() == null || item.getImageUrl().trim().isEmpty()) {
                        item.setImageUrl(getStringAny(itemSnapshot,
                                "imageUrl", "photoUrl", "foodImage", "image", "imageURL", "image_path"));
                    }
                    if (item.getLatitudeValue() == 0) {
                        Object lat = getObjectAny(itemSnapshot, "latitude", "hotelLatitude", "lat");
                        if (lat != null) item.setLatitude(lat);
                    }
                    if (item.getLongitudeValue() == 0) {
                        Object lng = getObjectAny(itemSnapshot, "longitude", "hotelLongitude", "lng");
                        if (lng != null) item.setLongitude(lng);
                    }
                    if ((item.getPickupTimeStart() == null || item.getPickupTimeStart().isEmpty())) {
                        String pickupStart = getStringAny(itemSnapshot, "pickupTimeStart", "pickupStart", "pickup_from");
                        if (pickupStart != null) item.setPickupTimeStart(pickupStart);
                    }
                    if ((item.getPickupTimeEnd() == null || item.getPickupTimeEnd().isEmpty())) {
                        String pickupEnd = getStringAny(itemSnapshot, "pickupTimeEnd", "pickupEnd", "pickup_to");
                        if (pickupEnd != null) item.setPickupTimeEnd(pickupEnd);
                    }
                    if (item.getDiscountPercentValue() == 0) {
                        Object discount = getObjectAny(itemSnapshot, "discountPercent", "discountPercentage", "discount");
                        if (discount != null) item.setDiscountPercent(discount);
                    }

                    String status = getStringAny(itemSnapshot, "status", "itemStatus");
                    boolean isStatusAvailable = status == null || "available".equalsIgnoreCase(status);
                    boolean hasQuantity = item.getQuantityValue() > 0;

                    if (!isStatusAvailable || !hasQuantity) continue;

                    if (currentLatitude != 0 && currentLongitude != 0) {
                        double distance = calculateDistance(
                                currentLatitude, currentLongitude,
                                item.getLatitudeValue(), item.getLongitudeValue()
                        );
                        item.setDistance(distance);
                    }

                    allFoodItems.add(item);
                }

                if (currentLatitude != 0) {
                    Collections.sort(allFoodItems, (a, b) -> Double.compare(a.getDistance(), b.getDistance()));
                }

                updateHotDeals();
                filterByCategory(selectedCategory);
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(getContext(), "Failed to load food items", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateHotDeals() {
        hotDeals.clear();
        if (allFoodItems.isEmpty()) {
            hotDealsAdapter.notifyDataSetChanged();
            return;
        }

        List<FoodItem> sortedByPrice = new ArrayList<>(allFoodItems);
        Collections.sort(sortedByPrice, Comparator.comparingDouble(FoodItem::getDiscountedPriceValue));

        int max = Math.min(6, sortedByPrice.size());
        for (int i = 0; i < max; i++) {
            hotDeals.add(sortedByPrice.get(i));
        }
        hotDealsAdapter.notifyDataSetChanged();
    }

    private String getStringAny(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Object v = snapshot.child(key).getValue();
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private Object getObjectAny(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            Object v = snapshot.child(key).getValue();
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Calculate distance between two coordinates using Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // Earth's radius in km

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private void filterByCategory(String category) {
        foodItems.clear();

        if (category.equals("All")) {
            foodItems.addAll(allFoodItems);
        } else {
            for (FoodItem item : allFoodItems) {
                if (item.getCategory() != null &&
                    item.getCategory().toLowerCase().equals(category.toLowerCase())) {
                    foodItems.add(item);
                }
            }
        }

        foodItemAdapter.notifyDataSetChanged();

        // Show/hide empty state
        if (foodItems.isEmpty()) {
            // emptyState.setVisibility(View.VISIBLE);
            rvFoodItems.setVisibility(View.GONE);
        } else {
            // emptyState.setVisibility(View.GONE);
            rvFoodItems.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // FoodItemAdapter.OnFoodItemClickListener implementation
    @Override
    public void onFoodItemClick(FoodItem foodItem) {
        Intent intent = new Intent(getContext(), FoodDetailActivity.class);
        intent.putExtra("foodItem", foodItem);
        startActivity(intent);
    }

    @Override
    public void onAddToCartClick(FoodItem foodItem) {
        // Add to cart using SQLite
        addToCart(foodItem);
        Toast.makeText(getContext(), foodItem.getFoodName() + " added to cart!",
            Toast.LENGTH_SHORT).show();
    }

    private void addToCart(FoodItem foodItem) {
        // REQUIREMENT: SQLite Data Storage
        CartDatabaseHelper cartDb = CartDatabaseHelper.getInstance(requireContext());
        com.replate.app.models.CartItem cartItem = new com.replate.app.models.CartItem(
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
        cartDb.addItem(cartItem);
    }

    // CategoryAdapter.OnCategoryClickListener implementation
    @Override
    public void onCategoryClick(Category category) {
        selectedCategory = category.getName();
        filterByCategory(selectedCategory);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register shake sensor
        if (sensorManager != null && accelerometer != null && shakeDetector != null) {
            sensorManager.registerListener(shakeDetector, accelerometer,
                    SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister shake sensor
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
    }
}
