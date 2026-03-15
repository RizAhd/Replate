package com.replate.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.adapters.CartAdapter;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.models.CartItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cart Activity - Shows items in SQLite-backed shopping cart
 * Requirement: Data Storage (SQLite)
 */
public class CartActivity extends AppCompatActivity implements CartAdapter.OnCartItemListener {

    private RecyclerView rvCartItems;
    private LinearLayout emptyState, checkoutSection;
    private TextView tvTotalAmount, tvClearCart, tvItemCount, tvSavingsValue, tvCartSummary;
    private Button btnCheckout, btnBrowse;

    private CartDatabaseHelper cartDb;
    private CartAdapter adapter;
    private List<CartItem> cartItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        cartDb = CartDatabaseHelper.getInstance(this);

        initViews();
        loadCartItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCartItems();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        rvCartItems = findViewById(R.id.rvCartItems);
        emptyState = findViewById(R.id.emptyState);
        checkoutSection = findViewById(R.id.checkoutSection);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvClearCart = findViewById(R.id.tvClearCart);
        tvItemCount = findViewById(R.id.tvItemCount);
        tvSavingsValue = findViewById(R.id.tvSavingsValue);
        tvCartSummary = findViewById(R.id.tvCartSummary);
        btnCheckout = findViewById(R.id.btnCheckout);
        btnBrowse = findViewById(R.id.btnBrowse);

        rvCartItems.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter(this, cartItems, this);
        rvCartItems.setAdapter(adapter);

        ivBack.setOnClickListener(v -> finish());

        tvClearCart.setOnClickListener(v -> {
            if (!cartItems.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Clear Cart")
                        .setMessage("Remove all items from cart?")
                        .setPositiveButton("Clear", (d, w) -> {
                            cartDb.clearCart();
                            loadCartItems();
                            Toast.makeText(this, "Cart cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        btnCheckout.setOnClickListener(v -> {
            Intent intent = new Intent(this, CheckoutActivity.class);
            startActivity(intent);
        });

        btnBrowse.setOnClickListener(v -> finish());
    }

    private void loadCartItems() {
        cartItems.clear();
        cartItems.addAll(cartDb.getAllItems());
        adapter.notifyDataSetChanged();
        updateUI();
    }

    private void updateUI() {
        if (cartItems.isEmpty()) {
            rvCartItems.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            checkoutSection.setVisibility(View.GONE);
            if (tvCartSummary != null) {
                tvCartSummary.setText("Your cart will automatically keep items saved offline.");
            }
        } else {
            rvCartItems.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            checkoutSection.setVisibility(View.VISIBLE);

            double total = cartDb.getCartTotal();
            double savings = 0;
            int itemCount = 0;
            for (CartItem item : cartItems) {
                itemCount += item.getQuantity();
                savings += (item.getOriginalPrice() - item.getPrice()) * item.getQuantity();
            }

            tvTotalAmount.setText(String.format(Locale.getDefault(), "LKR %.2f", total));
            if (tvItemCount != null) {
                tvItemCount.setText(itemCount == 1 ? "1 item" : itemCount + " items");
            }
            if (tvSavingsValue != null) {
                tvSavingsValue.setText(String.format(Locale.getDefault(), "LKR %.2f", Math.max(savings, 0)));
            }
            if (tvCartSummary != null) {
                tvCartSummary.setText(itemCount == 1
                        ? "You have 1 rescue meal ready for checkout"
                        : "You have " + itemCount + " rescue meals ready for checkout");
            }
        }
    }

    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        cartDb.updateQuantity(item.getFoodId(), newQuantity);
        loadCartItems();
    }

    @Override
    public void onRemoveItem(CartItem item) {
        cartDb.removeItem(item.getFoodId());
        loadCartItems();
        Toast.makeText(this, item.getFoodName() + " removed", Toast.LENGTH_SHORT).show();
    }
}
