package com.replate.app.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.replate.app.R;
import com.replate.app.models.BannerItem;
import com.replate.app.utils.GlideSafe;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Home promo carousel. Each banner is either a server image (URL) or one of the
 * bundled photos, with an optional tap action.
 */
public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

    public interface OnBannerClickListener {
        void onBannerClick(@NonNull BannerItem banner);
    }

    private final List<BannerItem> banners = new ArrayList<>();
    @Nullable
    private final OnBannerClickListener listener;

    public BannerAdapter(@Nullable OnBannerClickListener listener) {
        this.listener = listener;
    }

    /** Replaces the whole list (used when server banners replace the bundled ones). */
    public void submit(@NonNull List<BannerItem> items) {
        banners.clear();
        banners.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner, parent, false);
        return new BannerViewHolder(view);
    }

    // Fills one banner with text, image and tap action.
    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        BannerItem banner = banners.get(position);
        Context context = holder.itemView.getContext();

        setTextOrHide(holder.tvBannerBadge, banner.getBadge());
        setTextOrHide(holder.tvBannerTitle, banner.getTitle());
        setTextOrHide(holder.tvBannerSubtitle, banner.getSubtitle());

        holder.ivBanner.setImageDrawable(null);
        Object source = null;
        if (!TextUtils.isEmpty(banner.getImageUrl())) {
            source = banner.getImageUrl().trim();
        } else if (banner.getLocalImageRes() != 0) {
            source = banner.getLocalImageRes();
        }

        if (source != null && GlideSafe.canLoad(context)) {
            Glide.with(context)
                    .load(source)
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .placeholder(R.drawable.bg_gradient_green)
                    .error(R.drawable.bg_gradient_green)
                    .centerCrop()
                    .into(holder.ivBanner);
        } else {
            holder.ivBanner.setImageResource(R.drawable.bg_gradient_green);
        }

        String title = banner.getTitle() == null ? "" : banner.getTitle();
        String subtitle = banner.getSubtitle() == null ? "" : banner.getSubtitle();
        holder.bannerCard.setContentDescription(
                context.getString(R.string.banner_content_description, title, subtitle));

        holder.bannerCard.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBannerClick(banner);
            }
        });
    }

    // Shows the text, or hides the view when there is none.
    private static void setTextOrHide(TextView view, @Nullable String text) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(text);
        }
    }

    @Override
    public int getItemCount() {
        return banners.size();
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        View bannerCard;
        ImageView ivBanner;
        TextView tvBannerBadge;
        TextView tvBannerTitle;
        TextView tvBannerSubtitle;

        BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            bannerCard = itemView.findViewById(R.id.bannerCard);
            ivBanner = itemView.findViewById(R.id.ivBanner);
            tvBannerBadge = itemView.findViewById(R.id.tvBannerBadge);
            tvBannerTitle = itemView.findViewById(R.id.tvBannerTitle);
            tvBannerSubtitle = itemView.findViewById(R.id.tvBannerSubtitle);
        }
    }
}
