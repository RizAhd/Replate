/**
 * Stock changes for orders (used by the order triggers in index.js). Takes the database as a parameter
 * so the same code can be tested against the Realtime Database emulator (scripts/test-stock.js).
 */
const { clampQuantity } = require("./safe");

function createStock(db) {
  // ── ORDER STOCK ────────────────────────────────────────────
  // Stock is changed here, on the server, and nowhere else: the database rules do not let customers write
  // foodItems quantity/status. Two protections keep it correct:
  //  - the stockAdjusted / stockRestored flags (flipped in a transaction) make a retried trigger a no-op
  //  - each quantity change is a transaction, so simultaneous orders cannot overwrite each other
  // A line only counts if the dish really belongs to the order's hotel, and one line can take at most
  // MAX_ORDER_LINE_QUANTITY portions, so an order cannot be used to empty somebody else's listing.
  function orderLines(order) {
    const raw = order.items || [];
    const items = Array.isArray(raw) ? raw : Object.values(raw);
    const lines = [];
    for (const item of items) {
      const foodId = String((item && item.foodId) || "").trim();
      if (!foodId || /[.#$\[\]/]/.test(foodId)) continue;
      lines.push({ foodId, quantity: clampQuantity(item && item.quantity) || 1 });
    }
    return lines;
  }

  // direction -1 takes portions out of stock (new order), +1 puts them back (cancelled order)
  async function changeStockForOrder(order, direction) {
    const hotelId = String(order.hotelId || "").trim();
    for (const { foodId, quantity } of orderLines(order)) {
      const foodSnap = await db.ref(`foodItems/${foodId}`).get();
      if (!foodSnap.exists()) continue;
      const food = foodSnap.val() || {};
      if (hotelId && String(food.hotelId || "") !== hotelId) {
        console.warn(`[stock] ${foodId} does not belong to hotel ${hotelId}, line ignored`);
        continue;
      }

      const result = await db.ref(`foodItems/${foodId}/quantity`).transaction((current) => {
        // Missing item: leave it alone (Firebase re-runs this with the server value if it exists)
        if (current === null) return current;
        const qty = Number(current);
        return Math.max(0, (Number.isFinite(qty) ? qty : 0) + direction * quantity);
      });
      if (!result.committed || !result.snapshot.exists()) continue;

      const newQty = Number(result.snapshot.val()) || 0;
      const paused = String(food.status || "").toLowerCase() === "paused";
      if (direction < 0 && newQty <= 0 && !paused) {
        await db.ref(`foodItems/${foodId}`).update({ status: "sold_out", isAvailable: false, available: false });
        console.log(`[stock] ${foodId} sold out`);
      } else if (direction > 0 && newQty > 0 && String(food.status || "").toLowerCase() === "sold_out") {
        // Only a dish that sold out automatically comes back; a listing the hotel paused stays paused
        await db.ref(`foodItems/${foodId}`).update({ status: "available", isAvailable: true, available: true });
        console.log(`[stock] ${foodId} is available again`);
      }
    }
  }

  // Takes the order's portions out of stock, only once.
  async function adjustStockForOrder(orderId, order) {
    const flag = await db.ref(`orders/${orderId}/stockAdjusted`).transaction((current) => (current ? undefined : true));
    if (!flag.committed) {
      console.log(`[stock] order ${orderId} was already adjusted, skipping`);
      return;
    }
    const status = (await db.ref(`orders/${orderId}/orderStatus`).get()).val();
    if (String(status || "").toLowerCase() === "cancelled") {
      // Cancelled before this ran: nothing was taken, so there is nothing to give back later
      await db.ref(`orders/${orderId}/stockRestored`).set(true);
      return;
    }
    await changeStockForOrder(order, -1);
  }

  // Puts a cancelled order's portions back, only once.
  async function restoreStockForOrder(orderId, order) {
    const adjusted = (await db.ref(`orders/${orderId}/stockAdjusted`).get()).val();
    if (!adjusted) return;
    const flag = await db.ref(`orders/${orderId}/stockRestored`).transaction((current) => (current ? undefined : true));
    if (!flag.committed) return;
    await changeStockForOrder(order, +1);
  }

  return { orderLines, changeStockForOrder, adjustStockForOrder, restoreStockForOrder };
}

module.exports = { createStock };
