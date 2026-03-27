package com.replate.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.replate.app.R;

import java.util.List;

/**
 * Adapter for banner/promo carousel
 */
public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

    private final Context context;
    private final List<Integer> bannerImages;

    private static final String[] BANNER_BADGES = {
            "HOT DEAL", "FOOD RESCUE", "SMART SAVINGS"
    };

    private static final String[] BANNER_TITLES = {
            "Low Prices, Big Smiles",
            "Don't Waste Good Food",
            "Rescue Meals Near You"
    };

    private static final String[] BANNER_SUBTITLES = {
            "Get hotel-quality food at rescue prices today.",
            "Every order helps reduce food waste in Sri Lanka.",
            "Fresh surplus meals are waiting around your location."
    };

    public BannerAdapter(Context context, List<Integer> bannerImages) {
        this.context = context;
        this.bannerImages = bannerImages;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_banner, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        try {
            holder.ivBanner.setImageResource(bannerImages.get(position));
        } catch (Exception ignored) {
            holder.ivBanner.setImageResource(R.drawable.bg_gradient_green);
        }

        int index = position % BANNER_TITLES.length;
        holder.tvBannerBadge.setText(BANNER_BADGES[index]);
        holder.tvBannerTitle.setText(BANNER_TITLES[index]);
        holder.tvBannerSubtitle.setText(BANNER_SUBTITLES[index]);
    }

    @Override
    public int getItemCount() {
        return bannerImages.size();
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBanner;
        TextView tvBannerBadge;
        TextView tvBannerTitle;
        TextView tvBannerSubtitle;

        BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBanner = itemView.findViewById(R.id.ivBanner);
            tvBannerBadge = itemView.findViewById(R.id.tvBannerBadge);
            tvBannerTitle = itemView.findViewById(R.id.tvBannerTitle);
            tvBannerSubtitle = itemView.findViewById(R.id.tvBannerSubtitle);
        }
    }
}
