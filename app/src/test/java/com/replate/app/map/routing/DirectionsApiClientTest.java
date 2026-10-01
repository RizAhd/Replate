package com.replate.app.map.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.gms.maps.model.LatLng;

import org.junit.Test;

import java.util.List;

public class DirectionsApiClientTest {

    // Example from Google's encoded polyline algorithm documentation
    private static final String GOOGLE_EXAMPLE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Test
    public void decodesGoogleDocumentationExample() {
        List<LatLng> points = DirectionsApiClient.decodePolyline(GOOGLE_EXAMPLE);

        assertEquals(3, points.size());
        assertEquals(38.5, points.get(0).latitude, 1e-5);
        assertEquals(-120.2, points.get(0).longitude, 1e-5);
        assertEquals(40.7, points.get(1).latitude, 1e-5);
        assertEquals(-120.95, points.get(1).longitude, 1e-5);
        assertEquals(43.252, points.get(2).latitude, 1e-5);
        assertEquals(-126.453, points.get(2).longitude, 1e-5);
    }

    @Test
    public void emptyStringGivesNoPoints() {
        assertTrue(DirectionsApiClient.decodePolyline("").isEmpty());
    }

    @Test
    public void truncatedInputKeepsDecodedPointsInsteadOfThrowing() {
        // First point is complete; the second is cut off mid-value
        List<LatLng> points = DirectionsApiClient.decodePolyline("_p~iF~ps|U_ulLnn");

        assertEquals(1, points.size());
        assertEquals(38.5, points.get(0).latitude, 1e-5);
    }

    @Test
    public void describeStatusGivesFriendlyMessages() {
        assertEquals("No driving route was found to this place.",
                DirectionsApiClient.describeStatus("ZERO_RESULTS"));
        assertEquals("Too many route requests right now. Please try again shortly.",
                DirectionsApiClient.describeStatus("OVER_QUERY_LIMIT"));
        assertEquals("Directions are unavailable right now.",
                DirectionsApiClient.describeStatus("REQUEST_DENIED"));
    }

    @Test
    public void describeStatusNeverLeaksRawCodes() {
        for (String status : new String[]{"UNKNOWN_ERROR", "MAX_WAYPOINTS_EXCEEDED", "", null}) {
            String message = DirectionsApiClient.describeStatus(status);
            assertFalse(message.contains("_"));
            assertEquals("Route unavailable. Please try again.", message);
        }
    }
}
