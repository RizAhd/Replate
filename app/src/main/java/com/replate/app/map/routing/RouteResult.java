package com.replate.app.map.routing;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

// A route on the map: line points, distance and time.
public class RouteResult {
    private final List<LatLng> polylinePoints;
    private final String distanceText;
    private final String durationText;

    public RouteResult(List<LatLng> polylinePoints, String distanceText, String durationText) {
        this.polylinePoints = polylinePoints;
        this.distanceText = distanceText;
        this.durationText = durationText;
    }

    public List<LatLng> getPolylinePoints() {
        return polylinePoints;
    }

    public String getDistanceText() {
        return distanceText;
    }

    public String getDurationText() {
        return durationText;
    }
}

