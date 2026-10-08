/**
 * Checks the shared order rules in web-panels/js/data-schema.js (no browser or emulator needed).
 * These decide what counts as revenue, "pending" and "food rescued" on every admin and hotel page.
 * Run:  node scripts/test-schema.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

const code = fs.readFileSync(path.join(__dirname, '..', 'web-panels', 'js', 'data-schema.js'), 'utf8');
const S = vm.runInNewContext(code + '\nReplateSchema', {});

let passed = 0;
function test(name, fn) {
    try { fn(); passed++; console.log('  ok   ', name); }
    catch (e) { console.log('  FAIL ', name, '->', e.message); process.exitCode = 1; }
}

test('completed and delivered orders are successful', () => {
    assert.strictEqual(S.isSuccessfulOrder({ orderStatus: 'completed' }), true);
    assert.strictEqual(S.isSuccessfulOrder({ orderStatus: 'delivered' }), true);
});

test('unfinished states are not revenue', () => {
    ['pending_pickup', 'pending_delivery', 'confirmed', 'preparing', 'ready', 'out_for_delivery']
        .forEach((status) => assert.strictEqual(S.isSuccessfulOrder({ orderStatus: status }), false, status));
});

test('cancelled orders are neither successful nor open', () => {
    assert.strictEqual(S.isSuccessfulOrder({ orderStatus: 'cancelled' }), false);
    assert.strictEqual(S.isOpenOrder({ orderStatus: 'cancelled' }), false);
});

test('every unfinished state counts as pending (open)', () => {
    ['pending_pickup', 'pending_delivery', 'confirmed', 'preparing', 'ready', 'out_for_delivery']
        .forEach((status) => assert.strictEqual(S.isOpenOrder({ orderStatus: status }), true, status));
    assert.strictEqual(S.isOpenOrder({ orderStatus: 'completed' }), false);
    assert.strictEqual(S.isOpenOrder({ orderStatus: 'delivered' }), false);
});

test('order amount reads totalAmount, then totalPrice, and never returns NaN', () => {
    assert.strictEqual(S.orderAmount({ totalAmount: '250.50' }), 250.5);
    assert.strictEqual(S.orderAmount({ totalPrice: 100 }), 100);
    assert.strictEqual(S.orderAmount({ totalAmount: 'abc' }), 0);
    assert.strictEqual(S.orderAmount({}), 0);
});

test('item count adds quantities and treats a missing quantity as one', () => {
    const order = { items: [{ quantity: 2 }, { quantity: '3' }, {}] };
    assert.strictEqual(S.orderItemCount(order), 6);
    assert.strictEqual(S.orderItemCount({ items: { a: { quantity: 4 } } }), 4);
    assert.strictEqual(S.orderItemCount({}), 0);
});

test('pickup expiry only applies to active pickup orders with a recorded expiry', () => {
    const now = 1_000_000;
    assert.strictEqual(S.isPickupExpired({ pickupExpiresAt: now - 1 }, now), true);
    assert.strictEqual(S.isPickupExpired({ pickupExpiresAt: now + 1 }, now), false);
    assert.strictEqual(S.isPickupExpired({}, now), false);
    assert.strictEqual(S.isPickupExpired({ pickupExpiresAt: now - 1, orderType: 'Delivery' }, now), false);
});

test('hotel status labels: blank and legacy values land in a real tab', () => {
    assert.strictEqual(S.normalizeHotelStatus(''), 'pending');
    assert.strictEqual(S.normalizeHotelStatus('active'), 'approved');
    assert.strictEqual(S.normalizeHotelStatus('declined'), 'rejected');
    assert.strictEqual(S.normalizeHotelStatus('suspended'), 'suspended');
});

test('daily earnings add completed orders on the day they were completed, newest day last', () => {
    const now = new Date(2026, 9, 5, 15, 0).getTime();              // 5 Oct 2026, 15:00 local
    const today = new Date(2026, 9, 5, 9, 0).getTime();
    const yesterday = new Date(2026, 9, 4, 20, 0).getTime();
    const tooOld = new Date(2026, 8, 1, 10, 0).getTime();
    const days = S.dailyEarnings([
        { orderStatus: 'completed', totalAmount: 500, completedAt: today },
        { orderStatus: 'delivered', totalAmount: 250, completedAt: today },
        { orderStatus: 'completed', totalAmount: 100, completedAt: yesterday },
        { orderStatus: 'pending_pickup', totalAmount: 999, createdAt: today },   // not earned yet
        { orderStatus: 'cancelled', totalAmount: 999, completedAt: today },     // never earned
        { orderStatus: 'completed', totalAmount: 999, completedAt: tooOld }     // outside the window
    ], 7, now);
    assert.strictEqual(days.length, 7);
    assert.strictEqual(days[6].amount, 750);   // today
    assert.strictEqual(days[5].amount, 100);   // yesterday
    assert.strictEqual(days[0].amount, 0);
});

test('popular items add quantities across orders and ignore cancelled ones', () => {
    const top = S.popularItems([
        { orderStatus: 'completed', items: [{ foodName: 'Rice', quantity: 2 }, { foodName: 'Soup', quantity: 1 }] },
        { orderStatus: 'pending_pickup', items: [{ foodName: 'Rice', quantity: 3 }] },
        { orderStatus: 'cancelled', items: [{ foodName: 'Soup', quantity: 50 }] }
    ], 5);
    assert.strictEqual(Array.from(top, (t) => t.name + ':' + t.quantity).join(','), 'Rice:5,Soup:1');
    assert.strictEqual(S.popularItems([], 5).length, 0);
});

const NOW = new Date(2026, 9, 9, 15, 0).getTime();   // 9 Oct 2026, 15:00 local
const at = (d, h) => new Date(2026, 9, d, h || 10, 0).getTime();

test('expired pickup: only unfinished pickup orders past their expiry', () => {
    assert.strictEqual(S.isExpiredPickupOrder({ orderStatus: 'pending_pickup', pickupExpiresAt: NOW - 1 }, NOW), true);
    assert.strictEqual(S.isExpiredPickupOrder({ orderStatus: 'ready', pickupExpiresAt: NOW - 1 }, NOW), true);
    assert.strictEqual(S.isExpiredPickupOrder({ orderStatus: 'completed', pickupExpiresAt: NOW - 1 }, NOW), false);
    assert.strictEqual(S.isExpiredPickupOrder({ orderStatus: 'cancelled', pickupExpiresAt: NOW - 1 }, NOW), false);
    assert.strictEqual(S.isExpiredPickupOrder({ orderStatus: 'pending_pickup', pickupExpiresAt: NOW + 1 }, NOW), false);
    assert.strictEqual(S.isExpiredPickupOrder({ orderStatus: 'out_for_delivery', orderType: 'delivery', pickupExpiresAt: NOW - 1 }, NOW), false);
});

test('date ranges use calendar days and keep today', () => {
    assert.strictEqual(S.withinRange({ createdAt: at(9, 0) }, 'today', NOW), true);
    assert.strictEqual(S.withinRange({ createdAt: at(8, 23) }, 'today', NOW), false);
    assert.strictEqual(S.withinRange({ createdAt: at(3, 0) }, '7d', NOW), true);      // 7 days incl. today starts 3 Oct
    assert.strictEqual(S.withinRange({ createdAt: at(2, 23) }, '7d', NOW), false);
    assert.strictEqual(S.withinRange({ createdAt: new Date(2026, 8, 10, 1).getTime() }, '30d', NOW), true);
    assert.strictEqual(S.withinRange({ createdAt: new Date(2026, 8, 9, 23).getTime() }, '30d', NOW), false);
    assert.strictEqual(S.withinRange({ createdAt: 1 }, 'all', NOW), true);
    assert.strictEqual(S.withinRange({}, 'all', NOW), true);
    assert.strictEqual(S.withinRange({}, '7d', NOW), false);
    assert.strictEqual(S.withinRange({ timestamp: at(9) }, 'today', NOW), true);       // older orders
});

test('average rating counts one rating per order and ignores bad values', () => {
    const r = S.averageRating([
        { orderId: 'A', rating: 5 }, { orderId: 'A', foodId: 'f1', rating: 5 },       // order review + dish copy
        { orderId: 'B', rating: 3 },
        { orderId: '', rating: 4 }, { rating: 4 },                                    // standalone dish reviews
        { rating: 0 }, { rating: 'x' }, { rating: 9 }, null
    ]);
    assert.strictEqual(r.count, 4);
    assert.strictEqual(r.average, (5 + 3 + 4 + 4) / 4);
    assert.strictEqual(S.averageRating([]).average, 0);
    assert.strictEqual(S.averageRating([]).count, 0);
});

test('hotel performance: revenue only from completed orders, cancel rate, ratings, sorted by revenue', () => {
    const orders = [
        { orderId: 'o1', hotelId: 'h1', hotelName: 'Alpha', orderStatus: 'completed', totalAmount: 500 },
        { orderId: 'o2', hotelId: 'h1', hotelName: 'Alpha', orderStatus: 'delivered', totalAmount: 250 },
        { orderId: 'o3', hotelId: 'h1', hotelName: 'Alpha', orderStatus: 'cancelled', totalAmount: 999 },
        { orderId: 'o4', hotelId: 'h1', hotelName: 'Alpha', orderStatus: 'pending_pickup', totalAmount: 999 },
        { orderId: 'o5', hotelId: 'h2', hotelName: 'Beta', orderStatus: 'completed', totalAmount: 900 },
        { orderId: 'o6', hotelId: 'h3', hotelName: 'Gamma', orderStatus: 'cancelled', totalAmount: 100 }
    ];
    const reviews = [
        { orderId: 'o1', hotelId: 'h1', rating: 5 },
        { orderId: 'o1', hotelId: 'h1', foodId: 'f', rating: 5 },
        { orderId: 'o2', rating: 3 },                                                  // matched through the order
        { orderId: 'o5', hotelId: 'h2', rating: 4 },
        { orderId: 'zz', hotelId: 'nobody', rating: 1 }
    ];
    const rows = S.hotelPerformance(orders, reviews);
    assert.strictEqual(rows.map((r) => r.hotelId).join(','), 'h2,h1,h3');
    const alpha = rows.find((r) => r.hotelId === 'h1');
    assert.strictEqual(alpha.hotelName, 'Alpha');
    assert.strictEqual(alpha.orders, 4);
    assert.strictEqual(alpha.completed, 2);
    assert.strictEqual(alpha.cancelled, 1);
    assert.strictEqual(alpha.revenue, 750);
    assert.strictEqual(alpha.cancelRatePct, 25);
    assert.strictEqual(alpha.avgRating, 4);
    assert.strictEqual(alpha.ratingCount, 2);
    const gamma = rows.find((r) => r.hotelId === 'h3');
    assert.strictEqual(gamma.revenue, 0);
    assert.strictEqual(gamma.cancelRatePct, 100);
    assert.strictEqual(gamma.ratingCount, 0);
    assert.strictEqual(S.hotelPerformance([], []).length, 0);
    // ids that look like object properties must not break the grouping
    assert.strictEqual(S.hotelPerformance([{ hotelId: 'constructor', orderStatus: 'completed', totalAmount: 1 }], [{ hotelId: 'toString', rating: 5 }]).length, 1);
});

test('cancelled report lists who cancelled and expired pickups, newest first', () => {
    const rows = S.cancelledReport([
        { orderId: 'a', orderStatus: 'cancelled', createdAt: at(1) },
        { orderId: 'b', orderStatus: 'cancelled', cancelledBy: 'hotel', createdAt: at(5) },
        { orderId: 'c', orderStatus: 'pending_pickup', pickupExpiresAt: NOW - 5, createdAt: at(3) },
        { orderId: 'd', orderStatus: 'completed', createdAt: at(8) },
        { orderId: 'e', orderStatus: 'preparing', pickupExpiresAt: NOW + 5, createdAt: at(8) }
    ], NOW);
    assert.strictEqual(rows.map((r) => r.order.orderId + ':' + r.kind).join('|'),
        'b:Cancelled by hotel|c:Expired pickup|a:Cancelled by customer');
});

test('csv escaping quotes commas, quotes and new lines', () => {
    assert.strictEqual(S.csvEscape('plain'), 'plain');
    assert.strictEqual(S.csvEscape('a,b'), '"a,b"');
    assert.strictEqual(S.csvEscape('say "hi"'), '"say ""hi"""');
    assert.strictEqual(S.csvEscape('line1\nline2'), '"line1\nline2"');
    assert.strictEqual(S.csvEscape(null), '');
    assert.strictEqual(S.csvEscape(undefined), '');
    assert.strictEqual(S.csvEscape(12.5), '12.5');
    assert.strictEqual(S.csvEscape(-3), '-3');
});

test('csv escaping defuses spreadsheet formulas', () => {
    ['=1+1', '+SUM(A1)', '-2+3', '@cmd', '=HYPERLINK("x","y")'].forEach((text) => {
        const out = S.csvEscape(text);
        assert.ok(out.replace(/^"/, '').startsWith("'"), text + ' -> ' + out);
    });
    assert.strictEqual(S.csvEscape('=A,B'), '"\'=A,B"');
});

test('toCsv writes a header, then one line per row with the chosen columns', () => {
    const csv = S.toCsv(
        [{ name: 'Rice, large', price: 100 }, { name: '=evil()', price: 5 }],
        [{ label: 'Name', key: 'name' }, { label: 'Price (LKR)', get: (r) => r.price }]);
    assert.strictEqual(csv, 'Name,Price (LKR)\r\n"Rice, large",100\r\n\'=evil(),5');
    assert.strictEqual(S.toCsv([], [{ label: 'A', key: 'a' }]), 'A');
});

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);
