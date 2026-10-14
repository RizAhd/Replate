package com.replate.app.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.replate.app.R;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One way to show a dish photo on the food cards.
 * A photo can be a web link, a gs:// link or a Firebase Storage path; Storage links are resolved once
 * and cached. The photo is loaded at the size of its view (sharp on every screen), fades in over the
 * quiet placeholder, and a row that was recycled meanwhile is never filled with an old photo.
 */
public final class FoodImages {

    /** Short cross-fade from the placeholder, so photos do not pop in. */
    private static final int FADE_MS = 150;

    private static final Map<String, String> RESOLVED = new ConcurrentHashMap<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private FoodImages() {}

    public static void load(Context context, ImageView target, @Nullable String source) {
        load(context, target, source, null);
    }

    /**
     * Same as {@link #load(Context, ImageView, String)}; {@code onShown} runs once the photo or the
     * placeholder is on screen (the detail screen waits for it before its photo moves in).
     */
    public static void load(Context context, ImageView target, @Nullable String source, @Nullable Runnable onShown) {
        if (target == null || !GlideSafe.canLoad(context)) {
            if (onShown != null) onShown.run();
            return;
        }
        String src = source == null ? "" : source.trim();
        // Remember which photo this view wants, so a late Storage answer cannot fill a recycled row
        target.setTag(R.id.tag_food_image_source, src);
        if (src.isEmpty() || FAILED.contains(src)) {
            Glide.with(context).clear(target);
            target.setImageResource(R.drawable.placeholder_food);
            if (onShown != null) onShown.run();
            return;
        }
        if (src.startsWith("http://") || src.startsWith("https://")) {
            show(context, target, src, onShown);
            return;
        }
        String cached = RESOLVED.get(src);
        if (cached != null) {
            show(context, target, cached, onShown);
            return;
        }

        // Clear the old photo straight away while the Storage link is being resolved
        Glide.with(context).clear(target);
        target.setImageResource(R.drawable.placeholder_food);
        StorageReference ref;
        try {
            ref = src.startsWith("gs://")
                    ? FirebaseStorage.getInstance().getReferenceFromUrl(src)
                    : FirebaseStorage.getInstance().getReference().child(src);
        } catch (IllegalArgumentException e) {
            FAILED.add(src);
            if (onShown != null) onShown.run();
            return;
        }
        ref.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    String resolved = uri == null ? "" : uri.toString();
                    if (resolved.isEmpty()) {
                        FAILED.add(src);
                        if (onShown != null) onShown.run();
                        return;
                    }
                    RESOLVED.put(src, resolved);
                    if (src.equals(target.getTag(R.id.tag_food_image_source)) && GlideSafe.canLoad(context)) {
                        show(context, target, resolved, onShown);
                    } else if (onShown != null) {
                        onShown.run();
                    }
                })
                .addOnFailureListener(e -> {
                    FAILED.add(src);
                    if (onShown != null) onShown.run();
                });
    }

    /** Stops a pending load and frees the bitmap, for rows that scroll off screen. */
    public static void clear(Context context, ImageView target) {
        if (target == null || !GlideSafe.canLoad(context)) {
            return;
        }
        target.setTag(R.id.tag_food_image_source, null);
        Glide.with(context).clear(target);
    }

    private static void show(Context context, ImageView target, String url, @Nullable Runnable onShown) {
        RequestBuilder<Drawable> request = Glide.with(context)
                .load(url)
                .placeholder(R.drawable.placeholder_food)
                .error(R.drawable.placeholder_food)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(FADE_MS));
        if (onShown != null) {
            request = request.listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                            @NonNull Target<Drawable> t, boolean isFirstResource) {
                    onShown.run();
                    return false;
                }

                @Override
                public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                               Target<Drawable> t, @NonNull DataSource dataSource,
                                               boolean isFirstResource) {
                    onShown.run();
                    return false;
                }
            });
        }
        request.into(target);
    }
}
