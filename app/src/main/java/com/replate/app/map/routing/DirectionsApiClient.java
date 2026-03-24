package com.replate.app.map.routing;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirectionsApiClient {

    public interface Callback {
        void onSuccess(RouteResult result);
        void onError(String message);
    }

    private final String mapsApiKey;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public DirectionsApiClient(@NonNull String mapsApiKey) {
        this.mapsApiKey = mapsApiKey;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void fetchRoute(@NonNull LatLng origin,
                           @NonNull LatLng destination,
                           @NonNull Callback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String originParam = origin.latitude + "," + origin.longitude;
                String destParam = destination.latitude + "," + destination.longitude;

                String urlString = "https://maps.googleapis.com/maps/api/directions/json"
                        + "?origin=" + URLEncoder.encode(originParam, StandardCharsets.UTF_8.name())
                        + "&destination=" + URLEncoder.encode(destParam, StandardCharsets.UTF_8.name())
                        + "&mode=driving"
                        + "&key=" + URLEncoder.encode(mapsApiKey, StandardCharsets.UTF_8.name());

                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);

                int code = connection.getResponseCode();
                InputStream inputStream = (code >= 200 && code < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String responseBody = readStream(inputStream);
                if (responseBody == null || responseBody.isEmpty()) {
                    postError(callback, "Empty directions response");
                    return;
                }

                JSONObject root = new JSONObject(responseBody);
                String status = root.optString("status", "UNKNOWN");
                if (!"OK".equals(status)) {
                    postError(callback, "Directions failed: " + status);
                    return;
                }

                JSONArray routes = root.optJSONArray("routes");
                if (routes == null || routes.length() == 0) {
                    postError(callback, "No route found");
                    return;
                }

                JSONObject routeObj = routes.getJSONObject(0);
                JSONObject overviewPolyline = routeObj.getJSONObject("overview_polyline");
                String encodedPolyline = overviewPolyline.optString("points", "");

                JSONArray legs = routeObj.optJSONArray("legs");
                String distanceText = "-";
                String durationText = "-";
                if (legs != null && legs.length() > 0) {
                    JSONObject leg0 = legs.getJSONObject(0);
                    distanceText = leg0.getJSONObject("distance").optString("text", "-");
                    durationText = leg0.getJSONObject("duration").optString("text", "-");
                }

                List<LatLng> points = decodePolyline(encodedPolyline);
                if (points.isEmpty()) {
                    postError(callback, "Route polyline is empty");
                    return;
                }

                RouteResult routeResult = new RouteResult(points, distanceText, durationText);
                mainHandler.post(() -> callback.onSuccess(routeResult));

            } catch (Exception e) {
                postError(callback, "Route fetch error: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void postError(@NonNull Callback callback, @NonNull String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private String readStream(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private List<LatLng> decodePolyline(@NonNull String encoded) {
        List<LatLng> polyline = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < encoded.length()) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dLat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dLng;

            LatLng point = new LatLng(lat / 1E5, lng / 1E5);
            polyline.add(point);
        }

        return polyline;
    }
}

