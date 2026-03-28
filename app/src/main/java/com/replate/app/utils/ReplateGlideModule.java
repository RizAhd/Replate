package com.replate.app.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Custom Glide configuration for optimal performance.
 * - Limits memory cache to 20MB
 * - Limits disk cache to 100MB
 * - Uses RGB_565 for lower memory usage
 */
@GlideModule
public final class ReplateGlideModule extends AppGlideModule {

    private static final int MEMORY_CACHE_SIZE = 20 * 1024 * 1024;   // 20 MB
    private static final int DISK_CACHE_SIZE = 100 * 1024 * 1024;    // 100 MB

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setMemoryCache(new LruResourceCache(MEMORY_CACHE_SIZE));
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, "glide_cache", DISK_CACHE_SIZE));
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565)
                        .disallowHardwareConfig()
        );
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}

