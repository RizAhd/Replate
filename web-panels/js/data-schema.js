// Shared order, hotel and report rules used by every admin and hotel page.

const ReplateSchema = (() => {

    // Turns any old or new status text into one known status.
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

    // Picks the badge colour class for a status.
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

    // Gives the words shown for a status.
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

    // Picks the background colour class for a status.
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

    // Makes text like "Rice x2, Soup x1" from the order items.
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

    // Returns the order items as a plain list.
    function extractItemsArray(order) {
        const items = order.items;
        if (!items) return [];
        if (Array.isArray(items)) return items;
        if (typeof items === 'object') return Object.values(items);
        return [];
    }

    // ── One definition of "money earned" and "pending", used by every panel ──────────────
    // An order earns revenue and counts as food rescued only once it was really handed over
    // (completed for pickup, delivered for delivery). Cancelled orders count for nothing and every
    // other state (pending, confirmed, preparing, ready, out for delivery) is still pending.
    function isSuccessfulOrder(order) {
        const s = normalizeOrderStatus(order && order.orderStatus);
        return s === 'completed' || s === 'delivered';
    }

    // True when the order was cancelled.
    function isCancelledOrder(order) {
        return normalizeOrderStatus(order && order.orderStatus) === 'cancelled';
    }

    // True when the order is neither finished nor cancelled.
    function isOpenOrder(order) {
        return !isSuccessfulOrder(order) && !isCancelledOrder(order);
    }

    // Reads the order total as a number, 0 if missing.
    function orderAmount(order) {
        const n = parseFloat((order && (order.totalAmount || order.totalPrice)) || 0);
        return Number.isFinite(n) ? n : 0;
    }

    // Reads an item's quantity as a number.
    function itemQuantity(item) {
        const q = parseInt(item && item.quantity, 10);
        return q > 0 ? q : 1;
    }

    /** Total number of dishes in an order (a missing quantity counts as 1). */
    function orderItemCount(order) {
        return extractItemsArray(order || {}).reduce((sum, item) => sum + itemQuantity(item), 0);
    }

    // Turns a time into a day text like 2026-10-05.
    function dayKey(ts) {
        const d = new Date(ts);
        return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }

    /** When an order earned its money: the day the hotel completed it (falls back to the last update). */
    function earnedAt(order) {
        return Number(order && order.completedAt) || Number(order && order.updatedAt) || Number(order && order.createdAt) || 0;
    }

    /**
     * Earnings per day for the last `days` days, oldest first. Only completed and delivered orders
     * count, on the day they were completed.
     */
    function dailyEarnings(orders, days = 7, now = Date.now()) {
        const keys = [];
        const totals = {};
        for (let i = days - 1; i >= 0; i--) {
            const d = new Date(now);
            d.setDate(d.getDate() - i);
            const key = dayKey(d.getTime());
            keys.push(key);
            totals[key] = 0;
        }
        (orders || []).forEach((order) => {
            if (!isSuccessfulOrder(order)) return;
            const key = dayKey(earnedAt(order));
            if (key in totals) totals[key] += orderAmount(order);
        });
        return keys.map((key) => ({ key, amount: totals[key] }));
    }

    /** The dishes people order most (quantities added up, cancelled orders ignored), best first. */
    function popularItems(orders, limit = 5) {
        const counts = {};
        (orders || []).forEach((order) => {
            if (isCancelledOrder(order)) return;
            extractItemsArray(order).forEach((item) => {
                const name = String((item && item.foodName) || 'Item').trim() || 'Item';
                counts[name] = (counts[name] || 0) + itemQuantity(item);
            });
        });
        return Object.entries(counts)
            .map(([name, quantity]) => ({ name, quantity }))
            .sort((a, b) => b.quantity - a.quantity || a.name.localeCompare(b.name))
            .slice(0, limit);
    }

    // Shows an amount with two decimals.
    function formatAmount(val) {
        const n = parseFloat(val);
        return isNaN(n) ? '0.00' : n.toFixed(2);
    }

    // Shortens the order id to its last 8 characters.
    function shortOrderId(order, firebaseKey) {
        const id = order.orderId || order.qrCode || order.pickupCode || firebaseKey || '';
        return id.length > 8 ? id.substring(id.length - 8) : id;
    }

    // Returns admin, hotel or customer; anything else is customer.
    function normalizeUserRole(userLike) {
        const raw = userLike?.role || userLike?.userType || 'customer';
        const role = String(raw).toLowerCase().trim();
        if (role === 'admin' || role === 'hotel' || role === 'customer') return role;
        return 'customer';
    }

    // Returns pending, approved, rejected or suspended.
    function normalizeHotelStatus(raw) {
        if (!raw) return 'pending';
        const s = String(raw).toLowerCase().trim();
        if (s === 'approved' || s === 'active') return 'approved';
        if (s === 'rejected' || s === 'declined') return 'rejected';
        if (s === 'suspended') return 'suspended';
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

    // Turns a value into a number, or the fallback.
    function toNumber(val, fallback = 0) {
        const n = Number(val);
        return Number.isFinite(n) ? n : fallback;
    }

    // Makes a simple lowercase region key from a place name.
    function toSearchRegion(text) {
        if (!text) return 'unknown_region';
        return String(text)
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '_')
            .replace(/^_+|_+$/g, '') || 'unknown_region';
    }

    // Finds the hotel owned by this user, new or old style.
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

    /** The hotel record. The licence link is stored separately (hotelLicenses/{key}) because every signed-in user can read this node. */
    function buildHotelPayload(formData, uid) {
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

        // Joins the results of several queries without repeats.
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

    // ── Reporting helpers (admin analytics and reports) ─────────────────────────────────
    /** True for an unfinished pickup order whose QR code and OTP have run out (nobody collected it). */
    function isExpiredPickupOrder(order, now = Date.now()) {
        return isOpenOrder(order) && isPickupExpired(order, now);
    }

    /** When an order was placed (older orders used `timestamp`); 0 when unknown. */
    function placedAt(order) {
        return Number(order && (order.createdAt || order.timestamp)) || 0;
    }

    /** True when the row was created inside 'today' | '7d' | '30d' | 'all' (calendar days, today included). */
    function withinRange(order, rangeKey, now = Date.now()) {
        const days = { today: 1, '7d': 7, '30d': 30 }[rangeKey];
        if (!days) return true;
        const start = new Date(now);
        start.setHours(0, 0, 0, 0);
        start.setDate(start.getDate() - (days - 1));
        return placedAt(order) >= start.getTime();
    }

    /** Average star rating, counting one rating per order (an order review and its per-dish copies count once). */
    function averageRating(reviews) {
        const seen = new Set();
        let sum = 0, count = 0;
        (reviews || []).forEach((review, i) => {
            const rating = Number(review && review.rating);
            if (!Number.isFinite(rating) || rating <= 0 || rating > 5) return;
            const key = review.orderId ? 'o:' + review.orderId : 'r:' + (review.id || i);
            if (seen.has(key)) return;
            seen.add(key);
            sum += rating;
            count++;
        });
        return { average: count ? sum / count : 0, count };
    }

    /**
     * One row per hotel that has orders, best revenue first. Revenue counts completed and delivered
     * orders only. Ratings come from that hotel's reviews (matched by hotelId, or through the order).
     */
    function hotelPerformance(orders, reviews) {
        const rows = Object.create(null);
        const hotelByOrder = Object.create(null);
        (orders || []).forEach((order) => {
            if (!order) return;
            const id = String(order.hotelId || order.hotelName || 'unknown');
            if (order.orderId) hotelByOrder[order.orderId] = id;
            const row = rows[id] || (rows[id] = {
                hotelId: id, hotelName: '', orders: 0, completed: 0, cancelled: 0,
                cancelRatePct: 0, revenue: 0, avgRating: 0, ratingCount: 0
            });
            if (!row.hotelName && order.hotelName) row.hotelName = order.hotelName;
            row.orders++;
            if (isSuccessfulOrder(order)) { row.completed++; row.revenue += orderAmount(order); }
            else if (isCancelledOrder(order)) row.cancelled++;
        });

        const byHotel = Object.create(null);
        (reviews || []).forEach((review) => {
            if (!review) return;
            const id = String(review.hotelId || hotelByOrder[review.orderId] || '');
            if (id in rows) (byHotel[id] = byHotel[id] || []).push(review);
        });

        return Object.values(rows).map((row) => {
            const rating = averageRating(byHotel[row.hotelId]);
            row.hotelName = row.hotelName || 'Unknown';
            row.cancelRatePct = Math.round((row.cancelled / row.orders) * 1000) / 10;
            row.avgRating = rating.average;
            row.ratingCount = rating.count;
            return row;
        }).sort((a, b) => b.revenue - a.revenue || b.orders - a.orders || a.hotelName.localeCompare(b.hotelName));
    }

    /**
     * Cancelled orders and expired pickups as { kind, order }, newest first. A cancelled order with
     * no cancelledBy was cancelled by the customer (the app does not set the field).
     */
    function cancelledReport(orders, now = Date.now()) {
        const rows = [];
        (orders || []).forEach((order) => {
            if (!order) return;
            let kind = '';
            if (isCancelledOrder(order)) {
                kind = String(order.cancelledBy || '').toLowerCase() === 'hotel' ? 'Cancelled by hotel' : 'Cancelled by customer';
            } else if (isExpiredPickupOrder(order, now)) {
                kind = 'Expired pickup';
            }
            if (kind) rows.push({ kind, order });
        });
        return rows.sort((a, b) => placedAt(b.order) - placedAt(a.order));
    }

    /** One CSV cell: quoted when needed; text starting with = + - @ gets a leading quote so spreadsheets never run it as a formula. */
    function csvEscape(value) {
        let text = value === null || value === undefined ? '' : String(value);
        if (typeof value !== 'number' && /^[=+\-@\t\r]/.test(text)) text = "'" + text;
        return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text;
    }

    /** rows: objects; columns: [{ label, key | get(row) }]. Returns CSV text with a header line. */
    function toCsv(rows, columns) {
        const header = columns.map((c) => csvEscape(c.label)).join(',');
        const lines = (rows || []).map((row) => columns.map((c) =>
            csvEscape(typeof c.get === 'function' ? c.get(row) : row[c.key])).join(','));
        return [header].concat(lines).join('\r\n');
    }

    return {
        isExpiredPickupOrder,
        placedAt,
        withinRange,
        averageRating,
        hotelPerformance,
        cancelledReport,
        csvEscape,
        toCsv,
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
        isSuccessfulOrder,
        isCancelledOrder,
        isOpenOrder,
        orderAmount,
        itemQuantity,
        orderItemCount,
        dayKey,
        earnedAt,
        dailyEarnings,
        popularItems,
        toNumber,
        toSearchRegion,
        findHotelByUserId,
        buildHotelPayload
    };
})();
