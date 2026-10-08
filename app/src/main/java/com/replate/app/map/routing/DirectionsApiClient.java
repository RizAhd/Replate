package com.replate.app.map.routing;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirectionsApiClient {

    public interface Callback {
        void onSuccess(RouteResult result);
        void onError(String message);
    }

    private static final String TAG = "DirectionsApiClient";

    // Shared by every client instance: the map screen creates a new client each time its view is
    // built, and a per-instance executor was never shut down.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final RouteCache ROUTE_CACHE = new RouteCache();

    private final String mapsApiKey;
    private final Handler mainHandler;
    // Application context, used to tell Google which app (package + signing cert) is calling
    private final Context appContext;
    private volatile String certSha1;
    private volatile boolean certLoaded;

    public DirectionsApiClient(@NonNull String mapsApiKey) {
        this(null, mapsApiKey);
    }

    public DirectionsApiClient(@Nullable Context context, @NonNull String mapsApiKey) {
        this.mapsApiKey = mapsApiKey;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.appContext = context != null ? context.getApplicationContext() : null;
    }

    public void fetchRoute(@NonNull LatLng origin,
                           @NonNull LatLng destination,
                           @NonNull Callback callback) {
        final String cacheKey = RouteCache.key(
                origin.latitude, origin.longitude, destination.latitude, destination.longitude);
        RouteResult cached = ROUTE_CACHE.get(cacheKey, System.currentTimeMillis());
        if (cached != null) {
            mainHandler.post(() -> callback.onSuccess(cached));
            return;
        }

        EXECUTOR.execute(() -> {
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
                addAppIdentityHeaders(connection);

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
                    String errorMessage = root.optString("error_message", "");
                    Log.w(TAG, "Directions status=" + status + " error_message=" + errorMessage);
                    postError(callback, describeStatus(status));
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
                ROUTE_CACHE.put(cacheKey, routeResult, System.currentTimeMillis());
                mainHandler.post(() -> callback.onSuccess(routeResult));

            } catch (UnknownHostException | ConnectException e) {
                postError(callback, "No internet connection. Check your network and try again.");
            } catch (SocketTimeoutException e) {
                postError(callback, "The route request timed out. Please try again.");
            } catch (Exception e) {
                Log.w(TAG, "Route fetch failed", e);
                postError(callback, "Couldn't load the route. Please try again.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /** Lets a key that is restricted to this Android app be accepted; any failure just skips the headers. */
    private void addAppIdentityHeaders(@NonNull HttpURLConnection connection) {
        if (appContext == null) return;
        try {
            connection.setRequestProperty("X-Android-Package", appContext.getPackageName());
            String sha1 = signingCertSha1();
            if (sha1 != null) {
                connection.setRequestProperty("X-Android-Cert", sha1);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not add the app identity headers", e);
        }
    }

    @Nullable
    private String signingCertSha1() {
        if (!certLoaded) {
            certSha1 = readSigningCertSha1();
            certLoaded = true;
        }
        return certSha1;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private String readSigningCertSha1() {
        try {
            PackageManager pm = appContext.getPackageManager();
            Signature signature = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo info = pm.getPackageInfo(appContext.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                Signature[] signers = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
                if (signers != null && signers.length > 0) signature = signers[0];
            } else {
                PackageInfo info = pm.getPackageInfo(appContext.getPackageName(), PackageManager.GET_SIGNATURES);
                if (info.signatures != null && info.signatures.length > 0) signature = info.signatures[0];
            }
            if (signature == null) return null;
            return toHex(MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()));
        } catch (Exception e) {
            Log.w(TAG, "Could not read the signing certificate", e);
            return null;
        }
    }

    /** Uppercase hex with no separators, the format of the X-Android-Cert header. */
    static String toHex(@NonNull byte[] bytes) {
        char[] digits = "0123456789ABCDEF".toCharArray();
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(digits[(b >> 4) & 0xF]).append(digits[b & 0xF]);
        }
        return hex.toString();
    }

    private void postError(@NonNull Callback callback, @NonNull String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    /** Turns a Directions API status code into text a customer can act on. */
    static String describeStatus(String status) {
        if (status == null) {
            return "Route unavailable. Please try again.";
        }
        switch (status) {
            case "ZERO_RESULTS":
                return "No driving route was found to this place.";
            case "NOT_FOUND":
                return "This location couldn't be found.";
            case "OVER_QUERY_LIMIT":
            case "OVER_DAILY_LIMIT":
                return "Too many route requests right now. Please try again shortly.";
            case "REQUEST_DENIED":
                return "Directions are unavailable right now.";
            case "INVALID_REQUEST":
                return "That route request wasn't valid.";
            default:
                return "Route unavailable. Please try again.";
        }
    }

    private String readStream(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    /**
     * Decodes a Google encoded polyline. A truncated or malformed string returns the points
     * decoded so far instead of throwing.
     */
    static List<LatLng> decodePolyline(@NonNull String encoded) {
        List<LatLng> polyline = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;
        int length = encoded.length();

        while (index < length) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                if (index >= length) {
                    return polyline;
                }
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dLat;

            shift = 0;
            result = 0;
            do {
                if (index >= length) {
                    return polyline;
                }
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dLng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dLng;

            polyline.add(new LatLng(lat / 1E5, lng / 1E5));
        }

        return polyline;
    }
}

