package com.replate.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.utils.AccountDeletionManager;
import com.replate.app.utils.ToastUtils;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Profile Fragment - User profile and settings
 */
public class ProfileFragment extends Fragment {

    private CircleImageView ivProfileImage;
    private TextView tvUserName, tvUserEmail, tvOrdersCount, tvSavings;
    private Button btnEditProfile, btnLogout, btnDeleteAccount;
    private LinearLayout menuOrders, menuFavorites, menuNotifications, menuSettings, menuHelp;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ReplateDatabaseHelper localDb;

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
        localDb = ReplateDatabaseHelper.getInstance(requireContext());

        initViews(view);
        loadCachedProfile(); // Instant display from SQLite
        loadUserData();      // Fetch fresh from Firebase
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
        btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount);
        menuOrders = view.findViewById(R.id.menuOrders);
        menuFavorites = view.findViewById(R.id.menuFavorites);
        menuNotifications = view.findViewById(R.id.menuNotifications);
        menuSettings = view.findViewById(R.id.menuSettings);
        menuHelp = view.findViewById(R.id.menuHelp);
    }

    /**
     * Load cached user profile from SQLite for instant display.
     */
    private void loadCachedProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || localDb == null) return;

        java.util.Map<String, String> cached = localDb.getCachedUserProfile(user.getUid());
        if (cached == null) return;

        String name = cached.get("name");
        String email = cached.get("email");
        String photoUrl = cached.get("photoUrl");

        if (name != null && !name.isEmpty()) {
            tvUserName.setText(name);
        }
        if (email != null && !email.isEmpty()) {
            tvUserEmail.setText(email);
        }
        if (photoUrl != null && !photoUrl.isEmpty() && getContext() != null) {
            Glide.with(getContext())
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(ivProfileImage);
        }
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
                            String email = snapshot.child("email").getValue(String.class);
                            String phone = snapshot.child("phone").getValue(String.class);
                            String address = snapshot.child("address").getValue(String.class);
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

                            // Cache to SQLite for instant display next time
                            if (localDb != null) {
                                localDb.cacheUserProfile(
                                        user.getUid(),
                                        name,
                                        email != null ? email : user.getEmail(),
                                        phone,
                                        profileImage,
                                        address
                                );
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Cached data already shown, no action needed
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
            // SettingsActivity contains the complete photo upload/edit flow.
            startActivity(new Intent(getContext(), SettingsActivity.class));
        });

        menuOrders.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(R.id.nav_orders);
            }
        });

        menuFavorites.setOnClickListener(v -> {
            ToastUtils.showCustomToast(getContext(), "Favorites coming soon!");
        });

        menuNotifications.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), NotificationsActivity.class));
        });

        menuSettings.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), SettingsActivity.class));
        });

        menuHelp.setOnClickListener(v -> {
            ToastUtils.showCustomToast(getContext(), "Help & Support coming soon!");
        });

        btnLogout.setOnClickListener(v -> showLogoutDialog());

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
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
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).logoutToLogin();
                    } else {
                        try {
                            mAuth.signOut();
                            Intent intent = new Intent(requireContext(), LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            requireActivity().finish();
                        } catch (Exception e) {
                            Log.e("Logout", "Logout fallback error", e);
                            ToastUtils.showCustomToast(getContext(), "Logout failed");
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        // Step 1: Warning confirmation dialog
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_account_confirm_title))
                .setMessage(getString(R.string.delete_account_confirm_message))
                .setPositiveButton(getString(R.string.delete_account_confirm_btn), (d, w) ->
                        showPasswordConfirmDialog())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPasswordConfirmDialog() {
        // Step 2: Password entry dialog
        EditText etPassword = new EditText(requireContext());
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setHint(getString(R.string.delete_account_password_hint));
        int pad = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        etPassword.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_account_password_title))
                .setView(etPassword)
                .setPositiveButton(getString(R.string.delete_account_confirm_btn), (d, w) -> {
                    String password = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        ToastUtils.showCustomToast(getContext(), getString(R.string.delete_account_password_hint));
                        return;
                    }
                    performDeletion(password);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performDeletion(@NonNull String password) {
        if (getContext() == null) return;

        // Show non-cancelable progress dialog
        AlertDialog progress = new AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.delete_account_deleting))
                .setCancelable(false)
                .create();
        progress.show();

        AccountDeletionManager.deleteAccount(requireContext(), password,
                new AccountDeletionManager.DeletionCallback() {
                    @Override
                    public void onSuccess() {
                        if (progress.isShowing()) progress.dismiss();
                        // Sign out and navigate to login
                        try {
                            mAuth.signOut();
                        } catch (Exception ignored) {}
                        ToastUtils.showCustomToast(getContext(), getString(R.string.delete_account_success));
                        try {
                            Intent intent = new Intent(requireContext(), LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e("DeleteAccount", "Navigation error after deletion", e);
                        }
                    }

                    @Override
                    public void onWrongPassword() {
                        if (progress.isShowing()) progress.dismiss();
                        ToastUtils.showCustomToast(getContext(), getString(R.string.delete_account_wrong_password));
                        showPasswordConfirmDialog(); // Let user retry
                    }

                    @Override
                    public void onFailure(@NonNull String reason) {
                        if (progress.isShowing()) progress.dismiss();
                        ToastUtils.showCustomToast(getContext(), getString(R.string.delete_account_failed));
                        Log.e("DeleteAccount", "Deletion failed: " + reason);
                    }
                });
    }
}
