package com.replate.app.map.data;

import androidx.annotation.NonNull;

import com.replate.app.map.model.FoodPlace;

import java.util.List;

// Source of the places shown on the map.
public interface PlacesDataSource {

    interface Callback {
        void onSuccess(@NonNull List<FoodPlace> places);
        void onError(@NonNull String message);
    }

    void fetchPlaces(@NonNull Callback callback);
}
