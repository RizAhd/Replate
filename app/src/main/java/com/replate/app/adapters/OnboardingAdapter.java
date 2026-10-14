package com.replate.app.adapters;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.replate.app.R;
import com.replate.app.models.OnboardingItem;

import java.util.Collections;
import java.util.List;

/**
 * Adapter for the onboarding ViewPager2: one page per slide, each with its photo, its text and
 * the example of the app that belongs to it.
 */
public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private static final String TAG = "OnboardingAdapter";
    /** What the example QR code contains: clearly a sample, never a real order. */
    private static final String SAMPLE_QR_TEXT = "REPLATE sample pickup code 482913";

    private final List<OnboardingItem> onboardingItems;
    @Nullable private Bitmap sampleQr;

    public OnboardingAdapter(List<OnboardingItem> onboardingItems) {
        this.onboardingItems = onboardingItems;
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        holder.bind(onboardingItems.get(position));
    }

    @Override
    public int getItemCount() {
        return onboardingItems.size();
    }

    // Draws the example QR code once, at the size it is shown, in the app's dark text colour.
    @Nullable
    private Bitmap sampleQr(@NonNull View anyView) {
        if (sampleQr != null) {
            return sampleQr;
        }
        int size = anyView.getResources().getDimensionPixelSize(R.dimen.onboarding_qr_size);
        int dark = ContextCompat.getColor(anyView.getContext(), R.color.text_primary);
        int light = ContextCompat.getColor(anyView.getContext(), R.color.surface_white);
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(SAMPLE_QR_TEXT, BarcodeFormat.QR_CODE,
                    size, size, Collections.singletonMap(EncodeHintType.MARGIN, 0));
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int row = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[row + x] = matrix.get(x, y) ? dark : light;
                }
            }
            sampleQr = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565);
        } catch (Exception e) {
            Log.w(TAG, "Sample QR could not be drawn", e);
        }
        return sampleQr;
    }

    /** Holds the views of one slide. */
    class OnboardingViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageOnboarding;
        private final TextView textTitle;
        private final TextView textDescription;
        private final View sampleMeal;
        private final View samplePickup;
        private final View sampleCode;

        OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            imageOnboarding = itemView.findViewById(R.id.imageOnboarding);
            textTitle = itemView.findViewById(R.id.textTitle);
            textDescription = itemView.findViewById(R.id.textDescription);
            sampleMeal = itemView.findViewById(R.id.sampleMeal);
            samplePickup = itemView.findViewById(R.id.samplePickup);
            sampleCode = itemView.findViewById(R.id.sampleCode);

            TextView priceWas = itemView.findViewById(R.id.tvSamplePriceWas);
            priceWas.setPaintFlags(priceWas.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        // Shows one slide: its photo, text and the matching example.
        void bind(@NonNull OnboardingItem item) {
            imageOnboarding.setImageResource(item.getPhoto());
            imageOnboarding.setContentDescription(itemView.getContext().getString(item.getPhotoDescription()));
            textTitle.setText(item.getTitle());
            textDescription.setText(item.getDescription());

            OnboardingItem.Sample sample = item.getSample();
            sampleMeal.setVisibility(sample == OnboardingItem.Sample.MEAL_CARD ? View.VISIBLE : View.GONE);
            samplePickup.setVisibility(sample == OnboardingItem.Sample.PICKUP_CHIP ? View.VISIBLE : View.GONE);
            sampleCode.setVisibility(sample == OnboardingItem.Sample.PICKUP_CODE ? View.VISIBLE : View.GONE);
            if (sample == OnboardingItem.Sample.PICKUP_CODE) {
                ImageView qr = sampleCode.findViewById(R.id.ivSampleQr);
                qr.setImageBitmap(sampleQr(itemView));
            }
        }
    }
}
