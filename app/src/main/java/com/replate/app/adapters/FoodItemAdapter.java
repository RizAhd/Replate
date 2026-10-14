package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.utils.Money;
import com.replate.app.R;
import com.replate.app.models.FoodItem;
import com.replate.app.utils.FoodImages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Adapter for displaying food items in a RecyclerView
 */
public class FoodItemAdapter extends RecyclerView.Adapter<FoodItemAdapter.FoodViewHolder> {

    private final Context context;
    private List<FoodItem> foodItems;
    private final OnFoodItemClickListener listener;
    private static final Executor DIFF_EXECUTOR = Executors.newSingleThreadExecutor();
    private int diffGeneration = 0;

    /** How a card is laid out: full-width list row, ~78%-width carousel card, or compact grid cell. */
    public enum Style { LIST, CAROUSEL, GRID }

    private Style style = Style.LIST;

    // Switches between list, carousel and grid cards.
    public void setStyle(Style newStyle) {
        if (newStyle != null && newStyle != style) {
            style = newStyle;
            notifyDataSetChanged();
        }
    }

    // Click listener interface
    public interface OnFoodItemClickListener {
        void onFoodItemClick(FoodItem foodItem);
        void onAddToCartClick(FoodItem foodItem);
    }

    public FoodItemAdapter(Context context, List<FoodItem> foodItems, OnFoodItemClickListener listener) {
        this.context = context;
        this.foodItems = foodItems;
        this.listener = listener;
        setHasStableIds(true);
    }

    // Inflates the card layout for the current style.
    @NonNull
    @Override
    public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Style viewStyle = Style.values()[viewType];
        int layout = viewStyle == Style.GRID ? R.layout.item_food_grid : R.layout.item_food;
        View view = LayoutInflater.from(context).inflate(layout, parent, false);
        if (viewStyle == Style.CAROUSEL) {
            // Horizontal lists show a card at ~78% of the screen so the next one peeks in
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            lp.width = (int) (screenWidth * 0.78f);
            int gap = context.getResources().getDimensionPixelSize(R.dimen.space_1);
            lp.setMargins(gap, lp.topMargin, gap, lp.bottomMargin);
            view.setLayoutParams(lp);
        }
        return new FoodViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return style.ordinal();
    }

    @Override
    public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
        FoodItem item = foodItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return foodItems.size();
    }

    // Stops the photo load of a row that scrolled away, so it never lands in a reused row.
    @Override
    public void onViewRecycled(@NonNull FoodViewHolder holder) {
        super.onViewRecycled(holder);
        FoodImages.clear(context, holder.ivFoodImage);
    }

    // Gives each dish a steady id so rows keep their place.
    @Override
    public long getItemId(int position) {
        FoodItem item = foodItems.get(position);
        String key = item.getFoodId();
        if (key == null || key.trim().isEmpty()) {
            key = (item.getFoodName() + "|" + item.getHotelName());
        }
        return key.hashCode();
    }

    // Compares old and new lists in the background, then refreshes only what changed.
    public void updateData(List<FoodItem> newFoodItems) {
        List<FoodItem> oldList = this.foodItems == null ? Collections.emptyList() : new ArrayList<>(this.foodItems);
        List<FoodItem> newList = newFoodItems == null ? Collections.emptyList() : new ArrayList<>(newFoodItems);

        final int gen = ++diffGeneration;

        DIFF_EXECUTOR.execute(() -> {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldList.size();
                }

                @Override
                public int getNewListSize() {
                    return newList.size();
                }

                // Same dish if the ids match, else same name and hotel.
                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    FoodItem oldItem = oldList.get(oldItemPosition);
                    FoodItem newItem = newList.get(newItemPosition);

                    String oldId = oldItem.getFoodId();
                    String newId = newItem.getFoodId();
                    if (!isBlank(oldId) && !isBlank(newId)) {
                        return oldId.equals(newId);
                    }
                    return safe(oldItem.getFoodName()).equals(safe(newItem.getFoodName()))
                            && safe(oldItem.getHotelName()).equals(safe(newItem.getHotelName()));
                }

                // Checks if anything shown on the card has changed.
                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    FoodItem oldItem = oldList.get(oldItemPosition);
                    FoodItem newItem = newList.get(newItemPosition);

                    return safe(oldItem.getFoodName()).equals(safe(newItem.getFoodName()))
                            && safe(oldItem.getHotelName()).equals(safe(newItem.getHotelName()))
                            && oldItem.getOriginalPriceValue() == newItem.getOriginalPriceValue()
                            && oldItem.getDiscountedPriceValue() == newItem.getDiscountedPriceValue()
                            && oldItem.getDiscountPercentValue() == newItem.getDiscountPercentValue()
                            && oldItem.getDistance() == newItem.getDistance()
                            && safe(oldItem.getPickupTimeFormatted()).equals(safe(newItem.getPickupTimeFormatted()))
                            && safe(oldItem.getImageUrl()).equals(safe(newItem.getImageUrl()));
                }
            });

            // Only dispatch if this is still the latest update
            if (gen == diffGeneration) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (gen == diffGeneration) {
                        this.foodItems = new ArrayList<>(newList);
                        diffResult.dispatchUpdatesTo(this);
                    }
                });
            }
        });
    }

    // True when the text is null or empty.
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // Turns null text into an empty string.
    private String safe(String value) {
        return value == null ? "" : value;
    }

    class FoodViewHolder extends RecyclerView.ViewHolder {
        View cardView;
        ImageView ivFoodImage;
        TextView tvFoodName, tvHotelName, tvOriginalPrice, tvDiscountedPrice;
        TextView tvDiscount, tvDistance, tvPickupTime, tvStockLeft;
        View ivAddToCart;

        FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            View imageCandidate = itemView.findViewById(R.id.ivFoodImage);
            ivFoodImage = imageCandidate instanceof ImageView ? (ImageView) imageCandidate : null;
            tvFoodName = itemView.findViewById(R.id.tvFoodName);
            tvHotelName = itemView.findViewById(R.id.tvHotelName);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvDiscountedPrice = itemView.findViewById(R.id.tvDiscountedPrice);
            tvDiscount = itemView.findViewById(R.id.tvDiscount);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvPickupTime = itemView.findViewById(R.id.tvPickupTime);
            tvStockLeft = itemView.findViewById(R.id.tvStockLeft);
            View addButton = itemView.findViewById(R.id.ivAddToCart);
            ivAddToCart = addButton != null ? addButton : itemView.findViewById(R.id.cardView);
        }

        // Fills one food card: name, hotel, prices, badges and photo.
        void bind(FoodItem item) {
            if (tvFoodName != null) tvFoodName.setText(item.getFoodName());
            if (tvHotelName != null) {
                String hotelName = item.getHotelName();
                tvHotelName.setText(hotelName != null && !hotelName.trim().isEmpty()
                        ? hotelName : context.getString(R.string.hotel_name_fallback));
            }

            // Only show a saving when there really is one: no "-0%" badge and no struck-through price
            boolean hasDiscount = item.getDiscountPercentValue() > 0
                    && item.getOriginalPriceValue() > item.getDiscountedPriceValue();
            if (tvOriginalPrice != null) {
                tvOriginalPrice.setText(Money.format(item.getOriginalPriceValue()));
                tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                tvOriginalPrice.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
            }
            if (tvDiscountedPrice != null) {
                tvDiscountedPrice.setText(Money.format(item.getDiscountedPriceValue()));
            }

            if (tvDiscount != null) {
                tvDiscount.setText(String.format(Locale.getDefault(), "-%d%%", item.getDiscountPercentValue()));
                tvDiscount.setVisibility(hasDiscount ? View.VISIBLE : View.GONE);
            }

            // An unknown distance (no location yet) is hidden rather than labelled "Nearby", which was a guess
            if (tvDistance != null) {
                if (item.getDistance() > 0) {
                    tvDistance.setText(item.getDistanceFormatted());
                    tvDistance.setVisibility(View.VISIBLE);
                } else {
                    tvDistance.setVisibility(View.GONE);
                }
            }

            if (tvPickupTime != null) {
                // Dishes without a pickup window just show no line
                boolean hasWindow = !isBlank(item.getPickupTimeStart()) || !isBlank(item.getPickupTimeEnd());
                tvPickupTime.setVisibility(hasWindow ? View.VISIBLE : View.GONE);
                if (hasWindow) {
                    tvPickupTime.setText(context.getString(R.string.food_pickup_template, item.getPickupTimeFormatted()));
                }
            }

            // A little urgency, only when it is true: just a few portions are left
            if (tvStockLeft != null) {
                boolean low = item.isLowStock() && item.isAvailableValue();
                tvStockLeft.setVisibility(low ? View.VISIBLE : View.GONE);
                if (low) {
                    tvStockLeft.setText(context.getString(R.string.food_only_left, item.getQuantityValue()));
                }
            }

            FoodImages.load(context, ivFoodImage, item.getImageUrl());

            // Click listeners
            if (cardView != null) {
                cardView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onFoodItemClick(item);
                    }
                });
            }

            if (ivAddToCart != null) {
                ivAddToCart.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAddToCartClick(item);
                    }
                });
            }
        }
    }
}
