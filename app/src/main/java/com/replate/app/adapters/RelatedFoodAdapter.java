package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.replate.app.R;
import com.replate.app.models.FoodItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RelatedFoodAdapter extends RecyclerView.Adapter<RelatedFoodAdapter.RelatedViewHolder> {

    public interface OnRelatedClickListener {
        void onRelatedFoodClick(FoodItem item);
    }

    private final Context context;
    private final OnRelatedClickListener listener;
    private final List<FoodItem> items = new ArrayList<>();

    public RelatedFoodAdapter(Context context, OnRelatedClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RelatedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_related_food, parent, false);
        return new RelatedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RelatedViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void submitList(List<FoodItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    class RelatedViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivImage;
        private final TextView tvName;
        private final TextView tvPrice;

        RelatedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivRelatedImage);
            tvName = itemView.findViewById(R.id.tvRelatedName);
            tvPrice = itemView.findViewById(R.id.tvRelatedPrice);
        }

        void bind(FoodItem item) {
            tvName.setText(item.getFoodName());
            tvPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", item.getDiscountedPriceValue()));
            loadImage(item.getImageUrl());
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onRelatedFoodClick(item);
            });
        }

        private void loadImage(String imageSource) {
            if (imageSource == null || imageSource.trim().isEmpty()) {
                ivImage.setImageResource(R.drawable.placeholder_food);
                return;
            }

            String src = imageSource.trim();
            if (src.startsWith("http://") || src.startsWith("https://")) {
                Glide.with(context).load(src).placeholder(R.drawable.placeholder_food).into(ivImage);
                return;
            }

            if (src.startsWith("gs://")) {
                FirebaseStorage.getInstance().getReferenceFromUrl(src).getDownloadUrl()
                        .addOnSuccessListener(uri -> Glide.with(context).load(uri).placeholder(R.drawable.placeholder_food).into(ivImage))
                        .addOnFailureListener(e -> ivImage.setImageResource(R.drawable.placeholder_food));
                return;
            }

            FirebaseStorage.getInstance().getReference().child(src).getDownloadUrl()
                    .addOnSuccessListener(uri -> Glide.with(context).load(uri).placeholder(R.drawable.placeholder_food).into(ivImage))
                    .addOnFailureListener(e -> ivImage.setImageResource(R.drawable.placeholder_food));
        }
    }
}
