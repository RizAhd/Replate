
const ReplateSchema = (() => {

    function normalizeOrderStatus(raw) {
        if (!raw) return 'pending_pickup';
        const s = String(raw).toLowerCase().trim();
        if (s === 'completed' || s === 'complete' || s === 'done') return 'completed';
        if (s === 'cancelled' || s === 'canceled') return 'cancelled';
        if (s === 'delivered') return 'delivered';
        if (s === 'out_for_delivery') return 'out_for_delivery';
        if (s === 'preparing') return 'preparing';
        if (s === 'confirmed') return 'confirmed';
        if (s === 'ready') return 'ready';
        if (s === 'pending_delivery') return 'pending_delivery';
        if (s === 'pending' || s === 'pending_pickup') return 'pending_pickup';
        return 'pending_pickup';
    }

    function statusBadgeClass(status) {
        const s = normalizeOrderStatus(status);
        switch (s) {
            case 'completed':
            case 'delivered':
                return 'badge-success';
            case 'cancelled':
                return 'badge-danger';
            case 'confirmed':
            case 'preparing':
            case 'ready':
            case 'out_for_delivery':
                return 'badge-primary';
            default:
                return 'badge-warning';
        }
    }

    function statusLabel(status) {
        const s = normalizeOrderStatus(status);
        switch (s) {
            case 'pending_pickup':     return 'Pending Pickup';
            case 'pending_delivery':   return 'Pending Delivery';
            case 'confirmed':          return 'Confirmed';
            case 'preparing':          return 'Preparing';
            case 'ready':              return 'Ready for Pickup';
            case 'out_for_delivery':   return 'Out for Delivery';
            case 'delivered':          return 'Delivered';
            case 'completed':          return 'Completed';
            case 'cancelled':          return 'Cancelled';
            default:                   return 'Pending';
        }
    }

    function statusBgClass(status) {
        const s = normalizeOrderStatus(status);
        switch (s) {
            case 'completed':
            case 'delivered':
                return 'bg-success';
            case 'cancelled':
                return 'bg-danger';
            case 'confirmed':
            case 'preparing':
            case 'ready':
            case 'out_for_delivery':
                return 'bg-primary';
            default:
                return 'bg-warning text-dark';
        }
    }

    function extractItemNames(order) {
        const items = order.items;
        if (!items) return 'N/A';
        if (Array.isArray(items)) {
            return items.map(i => `${i.foodName || 'Item'} ×${i.quantity || 1}`).join(', ');
        }
        if (typeof items === 'object') {
            return Object.values(items)
                .map(i => `${i.foodName || 'Item'} ×${i.quantity || 1}`)
                .join(', ');
        }
        return 'N/A';
    }

    function extractItemsArray(order) {
        const items = order.items;
        if (!items) return [];
        if (Array.isArray(items)) return items;
        if (typeof items === 'object') return Object.values(items);
        return [];
    }

    function formatAmount(val) {
        const n = parseFloat(val);
        return isNaN(n) ? '0.00' : n.toFixed(2);
    }

    function shortOrderId(order, firebaseKey) {
        const id = order.orderId || order.qrCode || order.pickupCode || firebaseKey || '';
        return id.length > 8 ? id.substring(id.length - 8) : id;
    }

    function normalizeUserRole(userLike) {
        const raw = userLike?.role || userLike?.userType || 'customer';
        const role = String(raw).toLowerCase().trim();
        if (role === 'admin' || role === 'hotel' || role === 'customer') return role;
        return 'customer';
    }

    function normalizeHotelStatus(raw) {
        if (!raw) return 'pending';
        const s = String(raw).toLowerCase().trim();
        if (s === 'approved' || s === 'active') return 'approved';
        if (s === 'rejected' || s === 'declined') return 'rejected';
        return 'pending';
    }

    /**
     * True when a pickup order's QR code and OTP have expired. The app stores `pickupExpiresAt`
     * (end of the one-hour pickup slot) at checkout; orders without it never expire on their own.
     */
    function isPickupExpired(order, now = Date.now()) {
        const expiresAt = Number(order && order.pickupExpiresAt);
        if (!Number.isFinite(expiresAt) || expiresAt <= 0) return false;
        const isDelivery = String((order && order.orderType) || '').toLowerCase() === 'delivery';
        return !isDelivery && now > expiresAt;
    }

    function toNumber(val, fallback = 0) {
        const n = Number(val);
        return Number.isFinite(n) ? n : fallback;
    }

    function toSearchRegion(text) {
        if (!text) return 'unknown_region';
        return String(text)
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '_')
            .replace(/^_+|_+$/g, '') || 'unknown_region';
    }

    async function findHotelByUserId(database, uid) {
        const byUser = await database.ref('hotels').orderByChild('userId').equalTo(uid).once('value');
        if (byUser.exists()) {
            const [key, value] = Object.entries(byUser.val())[0];
            return { key, value };
        }

        const legacy = await database.ref('hotels/' + uid).once('value');
        if (legacy.exists()) {
            return { key: uid, value: legacy.val() };
        }

        return null;
    }

    function buildHotelPayload(formData, uid, licenseUrl) {
        return {
            hotelName: formData.hotelName,
            ownerName: formData.ownerName,
            email: formData.email,
            phone: formData.phone,
            address: formData.address,
            latitude: toNumber(formData.latitude),
            longitude: toNumber(formData.longitude),
            userId: uid,
            status: 'pending',
            rejectionReason: '',
            licenseUrl,
            license: licenseUrl,
            searchRegion: toSearchRegion(formData.address),
            createdAt: Date.now(),
            registeredAt: new Date().toISOString()
        };
    }

    /**
     * Rows under `path` whose `childKey` equals one of the given ids (one query per id, merged).
     * The database rules only let a hotel read its own orders and listings by running these queries,
     * never by downloading the whole list. The result behaves like a snapshot
     * (forEach / exists / numChildren).
     *   scopedList(database, 'orders', 'hotelId', ids).on(cb[, errCb]) -> function that stops listening
     *   scopedList(database, 'orders', 'hotelId', ids).once()          -> Promise of the merged snapshot
     */
    function scopedList(database, path, childKey, ids) {
        const list = Array.from(new Set(Array.from(ids || []).map(String).filter(Boolean)));
        const queries = list.map((id) => database.ref(path).orderByChild(childKey).equalTo(id));

        function merge(snaps) {
            const seen = new Set();
            const items = [];
            snaps.forEach((snap) => {
                if (!snap) return;
                snap.forEach((child) => {
                    if (!seen.has(child.key)) {
                        seen.add(child.key);
                        items.push(child);
                    }
                });
            });
            return {
                forEach(fn) {
                    for (const child of items) {
                        if (fn(child) === true) return true;
                    }
                    return false;
                },
                exists() { return items.length > 0; },
                numChildren() { return items.length; }
            };
        }

        return {
            once() {
                return Promise.all(queries.map((q) => q.once('value'))).then(merge);
            },
            on(callback, errorCallback) {
                if (queries.length === 0) {
                    callback(merge([]));
                    return () => {};
                }
                const latest = new Array(queries.length).fill(null);
                const handlers = queries.map((q, i) => q.on('value', (snap) => {
                    latest[i] = snap;
                    if (latest.every(Boolean)) callback(merge(latest));
                }, errorCallback));
                return () => queries.forEach((q, i) => q.off('value', handlers[i]));
            }
        };
    }

    /** A hotel's own orders. */
    function hotelOrders(database, ids) {
        return scopedList(database, 'orders', 'hotelId', ids);
    }

    /** A hotel's own food listings. */
    function hotelFoodItems(database, ids) {
        return scopedList(database, 'foodItems', 'hotelId', ids);
    }

    return {
        scopedList,
        hotelOrders,
        hotelFoodItems,
        normalizeOrderStatus,
        statusBadgeClass,
        statusLabel,
        statusBgClass,
        extractItemNames,
        extractItemsArray,
        formatAmount,
        shortOrderId,
        normalizeUserRole,
        normalizeHotelStatus,
        isPickupExpired,
        toNumber,
        toSearchRegion,
        findHotelByUserId,
        buildHotelPayload
    };
})();
