package com.replate.app.utils;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

/**
 * Form validation messages shown inline, under the field they belong to (never as a toast).
 */
public final class FormErrors {

    private FormErrors() {
    }

    /** Shows the message under the field (in its TextInputLayout when it has one) and scrolls to it. */
    public static void show(@NonNull EditText field, @Nullable CharSequence message) {
        TextInputLayout layout = findLayout(field);
        View shown = layout != null ? layout : field;
        if (layout != null) {
            layout.setError(message);
        } else {
            field.setError(message);
        }
        if (message != null) {
            field.requestFocus();
            shown.requestRectangleOnScreen(new Rect(0, 0, shown.getWidth(), shown.getHeight()));
        }
    }

    /** Removes the messages from the given fields. */
    public static void clear(@NonNull EditText... fields) {
        for (EditText field : fields) {
            if (field == null) continue;
            TextInputLayout layout = findLayout(field);
            if (layout != null) {
                layout.setError(null);
            } else {
                field.setError(null);
            }
        }
    }

    // Finds the TextInputLayout that wraps the field, if any.
    @Nullable
    private static TextInputLayout findLayout(@NonNull View field) {
        ViewParent parent = field.getParent();
        while (parent instanceof View) {
            if (parent instanceof TextInputLayout) {
                return (TextInputLayout) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
}
