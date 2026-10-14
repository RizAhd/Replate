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

    // ── One vocabulary for every status: the words shown and the badge colour ──────────
    const ORDER_STATUS_VIEW = {
        pending_pickup:   { label: 'Pending pickup',   cls: 'badge-warning' },
        pending_delivery: { label: 'Pending delivery', cls: 'badge-warning' },
        confirmed:        { label: 'Confirmed',        cls: 'badge-primary' },
        preparing:        { label: 'Preparing',        cls: 'badge-primary' },
        ready:            { label: 'Ready for pickup', cls: 'badge-primary' },
        out_for_delivery: { label: 'Out for delivery', cls: 'badge-primary' },
        delivered:        { label: 'Delivered',        cls: 'badge-success' },
        completed:        { label: 'Completed',        cls: 'badge-success' },
        cancelled:        { label: 'Cancelled',        cls: 'badge-danger' }
    };

    /** { label, cls } for an order status. */
    function orderStatusView(status) {
        return ORDER_STATUS_VIEW[normalizeOrderStatus(status)];
    }

    // Picks the badge colour class for a status.
    function statusBadgeClass(status) {
        return orderStatusView(status).cls;
    }

    // Gives the words shown for a status.
    function statusLabel(status) {
        return orderStatusView(status).label;
    }

    const HOTEL_STATUS_VIEW = {
        approved:  { label: 'Approved',         cls: 'badge-success' },
        pending:   { label: 'Pending approval', cls: 'badge-warning' },
        rejected:  { label: 'Not approved',     cls: 'badge-danger' },
        suspended: { label: 'Suspended',        cls: 'badge-danger' }
    };

    /** { label, cls } for a hotel's approval status. */
    function hotelStatusView(raw) {
        return HOTEL_STATUS_VIEW[normalizeHotelStatus(raw)];
    }

    /**
     * What a listing looks like to customers, as { key, label, cls }:
     * active, soldout, hidden (by an admin) or inactive (hidden).
     */
    function listingState(item) {
        const it = item || {};
        const status = String(it.status || '').toLowerCase();
        if (it.suspendedByAdmin === true) return { key: 'hidden', label: 'Hidden by admin', cls: 'badge-neutral' };
        if (status === 'inactive') return { key: 'inactive', label: 'Hidden', cls: 'badge-neutral' };
        if (status === 'sold_out' || it.isAvailable === false || toNumber(it.quantity, 1) <= 0) {
            return { key: 'soldout', label: 'Sold out', cls: 'badge-warning' };
        }
        return { key: 'active', label: 'Active', cls: 'badge-success' };
    }

    /**
     * Payment as { label, cls }. Cash is paid at the counter, so a completed or delivered cash
     * order counts as paid even though the app leaves paymentStatus at "pending".
     */
    function paymentView(order) {
        const o = order || {};
        const raw = String(o.paymentStatus || '').toLowerCase().trim();
        const cash = String(o.paymentMethod || 'Cash').toLowerCase().includes('cash');
        if (raw === 'paid') return { label: 'Paid', cls: 'badge-success' };
        if (raw === 'failed') return { label: 'Failed', cls: 'badge-danger' };
        if (cash && isSuccessfulOrder(o)) return { label: 'Paid (cash)', cls: 'badge-success' };
        if (isCancelledOrder(o)) return { label: 'Not paid', cls: 'badge-neutral' };
        if (raw && raw !== 'pending') return { label: raw.charAt(0).toUpperCase() + raw.slice(1), cls: 'badge-neutral' };
        return { label: 'Pending', cls: 'badge-warning' };
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

    // Shows an amount with two decimals (plain digits, for CSV files).
    function formatAmount(val) {
        const n = parseFloat(val);
        return isNaN(n) ? '0.00' : n.toFixed(2);
    }

    // ── One money format and one date format for every page ───────────────────────────
    const MONEY = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const DATE_TIME = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false });
    const DATE_ONLY = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

    /** "6,340.00" (no currency). */
    function formatNumber2(val) {
        const n = parseFloat(val);
        return MONEY.format(Number.isFinite(n) ? n : 0);
    }

    /** "LKR 6,340.00" */
    function formatMoney(val) {
        return 'LKR ' + formatNumber2(val);
    }

    /** "14 Oct 2026, 12:27" ("—" when unknown). */
    function formatDateTime(ts) {
        const n = Number(ts);
        return n > 0 ? DATE_TIME.format(new Date(n)) : '—';
    }

    /** "14 Oct 2026" ("—" when unknown). */
    function formatDate(ts) {
        const n = Number(ts);
        return n > 0 ? DATE_ONLY.format(new Date(n)) : '—';
    }

    const ONE_DECIMAL = new Intl.NumberFormat('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    const PERCENT = new Intl.NumberFormat('en-US', { style: 'percent', maximumFractionDigits: 1 });
    const DAY_MONTH = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' });
    const WEEKDAY_DATE = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });

    /** A star rating with one decimal: "4.0". */
    function formatRating(val) {
        const n = parseFloat(val);
        return ONE_DECIMAL.format(Number.isFinite(n) ? n : 0);
    }

    /** A percentage given as a number of percent: 37.5 -> "37.5%", 50 -> "50%". */
    function formatPercent(pct) {
        const n = parseFloat(pct);
        return PERCENT.format((Number.isFinite(n) ? n : 0) / 100);
    }

    /** "14 Oct" */
    function formatDayMonth(ts) {
        return DAY_MONTH.format(new Date(Number(ts)));
    }

    /** "Wed 14 Oct" */
    function formatWeekdayDate(ts) {
        return WEEKDAY_DATE.format(new Date(Number(ts)));
    }

    /** "1 order", "3 orders" */
    function plural(count, one, many) {
        return count + ' ' + (count === 1 ? one : (many || one + 's'));
    }

    /** The order number people see: the last 8 characters of the order id, upper case ("#" is added by the page). Same rule as the app. */
    function shortOrderId(order, firebaseKey) {
        const o = order || {};
        const id = String(o.orderId || o.qrCode || o.pickupCode || firebaseKey || '');
        return (id.length > 8 ? id.substring(id.length - 8) : id).toUpperCase();
    }

    // ── Pickup times: one separator (an en dash) everywhere ─────────────────────────────
    /** "18:00 – 20:00" for a dish's pickup window; "—" when neither end is set. */
    function formatPickupWindow(start, end) {
        const a = String(start || '').trim();
        const b = String(end || '').trim();
        return a || b ? (a || '?') + ' – ' + (b || '?') : '—';
    }

    /** An order's pickup time as written by the app ("18:30", or "18:00 - 19:00" in older orders), with an en dash. */
    function formatPickupSlot(raw) {
        const text = String(raw || '').trim();
        return text.replace(/(\d{1,2}:\d{2})\s*(?:-|–|—|to)\s*(\d{1,2}:\d{2})/, '$1 – $2');
    }

    /** "18:00" (24-hour, local time). */
    const CLOCK = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false });
    function formatClock(ts) {
        return CLOCK.format(new Date(Number(ts)));
    }

    /** Length of a pickup slot: the QR code and pickup code work for one hour from the chosen time (app: PickupWindow.WINDOW_MINUTES). */
    const PICKUP_WINDOW_MINUTES = 60;
    /** Under this many minutes left, the countdown is shown as urgent. */
    const PICKUP_URGENT_MINUTES = 15;

    /**
     * When the customer's pickup slot opens: the time they chose (the first "HH:mm" in pickupTime) on the
     * slot's own day, or one hour before the code expires when the time cannot be read.
     */
    function pickupStartsAt(order, expiresAt) {
        const fallback = expiresAt - PICKUP_WINDOW_MINUTES * 60000;
        const match = /(\d{1,2}):(\d{2})/.exec(String((order && order.pickupTime) || ''));
        if (!match) return fallback;
        const hour = Number(match[1]);
        const minute = Number(match[2]);
        if (hour > 23 || minute > 59) return fallback;
        const start = new Date(fallback);
        start.setHours(hour, minute, 0, 0);
        return start.getTime() <= expiresAt ? start.getTime() : fallback;
    }

    /**
     * Time left to collect an open pickup order, in the same words as the app:
     *   before the slot   -> { text: "Pickup starts at 18:00", tone: 'upcoming' }
     *   during the slot   -> { text: "Collect within 42 min", tone: 'normal' | 'urgent' (under 15 min) }
     *   after the slot    -> { text: "Code expired", tone: 'expired' }
     * null for deliveries, finished or cancelled orders and orders without a recorded expiry.
     */
    function pickupCountdown(order, now = Date.now()) {
        const o = order || {};
        if (String(o.orderType || '').toLowerCase() === 'delivery' || !isOpenOrder(o)) return null;
        const expiresAt = Number(o.pickupExpiresAt);
        if (!Number.isFinite(expiresAt) || expiresAt <= 0) return null;
        if (now > expiresAt) return { text: 'Code expired', tone: 'expired', minutesLeft: 0 };
        const startsAt = pickupStartsAt(o, expiresAt);
        if (now < startsAt) return { text: 'Pickup starts at ' + formatClock(startsAt), tone: 'upcoming', minutesLeft: null };
        const minutesLeft = Math.max(1, Math.ceil((expiresAt - now) / 60000));
        return {
            text: 'Collect within ' + minutesLeft + ' min',
            tone: minutesLeft < PICKUP_URGENT_MINUTES ? 'urgent' : 'normal',
            minutesLeft
        };
    }

    // ── Impact estimate: the same numbers as the app (Profile > impact card) ─────────────
    /** Estimates, not measurements: about 0.4 kg of food per rescued meal, 2.5 kg CO2e per kg of food not wasted. */
    const IMPACT = Object.freeze({ KG_FOOD_PER_MEAL: 0.4, KG_CO2E_PER_KG_FOOD: 2.5 });

    /** { meals, foodKg, co2eKg }: meals = dishes in completed and delivered orders; the two weights are estimates. */
    function impactFromOrders(orders) {
        const meals = (orders || []).reduce((sum, order) =>
            sum + (order && isSuccessfulOrder(order) ? orderItemCount(order) : 0), 0);
        const foodKg = meals * IMPACT.KG_FOOD_PER_MEAL;
        return { meals, foodKg, co2eKg: foodKg * IMPACT.KG_CO2E_PER_KG_FOOD };
    }

    const KG = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });
    /** "11.6 kg", "29 kg", "1,160 kg" */
    function formatKg(kg) {
        const n = Number(kg);
        return KG.format(Number.isFinite(n) ? n : 0) + ' kg';
    }

    // ── "vs last week" on analytics cards ───────────────────────────────────────────────
    /**
     * This week compared with last week, as { dir: 'up' | 'down' | 'flat' | 'none', text }:
     * "12% vs last week", "New this week" (nothing last week), "Same as last week"; 'none' (no text) when both are 0.
     */
    function weekDelta(current, previous) {
        const cur = Number(current) || 0;
        const prev = Number(previous) || 0;
        if (cur === 0 && prev === 0) return { dir: 'none', text: '' };
        if (prev === 0) return { dir: 'up', text: 'New this week' };
        const pct = Math.round(((cur - prev) / prev) * 100);
        if (pct === 0) return { dir: 'flat', text: 'Same as last week' };
        return { dir: pct > 0 ? 'up' : 'down', text: formatPercent(Math.abs(pct)) + ' vs last week' };
    }

    // ── Relative time, long style: "just now", "5 minutes ago", "1 hour ago", then the date ──
    const RELATIVE = new Intl.RelativeTimeFormat('en', { numeric: 'always', style: 'long' });
    function formatRelative(ts, now = Date.now()) {
        const t = Number(ts);
        if (!(t > 0)) return '';
        const seconds = Math.floor((now - t) / 1000);
        if (seconds < 60) return 'just now';
        if (seconds < 3600) return RELATIVE.format(-Math.floor(seconds / 60), 'minute');
        if (seconds < 86400) return RELATIVE.format(-Math.floor(seconds / 3600), 'hour');
        return formatDate(t);
    }

    // ── New-order alerts for hotels ─────────────────────────────────────────────────────
    /** True for an order the hotel has not acted on yet (pending pickup or pending delivery). */
    function isNewOrder(order) {
        const s = normalizeOrderStatus(order && order.orderStatus);
        return s === 'pending_pickup' || s === 'pending_delivery';
    }

    /** The rows ({ key, order }) that were not in knownKeys and still wait for the hotel: the orders to announce. */
    function newPendingOrders(knownKeys, rows) {
        const known = knownKeys instanceof Set ? knownKeys : new Set(knownKeys || []);
        return (rows || []).filter((row) => row && !known.has(row.key) && isNewOrder(row.order));
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

    /** First moment of 'today' | '7d' | '30d' (calendar days, today included); 0 for 'all'. */
    function rangeStart(rangeKey, now = Date.now()) {
        const days = { today: 1, '7d': 7, '30d': 30 }[rangeKey];
        if (!days) return 0;
        const start = new Date(now);
        start.setHours(0, 0, 0, 0);
        start.setDate(start.getDate() - (days - 1));
        return start.getTime();
    }

    /** True when the row was created inside 'today' | '7d' | '30d' | 'all' (calendar days, today included). */
    function withinRange(order, rangeKey, now = Date.now()) {
        const start = rangeStart(rangeKey, now);
        return !start || placedAt(order) >= start;
    }

    // ── Weekly charts: one point per calendar week, empty weeks included ───────────────
    /** Monday 00:00 (local time) of the week a moment falls in. */
    function weekStart(ts) {
        const d = new Date(ts);
        d.setHours(0, 0, 0, 0);
        d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
        return d.getTime();
    }

    /**
     * Orders and revenue per week, oldest first, from the week of `from` (0: the oldest order) to
     * this week. Weeks with nothing in them are there with 0, so a chart's time line never skips.
     * Each week is { start, label: "6 Oct" (its Monday), orders, revenue }. An order counts in the
     * week it was placed; revenue only from completed and delivered orders.
     */
    function weeklySeries(orders, from = 0, now = Date.now()) {
        const list = (orders || []).filter(Boolean);
        let first = Number(from) || 0;
        if (!first) first = list.reduce((min, o) => Math.min(min, placedAt(o) || now), now);
        const weeks = [];
        const index = new Map();
        const last = weekStart(now);
        for (let start = weekStart(Math.min(first, now)); start <= last;) {
            index.set(start, weeks.length);
            weeks.push({ start, label: DAY_MONTH.format(new Date(start)), orders: 0, revenue: 0 });
            const next = new Date(start);
            next.setDate(next.getDate() + 7); // calendar step: stays on Monday 00:00 across clock changes
            start = next.getTime();
        }
        list.forEach((order) => {
            const i = index.get(weekStart(Math.min(placedAt(order) || now, now)));
            if (i === undefined) return;
            weeks[i].orders++;
            if (isSuccessfulOrder(order)) weeks[i].revenue += orderAmount(order);
        });
        return weeks;
    }

    // ── Hotel orders list: what needs doing now, and what is finished ──────────────────
    /**
     * 'history' for finished orders (completed, delivered, cancelled) and for pickups whose slot has
     * ended (expired; they keep their Done and Cancel buttons there); 'active' for everything else.
     */
    function orderGroup(order, now = Date.now()) {
        if (!isOpenOrder(order)) return 'history';
        return isPickupExpired(order, now) ? 'history' : 'active';
    }

    /**
     * When an open order is due: the end of its pickup slot, or the moment it was placed for a
     * delivery or an older order without a slot (those are due straight away).
     */
    function orderDueAt(order) {
        const slotEnd = Number(order && order.pickupExpiresAt);
        const isDelivery = String((order && order.orderType) || '').toLowerCase() === 'delivery';
        return !isDelivery && slotEnd > 0 ? slotEnd : placedAt(order);
    }

    /** Sort order for the Active list: the soonest pickup first. Rows are { key, order }. */
    function compareActive(a, b) {
        return orderDueAt(a.order) - orderDueAt(b.order) || placedAt(a.order) - placedAt(b.order);
    }

    /** Sort order for the History list: the newest order first. Rows are { key, order }. */
    function compareHistory(a, b) {
        return placedAt(b.order) - placedAt(a.order);
    }

    /** True when an order matches the search: customer name, phone, order id or pickup code ("#" optional). */
    function orderMatchesSearch(order, key, query) {
        const q = String(query || '').trim().toLowerCase().replace(/^#/, '');
        if (!q) return true;
        const o = order || {};
        const fields = [o.customerName, key, o.orderId, o.qrCode, o.pickupCode, o.otpCode, o.customerPhone];
        if (fields.some((v) => String(v || '').toLowerCase().includes(q))) return true;
        // a phone number typed with spaces or dashes ("077 111 2222") still matches
        const digits = q.replace(/\D/g, '');
        return /^[\d\s()+-]+$/.test(q) && digits.length >= 3 &&
            String(o.customerPhone || '').replace(/\D/g, '').includes(digits);
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
        rangeStart,
        withinRange,
        weekStart,
        weeklySeries,
        orderGroup,
        orderDueAt,
        compareActive,
        compareHistory,
        orderMatchesSearch,
        averageRating,
        hotelPerformance,
        cancelledReport,
        csvEscape,
        toCsv,
        scopedList,
        hotelOrders,
        hotelFoodItems,
        normalizeOrderStatus,
        orderStatusView,
        statusBadgeClass,
        statusLabel,
        hotelStatusView,
        listingState,
        paymentView,
        extractItemNames,
        extractItemsArray,
        formatAmount,
        formatNumber2,
        formatMoney,
        formatDateTime,
        formatDate,
        formatRating,
        formatPercent,
        formatDayMonth,
        formatWeekdayDate,
        plural,
        shortOrderId,
        formatPickupWindow,
        formatPickupSlot,
        formatClock,
        PICKUP_WINDOW_MINUTES,
        PICKUP_URGENT_MINUTES,
        pickupCountdown,
        IMPACT,
        impactFromOrders,
        formatKg,
        weekDelta,
        formatRelative,
        isNewOrder,
        newPendingOrders,
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
