package com.replate.app.utils;

import android.content.Context;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.replate.app.R;
import com.replate.app.helpers.ReplateDatabaseHelper;

/**
 * The heart next to a hotel's name (Food detail and the Map sheet). Saved hotels live in the
 * phone's SQLite database and are listed on the Favorites screen.
 */
public final class FavoriteHotelToggle {

    private FavoriteHotelToggle() {}

    /** Shows the saved state for this hotel and makes the heart save or remove it. */
    public static void bind(Context context, @Nullable ImageView heart, @Nullable String hotelId,
                            @Nullable String hotelName, @Nullable String address) {
        if (heart == null) {
            return;
        }
        if (hotelId == null || hotelId.trim().isEmpty()) {
            // Without an id the hotel cannot be saved or found again
            heart.setVisibility(android.view.View.GONE);
            return;
        }
        heart.setVisibility(android.view.View.VISIBLE);

        ReplateDatabaseHelper db = ReplateDatabaseHelper.getInstance(context);
        render(context, heart, db.isFavoriteHotel(hotelId));
        heart.setOnClickListener(v -> {
            boolean nowSaved = !db.isFavoriteHotel(hotelId);
            if (nowSaved) {
                db.addFavoriteHotel(hotelId, hotelName, address);
            } else {
                db.removeFavoriteHotel(hotelId);
            }
            render(context, heart, nowSaved);
            ToastUtils.showCustomToast(context, nowSaved
                    ? R.string.favorite_hotel_added : R.string.favorite_hotel_removed);
        });
    }

    private static void render(Context context, ImageView heart, boolean saved) {
        heart.setImageResource(saved ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        heart.setColorFilter(ContextCompat.getColor(context,
                saved ? R.color.md_theme_error : R.color.text_secondary));
        heart.setContentDescription(context.getString(saved
                ? R.string.favorite_hotel_remove_cd : R.string.favorite_hotel_add_cd));
    }
}
