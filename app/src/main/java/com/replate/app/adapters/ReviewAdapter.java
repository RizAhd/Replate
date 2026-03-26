package com.replate.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

    public interface ReviewActionListener {
        void onEditReview(Review review);
        void onDeleteReview(Review review);
    }

    private final List<Review> reviews = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final String currentUserId;
    private final ReviewActionListener actionListener;

    public ReviewAdapter(String currentUserId, ReviewActionListener actionListener) {
        this.currentUserId = currentUserId == null ? "" : currentUserId;
        this.actionListener = actionListener;
    }

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
        private final TextView tvVerifiedPurchase;
        private final TextView btnEditReview;
        private final TextView btnDeleteReview;
        private final LinearLayout layoutReviewActions;
        private final RatingBar ratingBar;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvReviewer = itemView.findViewById(R.id.tvReviewer);
            tvDate = itemView.findViewById(R.id.tvReviewDate);
            tvComment = itemView.findViewById(R.id.tvReviewComment);
            tvVerifiedPurchase = itemView.findViewById(R.id.tvVerifiedPurchase);
            btnEditReview = itemView.findViewById(R.id.btnEditReview);
            btnDeleteReview = itemView.findViewById(R.id.btnDeleteReview);
            layoutReviewActions = itemView.findViewById(R.id.layoutReviewActions);
            ratingBar = itemView.findViewById(R.id.ratingReviewItem);
        }

        void bind(Review review) {
            tvReviewer.setText(review.getUserName());
            ratingBar.setRating(review.getRatingValue());
            tvComment.setText(review.getComment().trim().isEmpty() ? "No comment" : review.getComment());

            long ts = review.getCreatedAtValue();
            tvDate.setText(ts > 0 ? dateFormat.format(new Date(ts)) : "Recently");

            tvVerifiedPurchase.setVisibility(review.isVerifiedPurchase() ? View.VISIBLE : View.GONE);

            boolean isOwner = review.getUserId() != null
                    && !review.getUserId().trim().isEmpty()
                    && review.getUserId().equals(currentUserId);
            layoutReviewActions.setVisibility(isOwner ? View.VISIBLE : View.GONE);

            if (isOwner && actionListener != null) {
                btnEditReview.setOnClickListener(v -> actionListener.onEditReview(review));
                btnDeleteReview.setOnClickListener(v -> actionListener.onDeleteReview(review));
            } else {
                btnEditReview.setOnClickListener(null);
                btnDeleteReview.setOnClickListener(null);
            }
        }
    }
}
