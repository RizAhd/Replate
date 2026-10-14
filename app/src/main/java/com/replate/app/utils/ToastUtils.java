package com.replate.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.snackbar.Snackbar;
import com.replate.app.R;

/**
 * Shared helper for short messages across activities, fragments and receivers.
 *
 * On a screen the user is looking at, the message is a Material Snackbar (up to five lines, above the
 * bottom tabs). With no visible screen (a receiver or background work, a screen that is closing, or a
 * dialog that has focus) it falls back to a plain system toast, so the message is never lost.
 */
public final class ToastUtils {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final int SNACKBAR_MAX_LINES = 5;
    /** Messages longer than this stay up longer, so they can be read to the end. */
    private static final int LONG_MESSAGE_CHARS = 60;

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

    // Shows the message as a snackbar on the current screen, or as a toast when there is none.
    public static void showCustomToast(Context context, String message) {
        if (context == null) {
            return;
        }
        final String safeMessage = TextUtils.isEmpty(message) ? "" : message;
        final Activity activity = findActivity(context);
        final Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        // Posted, so a message followed by finish() in the same tap is seen as "screen closing" and
        // becomes a toast that outlives the screen.
        MAIN_HANDLER.post(() -> {
            if (activity != null && canShowSnackbar(activity) && showSnackbar(activity, safeMessage)) {
                return;
            }
            showToast(appContext, safeMessage);
        });
    }

    // True when the activity is on screen, not closing, and no dialog has taken the focus.
    private static boolean canShowSnackbar(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) {
            return false;
        }
        if (activity instanceof ComponentActivity) {
            return ((ComponentActivity) activity).getLifecycle().getCurrentState()
                    .isAtLeast(Lifecycle.State.STARTED);
        }
        return true;
    }

    // Shows a Material snackbar; false if the screen has no content view yet.
    private static boolean showSnackbar(@NonNull Activity activity, @NonNull String message) {
        try {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) {
                return false;
            }
            int duration = message.length() > LONG_MESSAGE_CHARS ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT;
            Snackbar snackbar = Snackbar.make(root, message, duration);
            snackbar.setTextMaxLines(SNACKBAR_MAX_LINES);
            // Keep it above the bottom tabs on the main screen
            View bottomNav = activity.findViewById(R.id.bottomNavigation);
            if (bottomNav != null && bottomNav.isShown()) {
                snackbar.setAnchorView(bottomNav);
            }
            snackbar.show();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Plain system toast (no custom view: custom toast views are deprecated and blocked in the background).
    private static void showToast(@NonNull Context context, @NonNull String message) {
        try {
            Toast.makeText(context, message,
                    message.length() > LONG_MESSAGE_CHARS ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    // Walks the context wrappers to the activity, if there is one.
    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
