package com.replate.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.LoginActivity;
import com.replate.app.MainActivity;
import com.replate.app.NotificationsActivity;
import com.replate.app.R;
import com.replate.app.SettingsActivity;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Profile Fragment - User profile and settings
 */
public class ProfileFragment extends Fragment {

    private CircleImageView ivProfileImage;
    private TextView tvUserName, tvUserEmail, tvOrdersCount, tvSavings;
    private Button btnEditProfile, btnLogout;
    private LinearLayout menuOrders, menuFavorites, menuNotifications, menuSettings, menuHelp;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initViews(view);
        loadUserData();
        loadStats();
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        ivProfileImage = view.findViewById(R.id.ivProfileImage);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvOrdersCount = view.findViewById(R.id.tvOrdersCount);
        tvSavings = view.findViewById(R.id.tvSavings);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnLogout = view.findViewById(R.id.btnLogout);
        menuOrders = view.findViewById(R.id.menuOrders);
        menuFavorites = view.findViewById(R.id.menuFavorites);
        menuNotifications = view.findViewById(R.id.menuNotifications);
        menuSettings = view.findViewById(R.id.menuSettings);
        menuHelp = view.findViewById(R.id.menuHelp);
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        tvUserEmail.setText(user.getEmail());

        mDatabase.child("users").child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String name = snapshot.child("name").getValue(String.class);
                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage == null || profileImage.trim().isEmpty()) {
                                profileImage = snapshot.child("profileImageUrl").getValue(String.class);
                            }

                            if (name != null && !name.isEmpty()) {
                                tvUserName.setText(name);
                            }

                            if (profileImage != null && !profileImage.isEmpty() && getContext() != null) {
                                Glide.with(getContext())
                                        .load(profileImage)
                                        .placeholder(R.drawable.ic_profile_placeholder)
                                        .into(ivProfileImage);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });
    }

    private void loadStats() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        // Load order count and savings
        mDatabase.child("orders")
                .orderByChild("userId")
                .equalTo(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int orderCount = 0;
                        double totalSavings = 0;

                        for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                            orderCount++;
                            // Calculate savings (original - discounted)
                            Object originalObj = orderSnapshot.child("originalTotal").getValue();
                            Object discountedObj = orderSnapshot.child("totalAmount").getValue();
                            double original = toDouble(originalObj);
                            double discounted = toDouble(discountedObj);
                            if (original > 0 && discounted > 0) {
                                totalSavings += (original - discounted);
                            }
                        }

                        tvOrdersCount.setText(String.valueOf(orderCount));
                        tvSavings.setText(String.format("LKR %.0f", totalSavings));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Handle error
                    }
                });
    }

    private void setupClickListeners() {
        btnEditProfile.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), com.replate.app.EditProfileActivity.class));
        });

        menuOrders.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.nav_orders);
            }
        });

        menuFavorites.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Favorites coming soon!", Toast.LENGTH_SHORT).show();
        });

        menuNotifications.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), NotificationsActivity.class));
        });

        menuSettings.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), SettingsActivity.class));
        });

        menuHelp.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Help & Support coming soon!", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserData();
        loadStats();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();

                    // Clear saved preferences
                    requireActivity().getSharedPreferences("REPLATE_PREFS", 0)
                            .edit().clear().apply();

                    // Go to login
                    Intent intent = new Intent(getContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
