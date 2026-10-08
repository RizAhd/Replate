package com.replate.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.utils.Money;
import com.replate.app.adapters.CartAdapter;
import com.replate.app.helpers.CartDatabaseHelper;
import com.replate.app.models.CartItem;
import com.replate.app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cart Activity - Shows items in SQLite-backed shopping cart
 * Requirement: Data Storage (SQLite)
 */
public class CartActivity extends AppCompatActivity implements CartAdapter.OnCartItemListener {

    private RecyclerView rvCartItems;
    private View emptyState;
    private View checkoutSection;
    private TextView tvTotalAmount, tvClearCart, tvItemCount, tvSavingsValue, tvCartSummary;
    private Button btnCheckout, btnBrowse;

    private CartDatabaseHelper cartDb;
    private CartAdapter adapter;
    private List<CartItem> cartItems = new ArrayList<>();

    // Opens the cart database, finds the views, loads the items.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        cartDb = CartDatabaseHelper.getInstance(this);

        initViews();
        loadCartItems();
    }

    // Reload the cart each time the screen comes back.
    @Override
    protected void onResume() {
        super.onResume();
        loadCartItems();
    }

    // Finds the views and sets up the list and buttons.
    private void initViews() {
        try {
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

            if (rvCartItems != null) {
                rvCartItems.setLayoutManager(new LinearLayoutManager(this));
                adapter = new CartAdapter(this, cartItems, this);
                rvCartItems.setAdapter(adapter);
            }

            if (ivBack != null) {
                ivBack.setOnClickListener(v -> finish());
            }

            if (tvClearCart != null) {
                // Ask before emptying the whole cart.
                tvClearCart.setOnClickListener(v -> {
                    if (!cartItems.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle(getString(R.string.cart_clear_title))
                                .setMessage(getString(R.string.cart_clear_message))
                                .setPositiveButton(getString(R.string.cart_clear_confirm), (d, w) -> {
                                    if (cartDb != null) {
                                        cartDb.clearCart();
                                    }
                                    loadCartItems();
                                    ToastUtils.showCustomToast(this, getString(R.string.cart_cleared));
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    }
                });
            }

            if (btnCheckout != null) {
                // Go to checkout.
                btnCheckout.setOnClickListener(v -> {
                    Intent intent = new Intent(this, CheckoutActivity.class);
                    startActivity(intent);
                });
            }

            if (btnBrowse != null) {
                // Empty cart: go and browse nearby food.
                btnBrowse.setOnClickListener(v -> {
                    Intent browseIntent = new Intent(this, SearchActivity.class);
                    browseIntent.putExtra("initial_filter", "nearby");
                    browseIntent.putExtra("from_cart_empty", true);
                    startActivity(browseIntent);
                    finish();
                });
            }
        } catch (Exception e) {
            android.util.Log.e("CartActivity", "Error initializing views", e);
        }
    }

    // Reads the saved cart items and refreshes the screen.
    private void loadCartItems() {
        try {
            cartItems.clear();
            if (cartDb != null) {
                cartItems.addAll(cartDb.getAllItems());
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateUI();
        } catch (Exception e) {
            android.util.Log.e("CartActivity", "Error loading cart items", e);
        }
    }

    // Shows the empty state, or the total, count and savings.
    private void updateUI() {
        try {
            if (cartItems.isEmpty()) {
                if (rvCartItems != null) rvCartItems.setVisibility(View.GONE);
                if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                if (checkoutSection != null) checkoutSection.setVisibility(View.GONE);
                if (tvCartSummary != null) {
                    tvCartSummary.setText(getString(R.string.cart_summary_offline_saved));
                }
            } else {
                if (rvCartItems != null) rvCartItems.setVisibility(View.VISIBLE);
                if (emptyState != null) emptyState.setVisibility(View.GONE);
                if (checkoutSection != null) checkoutSection.setVisibility(View.VISIBLE);

                // Add up the item count and the money saved.
                double total = cartDb != null ? cartDb.getCartTotal() : 0;
                double savings = 0;
                int itemCount = 0;
                for (CartItem item : cartItems) {
                    if (item != null) {
                        itemCount += item.getQuantity();
                        savings += (item.getOriginalPrice() - item.getPrice()) * item.getQuantity();
                    }
                }

                if (tvTotalAmount != null) {
                    tvTotalAmount.setText(Money.format(total));
                }
                if (tvItemCount != null) {
                    tvItemCount.setText(getResources().getQuantityString(R.plurals.order_items_count, itemCount, itemCount));
                }
                if (tvSavingsValue != null) {
                    tvSavingsValue.setText(Money.format(Math.max(savings, 0)));
                }
                if (tvCartSummary != null) {
                    tvCartSummary.setText(itemCount == 1
                            ? getString(R.string.cart_summary_single_ready)
                            : getString(R.string.cart_summary_multi_ready, itemCount));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("CartActivity", "Error updating UI", e);
        }
    }

    // Saves the new quantity and reloads the cart.
    @Override
    public void onQuantityChanged(CartItem item, int newQuantity) {
        if (cartDb == null || item == null || item.getFoodId() == null) {
            return;
        }
        cartDb.updateQuantity(item.getFoodId(), newQuantity);
        loadCartItems();
    }

    // Removes the item and tells the user.
    @Override
    public void onRemoveItem(CartItem item) {
        if (cartDb == null || item == null || item.getFoodId() == null) {
            return;
        }
        cartDb.removeItem(item.getFoodId());
        loadCartItems();
        String label = item.getFoodName() == null ? getString(R.string.cart_item_fallback) : item.getFoodName();
        ToastUtils.showCustomToast(this, getString(R.string.cart_removed, label));
    }
}
