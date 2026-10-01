package com.replate.app.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.replate.app.R;
import com.replate.app.models.BannerItem;
import com.replate.app.utils.SafeSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Supplies the Home banners: bundled photos shown instantly, replaced by the live list from
 * {@code banners/} in the Realtime Database when it arrives (and cached for the next launch).
 */
public final class BannerRepository {

    public interface Callback {
        void onLoaded(@NonNull List<BannerItem> banners);

        void onError();
    }

    private static final String TAG = "BannerRepository";
    private static final String PREFS_NAME = "REPLATE_BANNERS";
    private static final String KEY_CACHED = "cached_banners";

    private BannerRepository() {}

    /** Offline / first-launch banners that ship inside the app. */
    @NonNull
    public static List<BannerItem> defaultBanners(@NonNull Context context) {
        List<BannerItem> banners = new ArrayList<>();
        banners.add(new BannerItem("local_1",
                context.getString(R.string.banner_1_badge),
                context.getString(R.string.banner_1_title),
                context.getString(R.string.banner_1_subtitle),
                R.drawable.banner_photo_fresh_meals, null));
        banners.add(new BannerItem("local_2",
                context.getString(R.string.banner_2_badge),
                context.getString(R.string.banner_2_title),
                context.getString(R.string.banner_2_subtitle),
                R.drawable.banner_photo_bakery, "category:Bakery"));
        banners.add(new BannerItem("local_3",
                context.getString(R.string.banner_3_badge),
                context.getString(R.string.banner_3_title),
                context.getString(R.string.banner_3_subtitle),
                R.drawable.banner_photo_surplus_meals, null));
        banners.add(new BannerItem("local_4",
                context.getString(R.string.banner_4_badge),
                context.getString(R.string.banner_4_title),
                context.getString(R.string.banner_4_subtitle),
                R.drawable.banner_photo_healthy, "tab:map"));
        return banners;
    }

    /**
     * Keeps only banners that are switched on, inside their date window and that have something
     * to show, ordered by their {@code order} field. Pure function so it can be unit-tested.
     */
    @NonNull
    public static List<BannerItem> filterLiveAndSort(List<BannerItem> banners, long nowMillis) {
        List<BannerItem> live = new ArrayList<>();
        if (banners == null) {
            return live;
        }
        for (BannerItem banner : banners) {
            if (banner == null || !banner.isLiveAt(nowMillis)) {
                continue;
            }
            boolean hasImage = banner.getImageUrl() != null && !banner.getImageUrl().trim().isEmpty();
            boolean hasText = banner.getTitle() != null && !banner.getTitle().trim().isEmpty();
            if (hasImage || hasText) {
                live.add(banner);
            }
        }
        Collections.sort(live, (a, b) -> Long.compare(a.getOrder(), b.getOrder()));
        return live;
    }

    /** Last banner list that loaded successfully, or an empty list. */
    @NonNull
    public static List<BannerItem> getCached(@NonNull Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_CACHED, null);
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }
            List<BannerItem> cached = new Gson().fromJson(json, new TypeToken<List<BannerItem>>() {}.getType());
            return cached == null ? new ArrayList<>() : cached;
        } catch (Exception e) {
            Log.w(TAG, "Could not read cached banners", e);
            return new ArrayList<>();
        }
    }

    private static void saveCached(@NonNull Context context, @NonNull List<BannerItem> banners) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CACHED, new Gson().toJson(banners))
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "Could not cache banners", e);
        }
    }

    /**
     * Reads {@code banners/} once. An empty (or entirely expired) result is still a success: it
     * means "no server banners", and the caller decides whether to keep the bundled set.
     */
    public static void load(@NonNull Context context, @NonNull Callback callback) {
        final Context appContext = context.getApplicationContext();
        FirebaseDatabase.getInstance().getReference("banners")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<BannerItem> parsed = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            BannerItem item = SafeSnapshot.getValue(child, BannerItem.class);
                            if (item != null) {
                                item.setId(child.getKey());
                                parsed.add(item);
                            }
                        }
                        List<BannerItem> live = filterLiveAndSort(parsed, System.currentTimeMillis());
                        saveCached(appContext, live);
                        callback.onLoaded(live);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "Banner load cancelled: " + error.getMessage());
                        callback.onError();
                    }
                });
    }
}
