/**
 * REPLATE Data Schema Helpers
 * Shared utilities for normalizing Firebase data across all web panels.
 */

const ReplateSchema = (() => {

    /**
     * Normalise every possible order-status variant to one of:
     *   pending_pickup | completed | cancelled
     */
    function normalizeOrderStatus(raw) {
        if (!raw) return 'pending_pickup';
        const s = String(raw).toLowerCase().trim();
        if (s === 'completed' || s === 'complete' || s === 'done') return 'completed';
        if (s === 'cancelled' || s === 'canceled') return 'cancelled';
        return 'pending_pickup';
    }

    /** Bootstrap badge class for a normalised status. */
    function statusBadgeClass(status) {
        switch (normalizeOrderStatus(status)) {
            case 'completed': return 'badge-success';
            case 'cancelled': return 'badge-danger';
            default:          return 'badge-warning';
        }
    }

    /** Human-readable label for a normalised status. */
    function statusLabel(status) {
        switch (normalizeOrderStatus(status)) {
            case 'completed': return '✅ Completed';
            case 'cancelled': return '❌ Cancelled';
            default:          return '⏳ Pending Pickup';
        }
    }

    /** Bootstrap bg-* class */
    function statusBgClass(status) {
        switch (normalizeOrderStatus(status)) {
            case 'completed': return 'bg-success';
            case 'cancelled': return 'bg-danger';
            default:          return 'bg-warning text-dark';
        }
    }

    /**
     * Extract a display-friendly list of item names from an order object.
     * Firebase can store items as an Array OR a plain Object (numeric keys).
     */
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

    /** Extract items as an array regardless of Firebase storage form. */
    function extractItemsArray(order) {
        const items = order.items;
        if (!items) return [];
        if (Array.isArray(items)) return items;
        if (typeof items === 'object') return Object.values(items);
        return [];
    }

    /** Safe number formatter — handles String/Number ambiguity from Firebase. */
    function formatAmount(val) {
        const n = parseFloat(val);
        return isNaN(n) ? '0.00' : n.toFixed(2);
    }

    /** Short display ID for an order — last 8 chars of key or orderId field. */
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
        // New schema: hotels keyed by hotel id and linked via userId.
        const byUser = await database.ref('hotels').orderByChild('userId').equalTo(uid).once('value');
        if (byUser.exists()) {
            const [key, value] = Object.entries(byUser.val())[0];
            return { key, value };
        }

        // Backward compatibility for old builds where hotel node key == uid.
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
            // Keep legacy field for older pages.
            license: licenseUrl,
            searchRegion: toSearchRegion(formData.address),
            createdAt: Date.now(),
            registeredAt: new Date().toISOString()
        };
    }

    return {
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
        toNumber,
        toSearchRegion,
        findHotelByUserId,
        buildHotelPayload
    };
})();
