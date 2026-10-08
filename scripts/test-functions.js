/**
 * Checks the Cloud Function decisions that can be tested without Firebase (no emulator needed).
 * Run:  node scripts/test-functions.js
 */
const assert = require('assert');
const { reminderFor, reminderText, LOOKAHEAD_MS } = require('../firebase-functions/pickup-reminder');
const safe = require('../firebase-functions/safe');

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

// ---- safe.js: the small decisions that protect the public endpoints -------------------------------

test('names typed by users cannot inject HTML into an email', () => {
    assert.strictEqual(safe.escapeHtml('<img src=x onerror=alert(1)>'), '&lt;img src=x onerror=alert(1)&gt;');
    assert.strictEqual(safe.escapeHtml('Tom & "Jerry"'), 'Tom &amp; &quot;Jerry&quot;');
    assert.strictEqual(safe.escapeHtml(null), '');
});

test('plain-text names lose line breaks and are cut to a sane length', () => {
    assert.strictEqual(safe.plainName('Ann\r\nBcc: x@y.z'), 'Ann Bcc: x@y.z');
    assert.strictEqual(safe.plainName('x'.repeat(200)).length, 60);
    assert.strictEqual(safe.plainName('   '), 'there');
    assert.strictEqual(safe.plainName(undefined), 'there');
});

test('one-time codes are six digits and vary', () => {
    const seen = new Set();
    for (let i = 0; i < 200; i++) {
        const otp = safe.generateOtp();
        assert.ok(/^[0-9]{6}$/.test(otp), otp);
        seen.add(otp);
    }
    assert.ok(seen.size > 150, 'codes should not repeat much');
});

test('an order line takes between 1 and 50 portions', () => {
    assert.strictEqual(safe.clampQuantity(3), 3);
    assert.strictEqual(safe.clampQuantity('4'), 4);
    assert.strictEqual(safe.clampQuantity(9999), 50);
    assert.strictEqual(safe.clampQuantity(2.9), 2);
    ['abc', 0, -5, NaN, null, undefined, Infinity].forEach((bad) => assert.strictEqual(safe.clampQuantity(bad), 0, String(bad)));
});

test('failures show a fixed message and the right status, never internal text', () => {
    const unauth = safe.failureResponse(new Error('Missing Bearer token'), 'x');
    assert.deepStrictEqual([unauth.status, unauth.message], [401, 'Please sign in again.']);
    const expired = safe.failureResponse(Object.assign(new Error('Firebase ID token has expired'), { code: 'auth/id-token-expired' }), 'x');
    assert.strictEqual(expired.status, 401);
    assert.strictEqual(safe.failureResponse(new Error('Insufficient permissions'), 'x').status, 403);
    assert.strictEqual(safe.failureResponse(new Error('User profile not found'), 'x').status, 403);
    const internal = safe.failureResponse(new Error('connect ECONNREFUSED 10.0.0.5:5432 secret=abc'), 'Failed to send notification');
    assert.deepStrictEqual([internal.status, internal.message], [500, 'Failed to send notification']);
});

test('the rate limit allows a burst, then blocks until the window passes', () => {
    const now = 1000000;
    let record = null;
    for (let i = 0; i < 5; i++) {
        const d = safe.rateLimitDecision(record, now + i, { limit: 5 });
        assert.strictEqual(d.allowed, true, 'request ' + (i + 1));
        record = d.next;
    }
    const blocked = safe.rateLimitDecision(record, now + 10, { limit: 5 });
    assert.strictEqual(blocked.allowed, false);
    assert.strictEqual(blocked.next.count, 5, 'a blocked request is not counted again');
    const later = safe.rateLimitDecision(record, now + safe.RATE_WINDOW_MS + 1, { limit: 5 });
    assert.strictEqual(later.allowed, true);
    assert.strictEqual(later.next.count, 1);
});

test('reserved FCM keys are recognised', () => {
    ['google.sent_time', 'gcm.notification.x', 'from', 'notification', 'message_type', 'collapse_key', ''].forEach((k) =>
        assert.strictEqual(safe.isReservedDataKey(k), true, k));
    ['phase', 'segment', 'orderId'].forEach((k) => assert.strictEqual(safe.isReservedDataKey(k), false, k));
});

test('a notification list is cleaned and capped', () => {
    assert.deepStrictEqual(Array.from(safe.limitUserIds(['a', ' b ', 'a', '', null, 'c.d', 'e/f', 'g#h', 'ok'])), ['a', 'b', 'ok']);
    assert.strictEqual(safe.limitUserIds(Array.from({ length: 900 }, (_, i) => 'u' + i)).length, 500);
    assert.strictEqual(safe.limitUserIds('not an array').length, 0);
});

test('the engagement schedule uses Sri Lankan time whatever the server clock says', () => {
    // 02:00 UTC is 07:30 in Colombo (UTC+5:30)
    assert.strictEqual(safe.colomboHour(new Date(Date.UTC(2026, 9, 5, 2, 0))), 7);
    // 19:00 UTC is 00:30 the next day in Colombo
    assert.strictEqual(safe.colomboHour(new Date(Date.UTC(2026, 9, 5, 19, 0))), 0);
});

// ---- chat push and PayHere confirmation ---------------------------------------------------

test('a customer message goes to the hotel and a hotel message goes to the customer', () => {
    assert.deepStrictEqual({ ...safe.chatRecipient('hotelA_cust1', 'cust1') }, { to: 'hotel', hotelKey: 'hotelA', customerUid: 'cust1' });
    assert.deepStrictEqual({ ...safe.chatRecipient('hotelA_cust1', 'hotelOwner') }, { to: 'customer', hotelKey: 'hotelA', customerUid: 'cust1' });
});

test('a hotel key that contains an underscore still splits at the last one', () => {
    const r = safe.chatRecipient('-Nab_cd_Xyz123', 'Xyz123');
    assert.strictEqual(r.hotelKey, '-Nab_cd');
    assert.strictEqual(r.customerUid, 'Xyz123');
});

test('broken conversation ids or missing senders notify nobody', () => {
    ['', 'nounderscore', '_cust1', 'hotel_', null].forEach((id) => assert.strictEqual(safe.chatRecipient(id, 'x'), null, String(id)));
    assert.strictEqual(safe.chatRecipient('h_c', ''), null);
});

test('chat previews are one short line', () => {
    assert.strictEqual(safe.messagePreview('hi' + String.fromCharCode(10) + '  there'), 'hi there');
    assert.strictEqual(safe.messagePreview('x'.repeat(200)).length, 80);
    assert.strictEqual(safe.messagePreview('   '), 'Sent you a message');
});

test('PayHere signature: a correct one is accepted, any change is refused', () => {
    const crypto = require('crypto');
    const secret = 'my-secret';
    const fields = { merchant_id: '1211149', order_id: 'ord1', payhere_amount: '1000.00', payhere_currency: 'LKR', status_code: '2' };
    const hash = crypto.createHash('md5').update(secret).digest('hex').toUpperCase();
    fields.md5sig = crypto.createHash('md5').update('1211149ord11000.00LKR2' + hash).digest('hex').toUpperCase();
    assert.strictEqual(safe.verifyPayhereNotification(fields, secret), true);
    assert.strictEqual(safe.verifyPayhereNotification({ ...fields, payhere_amount: '1.00' }, secret), false);
    assert.strictEqual(safe.verifyPayhereNotification({ ...fields, status_code: '-2' }, secret), false);
    assert.strictEqual(safe.verifyPayhereNotification(fields, 'other-secret'), false);
    assert.strictEqual(safe.verifyPayhereNotification({ ...fields, md5sig: '' }, secret), false);
    assert.strictEqual(safe.verifyPayhereNotification(fields, ''), false);
});

test('the charged amount must equal the order total to the cent', () => {
    assert.strictEqual(safe.amountMatches('1000.00', 1000), true);
    assert.strictEqual(safe.amountMatches('999.99', 1000), false);
    assert.strictEqual(safe.amountMatches('abc', 1000), false);
    assert.strictEqual(safe.amountMatches('0', 0), false);
});

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);
