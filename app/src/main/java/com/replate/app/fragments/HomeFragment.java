package com.replate.app.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.utils.AppAnalytics;
import com.replate.app.FoodDetailActivity;
import com.replate.app.MainActivity;
import com.replate.app.NotificationsActivity;
import com.replate.app.R;
import com.replate.app.SearchActivity;
import com.replate.app.adapters.BannerAdapter;
import com.replate.app.adapters.CategoryAdapter;
import com.replate.app.adapters.FoodItemAdapter;
import com.replate.app.helpers.BannerRepository;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.helpers.FoodItemCacheManager;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.BannerItem;
import com.replate.app.models.Category;
import com.replate.app.models.FoodItem;
import com.replate.app.notifications.NotificationRouter;
import com.replate.app.utils.ShakeDetector;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment implements
        FoodItemAdapter.OnFoodItemClickListener,
        CategoryAdapter.OnCategoryClickListener {

    private static final long BANNER_AUTO_SCROLL_MS = 3200L;

    // Views
    private View ivMenu;
    private TextView tvCurrentLocation;
    private View ivNotification;
    private TextView tvNotifBadge;
    private androidx.appcompat.widget.SearchView searchView;
    private ViewPager2 bannerViewPager;
    private RecyclerView rvCategories, rvHotDeals, rvFoodItems, rvFavorites;
    private me.relex.circleindicator.CircleIndicator3 bannerIndicator;
    private View skeletonFeed;
    private ObjectAnimator skeletonPulse;
    private boolean feedLoading;

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

    // Caching
    private FoodItemCacheManager cacheManager;
    private ReplateDatabaseHelper localDb;
    private java.util.Set<String> favoriteFoodIds = new java.util.HashSet<>();
    private String selectedCategory = "All";

    // Notification badge
    private ValueEventListener notifBadgeListener;
    private DatabaseReference notifBadgeRef;

    // Real-time food items listener
    private ValueEventListener foodItemsListener;
    private DatabaseReference foodItemsRef;

    // Shake-to-refresh
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

    // Parses food snapshots off the main thread, one at a time and in order, so an older snapshot
    // can never overwrite a newer one. Shut down in onDestroyView.
    private java.util.concurrent.ExecutorService foodParseExecutor;
    private DatabaseReference favoritesRef;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private TextView tvEmptyFeed;
    private ValueEventListener favoritesListener;

    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable bannerAutoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (bannerViewPager == null || bannerAdapter == null || bannerAdapter.getItemCount() <= 1 || !isAdded()) {
                return;
            }
            int nextItem = (bannerViewPager.getCurrentItem() + 1) % bannerAdapter.getItemCount();
            bannerViewPager.setCurrentItem(nextItem, animationsEnabled());
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

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Initialize cache manager for faster startup
        cacheManager = new FoodItemCacheManager(requireContext());
        localDb = ReplateDatabaseHelper.getInstance(requireContext());

        // Initialize views
        ivMenu = view.findViewById(R.id.ivMenu);
        tvCurrentLocation = view.findViewById(R.id.tvCurrentLocation);
        ivNotification = view.findViewById(R.id.ivNotification);
        tvNotifBadge = view.findViewById(R.id.tvNotifBadge);
        searchView = view.findViewById(R.id.searchView);
        bannerViewPager = view.findViewById(R.id.bannerViewPager);
        bannerIndicator = view.findViewById(R.id.bannerIndicator);
        rvCategories = view.findViewById(R.id.rvCategories);
        rvHotDeals = view.findViewById(R.id.rvHotDeals);
        rvFoodItems = view.findViewById(R.id.rvDeals);
        rvFavorites = view.findViewById(R.id.rvFavorites);
        skeletonFeed = view.findViewById(R.id.skeletonFeed);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvEmptyFeed = view.findViewById(R.id.tvEmptyFeed);
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.green_primary);
            swipeRefresh.setOnRefreshListener(this::refreshData);
        }

        rvCategories.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        rvHotDeals.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvFoodItems.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFavorites.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        rvCategories.setHasFixedSize(true);
        rvHotDeals.setHasFixedSize(true);
        rvFoodItems.setHasFixedSize(true);
        rvFavorites.setHasFixedSize(true);

        // Cards slide when favorites or deals are added and removed; categories stay static
        rvCategories.setItemAnimator(null);
        // Insert/remove motion is welcome; cross-fading a card on every data refresh just makes images flicker
        for (RecyclerView list : new RecyclerView[]{rvHotDeals, rvFoodItems, rvFavorites}) {
            RecyclerView.ItemAnimator animator = list.getItemAnimator();
            if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
                ((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
            }
        }
        // Carousels settle on a card instead of stopping half-way between two
        new androidx.recyclerview.widget.LinearSnapHelper().attachToRecyclerView(rvHotDeals);
        new androidx.recyclerview.widget.LinearSnapHelper().attachToRecyclerView(rvFavorites);

        rvFoodItems.setItemViewCacheSize(10);
        rvHotDeals.setItemViewCacheSize(8);
        rvFavorites.setItemViewCacheSize(8);

        // Share RecyclerView pool between food lists for faster view recycling
        RecyclerView.RecycledViewPool sharedPool = new RecyclerView.RecycledViewPool();
        sharedPool.setMaxRecycledViews(0, 15);
        rvHotDeals.setRecycledViewPool(sharedPool);
        rvFoodItems.setRecycledViewPool(sharedPool);
        rvFavorites.setRecycledViewPool(sharedPool);


        categoryAdapter = new CategoryAdapter(getContext(), categories, this);
        rvCategories.setAdapter(categoryAdapter);

        foodItemAdapter = new FoodItemAdapter(getContext(), foodItems, this);
        rvFoodItems.setAdapter(foodItemAdapter);

        hotDealsAdapter = new FoodItemAdapter(getContext(), hotDeals, this);
        hotDealsAdapter.setStyle(FoodItemAdapter.Style.CAROUSEL);
        rvHotDeals.setAdapter(hotDealsAdapter);

        favoritesAdapter = new FoodItemAdapter(getContext(), favoriteItems, this);
        favoritesAdapter.setStyle(FoodItemAdapter.Style.CAROUSEL);
        rvFavorites.setAdapter(favoritesAdapter);

        // Setup banner
        setupBanner();


        // Setup categories
        setupCategories();

        // Setup click listeners
        setupClickListeners();

        // Listen for unread notification count (bell badge)
        observeUnreadNotifications();

        // Load cached food items immediately for faster perceived performance
        loadCachedFoodItems();

        // Defer heavy operations to after first frame render for smoother startup
        view.post(() -> {
            if (!isAdded()) return;
            observeFavorites();
            loadFoodItems();
            getUserLocation();
        });

        // Shake-to-refresh (accelerometer)
        sensorManager = (SensorManager) requireActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector();
        shakeDetector.setOnShakeListener(count -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    ToastUtils.showCustomToast(getContext(), R.string.home_refreshing_nearby_deals);
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
        bannerAdapter = new BannerAdapter(this::onBannerClicked);

        // Show something immediately: the last banners fetched from the server if we have them,
        // otherwise the photos bundled with the app. Fresh server banners replace these below.
        List<BannerItem> cached = BannerRepository.filterLiveAndSort(
                BannerRepository.getCached(requireContext()), System.currentTimeMillis());
        bannerAdapter.submit(cached.isEmpty() ? BannerRepository.defaultBanners(requireContext()) : cached);

        bannerViewPager.setAdapter(bannerAdapter);
        bannerIndicator.setViewPager(bannerViewPager);
        // Keeps the dots in sync when the list is swapped after the server responds
        bannerAdapter.registerAdapterDataObserver(bannerIndicator.getAdapterDataObserver());

        bannerViewPager.setOffscreenPageLimit(1);
        bannerViewPager.setPageTransformer((page, position) -> {
            page.setAlpha(0.9f + (1 - Math.abs(position)) * 0.1f);
            page.setScaleY(0.96f + (1 - Math.abs(position)) * 0.04f);
        });

        // Don't fight the user: pause auto-scroll while swiping, and restart the timer afterwards
        bannerViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    bannerHandler.removeCallbacks(bannerAutoScrollRunnable);
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    bannerHandler.removeCallbacks(bannerAutoScrollRunnable);
                    bannerHandler.postDelayed(bannerAutoScrollRunnable, BANNER_AUTO_SCROLL_MS);
                }
            }
        });

        loadServerBanners();
    }

    private void loadServerBanners() {
        if (getContext() == null) {
            return;
        }
        BannerRepository.load(requireContext(), new BannerRepository.Callback() {
            @Override
            public void onLoaded(@NonNull List<BannerItem> banners) {
                if (!isAdded() || bannerAdapter == null || bannerViewPager == null) {
                    return;
                }
                // No server banners configured: keep whatever is already showing
                if (banners.isEmpty()) {
                    return;
                }
                bannerAdapter.submit(banners);
                bannerViewPager.setCurrentItem(0, false);
            }

            @Override
            public void onError() {
                // Offline or denied: the cached/bundled banners stay on screen
            }
        });
    }

    private void onBannerClicked(@NonNull BannerItem banner) {
        String link = banner.getDeepLink() == null ? "" : banner.getDeepLink().trim();
        if (link.isEmpty() || !isAdded()) {
            return;
        }
        String lower = link.toLowerCase(Locale.US);
        if (lower.startsWith("category:")) {
            openCategoryFromBanner(link.substring("category:".length()).trim());
        } else if (lower.startsWith("tab:")) {
            openTabFromBanner(link.substring("tab:".length()).trim());
        } else if (NotificationRouter.isSafeDeepLink(link)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)));
            } catch (android.content.ActivityNotFoundException e) {
                ToastUtils.showCustomToast(getContext(), R.string.home_no_app_available_to_open);
            }
        }
    }

    private void openCategoryFromBanner(@NonNull String categoryName) {
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            if (category.getName().equalsIgnoreCase(categoryName)) {
                categoryAdapter.setSelectedPosition(i);
                rvCategories.scrollToPosition(i);
                onCategoryClick(category);
                return;
            }
        }
    }

    private void openTabFromBanner(@NonNull String tabName) {
        if (!(getActivity() instanceof MainActivity)) {
            return;
        }
        MainActivity main = (MainActivity) getActivity();
        switch (tabName.toLowerCase(Locale.US)) {
            case "map":
                main.switchToTab(R.id.nav_map);
                break;
            case "orders":
                main.switchToTab(R.id.nav_orders);
                break;
            case "cart":
                main.switchToTab(R.id.nav_cart);
                break;
            case "profile":
                main.switchToTab(R.id.nav_profile);
                break;
            case "home":
                main.switchToTab(R.id.nav_home);
                break;
            default:
                break;
        }
    }

    /** False when the user turned animations off, so the carousel shouldn't slide on its own. */
    private boolean animationsEnabled() {
        if (getContext() == null) {
            return true;
        }
        return android.provider.Settings.Global.getFloat(
                getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
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
        if (ivNotification != null) {
            ivNotification.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), NotificationsActivity.class);
                startActivity(intent);
            });
        }

        // Search bar click - redirect to search page on click
        if (searchView != null) {
            searchView.setOnSearchClickListener(v -> {
                Intent intent = new Intent(getContext(), SearchActivity.class);
                startActivity(intent);
            });

            searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    Intent intent = new Intent(getContext(), SearchActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    /**
     * Observe unread notification count and update bell badge.
     */
    private void observeUnreadNotifications() {
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        notifBadgeRef = mDatabase.child("notifications").child(uid);
        notifBadgeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                int unread = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean read = child.child("read").getValue(Boolean.class);
                    if (read == null || !read) unread++;
                }
                updateNotifBadge(unread);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                com.replate.app.utils.DatabaseErrorLogger.log("HomeFragment", "notification badge", error);
            }
        };
        notifBadgeRef.addValueEventListener(notifBadgeListener);
    }

    private void updateNotifBadge(int count) {
        if (tvNotifBadge == null) return;
        if (count > 0) {
            tvNotifBadge.setVisibility(View.VISIBLE);
            tvNotifBadge.setText(count > 99 ? "99+" : String.valueOf(count));
        } else {
            tvNotifBadge.setVisibility(View.GONE);
        }
    }

    private void getUserLocation() {
        if (!isAdded()) return;
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            showLocationNeedsAttention(true);
            return;
        }

        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(location -> {
                if (!isAdded()) return;
                if (location != null) {
                    currentLatitude = location.getLatitude();
                    currentLongitude = location.getLongitude();
                    updateCurrentLocationLabel(currentLatitude, currentLongitude);
                    applyDistanceSortAndRefresh();
                } else {
                    requestFreshCurrentLocation();
                }
            })
            .addOnFailureListener(e -> requestFreshCurrentLocation());
    }

    private void requestFreshCurrentLocation() {
        if (!isAdded()) return;
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fineGranted && !coarseGranted) {
            showLocationNeedsAttention(true);
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (!isAdded()) return;
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        updateCurrentLocationLabel(currentLatitude, currentLongitude);
                        applyDistanceSortAndRefresh();
                    } else {
                        showLocationNeedsAttention(false);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    showLocationNeedsAttention(false);
                });
    }

    /** Replaces the endless "Fetching location..." with a tappable hint when there is no permission or no fix. */
    private void showLocationNeedsAttention(boolean permissionMissing) {
        if (!isAdded() || tvCurrentLocation == null) return;
        tvCurrentLocation.setText(permissionMissing ? R.string.home_location_enable : R.string.home_location_retry);
        tvCurrentLocation.setOnClickListener(v -> {
            tvCurrentLocation.setText(R.string.default_location);
            tvCurrentLocation.setOnClickListener(null);
            if (permissionMissing) {
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            } else {
                getUserLocation();
            }
        });
    }

    private void updateCurrentLocationLabel(double latitude, double longitude) {
        if (!isAdded() || tvCurrentLocation == null) return;
        tvCurrentLocation.setOnClickListener(null);
        if (!Geocoder.isPresent()) {
            tvCurrentLocation.setText(String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude));
            return;
        }

        // Use a plain background thread per request to avoid terminated-executor crashes.
        Thread worker = new Thread(() -> {
            String label = String.format(Locale.getDefault(), "%.3f, %.3f", latitude, longitude);
            try {
                if (!isAdded()) return;
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
            uiHandler.post(() -> {
                if (!isAdded() || tvCurrentLocation == null) return;
                tvCurrentLocation.setText(finalLabel);
            });
        });
        worker.start();
    }

    private void applyDistanceSortAndRefresh() {
        if (allFoodItems == null || allFoodItems.isEmpty()) {
            return;
        }

        for (FoodItem item : allFoodItems) {
            if (item == null) {
                continue;
            }
            if (!item.hasValidCoordinates()) {
                item.setDistance(0);
                continue;
            }

            double distance = calculateDistance(
                    currentLatitude,
                    currentLongitude,
                    item.getLatitudeValue(),
                    item.getLongitudeValue()
            );
            item.setDistance(distance);
        }

        Collections.sort(allFoodItems, (a, b) -> Double.compare(a.getDistance(), b.getDistance()));
        updateHotDeals();
        updateFavorites();
        filterByCategory(selectedCategory);
    }

    /**
     * Load cached food items for faster startup
     * Shows cached data immediately while Firebase loads fresh data
     */
    private void loadCachedFoodItems() {
        try {
            // Try SQLite cache first (preferred), then SharedPreferences fallback
            List<FoodItem> cachedItems = null;

            if (localDb != null && localDb.isFoodCacheFresh(5 * 60 * 1000)) {
                cachedItems = localDb.getCachedFoodItems();
            }

            // Fallback to SharedPreferences cache if SQLite is empty
            if ((cachedItems == null || cachedItems.isEmpty()) && cacheManager != null && cacheManager.hasCachedData()) {
                cachedItems = cacheManager.getCachedFoodItems();
            }

            if (cachedItems == null || cachedItems.isEmpty()) {
                return;
            }

            allFoodItems.clear();
            allFoodItems.addAll(cachedItems);

            // Apply category and other filters
            updateHotDeals();
            updateFavorites();
            filterByCategory(selectedCategory);

            showLoading(false);
        } catch (Exception e) {
            android.util.Log.e("HomeFragment", "Error loading cached food items", e);
        }
    }

    /**
     * Public method for shake-to-refresh from MainActivity.
     */
    public void refreshData() {
        if (!com.replate.app.utils.NetworkUtils.isOnline(getContext())) {
            // Nothing to fetch; keep showing the saved listings instead of spinning forever
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
            ToastUtils.showCustomToast(getContext(), R.string.home_you_re_offline_showing_saved);
            return;
        }
        loadFoodItems();
        // Safety net: never leave the pull-to-refresh spinner running if the server stays silent
        uiHandler.postDelayed(() -> {
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
            }
        }, 8000);
    }

    private void loadFoodItems() {
        // Keep cached content visible while refreshing from network.
        if (allFoodItems == null || allFoodItems.isEmpty()) {
            showLoading(true);
            // If the server never answers (offline, no cache) stop the placeholders and say so
            uiHandler.postDelayed(() -> {
                if (feedLoading) {
                    showLoading(false);
                }
            }, 12000);
        }

        // Remove previous listener if any (prevents duplicates on re-calls)
        if (foodItemsRef != null && foodItemsListener != null) {
            foodItemsRef.removeEventListener(foodItemsListener);
        }

        foodItemsRef = mDatabase.child("foodItems");

        // Use addValueEventListener for REAL-TIME updates — fires instantly
        // when any food item is added, updated, or removed in Firebase
        foodItemsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Process Firebase data on a background thread to prevent UI jank
                if (foodParseExecutor == null || foodParseExecutor.isShutdown()) {
                    foodParseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
                }
                try {
                    foodParseExecutor.execute(() -> {
                    List<FoodItem> newItems = new ArrayList<>();

                    for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                        FoodItem item;
                        try {
                            item = itemSnapshot.getValue(FoodItem.class);
                        } catch (Exception e) {
                            // One malformed listing (e.g. wrong field type from the web panel)
                            // must not kill this thread and take the whole app down.
                            android.util.Log.w("HomeFragment", "Skipping malformed food item " + itemSnapshot.getKey(), e);
                            continue;
                        }
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

                        if (currentLatitude != 0 && currentLongitude != 0 && item.hasValidCoordinates()) {
                            double distance = calculateDistance(
                                    currentLatitude, currentLongitude,
                                    item.getLatitudeValue(), item.getLongitudeValue()
                            );
                            item.setDistance(distance);
                        } else {
                            item.setDistance(0);
                        }

                        newItems.add(item);
                    }

                    if (currentLatitude != 0) {
                        Collections.sort(newItems, (a, b) -> Double.compare(a.getDistance(), b.getDistance()));
                    }

                    // Cache on background thread (SQLite primary, SharedPreferences secondary)
                    if (!newItems.isEmpty()) {
                        try {
                            if (localDb != null) {
                                localDb.cacheFoodItems(new ArrayList<>(newItems));
                            }
                            if (cacheManager != null) {
                                cacheManager.saveFoodItems(new ArrayList<>(newItems));
                            }
                        } catch (Exception e) {
                            android.util.Log.e("HomeFragment", "Error caching food items", e);
                        }
                    }

                    // Post UI updates back to main thread
                    final List<FoodItem> finalItems = newItems;
                    uiHandler.post(() -> {
                        if (!isAdded()) return;
                        allFoodItems.clear();
                        allFoodItems.addAll(finalItems);
                        updateHotDeals();
                        updateFavorites();
                        filterByCategory(selectedCategory);
                        showLoading(false);
                    });
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    // The screen was torn down while a snapshot arrived; nothing to update
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                if (!isAdded()) return;
                Context ctx = getContext();
                if (ctx != null && (allFoodItems == null || allFoodItems.isEmpty())) {
                    ToastUtils.showCustomToast(ctx, R.string.home_failed_to_load_food_items);
                }
            }
        };

        foodItemsRef.addValueEventListener(foodItemsListener);
    }

    private void observeFavorites() {
        if (mAuth.getCurrentUser() == null) {
            updateFavorites();
            return;
        }
        if (favoritesRef != null && favoritesListener != null) {
            favoritesRef.removeEventListener(favoritesListener);
        }
        favoritesRef = mDatabase.child("favorites").child(mAuth.getCurrentUser().getUid());
        favoritesListener = new ValueEventListener() {
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
                        android.util.Log.w("HomeFragment", "Favorites listener cancelled: " + error.getMessage());
                    }
                };
        favoritesRef.addValueEventListener(favoritesListener);
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
        updateEmptyFeed();
    }

    /** Tells the customer why the list is empty instead of leaving a blank gap. */
    private void updateEmptyFeed() {
        if (tvEmptyFeed == null) {
            return;
        }
        if (foodItems.isEmpty() && !feedLoading) {
            tvEmptyFeed.setText(allFoodItems.isEmpty() ? R.string.home_empty_none : R.string.home_empty_category);
            tvEmptyFeed.setVisibility(View.VISIBLE);
        } else {
            tvEmptyFeed.setVisibility(View.GONE);
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

    /** Shows pulsing placeholder rows in the feed while data loads; no-op if the view is gone. */
    private void setSkeletonVisible(boolean show) {
        if (skeletonFeed == null) {
            return;
        }
        if (skeletonPulse != null) {
            skeletonPulse.cancel();
            skeletonPulse = null;
        }
        skeletonFeed.setAlpha(1f);
        skeletonFeed.setVisibility(show ? View.VISIBLE : View.GONE);
        boolean animationsOff = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled();
        if (show && !animationsOff) {
            skeletonPulse = ObjectAnimator.ofFloat(skeletonFeed, View.ALPHA, 1f, 0.45f);
            skeletonPulse.setDuration(800);
            skeletonPulse.setRepeatCount(ValueAnimator.INFINITE);
            skeletonPulse.setRepeatMode(ValueAnimator.REVERSE);
            skeletonPulse.start();
        }
    }

    private void showLoading(boolean show) {
        feedLoading = show;
        setSkeletonVisible(show);
        // Whenever loading ends (data arrived or failed), also stop the pull-to-refresh spinner
        if (!show && swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
        updateEmptyFeed();
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
        ToastUtils.showCustomToast(getContext(), foodItem.getFoodName() + " added to cart!");
    }

    private void addToCart(FoodItem foodItem) {
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
        AppAnalytics.addToCart(getContext(), foodItem.getFoodId(), foodItem.getFoodName(), foodItem.getDiscountedPriceValue(), 1);
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
        if (skeletonPulse != null) {
            skeletonPulse.cancel();
            skeletonPulse = null;
        }
        skeletonFeed = null;
        bannerHandler.removeCallbacksAndMessages(null);
        uiHandler.removeCallbacksAndMessages(null);
        // Remove notification badge listener
        if (notifBadgeRef != null && notifBadgeListener != null) {
            notifBadgeRef.removeEventListener(notifBadgeListener);
        }
        // Remove real-time food items listener
        if (foodItemsRef != null && foodItemsListener != null) {
            foodItemsRef.removeEventListener(foodItemsListener);
        }
        // Otherwise a new favorites listener piles up every time this tab is opened
        if (favoritesRef != null && favoritesListener != null) {
            favoritesRef.removeEventListener(favoritesListener);
        }
        if (foodParseExecutor != null) {
            foodParseExecutor.shutdownNow();
            foodParseExecutor = null;
        }
    }
}
