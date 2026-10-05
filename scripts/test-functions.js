/**
 * Checks the Cloud Function decisions that can be tested without Firebase (no emulator needed).
 * Run:  node scripts/test-functions.js
 */
const assert = require('assert');
const { reminderFor, reminderText, LOOKAHEAD_MS } = require('../firebase-functions/pickup-reminder');

let passed = 0;
function test(name, fn) {
    try { fn(); passed++; console.log('  ok   ', name); }
    catch (e) { console.log('  FAIL ', name, '->', e.message); process.exitCode = 1; }
}

const MIN = 60 * 1000;
const now = Date.UTC(2026, 9, 5, 12, 0);
// A slot that starts `startsInMin` minutes from now (the slot is one hour long)
const order = (startsInMin, extra = {}) => ({
    orderStatus: 'pending_pickup',
    orderType: 'Pickup',
    pickupTime: '12:30',
    hotelName: 'Ocean View',
    pickupExpiresAt: now + (startsInMin + 60) * MIN,
    ...extra
});

test('nothing is sent more than 30 minutes before the slot', () => {
    assert.strictEqual(reminderFor(order(31), now), null);
    assert.strictEqual(reminderFor(order(120), now), null);
});

test('a reminder is due from 30 minutes before the slot starts', () => {
    assert.strictEqual(reminderFor(order(30), now), 'soon');
    assert.strictEqual(reminderFor(order(5), now), 'soon');
});

test('a slot that is already running says it is open', () => {
    assert.strictEqual(reminderFor(order(-10), now), 'open');
    assert.strictEqual(reminderFor(order(-59), now), 'open');
});

test('an expired slot gets no reminder', () => {
    assert.strictEqual(reminderFor(order(-60), now), null);
    assert.strictEqual(reminderFor(order(-90), now), null);
});

test('only one reminder per order', () => {
    assert.strictEqual(reminderFor(order(10, { pickupReminderSent: true }), now), null);
});

test('only active pickup orders are reminded', () => {
    ['pending', 'pending_pickup', 'confirmed', 'preparing', 'ready']
        .forEach((status) => assert.strictEqual(reminderFor(order(10, { orderStatus: status }), now), 'soon', status));
    ['completed', 'delivered', 'cancelled', 'out_for_delivery']
        .forEach((status) => assert.strictEqual(reminderFor(order(10, { orderStatus: status }), now), null, status));
    assert.strictEqual(reminderFor(order(10, { orderType: 'Delivery' }), now), null);
});

test('orders without a recorded expiry are left alone', () => {
    assert.strictEqual(reminderFor({ orderStatus: 'pending_pickup', orderType: 'Pickup' }, now), null);
    assert.strictEqual(reminderFor(null, now), null);
});

test('every order the function can pick up is inside the look-ahead the query uses', () => {
    // The query reads orders whose slot ends within LOOKAHEAD_MS of now
    assert.strictEqual(LOOKAHEAD_MS, 90 * MIN);
    assert.strictEqual(reminderFor(order(30), now) !== null, order(30).pickupExpiresAt - now <= LOOKAHEAD_MS);
    assert.strictEqual(reminderFor(order(31), now) === null, order(31).pickupExpiresAt - now > LOOKAHEAD_MS);
});

test('the wording names the hotel and the time', () => {
    const soon = reminderText(order(10), 'soon');
    assert.ok(soon.body.includes('Ocean View') && soon.body.includes('12:30'));
    const open = reminderText(order(-5), 'open');
    assert.ok(open.title.toLowerCase().includes('open') && open.body.includes('Ocean View'));
    assert.ok(reminderText({}, 'open').body.includes('the hotel'));
});

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);
