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

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);
