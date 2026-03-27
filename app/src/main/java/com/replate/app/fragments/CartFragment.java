package com.replate.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.replate.app.CartActivity;
import com.replate.app.R;
import com.replate.app.helpers.CartDatabaseHelper;

import java.util.Locale;

public class CartFragment extends Fragment {

    private TextView tvCartCount;
    private TextView tvCartTotal;
    private Button btnOpenCart;
    private CartDatabaseHelper cartDatabaseHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_cart, container, false);

            // Safe initialization of database helper
            try {
                cartDatabaseHelper = CartDatabaseHelper.getInstance(requireContext());
            } catch (Exception e) {
                android.util.Log.e("CartFragment", "Error initializing CartDatabaseHelper", e);
                cartDatabaseHelper = null;
            }

            // Safe view binding with null checks
            tvCartCount = view.findViewById(R.id.tvCartCount);
            tvCartTotal = view.findViewById(R.id.tvCartTotal);
            btnOpenCart = view.findViewById(R.id.btnBrowseFood);

            // Only set listener if button is found
            if (btnOpenCart != null) {
                btnOpenCart.setOnClickListener(v -> startActivity(new Intent(requireContext(), CartActivity.class)));
            }

            return view;
        } catch (Exception e) {
            android.util.Log.e("CartFragment", "Error creating cart fragment view", e);
            if (isAdded()) {
                return new View(requireContext());
            }
            return null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCartDisplay();
    }

    /**
     * Safe method to update cart display with null checks
     */
    private void updateCartDisplay() {
        try {
            if (cartDatabaseHelper == null) {
                cartDatabaseHelper = CartDatabaseHelper.getInstance(requireContext());
            }

            if (cartDatabaseHelper != null) {
                int count = cartDatabaseHelper.getCartCount();
                double total = cartDatabaseHelper.getCartTotal();

                if (tvCartCount != null) {
                    tvCartCount.setText(String.valueOf(count));
                }

                if (tvCartTotal != null) {
                    tvCartTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", total));
                }

                if (btnOpenCart != null) {
                    btnOpenCart.setText(count == 0 ? "Browse and add meals" : "Open detailed cart");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("CartFragment", "Error updating cart display", e);
        }
    }
}
