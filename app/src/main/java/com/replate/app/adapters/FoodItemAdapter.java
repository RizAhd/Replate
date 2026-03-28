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

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.replate.app.R;
import com.replate.app.models.FoodItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    // Cache resolved Firebase Storage URLs to avoid repeated network resolution during scroll.
    private static final Map<String, String> RESOLVED_IMAGE_URL_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED_IMAGE_SOURCES = ConcurrentHashMap.newKeySet();

    class FoodViewHolder extends RecyclerView.ViewHolder {
        View cardView;
        ImageView ivFoodImage;
        TextView tvFoodName, tvHotelName, tvOriginalPrice, tvDiscountedPrice;
        TextView tvDiscount, tvDistance, tvPickupTime;
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
            View addButton = itemView.findViewById(R.id.ivAddToCart);
            ivAddToCart = addButton != null ? addButton : itemView.findViewById(R.id.cardView);
        }

        void bind(FoodItem item) {
            tvFoodName.setText(item.getFoodName());
            tvHotelName.setText(item.getHotelName());

            // Format prices with LKR
            tvOriginalPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", item.getOriginalPriceValue()));
            tvOriginalPrice.setPaintFlags(tvOriginalPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            tvDiscountedPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", item.getDiscountedPriceValue()));

            // Discount badge
            tvDiscount.setText(String.format(Locale.getDefault(), "-%d%%", item.getDiscountPercentValue()));

            // Keep distance visible for consistent card hierarchy.
            if (item.getDistance() > 0) {
                tvDistance.setText(item.getDistanceFormatted());
            } else {
                tvDistance.setText(context.getString(R.string.food_distance_nearby));
            }
            tvDistance.setVisibility(View.VISIBLE);

            // Pickup time
            tvPickupTime.setText(context.getString(R.string.food_pickup_template, item.getPickupTimeFormatted()));

            // Load image with Glide
            loadFoodImage(item.getImageUrl());

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

        private void loadFoodImage(String imageSource) {
            if (ivFoodImage == null) {
                return;
            }

            if (imageSource == null || imageSource.trim().isEmpty()) {
                ivFoodImage.setImageResource(R.drawable.placeholder_food);
                return;
            }

            String src = imageSource.trim();
            ivFoodImage.setTag(src);

            if (src.startsWith("http://") || src.startsWith("https://")) {
                Glide.with(context)
                        .load(src)
                        .dontAnimate()
                        .placeholder(R.drawable.placeholder_food)
                        .error(R.drawable.placeholder_food)
                        .centerCrop()
                        .into(ivFoodImage);
                return;
            }

            if (FAILED_IMAGE_SOURCES.contains(src)) {
                ivFoodImage.setImageResource(R.drawable.placeholder_food);
                return;
            }

            String cachedResolved = RESOLVED_IMAGE_URL_CACHE.get(src);
            if (cachedResolved != null && !cachedResolved.trim().isEmpty()) {
                Glide.with(context)
                        .load(cachedResolved)
                        .dontAnimate()
                        .thumbnail(0.25f)
                        .override(400, 300)
                        .placeholder(R.drawable.placeholder_food)
                        .error(R.drawable.placeholder_food)
                        .centerCrop()
                        .into(ivFoodImage);
                return;
            }

            com.google.firebase.storage.StorageReference ref = src.startsWith("gs://")
                    ? FirebaseStorage.getInstance().getReferenceFromUrl(src)
                    : FirebaseStorage.getInstance().getReference().child(src);

            ref.getDownloadUrl()
                    .addOnSuccessListener(uri -> {
                        String currentTag = ivFoodImage.getTag() == null ? "" : String.valueOf(ivFoodImage.getTag());
                        if (!src.equals(currentTag)) {
                            return;
                        }
                        String resolved = uri == null ? "" : uri.toString();
                        if (!resolved.isEmpty()) {
                            RESOLVED_IMAGE_URL_CACHE.put(src, resolved);
                        }
                        Glide.with(context)
                                .load(uri)
                                .dontAnimate()
                                .thumbnail(0.25f)
                                .override(400, 300)
                                .placeholder(R.drawable.placeholder_food)
                                .error(R.drawable.placeholder_food)
                                .centerCrop()
                                .into(ivFoodImage);
                    })
                    .addOnFailureListener(e -> {
                        FAILED_IMAGE_SOURCES.add(src);
                        String currentTag = ivFoodImage.getTag() == null ? "" : String.valueOf(ivFoodImage.getTag());
                        if (src.equals(currentTag)) {
                            ivFoodImage.setImageResource(R.drawable.placeholder_food);
                        }
                    });
        }
    }
}
