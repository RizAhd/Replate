package com.replate.app.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.FoodDetailActivity;
import com.replate.app.MainActivity;
import com.replate.app.NotificationsActivity;
import com.replate.app.R;
import com.replate.app.SearchActivity;
import com.replate.app.adapters.BannerAdapter;
import com.replate.app.adapters.CategoryAdapter;
import com.replate.app.adapters.FoodItemAdapter;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.models.Category;
import com.replate.app.models.FoodItem;
import com.replate.app.utils.ShakeDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment implements
        FoodItemAdapter.OnFoodItemClickListener,
        CategoryAdapter.OnCategoryClickListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final long BANNER_AUTO_SCROLL_MS = 3200L;

    // Views
    private View ivMenu;
    private TextView tvCurrentLocation;
    private View ivNotification;
    private androidx.appcompat.widget.SearchView searchView;
    private ViewPager2 bannerViewPager;
    private RecyclerView rvCategories, rvHotDeals, rvFoodItems, rvFavorites;
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
    private FoodItemAdapter favoritesAdapter;
    private BannerAdapter bannerAdapter;

    // Data
    private List<Category> categories = new ArrayList<>();
    private List<FoodItem> foodItems = new ArrayList<>();
    private List<FoodItem> allFoodItems = new ArrayList<>(); // Unfiltered list
    private List<FoodItem> hotDeals = new ArrayList<>();
    private List<FoodItem> favoriteItems = new ArrayList<>();
    private java.util.Set<String> favoriteFoodIds = new java.util.HashSet<>();
    private String selectedCategory = "All";

    // Shake to refresh (REQUIREMENT: Sensors)
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fineGranted || coarseGranted) {
                    getUserLocation();
                } else {
                    loadFoodItems();
                }
            });

    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private ExecutorService locationExecutor;
    private final Runnable bannerAutoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (bannerViewPager == null || bannerAdapter == null || bannerAdapter.getItemCount() <= 1 || !isAdded()) {
                return;
            }
            int nextItem = (bannerViewPager.getCurrentItem() + 1) % bannerAdapter.getItemCount();
            bannerViewPager.setCurrentItem(nextItem, true);
            bannerHandler.postDelayed(this, BANNER_AUTO_SCROLL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize location executor
        locationExecutor = Executors.newSingleThreadExecutor();

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
        rvFavorites = view.findViewById(R.id.rvFavorites);
        progressBar = view.findViewById(R.id.progressBar);

        rvCategories.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        rvHotDeals.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvFoodItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFavorites.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        rvCategories.setHasFixedSize(true);
        rvHotDeals.setHasFixedSize(true);
        rvFoodItems.setHasFixedSize(true);
        rvFavorites.setHasFixedSize(true);

        rvCategories.setItemAnimator(null);
        rvHotDeals.setItemAnimator(null);
        rvFoodItems.setItemAnimator(null);
        rvFavorites.setItemAnimator(null);

        rvFoodItems.setItemViewCacheSize(10);
        rvHotDeals.setItemViewCacheSize(8);
        rvFavorites.setItemViewCacheSize(8);

        categoryAdapter = new CategoryAdapter(getContext(), categories, this);
        rvCategories.setAdapter(categoryAdapter);

        foodItemAdapter = new FoodItemAdapter(getContext(), foodItems, this);
        rvFoodItems.setAdapter(foodItemAdapter);

        hotDealsAdapter = new FoodItemAdapter(getContext(), hotDeals, this);
        rvHotDeals.setAdapter(hotDealsAdapter);

        favoritesAdapter = new FoodItemAdapter(getContext(), favoriteItems, this);
        rvFavorites.setAdapter(favoritesAdapter);

        // Setup banner
        setupBanner();

        // Setup greeting (Removed)
        // setupGreeting();

        // Setup categories
        setupCategories();
        observeFavorites();

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
        bannerIndicator.setViewPager(bannerViewPager);

        bannerViewPager.setOffscreenPageLimit(3);
        bannerViewPager.setPageTransformer((page, position) -> {
            page.setAlpha(0.9f + (1 - Math.abs(position)) * 0.1f);
            page.setScaleY(0.96f + (1 - Math.abs(position)) * 0.04f);
        });
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
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            tvCurrentLocation.setText(getString(R.string.default_location));
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(location -> {
                if (location != null) {
                    currentLatitude = location.getLatitude();
                    currentLongitude = location.getLongitude();
                    updateCurrentLocationLabel(currentLatitude, currentLongitude);
                } else {
                    tvCurrentLocation.setText(getString(R.string.default_location));
                }
                loadFoodItems();
            })
            .addOnFailureListener(e -> {
                tvCurrentLocation.setText(getString(R.string.default_location));
                loadFoodItems();
            });
    }

    private void updateCurrentLocationLabel(double latitude, double longitude) {
        if (!Geocoder.isPresent() || !isAdded()) {
            tvCurrentLocation.setText(String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude));
            return;
        }

        // Always ensure we have a working executor
        synchronized (this) {
            if (locationExecutor == null || locationExecutor.isShutdown() || locationExecutor.isTerminated()) {
                locationExecutor = Executors.newSingleThreadExecutor();
            }
        }

        try {
            locationExecutor.execute(() -> {
                if (!isAdded()) return;

                String label = String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude);
                try {
                    Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String locality = address.getSubLocality();
                        if (locality == null || locality.trim().isEmpty()) locality = address.getLocality();
                        if (locality == null || locality.trim().isEmpty()) locality = address.getAdminArea();
                        if (locality != null && !locality.trim().isEmpty()) {
                            label = locality;
                        }
                    }
                } catch (Exception ignored) {
                }

                String finalLabel = label;
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && tvCurrentLocation != null) {
                            tvCurrentLocation.setText(finalLabel);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            // Executor was shut down, recreate it
            synchronized (this) {
                locationExecutor = Executors.newSingleThreadExecutor();
                try {
                    locationExecutor.execute(() -> {
                        if (isAdded() && tvCurrentLocation != null) {
                            String label = String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude);
                            tvCurrentLocation.setText(label);
                        }
                    });
                } catch (Exception ignored) {
                    if (isAdded() && tvCurrentLocation != null) {
                        tvCurrentLocation.setText(String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude));
                    }
                }
            }
        } catch (Exception e) {
            if (isAdded() && tvCurrentLocation != null) {
                tvCurrentLocation.setText(String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude));
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
                    if (status != null && !status.trim().isEmpty()) {
                        item.setStatus(status);
                    }

                    Object quantityRaw = getObjectAny(itemSnapshot, "quantity", "qty", "stock");
                    if (quantityRaw != null) {
                        item.setQuantity(quantityRaw);
                    }

                    if (!item.isListable()) continue;

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
                updateFavorites();
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

    private void observeFavorites() {
        if (mAuth.getCurrentUser() == null) {
            updateFavorites();
            return;
        }
        mDatabase.child("favorites").child(mAuth.getCurrentUser().getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        favoriteFoodIds.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String key = child.getKey();
                            Object val = child.getValue();
                            if (key != null && !key.trim().isEmpty()) {
                                favoriteFoodIds.add(key);
                            }
                            if (val instanceof String) {
                                String id = ((String) val).trim();
                                if (!id.isEmpty()) favoriteFoodIds.add(id);
                            } else if (val instanceof java.util.Map) {
                                Object foodId = ((java.util.Map<?, ?>) val).get("foodId");
                                if (foodId != null) {
                                    String id = String.valueOf(foodId).trim();
                                    if (!id.isEmpty()) favoriteFoodIds.add(id);
                                }
                            }
                        }
                        updateFavorites();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
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
        hotDealsAdapter.updateData(new ArrayList<>(hotDeals));
    }

    private void updateFavorites() {
        favoriteItems.clear();
        if (!favoriteFoodIds.isEmpty()) {
            for (FoodItem item : allFoodItems) {
                if (item.getFoodId() != null && favoriteFoodIds.contains(item.getFoodId())) {
                    favoriteItems.add(item);
                }
            }
        }

        if (favoriteItems.isEmpty()) {
            int fallbackCount = Math.min(6, allFoodItems.size());
            for (int i = 0; i < fallbackCount; i++) {
                favoriteItems.add(allFoodItems.get(i));
            }
        }

        favoritesAdapter.updateData(new ArrayList<>(favoriteItems));
        rvFavorites.setVisibility(favoriteItems.isEmpty() ? View.GONE : View.VISIBLE);
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

        if ("All".equalsIgnoreCase(category)) {
            foodItems.addAll(allFoodItems);
        } else {
            for (FoodItem item : allFoodItems) {
                if (matchesCategory(item.getCategory(), category)) {
                    foodItems.add(item);
                }
            }
        }

        foodItemAdapter.updateData(new ArrayList<>(foodItems));

        if (foodItems.isEmpty()) {
            rvFoodItems.setVisibility(View.GONE);
        } else {
            rvFoodItems.setVisibility(View.VISIBLE);
        }
    }

    private boolean matchesCategory(String itemCategoryRaw, String selectedCategoryRaw) {
        if (itemCategoryRaw == null || selectedCategoryRaw == null) return false;

        String itemCategory = normalizeCategory(itemCategoryRaw);
        String selectedCategory = normalizeCategory(selectedCategoryRaw);

        if (itemCategory.equals(selectedCategory)) return true;

        // Flexible mapping for broad tabs.
        if ("meals".equals(selectedCategory)) {
            return itemCategory.contains("rice")
                    || itemCategory.contains("curry")
                    || itemCategory.contains("kottu")
                    || itemCategory.contains("noodle")
                    || itemCategory.contains("meal")
                    || itemCategory.contains("biryani");
        }

        if ("desserts".equals(selectedCategory)) {
            return itemCategory.contains("dessert")
                    || itemCategory.contains("cake")
                    || itemCategory.contains("sweet");
        }

        if ("drinks".equals(selectedCategory)) {
            return itemCategory.contains("drink")
                    || itemCategory.contains("juice")
                    || itemCategory.contains("beverage");
        }

        return itemCategory.contains(selectedCategory);
    }

    private String normalizeCategory(String value) {
        return value.toLowerCase()
                .replace("&", "and")
                .replace("_", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
        bannerHandler.removeCallbacks(bannerAutoScrollRunnable);
        bannerHandler.postDelayed(bannerAutoScrollRunnable, BANNER_AUTO_SCROLL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister shake sensor
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
        bannerHandler.removeCallbacks(bannerAutoScrollRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bannerHandler.removeCallbacksAndMessages(null);
        if (locationExecutor != null && !locationExecutor.isShutdown()) {
            locationExecutor.shutdownNow();
        }
    }
}
