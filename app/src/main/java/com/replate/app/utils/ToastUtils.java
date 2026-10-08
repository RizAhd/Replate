package com.replate.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.replate.app.R;

/**
 * Shared helper for branded toast rendering across activities/fragments/receivers.
 */
public final class ToastUtils {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final int BOTTOM_MARGIN_DP = 64;

    private ToastUtils() {
    }

    /** Same as the String version, resolving the text safely even if the screen is already gone. */
    public static void showCustomToast(Context context, @StringRes int messageRes) {
        if (context == null) {
            return;
        }
        String message;
        try {
            message = context.getString(messageRes);
        } catch (Exception e) {
            return;
        }
        showCustomToast(context, message);
    }

    // Shows the branded toast with the given text.
    public static void showCustomToast(Context context, String message) {
        if (context == null) {
            return;
        }

        final Context safeContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        final String safeMessage = TextUtils.isEmpty(message) ? "" : message;

        Runnable showTask = () -> showInternal(safeContext, safeMessage);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showTask.run();
        } else {
            MAIN_HANDLER.post(showTask);
        }
    }

    // Builds and shows the toast view.
    private static void showInternal(@NonNull Context context, @NonNull String message) {
        try {
            LayoutInflater inflater = LayoutInflater.from(context);
            View toastView = inflater.inflate(R.layout.toast_custom, null, false);

            ImageView iconView = toastView.findViewById(R.id.ivToastIcon);
            TextView messageView = toastView.findViewById(R.id.tvToastMessage);

            if (iconView != null) {
                iconView.setImageResource(R.mipmap.ic_launcher);
            }
            if (messageView != null) {
                messageView.setText(message);
            }

            try {
                Animation fadeIn = AnimationUtils.loadAnimation(context, R.anim.toast_fade_in);
                toastView.startAnimation(fadeIn);
            } catch (Exception ignored) {
            }

            Toast toast = new Toast(context);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setView(toastView);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dpToPx(context, BOTTOM_MARGIN_DP));
            toast.show();
        } catch (Exception ignored) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    // Converts dp to pixels.
    private static int dpToPx(@NonNull Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

