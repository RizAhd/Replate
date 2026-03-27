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
        View view = inflater.inflate(R.layout.fragment_cart, container, false);
        cartDatabaseHelper = CartDatabaseHelper.getInstance(requireContext());

        tvCartCount = view.findViewById(R.id.tvCartCount);
        tvCartTotal = view.findViewById(R.id.tvCartTotal);
        btnOpenCart = view.findViewById(R.id.btnBrowseFood);

        btnOpenCart.setOnClickListener(v -> startActivity(new Intent(requireContext(), CartActivity.class)));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        int count = cartDatabaseHelper.getCartCount();
        double total = cartDatabaseHelper.getCartTotal();
        tvCartCount.setText(String.valueOf(count));
        tvCartTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", total));
        btnOpenCart.setText(count == 0 ? "Browse and add meals" : "Open detailed cart");
    }
}
