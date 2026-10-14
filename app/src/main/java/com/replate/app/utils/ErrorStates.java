package com.replate.app.utils;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.replate.app.R;

/**
 * Inline load errors with a Try again button, instead of a toast that disappears.
 * Works with both included layouts: view_error_state (replaces an empty list) and
 * view_error_banner (sits above a list that still shows saved data).
 */
public final class ErrorStates {

    private ErrorStates() {
    }

    /** Shows the error view with the message; Try again hides it and runs onRetry. */
    public static void show(@Nullable View errorView, @StringRes int message, @Nullable Runnable onRetry) {
        if (errorView == null) return;
        TextView text = errorView.findViewById(R.id.tvErrorMessage);
        if (text != null) text.setText(message);
        View retry = errorView.findViewById(R.id.btnErrorRetry);
        if (retry != null) {
            retry.setVisibility(onRetry != null ? View.VISIBLE : View.GONE);
            retry.setOnClickListener(onRetry == null ? null : v -> {
                hide(errorView);
                onRetry.run();
            });
        }
        errorView.setVisibility(View.VISIBLE);
    }

    /** Hides the error view. */
    public static void hide(@Nullable View errorView) {
        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    /** True while the error view is on screen. */
    public static boolean isShown(@Nullable View errorView) {
        return errorView != null && errorView.getVisibility() == View.VISIBLE;
    }
}
