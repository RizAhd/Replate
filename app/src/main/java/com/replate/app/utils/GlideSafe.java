package com.replate.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

/**
 * Glide.with(context) throws IllegalArgumentException if the Activity behind the context is
 * already destroyed. Async callbacks (Firebase Storage, etc.) can fire after that, so check first.
 */
public final class GlideSafe {

    private GlideSafe() {}

    public static boolean canLoad(Context context) {
        if (context == null) {
            return false;
        }
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                Activity activity = (Activity) current;
                return !activity.isFinishing() && !activity.isDestroyed();
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return true;
    }
}
