/**
 * Notification API helper for admin/hotel web panels.
 * Calls secure Firebase Functions endpoints with current Firebase ID token.
 */
const NotificationApi = (() => {
    async function call(functionName, payload) {
        const user = auth.currentUser;
        if (!user) throw new Error("Not authenticated");

        const idToken = await user.getIdToken();
        const base = (ENV.FUNCTIONS_BASE_URL || "").replace(/\/$/, "");
        if (!base) throw new Error("FUNCTIONS_BASE_URL is not configured");

        let res;
        try {
            res = await fetch(`${base}/${functionName}`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${idToken}`
                },
                body: JSON.stringify(payload || {})
            });
        } catch (networkError) {
            // The request never reached the server (offline, blocked, wrong URL)
            const err = new Error("Could not reach the notification service. Check your connection.");
            err.status = 0;
            throw err;
        }

        let body = {};
        try {
            body = await res.json();
        } catch (e) {
            body = { success: false, message: "Invalid server response" };
        }

        if (!res.ok || body.success === false) {
            const err = new Error(body.message || `Request failed (${res.status})`);
            err.status = res.status;
            throw err;
        }

        return body;
    }

    function normalizePayload(payload, defaults = {}) {
        return {
            category: "admin_broadcast",
            route: "notifications",
            ...defaults,
            ...(payload || {})
        };
    }

    /**
     * Only when the service itself is missing or unreachable is it safe to write the notifications
     * directly. After any other failure (for example a 500 once some pushes were already sent) a
     * direct write would deliver everything twice.
     */
    function canFallBack(err) {
        const status = err && err.status;
        return status === 0 || status === 404 || status === 502 || status === 503 || status === 504;
    }

    return {
        canFallBack,
        sendBroadcast: (payload) => call("sendBroadcastNotification", normalizePayload(payload, {
            category: "admin_broadcast"
        })),
        sendTargeted: (payload) => call("sendTargetedNotification", normalizePayload(payload)),
        // Admin only: disable or enable a user's account (Users page)
        setUserSuspended: (uid, suspended, reason) => call("setUserSuspended", { uid, suspended: !!suspended, reason: reason || "" }),
        sendDeal: (payload) => call("sendDealNotification", normalizePayload(payload, {
            category: payload && payload.isHotDeal ? "hot_deals" : "new_deals",
            route: "deals"
        })),
        sendOrderAccepted: (userIds, orderId, hotelId) => call("sendTargetedNotification", {
            userIds,
            title: "Order accepted",
            body: "Your order was accepted by the hotel.",
            category: "order_accepted",
            type: "order_status",
            route: "order",
            orderId,
            hotelId
        }),
        sendOrderCancelled: (userIds, orderId, hotelId) => call("sendTargetedNotification", {
            userIds,
            title: "Order cancelled",
            body: "Your order has been cancelled.",
            category: "order_cancelled",
            type: "order_status",
            route: "order",
            orderId,
            hotelId
        }),
        sendPickupReady: (userIds, orderId, hotelId) => call("sendTargetedNotification", {
            userIds,
            title: "Pickup ready",
            body: "Your food is ready for pickup.",
            category: "pickup_ready",
            type: "order_status",
            route: "order",
            orderId,
            hotelId
        })
    };
})();
