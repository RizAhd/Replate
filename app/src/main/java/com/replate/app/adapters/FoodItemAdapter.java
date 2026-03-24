package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.replate.app.R;
import com.replate.app.models.FoodItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for displaying food items in a RecyclerView
 */
public class FoodItemAdapter extends RecyclerView.Adapter<FoodItemAdapter.FoodViewHolder> {

    private final Context context;
    private List<FoodItem> foodItems;
    private final OnFoodItemClickListener listener;

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

    @NonNull
    @Override
    public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_food, parent, false);
        return new FoodViewHolder(view);
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

    @Override
    public long getItemId(int position) {
        FoodItem item = foodItems.get(position);
        String key = item.getFoodId();
        if (key == null || key.trim().isEmpty()) {
            key = (item.getFoodName() + "|" + item.getHotelName());
        }
        return key.hashCode();
    }

    public void updateData(List<FoodItem> newFoodItems) {
        List<FoodItem> oldList = this.foodItems == null ? Collections.emptyList() : this.foodItems;
        List<FoodItem> newList = newFoodItems == null ? Collections.emptyList() : newFoodItems;

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

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

        this.foodItems = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    class FoodViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView ivFoodImage;
        TextView tvFoodName, tvHotelName, tvOriginalPrice, tvDiscountedPrice;
        TextView tvDiscount, tvDistance, tvPickupTime;
        View ivAddToCart;

        FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
            tvFoodName = itemView.findViewById(R.id.tvFoodName);
            tvHotelName = itemView.findViewById(R.id.tvHotelName);
            tvOriginalPrice = itemView.findViewById(R.id.tvOriginalPrice);
            tvDiscountedPrice = itemView.findViewById(R.id.tvDiscountedPrice);
            tvDiscount = itemView.findViewById(R.id.tvDiscount);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvPickupTime = itemView.findViewById(R.id.tvPickupTime);
            View addButton = itemView.findViewById(R.id.ivAddToCart);
            // Fallback to the card tap area if add button is missing in a variant layout.
            ivAddToCart = addButton != null ? addButton : itemView.findViewById(R.id.cardView);
        }

        void bind(FoodItem item) {
            tvFoodName.setText(item.getFoodName());
            tvHotelName.setText(item.getHotelName());

            // Format prices with LKR
            tvOriginalPrice.setText(String.format("LKR %.2f", item.getOriginalPriceValue()));
            tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            tvDiscountedPrice.setText(String.format("LKR %.2f", item.getDiscountedPriceValue()));

            // Discount badge
            tvDiscount.setText(String.format("-%d%%", item.getDiscountPercentValue()));

            // Distance
            if (item.getDistance() > 0) {
                tvDistance.setText(item.getDistanceFormatted());
                tvDistance.setVisibility(View.VISIBLE);
            } else {
                tvDistance.setVisibility(View.GONE);
            }

            // Pickup time
            tvPickupTime.setText("Pickup: " + item.getPickupTimeFormatted());

            // Load image with Glide
            loadFoodImage(item.getImageUrl());

            // Click listeners
            cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFoodItemClick(item);
                }
            });

            if (ivAddToCart != null) {
                ivAddToCart.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAddToCartClick(item);
                    }
                });
            }
        }

        private void loadFoodImage(String imageSource) {
            if (imageSource == null || imageSource.trim().isEmpty()) {
                ivFoodImage.setImageResource(R.drawable.placeholder_food);
                return;
            }

            String src = imageSource.trim();

            if (src.startsWith("http://") || src.startsWith("https://")) {
                Glide.with(context)
                        .load(src)
                        .placeholder(R.drawable.placeholder_food)
                        .error(R.drawable.placeholder_food)
                        .centerCrop()
                        .into(ivFoodImage);
                return;
            }

            if (src.startsWith("gs://")) {
                FirebaseStorage.getInstance().getReferenceFromUrl(src).getDownloadUrl()
                        .addOnSuccessListener(uri -> Glide.with(context)
                                .load(uri)
                                .placeholder(R.drawable.placeholder_food)
                                .error(R.drawable.placeholder_food)
                                .centerCrop()
                                .into(ivFoodImage))
                        .addOnFailureListener(e -> ivFoodImage.setImageResource(R.drawable.placeholder_food));
                return;
            }

            FirebaseStorage.getInstance().getReference().child(src).getDownloadUrl()
                    .addOnSuccessListener(uri -> Glide.with(context)
                            .load(uri)
                            .placeholder(R.drawable.placeholder_food)
                            .error(R.drawable.placeholder_food)
                            .centerCrop()
                            .into(ivFoodImage))
                    .addOnFailureListener(e -> ivFoodImage.setImageResource(R.drawable.placeholder_food));
        }
    }
}
