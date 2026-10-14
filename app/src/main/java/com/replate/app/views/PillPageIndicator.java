package com.replate.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.replate.app.R;

/**
 * Page dots for a ViewPager2: the current page is a 24 x 8dp leaf-green pill, the others are
 * 8dp grey dots. The pill stretches from one dot to the next while the user swipes, so it follows
 * the finger. Used by the onboarding slides and the Home banner carousel.
 */
public class PillPageIndicator extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dotSize;
    private final float selectedWidth;
    private final float gap;
    private final int selectedColor;
    private final int unselectedColor;

    private int count;
    private int position;
    private float offset;

    @Nullable private ViewPager2 pager;
    @Nullable private RecyclerView.Adapter<?> observedAdapter;

    private final ViewPager2.OnPageChangeCallback pageCallback = new ViewPager2.OnPageChangeCallback() {
        // Moves the pill along with the swipe.
        @Override
        public void onPageScrolled(int pos, float positionOffset, int positionOffsetPixels) {
            position = pos;
            offset = positionOffset;
            invalidate();
        }

        // Keeps the spoken description on the page that is now showing.
        @Override
        public void onPageSelected(int pos) {
            position = pos;
            offset = 0f;
            updateDescription();
            invalidate();
        }
    };

    private final RecyclerView.AdapterDataObserver dataObserver = new RecyclerView.AdapterDataObserver() {
        // The list was swapped (for example banners from the server): count the pages again.
        @Override
        public void onChanged() {
            refreshCount();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            refreshCount();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            refreshCount();
        }
    };

    public PillPageIndicator(@NonNull Context context) {
        this(context, null);
    }

    public PillPageIndicator(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dotSize = getResources().getDimension(R.dimen.page_indicator_dot);
        selectedWidth = getResources().getDimension(R.dimen.page_indicator_selected_width);
        gap = getResources().getDimension(R.dimen.page_indicator_gap);
        selectedColor = ContextCompat.getColor(context, R.color.leaf);
        unselectedColor = ContextCompat.getColor(context, R.color.indicator_inactive);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Follows this pager's pages and its adapter's item count. Call after the adapter is set. */
    public void attachTo(@NonNull ViewPager2 viewPager) {
        if (pager != null) {
            pager.unregisterOnPageChangeCallback(pageCallback);
        }
        if (observedAdapter != null) {
            observedAdapter.unregisterAdapterDataObserver(dataObserver);
        }
        pager = viewPager;
        observedAdapter = viewPager.getAdapter();
        if (observedAdapter != null) {
            observedAdapter.registerAdapterDataObserver(dataObserver);
        }
        viewPager.registerOnPageChangeCallback(pageCallback);
        position = viewPager.getCurrentItem();
        offset = 0f;
        refreshCount();
    }

    // Reads the number of pages from the adapter and redraws.
    private void refreshCount() {
        int newCount = observedAdapter == null ? 0 : observedAdapter.getItemCount();
        if (pager != null) {
            position = Math.max(0, Math.min(pager.getCurrentItem(), Math.max(0, newCount - 1)));
        }
        if (newCount != count) {
            count = newCount;
            requestLayout();
        }
        updateDescription();
        invalidate();
    }

    // "Page 2 of 3" for screen readers; nothing to say when there is a single page.
    private void updateDescription() {
        setContentDescription(count > 1
                ? getContext().getString(R.string.page_indicator_cd, position + 1, count)
                : null);
    }

    // Width of all the dots plus the pill.
    private float contentWidth() {
        return count <= 0 ? 0f : (count - 1) * (dotSize + gap) + selectedWidth;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wanted = (int) Math.ceil(contentWidth()) + getPaddingLeft() + getPaddingRight();
        int wantedHeight = (int) Math.ceil(dotSize) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(wanted, widthMeasureSpec), resolveSize(wantedHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (count <= 1) {
            return; // one page needs no dots
        }
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float available = getWidth() - getPaddingLeft() - getPaddingRight();
        float x = getPaddingLeft() + Math.max(0f, (available - contentWidth()) / 2f);
        float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - dotSize) / 2f;
        float radius = dotSize / 2f;
        for (int slot = 0; slot < count; slot++) {
            int page = rtl ? count - 1 - slot : slot;
            float selected = page == position ? 1f - offset : (page == position + 1 ? offset : 0f);
            float width = dotSize + (selectedWidth - dotSize) * selected;
            paint.setColor(ColorUtils.blendARGB(unselectedColor, selectedColor, selected));
            rect.set(x, top, x + width, top + dotSize);
            canvas.drawRoundRect(rect, radius, radius, paint);
            x += width + gap;
        }
    }
}
