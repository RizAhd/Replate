/**
 * Checks the order stock logic (firebase-functions/stock.js) against the Realtime Database emulator.
 * Run:  npm run stock:test      (starts the emulator itself; needs Java 21 on the PATH)
 */
const assert = require('assert');
process.env.GCLOUD_PROJECT = 'demo-replate';
const admin = require('../firebase-functions/node_modules/firebase-admin');
const { createStock } = require('../firebase-functions/stock');

admin.initializeApp({ projectId: 'demo-replate', databaseURL: 'http://127.0.0.1:9000/?ns=demo-replate' });
const db = admin.database();
const { adjustStockForOrder, restoreStockForOrder } = createStock(db);

let passed = 0, failed = 0;
async function test(name, fn) {
    try { await fn(); passed++; console.log('  ok   ', name); }
    catch (e) { failed++; console.log('  FAIL ', name, '->', e.message); }
}
const val = async (path) => (await db.ref(path).get()).val();

(async () => {
    await db.ref('/').set({
        foodItems: {
            rice: { hotelId: 'hotelA', quantity: 5, status: 'available', isAvailable: true },
            soup: { hotelId: 'hotelA', quantity: 2, status: 'available', isAvailable: true },
            other: { hotelId: 'hotelB', quantity: 7, status: 'available', isAvailable: true },
            paused: { hotelId: 'hotelA', quantity: 1, status: 'paused', isAvailable: false },
        },
    });
    const place = async (id, order) => { await db.ref(`orders/${id}`).set(order); await adjustStockForOrder(id, order); };

    await test('an order takes its portions out of stock', async () => {
        await place('o1', { hotelId: 'hotelA', orderStatus: 'pending_pickup', items: [{ foodId: 'rice', quantity: 2 }] });
        assert.strictEqual(await val('foodItems/rice/quantity'), 3);
        assert.strictEqual(await val('orders/o1/stockAdjusted'), true);
    });

    await test('running the same trigger twice does not take stock twice', async () => {
        await adjustStockForOrder('o1', await val('orders/o1'));
        assert.strictEqual(await val('foodItems/rice/quantity'), 3);
    });

    await test('a dish that reaches zero is marked sold out', async () => {
        await place('o2', { hotelId: 'hotelA', orderStatus: 'pending_pickup', items: [{ foodId: 'soup', quantity: 2 }] });
        assert.strictEqual(await val('foodItems/soup/quantity'), 0);
        assert.strictEqual(await val('foodItems/soup/status'), 'sold_out');
        assert.strictEqual(await val('foodItems/soup/isAvailable'), false);
    });

    await test('stock never goes below zero', async () => {
        await place('o3', { hotelId: 'hotelA', orderStatus: 'pending_pickup', items: [{ foodId: 'rice', quantity: 50 }] });
        assert.strictEqual(await val('foodItems/rice/quantity'), 0);
    });

    await test('an order cannot drain a dish that belongs to another hotel', async () => {
        await place('o4', { hotelId: 'hotelA', orderStatus: 'pending_pickup', items: [{ foodId: 'other', quantity: 5 }] });
        assert.strictEqual(await val('foodItems/other/quantity'), 7);
    });

    await test('an absurd quantity is capped at 50 portions', async () => {
        await db.ref('foodItems/big').set({ hotelId: 'hotelA', quantity: 500, status: 'available' });
        await place('o5', { hotelId: 'hotelA', orderStatus: 'pending_pickup', items: [{ foodId: 'big', quantity: 99999 }] });
        assert.strictEqual(await val('foodItems/big/quantity'), 450);
    });

    await test('unknown dishes and bad ids are ignored without an error', async () => {
        await place('o6', { hotelId: 'hotelA', orderStatus: 'pending_pickup', items: [{ foodId: 'nope', quantity: 1 }, { foodId: 'a.b', quantity: 1 }, {}] });
        assert.strictEqual(await val('foodItems/nope'), null);
    });

    await test('a cancelled order gives its portions back, once', async () => {
        const order = await val('orders/o1');
        await db.ref('orders/o1/orderStatus').set('cancelled');
        await restoreStockForOrder('o1', { ...order, orderStatus: 'cancelled' });
        assert.strictEqual(await val('foodItems/rice/quantity'), 2);
        await restoreStockForOrder('o1', { ...order, orderStatus: 'cancelled' });
        assert.strictEqual(await val('foodItems/rice/quantity'), 2, 'a second cancel event must not give stock back again');
    });

    await test('a dish that sold out comes back when its order is cancelled', async () => {
        const order = await val('orders/o2');
        await restoreStockForOrder('o2', order);
        assert.strictEqual(await val('foodItems/soup/quantity'), 2);
        assert.strictEqual(await val('foodItems/soup/status'), 'available');
        assert.strictEqual(await val('foodItems/soup/isAvailable'), true);
    });

    await test('a listing the hotel paused stays paused when stock is restored', async () => {
        await place('o7', { hotelId: 'hotelA', orderStatus: 'pending_pickup', items: [{ foodId: 'paused', quantity: 1 }] });
        await restoreStockForOrder('o7', await val('orders/o7'));
        assert.strictEqual(await val('foodItems/paused/status'), 'paused');
    });

    await test('an order cancelled before the stock step took nothing and gives nothing back', async () => {
        const order = { hotelId: 'hotelA', orderStatus: 'cancelled', items: [{ foodId: 'big', quantity: 10 }] };
        await db.ref('orders/o8').set(order);
        await adjustStockForOrder('o8', order);
        await restoreStockForOrder('o8', order);
        assert.strictEqual(await val('foodItems/big/quantity'), 450);
    });

    await test('cancelling an order whose stock was never adjusted changes nothing', async () => {
        await db.ref('orders/o9').set({ hotelId: 'hotelA', orderStatus: 'cancelled', items: [{ foodId: 'big', quantity: 10 }] });
        await restoreStockForOrder('o9', await val('orders/o9'));
        assert.strictEqual(await val('foodItems/big/quantity'), 450);
    });

    console.log(`\n${passed} passed${failed ? `, ${failed} FAILED` : ''}`);
    await admin.app().delete();
    process.exit(failed ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
