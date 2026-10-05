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

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);
