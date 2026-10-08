package com.replate.app.notifications;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

// Passes new notifications to any screen that is listening.
public final class InAppNotificationCenter {

    public interface Listener {
        void onInAppNotification(InAppNotificationMessage message);
    }

    // One notification shown to the user while the app is open.
    public static final class InAppNotificationMessage {
        public final String title;
        public final String body;
        public final String category;
        public final String route;
        public final String orderId;
        public final String hotelId;
        public final String deepLink;
        public final Map<String, String> rawData;

        public InAppNotificationMessage(String title,
                                        String body,
                                        String category,
                                        String route,
                                        String orderId,
                                        String hotelId,
                                        String deepLink,
                                        Map<String, String> rawData) {
            this.title = title;
            this.body = body;
            this.category = NotificationCategories.normalize(category);
            this.route = route == null ? "notifications" : route;
            this.orderId = orderId == null ? "" : orderId;
            this.hotelId = hotelId == null ? "" : hotelId;
            this.deepLink = deepLink == null ? "" : deepLink;
            this.rawData = rawData;
        }
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private InAppNotificationCenter() {}

    // Starts sending notifications to this listener.
    public static void register(Listener listener) {
        if (listener == null) return;
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    // Stops sending notifications to this listener.
    public static void unregister(Listener listener) {
        if (listener == null) return;
        listeners.remove(listener);
    }

    // True when some screen is listening.
    public static boolean hasActiveListeners() {
        return !listeners.isEmpty();
    }

    // Sends the notification to every listener.
    public static void publish(InAppNotificationMessage message) {
        if (message == null) return;
        Iterator<Listener> iterator = listeners.iterator();
        while (iterator.hasNext()) {
            Listener listener = iterator.next();
            if (listener != null) {
                listener.onInAppNotification(message);
            }
        }
    }
}
