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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
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

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ReplateDatabaseHelper localDb;

    private OrderAdapter adapter;
    private List<Order> allOrders = new ArrayList<>();
    private List<Order> filteredOrders = new ArrayList<>();
    private ValueEventListener ordersListener;
    private Query ordersQuery;

    private String currentFilter = "active"; // active, completed, cancelled

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

        progressBar.setVisibility(View.VISIBLE);
        // Firebase never answers while offline with nothing cached: stop the spinner and say so
        progressBar.postDelayed(() -> {
            if (isAdded() && progressBar.getVisibility() == View.VISIBLE) {
                progressBar.setVisibility(View.GONE);
                android.widget.Toast.makeText(requireContext(), R.string.loading_took_too_long, android.widget.Toast.LENGTH_LONG).show();
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
                Context ctx = getContext();
                if (ctx != null) {
                    ToastUtils.showCustomToast(ctx, R.string.orders_failed_to_load_orders);
                }
            }
        };

        ordersQuery.addValueEventListener(ordersListener);
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
        if (filteredOrders.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvOrders.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvOrders.setVisibility(View.VISIBLE);
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
        new AlertDialog.Builder(requireContext())
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
