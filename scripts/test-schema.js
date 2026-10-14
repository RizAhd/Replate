/**
 * Checks the shared order rules in web-panels/js/data-schema.js (no browser or emulator needed).
 * These decide what counts as revenue, "pending" and "food rescued" on every admin and hotel page.
 * Run:  node scripts/test-schema.js
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

// Loads the shared schema file the way a browser page would.
const code = fs.readFileSync(path.join(__dirname, '..', 'web-panels', 'js', 'data-schema.js'), 'utf8');
const S = vm.runInNewContext(code + '\nReplateSchema', {});

let passed = 0;
// Runs one check and prints ok or FAIL.
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

test('a date range starts on the same day withinRange uses; all time has no start', () => {
    assert.strictEqual(S.rangeStart('7d', NOW), new Date(2026, 9, 3).getTime());
    assert.strictEqual(S.rangeStart('today', NOW), new Date(2026, 9, 9).getTime());
    assert.strictEqual(S.rangeStart('all', NOW), 0);
});

test('a week starts on Monday at midnight', () => {
    const monday = new Date(2026, 9, 5).getTime();
    assert.strictEqual(S.weekStart(NOW), monday);                                  // Friday 9 Oct
    assert.strictEqual(S.weekStart(monday), monday);
    assert.strictEqual(S.weekStart(new Date(2026, 9, 11, 23, 59).getTime()), monday); // Sunday night
    assert.strictEqual(S.weekStart(new Date(2026, 8, 13, 12).getTime()), new Date(2026, 8, 7).getTime());
});

test('weekly series fills weeks without orders with 0, so the time line never skips a week', () => {
    const orders = [
        { orderStatus: 'completed', totalAmount: 300, createdAt: new Date(2026, 8, 8, 12).getTime() },  // week of 7 Sep
        { orderStatus: 'cancelled', totalAmount: 999, createdAt: new Date(2026, 8, 9, 12).getTime() },  // counts, earns nothing
        { orderStatus: 'delivered', totalAmount: 450, createdAt: at(6) },                                // this week
        { orderStatus: 'pending_pickup', totalAmount: 120, createdAt: at(9) }
    ];
    const weeks = S.weeklySeries(orders, 0, NOW);   // all time: from the oldest order
    assert.ok(/^7 Sep/.test(weeks[0].label), weeks[0].label);   // "7 Sep" or "7 Sept", depending on the ICU version
    assert.strictEqual(weeks[4].label, '5 Oct');
    assert.deepStrictEqual(Array.from(weeks, (w) => w.orders), [2, 0, 0, 0, 2]);
    assert.deepStrictEqual(Array.from(weeks, (w) => w.revenue), [300, 0, 0, 0, 450]);
    assert.strictEqual(weeks.reduce((s, w) => s + w.orders, 0), orders.length);
});

test('weekly series follows the chosen range and shows this week even with no orders', () => {
    const from = S.rangeStart('30d', NOW);            // 10 Sep, a Thursday
    const weeks = S.weeklySeries([], from, NOW);
    assert.strictEqual(weeks.length, 5);              // weeks of 7, 14, 21, 28 Sep and 5 Oct
    assert.strictEqual(weeks[0].start, new Date(2026, 8, 7).getTime());
    assert.ok(weeks.every((w) => w.orders === 0 && w.revenue === 0));
    assert.strictEqual(S.weeklySeries([], 0, NOW).length, 1);
    assert.strictEqual(S.weeklySeries([{ orderStatus: 'completed', totalAmount: 50 }], S.rangeStart('today', NOW), NOW)[0].revenue, 50);
});

test('hotel orders: open orders are Active, finished and expired pickups are History', () => {
    assert.strictEqual(S.orderGroup({ orderStatus: 'pending_pickup', pickupExpiresAt: NOW + 1 }, NOW), 'active');
    assert.strictEqual(S.orderGroup({ orderStatus: 'out_for_delivery', orderType: 'Delivery' }, NOW), 'active');
    assert.strictEqual(S.orderGroup({ orderStatus: 'ready', pickupExpiresAt: NOW - 1 }, NOW), 'history');
    ['completed', 'delivered', 'cancelled'].forEach((s) => assert.strictEqual(S.orderGroup({ orderStatus: s }, NOW), 'history', s));
});

test('hotel orders: Active is soonest pickup first, History newest first', () => {
    const rows = [
        { key: 'late', order: { pickupExpiresAt: at(9, 20), createdAt: at(9, 8) } },
        { key: 'soon', order: { pickupExpiresAt: at(9, 16), createdAt: at(9, 12) } },
        { key: 'delivery', order: { orderType: 'Delivery', pickupExpiresAt: at(9, 23), createdAt: at(9, 14) } }
    ];
    assert.strictEqual(rows.slice().sort(S.compareActive).map((r) => r.key).join(','), 'delivery,soon,late');
    assert.strictEqual(rows.slice().sort(S.compareHistory).map((r) => r.key).join(','), 'delivery,soon,late');
});

test('hotel orders search: customer name, phone, order id and pickup code', () => {
    const order = { customerName: 'Dilini Wickramasinghe', customerPhone: '0771234116', orderId: 'ORDER-016', otpCode: '004321' };
    assert.strictEqual(S.orderMatchesSearch(order, 'k1', ''), true);
    assert.strictEqual(S.orderMatchesSearch(order, 'k1', 'dilini'), true);
    assert.strictEqual(S.orderMatchesSearch(order, 'k1', '077 123 4116'), true);
    assert.strictEqual(S.orderMatchesSearch(order, 'k1', '#order-016'), true);
    assert.strictEqual(S.orderMatchesSearch(order, 'k1', '004321'), true);
    assert.strictEqual(S.orderMatchesSearch(order, 'k1', 'nimal'), false);
    assert.strictEqual(S.orderMatchesSearch(order, 'k1', '999'), false);
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

test('one money format and one date format for every page', () => {
    assert.strictEqual(S.formatMoney(6340), 'LKR 6,340.00');
    assert.strictEqual(S.formatMoney('12.5'), 'LKR 12.50');
    assert.strictEqual(S.formatMoney('abc'), 'LKR 0.00');
    assert.strictEqual(S.formatDateTime(new Date(2026, 9, 14, 12, 27).getTime()), '14 Oct 2026, 12:27');
    assert.strictEqual(S.formatDate(new Date(2026, 9, 14, 12, 27).getTime()), '14 Oct 2026');
    assert.strictEqual(S.formatDateTime(0), '—');
    assert.strictEqual(S.plural(1, 'order'), '1 order');
    assert.strictEqual(S.plural(3, 'order'), '3 orders');
    assert.strictEqual(S.formatRating(3), '3.0');
    assert.strictEqual(S.formatRating('4.25'), '4.3');
    assert.strictEqual(S.formatPercent(37.5), '37.5%');
    assert.strictEqual(S.formatPercent(0), '0%');
    assert.strictEqual(S.formatPercent(100 / 3), '33.3%');
    assert.strictEqual(S.formatDayMonth(new Date(2026, 9, 14, 12).getTime()), '14 Oct');
    assert.strictEqual(S.formatWeekdayDate(new Date(2026, 9, 14, 12).getTime()), 'Wed 14 Oct');
});

test('one status vocabulary: order, hotel and listing states', () => {
    assert.strictEqual(S.orderStatusView('pending').label, 'Pending pickup');
    assert.strictEqual(S.orderStatusView('pending').cls, 'badge-warning');
    assert.strictEqual(S.statusLabel('out_for_delivery'), 'Out for delivery');
    assert.strictEqual(S.statusBadgeClass('delivered'), 'badge-success');
    assert.strictEqual(S.hotelStatusView('active').label, 'Approved');
    assert.strictEqual(S.hotelStatusView('declined').label, 'Not approved');
    assert.strictEqual(S.listingState({ status: 'available', isAvailable: true, quantity: 3 }).label, 'Active');
    assert.strictEqual(S.listingState({ status: 'sold_out', isAvailable: false }).label, 'Sold out');
    assert.strictEqual(S.listingState({ status: 'available', quantity: 0 }).label, 'Sold out');
    assert.strictEqual(S.listingState({ status: 'inactive', suspendedByAdmin: true, isAvailable: true }).label, 'Hidden by admin');
});

test('a completed cash order counts as paid; an unpaid cancelled order as not paid', () => {
    assert.strictEqual(S.paymentView({ paymentMethod: 'Cash', paymentStatus: 'pending', orderStatus: 'completed' }).label, 'Paid (cash)');
    assert.strictEqual(S.paymentView({ paymentMethod: 'Cash', paymentStatus: 'pending', orderStatus: 'pending_pickup' }).label, 'Pending');
    assert.strictEqual(S.paymentView({ paymentMethod: 'Cash', paymentStatus: 'pending', orderStatus: 'cancelled' }).label, 'Not paid');
    assert.strictEqual(S.paymentView({ paymentMethod: 'PayHere Online', paymentStatus: 'paid', orderStatus: 'cancelled' }).label, 'Paid');
    assert.strictEqual(S.paymentView({ paymentMethod: 'PayHere Online', paymentStatus: 'pending', orderStatus: 'delivered' }).label, 'Pending');
});

test('order number: last 8 characters of the order id, upper case (same rule as the app)', () => {
    assert.strictEqual(S.shortOrderId({ orderId: '-NabcDEF12gh' }), 'CDEF12GH');
    assert.strictEqual(S.shortOrderId({ orderId: 'abc123' }), 'ABC123');
    assert.strictEqual(S.shortOrderId({}, 'ord-xyz'), 'ORD-XYZ');               // falls back to the database key
    assert.strictEqual(S.shortOrderId({ qrCode: 'E2E-ORDER-O10' }, 'k'), 'RDER-O10');
    assert.strictEqual(S.shortOrderId(null, ''), '');
});

test('pickup times use one separator: an en dash', () => {
    assert.strictEqual(S.formatPickupWindow('18:00', '20:00'), '18:00 – 20:00');
    assert.strictEqual(S.formatPickupWindow('18:00', ''), '18:00 – ?');
    assert.strictEqual(S.formatPickupWindow('', ''), '—');
    assert.strictEqual(S.formatPickupSlot('18:00 - 19:00'), '18:00 – 19:00');
    assert.strictEqual(S.formatPickupSlot('18:00-19:00'), '18:00 – 19:00');
    assert.strictEqual(S.formatPickupSlot('18:30'), '18:30');
    assert.strictEqual(S.formatPickupSlot(undefined), '');
});

const clock = (h, m) => new Date(2026, 9, 14, h, m || 0).getTime();   // 14 Oct 2026, local time

test('pickup countdown: before, during and after the slot, in the app\'s words', () => {
    const order = { orderStatus: 'pending_pickup', orderType: 'Pickup', pickupTime: '18:00', pickupExpiresAt: clock(19) };
    const before = S.pickupCountdown(order, clock(17, 30));
    assert.strictEqual(before.text, 'Pickup starts at 18:00');
    assert.strictEqual(before.tone, 'upcoming');
    const during = S.pickupCountdown(order, clock(18, 18));
    assert.strictEqual(during.text, 'Collect within 42 min');
    assert.strictEqual(during.tone, 'normal');
    assert.strictEqual(during.minutesLeft, 42);
    assert.strictEqual(S.pickupCountdown(order, clock(18, 18) + 30000).text, 'Collect within 42 min');   // part minutes round up
    const late = S.pickupCountdown(order, clock(18, 50));
    assert.strictEqual(late.text, 'Collect within 10 min');
    assert.strictEqual(late.tone, 'urgent');                                                               // under 15 minutes
    assert.strictEqual(S.pickupCountdown(order, clock(18, 45)).tone, 'normal');                           // 15 min left
    assert.strictEqual(S.pickupCountdown(order, clock(18, 46)).tone, 'urgent');                           // 14 min left
    assert.strictEqual(S.pickupCountdown(order, clock(19)).text, 'Collect within 1 min');                 // the last moment still counts
    const after = S.pickupCountdown(order, clock(19) + 1);
    assert.strictEqual(after.text, 'Code expired');
    assert.strictEqual(after.tone, 'expired');
});

test('pickup countdown: older "18:00 - 19:00" times, unreadable times and shortened slots', () => {
    const older = { orderStatus: 'confirmed', pickupTime: '18:00 - 19:00', pickupExpiresAt: clock(19) };
    assert.strictEqual(S.pickupCountdown(older, clock(17)).text, 'Pickup starts at 18:00');
    assert.strictEqual(S.pickupCountdown(older, clock(18, 30)).text, 'Collect within 30 min');
    const unreadable = { orderStatus: 'ready', pickupTime: 'soon', pickupExpiresAt: clock(19) };
    assert.strictEqual(S.pickupCountdown(unreadable, clock(17, 59)).text, 'Pickup starts at 18:00');      // one hour before expiry
    assert.strictEqual(S.pickupCountdown(unreadable, clock(18, 10)).text, 'Collect within 50 min');
    const capped = { orderStatus: 'pending_pickup', pickupTime: '19:00', pickupExpiresAt: clock(19, 45) }; // dish window ends earlier
    assert.strictEqual(S.pickupCountdown(capped, clock(18, 50)).text, 'Pickup starts at 19:00');
    assert.strictEqual(S.pickupCountdown(capped, clock(19, 5)).text, 'Collect within 40 min');
    const lateNight = { orderStatus: 'pending_pickup', pickupTime: '23:30', pickupExpiresAt: new Date(2026, 9, 15, 0, 30).getTime() };
    assert.strictEqual(S.pickupCountdown(lateNight, clock(23, 0)).text, 'Pickup starts at 23:30');
    assert.strictEqual(S.pickupCountdown(lateNight, clock(23, 50)).text, 'Collect within 40 min');
});

test('pickup countdown: nothing for deliveries, finished orders and orders without an expiry', () => {
    const at18 = clock(18, 10);
    assert.strictEqual(S.pickupCountdown({ orderStatus: 'pending_delivery', orderType: 'Delivery', pickupExpiresAt: clock(19) }, at18), null);
    ['completed', 'delivered', 'cancelled'].forEach((s) =>
        assert.strictEqual(S.pickupCountdown({ orderStatus: s, pickupExpiresAt: clock(19) }, at18), null, s));
    assert.strictEqual(S.pickupCountdown({ orderStatus: 'pending_pickup', pickupTime: '18:00' }, at18), null);
    assert.strictEqual(S.pickupCountdown({ orderStatus: 'pending_pickup', pickupExpiresAt: 'x' }, at18), null);
    assert.strictEqual(S.pickupCountdown(null, at18), null);
});

const near = (actual, expected, label) => assert.ok(Math.abs(actual - expected) < 1e-9, `${label}: ${actual} != ${expected}`);

test('impact estimate: meals from completed and delivered orders, 0.4 kg food per meal, 2.5 kg CO2e per kg food', () => {
    assert.strictEqual(S.IMPACT.KG_FOOD_PER_MEAL, 0.4);
    assert.strictEqual(S.IMPACT.KG_CO2E_PER_KG_FOOD, 2.5);
    const impact = S.impactFromOrders([
        { orderStatus: 'completed', items: [{ quantity: 2 }, { quantity: 1 }] },
        { orderStatus: 'delivered', items: { a: { quantity: 4 } } },
        { orderStatus: 'pending_pickup', items: [{ quantity: 5 }] },          // not handed over yet
        { orderStatus: 'cancelled', items: [{ quantity: 3 }] },               // never rescued
        { orderStatus: 'completed', items: [{}] },                            // a line without a quantity is one meal
        null
    ]);
    assert.strictEqual(impact.meals, 8);
    near(impact.foodKg, 3.2, 'food kg');
    near(impact.co2eKg, 8, 'CO2e kg');
    const none = S.impactFromOrders([]);
    assert.strictEqual(none.meals + none.foodKg + none.co2eKg, 0);
    assert.strictEqual(S.formatKg(29 * 0.4), '11.6 kg');
    assert.strictEqual(S.formatKg(29 * 0.4 * 2.5), '29 kg');
    assert.strictEqual(S.formatKg(1160), '1,160 kg');
    assert.strictEqual(S.formatKg('x'), '0 kg');
});

test('week-over-week change is labelled "vs last week"', () => {
    const d = (cur, prev) => { const r = S.weekDelta(cur, prev); return r.dir + '|' + r.text; };
    assert.strictEqual(d(0, 0), 'none|');
    assert.strictEqual(d(3, 0), 'up|New this week');
    assert.strictEqual(d(12, 10), 'up|20% vs last week');
    assert.strictEqual(d(5, 10), 'down|50% vs last week');
    assert.strictEqual(d(10, 10), 'flat|Same as last week');
    assert.strictEqual(d(0, 4), 'down|100% vs last week');
});

test('relative time in the long style: "just now", "1 minute ago", "1 hour ago", then the date', () => {
    const now = clock(15);
    assert.strictEqual(S.formatRelative(now - 30000, now), 'just now');
    assert.strictEqual(S.formatRelative(now - 60000, now), '1 minute ago');
    assert.strictEqual(S.formatRelative(now - 5 * 60000, now), '5 minutes ago');
    assert.strictEqual(S.formatRelative(now - 3600000, now), '1 hour ago');
    assert.strictEqual(S.formatRelative(now - 3 * 3600000 - 59000, now), '3 hours ago');
    assert.strictEqual(S.formatRelative(now - 2 * 86400000, now), '12 Oct 2026');
    assert.strictEqual(S.formatRelative(0, now), '');
});

test('new-order alerts: only orders not seen before that still wait for the hotel', () => {
    const rows = [
        { key: 'a', order: { orderStatus: 'pending_pickup' } },
        { key: 'b', order: { orderStatus: 'pending' } },
        { key: 'c', order: { orderStatus: 'completed' } },
        { key: 'd', order: { orderStatus: 'pending_delivery' } },
        { key: 'e', order: { orderStatus: 'confirmed' } }
    ];
    assert.strictEqual(S.newPendingOrders(new Set(['a']), rows).map((r) => r.key).join(','), 'b,d');
    assert.strictEqual(S.newPendingOrders(['a', 'b', 'd'], rows).length, 0);
    assert.strictEqual(S.newPendingOrders(null, []).length, 0);
    assert.strictEqual(S.isNewOrder({ orderStatus: 'ready' }), false);
});

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);
