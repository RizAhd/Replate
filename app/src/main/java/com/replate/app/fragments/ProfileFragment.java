package com.replate.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.utils.AccountActions;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.replate.app.utils.Money;
import com.replate.app.utils.Savings;
import com.replate.app.FavoritesActivity;
import com.replate.app.MainActivity;
import com.replate.app.MessagesActivity;
import com.replate.app.NotificationsActivity;
import com.replate.app.R;
import com.replate.app.SettingsActivity;
import com.replate.app.helpers.ReplateDatabaseHelper;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Profile Fragment - User profile and settings
 */
public class ProfileFragment extends Fragment {

    private CircleImageView ivProfileImage;
    private TextView tvUserName, tvUserEmail, tvOrdersCount, tvSavings, tvSavingsChartEmpty;
    private BarChart chartSavings;
    private Button btnEditProfile, btnLogout, btnDeleteAccount;
    private LinearLayout menuOrders, menuFavorites, menuNotifications, menuMessages, menuSettings, menuHelp;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ReplateDatabaseHelper localDb;

    // Turns a saved value into a decimal number.
    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }

    // Builds the Profile screen and loads the user's data.
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

    // Finds the views on the screen.
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
        chartSavings = view.findViewById(R.id.chartSavings);
        tvSavingsChartEmpty = view.findViewById(R.id.tvSavingsChartEmpty);
        menuNotifications = view.findViewById(R.id.menuNotifications);
        menuMessages = view.findViewById(R.id.menuMessages);
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

    // Reads the user's profile from Firebase and shows it.
    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        // Phone and social sign-ins can have no email or display name, which left the hero card blank
        tvUserEmail.setText(contactLine(user));
        if (tvUserName.getText() == null || tvUserName.getText().toString().trim().isEmpty()) {
            tvUserName.setText(fallbackName(user));
        }

        mDatabase.child("users").child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    // Shows the fresh profile and saves a copy.
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String name = snapshot.child("name").getValue(String.class);
                            String email = snapshot.child("email").getValue(String.class);
                            String phone = com.replate.app.utils.SafeSnapshot.string(snapshot.child("phone"));
                            String address = snapshot.child("address").getValue(String.class);
                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage == null || profileImage.trim().isEmpty()) {
                                profileImage = snapshot.child("profileImageUrl").getValue(String.class);
                            }

                            if (name != null && !name.isEmpty()) {
                                tvUserName.setText(name);
                            } else if (isAdded()) {
                                tvUserName.setText(fallbackName(user));
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

                    // Logs why the refresh failed.
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Cached data is already on screen; just record why the refresh failed
                        com.replate.app.utils.DatabaseErrorLogger.log("ProfileFragment", "profile refresh", error);
                    }
                });
    }

    // Gives the email, or the phone number if there is none.
    private String contactLine(FirebaseUser user) {
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) return user.getEmail();
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().trim().isEmpty()) return user.getPhoneNumber();
        return "";
    }

    // Gives a name from the account, the email or a guest name.
    private String fallbackName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) return user.getDisplayName();
        String email = user.getEmail();
        if (email != null && email.indexOf('@') > 0) return email.substring(0, email.indexOf('@'));
        return getString(R.string.profile_guest_name);
    }

    // Counts the user's orders and the money saved.
    private void loadStats() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        // Load order count and savings
        mDatabase.child("orders")
                .orderByChild("userId")
                .equalTo(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    // Adds up the orders and savings and shows them.
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int orderCount = 0;
                        double totalSavings = 0;
                        List<double[]> savedPerOrder = new ArrayList<>(); // {createdAt, saved}

                        for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                            // A cancelled order was never bought, so it counts for neither stat
                            if ("cancelled".equals(com.replate.app.utils.OrderStatusAliases.canonical(
                                    orderSnapshot.child("orderStatus").getValue(String.class)))) {
                                continue;
                            }
                            orderCount++;
                            double saved = Savings.forOrder(
                                    toDouble(orderSnapshot.child("savingsAmount").getValue()),
                                    toDouble(orderSnapshot.child("originalTotal").getValue()),
                                    toDouble(orderSnapshot.child("totalAmount").getValue()));
                            totalSavings += saved;
                            savedPerOrder.add(new double[]{toDouble(orderSnapshot.child("createdAt").getValue()), saved});
                        }

                        tvOrdersCount.setText(String.valueOf(orderCount));
                        tvSavings.setText(Money.formatWhole(totalSavings));
                        renderSavingsChart(savedPerOrder);
                    }

                    // Logs the read error.
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        com.replate.app.utils.DatabaseErrorLogger.log("ProfileFragment", "order stats", error);
                    }
                });
    }

    /** Bar chart (MPAndroidChart) of the saving on the customer's last six orders, oldest on the left. */
    private void renderSavingsChart(List<double[]> savedPerOrder) {
        if (!isAdded() || chartSavings == null) return;

        java.util.Collections.sort(savedPerOrder, (a, b) -> Double.compare(a[0], b[0]));
        int from = Math.max(0, savedPerOrder.size() - 6);
        List<double[]> recent = savedPerOrder.subList(from, savedPerOrder.size());

        boolean hasData = false;
        List<BarEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("d MMM", Locale.getDefault());
        for (int i = 0; i < recent.size(); i++) {
            double[] order = recent.get(i);
            entries.add(new BarEntry(i, (float) order[1]));
            labels.add(order[0] > 0 ? dayFormat.format(new java.util.Date((long) order[0])) : "");
            if (order[1] > 0) hasData = true;
        }

        tvSavingsChartEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        chartSavings.setVisibility(hasData ? View.VISIBLE : View.GONE);
        if (!hasData) return;

        int green = ContextCompat.getColor(requireContext(), R.color.green_primary);
        int grey = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(green);
        dataSet.setValueTextColor(grey);
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            // Labels each bar with its saved amount.
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return Money.formatWhole(barEntry.getY());
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        chartSavings.setData(data);
        chartSavings.getDescription().setEnabled(false);
        chartSavings.getLegend().setEnabled(false);
        chartSavings.setScaleEnabled(false);
        chartSavings.setDrawGridBackground(false);
        chartSavings.getAxisRight().setEnabled(false);
        chartSavings.getAxisLeft().setAxisMinimum(0f);
        chartSavings.getAxisLeft().setTextColor(grey);
        chartSavings.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.divider));

        XAxis xAxis = chartSavings.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(grey);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));

        chartSavings.animateY(600);
        chartSavings.invalidate();
    }

    // Wires up the profile buttons.
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
            startActivity(new Intent(getContext(), FavoritesActivity.class));
        });

        menuNotifications.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), NotificationsActivity.class));
        });

        menuMessages.setOnClickListener(v -> startActivity(new Intent(getContext(), MessagesActivity.class)));

        menuSettings.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), SettingsActivity.class));
        });

        menuHelp.setOnClickListener(v -> {
            if (getContext() != null) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.help_title)
                        .setMessage(R.string.help_body)
                        .setPositiveButton(R.string.help_dismiss, null)
                        .show();
            }
        });

        btnLogout.setOnClickListener(v -> showLogoutDialog());

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    // Reloads the profile when the screen returns.
    @Override
    public void onResume() {
        super.onResume();
        loadUserData();
        loadStats();
    }

    // Asks to confirm sign out.
    private void showLogoutDialog() {
        AccountActions.confirmLogout(requireActivity());
    }

    // Asks to confirm account deletion.
    private void showDeleteAccountDialog() {
        AccountActions.confirmDelete(requireActivity());
    }
}
