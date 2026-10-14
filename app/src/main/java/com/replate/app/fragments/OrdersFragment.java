package com.replate.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.MainActivity;
import com.replate.app.OrderDetailActivity;
import com.replate.app.R;
import com.replate.app.adapters.OrderAdapter;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.models.Order;
import com.replate.app.utils.ErrorStates;
import com.replate.app.utils.SafeSnapshot;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orders Fragment - Shows user's order history
 */
public class OrdersFragment extends Fragment implements OrderAdapter.OnOrderClickListener {

    private TabLayout tabLayoutOrders;
    private RecyclerView rvOrders;
    private LinearLayout emptyState;
    private ProgressBar progressBar;
    private TextView tvEmptyMessage;
    private Button btnBrowse;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshOrders;
    private View errorState, errorBanner;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ReplateDatabaseHelper localDb;

    private OrderAdapter adapter;
    private List<Order> allOrders = new ArrayList<>();
    private List<Order> filteredOrders = new ArrayList<>();
    private ValueEventListener ordersListener;
    private Query ordersQuery;

    private String currentFilter = "active"; // active, completed, cancelled

    // Redraws the rows once a minute while the list is on screen, so the pickup countdowns stay current
    private final android.os.Handler countdownHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            if (adapter != null && adapter.getItemCount() > 0) {
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            }
            countdownHandler.postDelayed(this, 60_000L);
        }
    };

    // Builds the Orders screen and loads the orders.
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_orders, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        localDb = ReplateDatabaseHelper.getInstance(requireContext());

        initViews(view);
        setupTabs();

        // Load cached orders first for instant display
        loadCachedOrders();
        loadOrders();

        return view;
    }

    // Finds the views on the screen.
    private void initViews(View view) {
        tabLayoutOrders = view.findViewById(R.id.tabLayoutOrders);
        rvOrders = view.findViewById(R.id.rvOrders);
        emptyState = view.findViewById(R.id.emptyState);
        progressBar = view.findViewById(R.id.progressBar);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);
        btnBrowse = view.findViewById(R.id.btnBrowse);
        swipeRefreshOrders = view.findViewById(R.id.swipeRefreshOrders);
        errorState = view.findViewById(R.id.errorState);
        errorBanner = view.findViewById(R.id.errorBanner);
        swipeRefreshOrders.setColorSchemeResources(R.color.green_primary);
        swipeRefreshOrders.setOnRefreshListener(this::loadOrders);

        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OrderAdapter(getContext(), filteredOrders, this);
        rvOrders.setAdapter(adapter);

        btnBrowse.setOnClickListener(v -> {
            // Switch to home tab
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.nav_home);
            }
        });
    }

    // Sets up the Active, Completed and Cancelled tabs.
    private void setupTabs() {
        tabLayoutOrders.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            // Changes the filter to the chosen tab.
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        currentFilter = "active";
                        tvEmptyMessage.setText(R.string.orders_no_active_orders);
                        break;
                    case 1:
                        currentFilter = "completed";
                        tvEmptyMessage.setText(R.string.orders_no_completed_orders);
                        break;
                    case 2:
                        currentFilter = "cancelled";
                        tvEmptyMessage.setText(R.string.orders_no_cancelled_orders);
                        break;
                }
                filterOrders();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // Shows the orders saved on the phone.
    private void loadCachedOrders() {
        if (localDb == null) return;
        List<Order> cached = localDb.getCachedOrders();
        if (!cached.isEmpty() && allOrders.isEmpty()) {
            allOrders.clear();
            allOrders.addAll(cached);
            filterOrders();
        }
    }

    // Listens to Firebase for the user's orders.
    private void loadOrders() {
        if (mAuth.getCurrentUser() == null) return;

        ErrorStates.hide(errorState);
        ErrorStates.hide(errorBanner);
        // Pull-to-refresh shows its own spinner
        if (swipeRefreshOrders == null || !swipeRefreshOrders.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        // Firebase never answers while offline with nothing cached: stop the spinner and say so
        progressBar.postDelayed(() -> {
            if (isAdded() && (progressBar.getVisibility() == View.VISIBLE
                    || (swipeRefreshOrders != null && swipeRefreshOrders.isRefreshing()))) {
                progressBar.setVisibility(View.GONE);
                showLoadError(R.string.loading_took_too_long);
            }
        }, 12_000L);
        String userId = mAuth.getCurrentUser().getUid();

        // Remove previous listener if any
        if (ordersQuery != null && ordersListener != null) {
            ordersQuery.removeEventListener(ordersListener);
        }

        ordersQuery = mDatabase.child("orders")
                .orderByChild("userId")
                .equalTo(userId);

        ordersListener = new ValueEventListener() {
            // Reads the orders, sorts them and saves a copy.
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allOrders.clear();

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    Order order = SafeSnapshot.getValue(orderSnapshot, Order.class);
                    if (order != null) {
                        order.setOrderId(orderSnapshot.getKey());
                        allOrders.add(order);
                    }
                }

                // Sort by date (newest first)
                Collections.sort(allOrders, (a, b) ->
                        Long.compare(b.getCreatedAtValue(), a.getCreatedAtValue()));

                // Mirror the server list in SQLite so removed orders do not come back offline
                if (localDb != null) {
                    localDb.replaceCachedOrders(allOrders);
                }

                progressBar.setVisibility(View.GONE);
                if (swipeRefreshOrders != null) swipeRefreshOrders.setRefreshing(false);
                ErrorStates.hide(errorState);
                ErrorStates.hide(errorBanner);
                filterOrders();
            }

            // Falls back to the saved orders.
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                // Fallback: load from SQLite cache if Firebase fails
                if (localDb != null && allOrders.isEmpty()) {
                    List<Order> cached = localDb.getCachedOrders();
                    if (!cached.isEmpty()) {
                        allOrders.clear();
                        allOrders.addAll(cached);
                        filterOrders();
                    }
                }
                if (!isAdded()) return;
                showLoadError(R.string.orders_failed_to_load_orders);
            }
        };

        ordersQuery.addValueEventListener(ordersListener);
    }

    // Says the orders did not load, with a Try again button: in place of the list when nothing is saved,
    // or as a slim banner above the saved orders.
    private void showLoadError(int message) {
        if (swipeRefreshOrders != null) swipeRefreshOrders.setRefreshing(false);
        if (allOrders.isEmpty()) {
            emptyState.setVisibility(View.GONE);
            swipeRefreshOrders.setVisibility(View.GONE);
            ErrorStates.show(errorState, message, this::loadOrders);
        } else {
            ErrorStates.show(errorBanner, message, this::loadOrders);
        }
    }

    // Keeps the orders that fit the selected tab.
    private void filterOrders() {
        filteredOrders.clear();

        for (Order order : allOrders) {
            switch (currentFilter) {
                case "active":
                    if (order.isActive()) {
                        filteredOrders.add(order);
                    }
                    break;
                case "completed":
                    if (order.isCompleted()) {
                        filteredOrders.add(order);
                    }
                    break;
                case "cancelled":
                    if (order.isCancelled()) {
                        filteredOrders.add(order);
                    }
                    break;
            }
        }

        adapter.notifyDataSetChanged();
        updateUI();
    }

    // Shows the list, or the empty message.
    private void updateUI() {
        if (ErrorStates.isShown(errorState)) {
            return; // the load error is showing in place of the list
        }
        if (filteredOrders.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            swipeRefreshOrders.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            swipeRefreshOrders.setVisibility(View.VISIBLE);
        }
    }

    // Opens the order details.
    @Override
    public void onOrderClick(Order order) {
        Intent intent = new Intent(getContext(), OrderDetailActivity.class);
        intent.putExtra("order", order);
        startActivity(intent);
    }

    // Opens the order details with the pickup QR code.
    @Override
    public void onViewQRClick(Order order) {
        Intent intent = new Intent(getContext(), OrderDetailActivity.class);
        intent.putExtra("order", order);
        intent.putExtra("showQR", true);
        startActivity(intent);
    }

    // Asks the user to confirm the cancellation.
    @Override
    public void onCancelOrderClick(Order order) {
        if (order == null || order.getOrderId() == null || !order.isActive()) {
            if (isAdded()) {
                Context ctx = getContext();
                if (ctx != null) {
                    ToastUtils.showCustomToast(ctx, R.string.orders_this_order_cannot_be_cancelled);
                }
            }
            return;
        }

        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.orders_cancel_this_order)
                .setMessage(R.string.orders_are_you_sure_you_want)
                .setNegativeButton(R.string.orders_keep, null)
                .setPositiveButton(R.string.orders_cancel_order, (dialog, which) -> cancelOrder(order))
                .show();
    }

    // Marks the order as cancelled in Firebase.
    private void cancelOrder(@NonNull Order order) {
        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> updates = new HashMap<>();
        updates.put("orderStatus", "cancelled");
        updates.put("updatedAt", System.currentTimeMillis());
        updates.put("cancelledAt", System.currentTimeMillis());

        mDatabase.child("orders").child(order.getOrderId())
                .updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    progressBar.setVisibility(View.GONE);
                    if (!isAdded()) return;
                    Context ctx = getContext();
                    if (ctx != null) {
                        ToastUtils.showCustomToast(ctx, R.string.orders_order_cancelled);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    if (!isAdded()) return;
                    Context ctx = getContext();
                    if (ctx != null) {
                        ToastUtils.showCustomToast(ctx, R.string.orders_failed_to_cancel_order);
                    }
                });
    }

    // Reloads the orders when the screen comes back.
    @Override
    public void onResume() {
        super.onResume();
        // Re-attach real-time listener when fragment comes back to foreground
        // This ensures new orders (e.g. after PayHere payment) appear immediately
        if (ordersQuery == null || ordersListener == null) {
            loadOrders();
        }
        countdownHandler.removeCallbacks(countdownTick);
        countdownHandler.postDelayed(countdownTick, 60_000L);
    }

    // Stops the countdown refresh while the list is not on screen.
    @Override
    public void onPause() {
        super.onPause();
        countdownHandler.removeCallbacks(countdownTick);
    }

    // Removes the orders listener.
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ordersQuery != null && ordersListener != null) {
            ordersQuery.removeEventListener(ordersListener);
            ordersListener = null;
            ordersQuery = null;
        }
    }
}
