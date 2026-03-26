package com.replate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;
import com.replate.app.models.Review;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private final List<Review> reviews = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        holder.bind(reviews.get(position));
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    public void submitList(List<Review> newReviews) {
        reviews.clear();
        if (newReviews != null) reviews.addAll(newReviews);
        notifyDataSetChanged();
    }

    class ReviewViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvReviewer;
        private final TextView tvDate;
        private final TextView tvComment;
        private final RatingBar ratingBar;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvReviewer = itemView.findViewById(R.id.tvReviewer);
            tvDate = itemView.findViewById(R.id.tvReviewDate);
            tvComment = itemView.findViewById(R.id.tvReviewComment);
            ratingBar = itemView.findViewById(R.id.ratingReviewItem);
        }

        void bind(Review review) {
            tvReviewer.setText(review.getUserName());
            ratingBar.setRating(review.getRatingValue());
            tvComment.setText(review.getComment().trim().isEmpty() ? "No comment" : review.getComment());

            long ts = review.getCreatedAtValue();
            tvDate.setText(ts > 0 ? dateFormat.format(new Date(ts)) : "Recently");
        }
    }
}

