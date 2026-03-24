package com.replate.app.map.data;

import androidx.annotation.NonNull;

import com.replate.app.map.model.FoodPlace;

import java.util.List;

public class FallbackPlacesDataSource implements PlacesDataSource {

    private final PlacesDataSource primary;
    private final PlacesDataSource fallback;

    public FallbackPlacesDataSource(@NonNull PlacesDataSource primary,
                                    @NonNull PlacesDataSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public void fetchPlaces(@NonNull Callback callback) {
        primary.fetchPlaces(new Callback() {
            @Override
            public void onSuccess(@NonNull List<FoodPlace> places) {
                if (!places.isEmpty()) {
                    callback.onSuccess(places);
                    return;
                }
                fallback.fetchPlaces(callback);
            }

            @Override
            public void onError(@NonNull String message) {
                fallback.fetchPlaces(callback);
            }
        });
    }
}

