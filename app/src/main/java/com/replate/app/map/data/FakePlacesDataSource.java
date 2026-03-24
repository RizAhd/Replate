package com.replate.app.map.data;

import androidx.annotation.NonNull;

import com.replate.app.map.model.FoodPlace;

import java.util.ArrayList;
import java.util.List;

public class FakePlacesDataSource implements PlacesDataSource {

    @Override
    public void fetchPlaces(@NonNull Callback callback) {
        callback.onSuccess(buildPlaces());
    }

    public List<FoodPlace> buildPlacesForFallbackOnly() {
        return buildPlaces();
    }

    private List<FoodPlace> buildPlaces() {
        List<FoodPlace> places = new ArrayList<>();

        places.add(new FoodPlace(
                "haggala_green",
                "Haggala Green Hotel",
                "Haggala, Nuwara Eliya",
                6.9279,
                80.8010,
                "Hotel",
                4.5f
        ));

        places.add(new FoodPlace(
                "nuwara_town_dine",
                "Nuwara Town Dine",
                "Nuwara Eliya Town",
                6.9497,
                80.7891,
                "Restaurant",
                4.2f
        ));

        places.add(new FoodPlace(
                "moon_plains_bistro",
                "Moon Plains Bistro",
                "Near Moon Plains, Nuwara Eliya",
                6.9648,
                80.7718,
                "Cafe",
                4.4f
        ));

        places.add(new FoodPlace(
                "single_tree_kitchen",
                "Single Tree Kitchen",
                "Single Tree Hill, Nuwara Eliya",
                6.9667,
                80.7714,
                "Food Shop",
                4.1f
        ));

        return places;
    }
}
