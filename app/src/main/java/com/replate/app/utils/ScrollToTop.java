package com.replate.app.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tapping the already-selected bottom tab should bring that screen back to the top, like most apps.
 * Finds the outermost scrolling container in a tab's view and scrolls it smoothly.
 */
public final class ScrollToTop {

    private ScrollToTop() {}

    /** @return true if something was scrolled */
    public static boolean apply(@Nullable View root) {
        if (root == null) {
            return false;
        }
        Deque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (view instanceof NestedScrollView) {
                ((NestedScrollView) view).smoothScrollTo(0, 0);
                return true;
            }
            if (view instanceof ScrollView) {
                ((ScrollView) view).smoothScrollTo(0, 0);
                return true;
            }
            if (view instanceof RecyclerView) {
                RecyclerView list = (RecyclerView) view;
                if (list.getAdapter() != null && list.getAdapter().getItemCount() > 0) {
                    list.smoothScrollToPosition(0);
                    return true;
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
        return false;
    }
}
