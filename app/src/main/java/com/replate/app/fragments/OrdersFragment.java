package com.replate.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.replate.app.models.Order;

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

    private OrderAdapter adapter;
    private List<Order> allOrders = new ArrayList<>();
    private List<Order> filteredOrders = new ArrayList<>();

    private String currentFilter = "active"; // active, completed, cancelled

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_orders, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initViews(view);
        setupTabs();
        loadOrders();

        return view;
    }

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

    private void setupTabs() {
        tabLayoutOrders.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        currentFilter = "active";
                        tvEmptyMessage.setText("No active orders");
                        break;
                    case 1:
                        currentFilter = "completed";
                        tvEmptyMessage.setText("No completed orders");
                        break;
                    case 2:
                        currentFilter = "cancelled";
                        tvEmptyMessage.setText("No cancelled orders");
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

    private void loadOrders() {
        if (mAuth.getCurrentUser() == null) return;

        progressBar.setVisibility(View.VISIBLE);
        String userId = mAuth.getCurrentUser().getUid();

        Query ordersQuery = mDatabase.child("orders")
                .orderByChild("userId")
                .equalTo(userId);

        ordersQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allOrders.clear();

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    Order order = orderSnapshot.getValue(Order.class);
                    if (order != null) {
                        order.setOrderId(orderSnapshot.getKey());
                        allOrders.add(order);
                    }
                }

                // Sort by date (newest first)
                Collections.sort(allOrders, (a, b) ->
                        Long.compare(b.getCreatedAtValue(), a.getCreatedAtValue()));

                progressBar.setVisibility(View.GONE);
                filterOrders();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(requireContext(), "Failed to load orders", Toast.LENGTH_SHORT).show();
            }
        });
    }

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

    private void updateUI() {
        if (filteredOrders.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvOrders.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvOrders.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onOrderClick(Order order) {
        Intent intent = new Intent(getContext(), OrderDetailActivity.class);
        intent.putExtra("order", order);
        startActivity(intent);
    }

    @Override
    public void onViewQRClick(Order order) {
        Intent intent = new Intent(getContext(), OrderDetailActivity.class);
        intent.putExtra("order", order);
        intent.putExtra("showQR", true);
        startActivity(intent);
    }

    @Override
    public void onCancelOrderClick(Order order) {
        if (order == null || order.getOrderId() == null || !order.isActive()) {
            Toast.makeText(getContext(), "This order cannot be cancelled", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Cancel this order?")
                .setMessage("Are you sure you want to cancel this order? This action cannot be undone.")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Cancel Order", (dialog, which) -> cancelOrder(order))
                .show();
    }

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
                    if (!isAdded() || getContext() == null) return;
                    Toast.makeText(requireContext(), "Order cancelled", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    if (!isAdded() || getContext() == null) return;
                    Toast.makeText(requireContext(), "Failed to cancel order", Toast.LENGTH_SHORT).show();
                });
    }
}
