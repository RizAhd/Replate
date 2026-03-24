package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.Category;

import java.util.List;

/**
 * Adapter for displaying food categories
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private Context context;
    private List<Category> categories;
    private OnCategoryClickListener listener;
    private int selectedPosition = -1;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public CategoryAdapter(Context context, List<Category> categories, OnCategoryClickListener listener) {
        this.context = context;
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category, position);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView ivIcon;
        TextView tvName;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardCategory);
            ivIcon = itemView.findViewById(R.id.ivCategoryIcon);
            tvName = itemView.findViewById(R.id.tvCategoryName);
        }

        void bind(Category category, int position) {
            tvName.setText(category.getName());
            ivIcon.setImageResource(category.getIcon());

            boolean selected = position == selectedPosition;
            if (selected) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.green_primary));
                tvName.setTextColor(ContextCompat.getColor(context, R.color.white));
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.white));
            } else {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_white));
                tvName.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.green_primary));
            }

            cardView.setOnClickListener(v -> {
                int previousSelected = selectedPosition;
                selectedPosition = position;
                if (previousSelected >= 0) {
                    notifyItemChanged(previousSelected);
                }
                notifyItemChanged(selectedPosition);

                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}
