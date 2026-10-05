/**
 * When a customer should be reminded about a pickup. Kept free of Firebase so it can be tested
 * (scripts/test-functions.js).
 *
 * A pickup slot is one hour long and ends at `pickupExpiresAt` (set at checkout). The reminder goes
 * out once, 30 minutes before the slot starts; an order placed closer to the slot than that gets it
 * at the next run, and then says the slot is already open.
 */
const WINDOW_MS = 60 * 60 * 1000;
const REMIND_BEFORE_MS = 30 * 60 * 1000;
const ACTIVE_PICKUP_STATUSES = new Set(["pending", "pending_pickup", "confirmed", "preparing", "ready"]);

/** Orders whose slot ends within this long from now are the only ones worth looking at. */
const LOOKAHEAD_MS = WINDOW_MS + REMIND_BEFORE_MS;

/** Returns "soon" (slot not started yet), "open" (slot is running), or null (no reminder due). */
function reminderFor(order, now) {
  if (!order || order.pickupReminderSent) return null;
  if (String(order.orderType || "").toLowerCase() === "delivery") return null;
  if (!ACTIVE_PICKUP_STATUSES.has(String(order.orderStatus || "").toLowerCase())) return null;

  const expiresAt = Number(order.pickupExpiresAt);
  if (!Number.isFinite(expiresAt) || expiresAt <= now) return null;

  const slotStart = expiresAt - WINDOW_MS;
  if (now < slotStart - REMIND_BEFORE_MS) return null;
  return now < slotStart ? "soon" : "open";
}

/** The words of the reminder. */
function reminderText(order, phase) {
  const hotel = String(order.hotelName || "the hotel").trim() || "the hotel";
  if (phase === "soon") {
    const time = String(order.pickupTime || "").trim();
    return {
      title: "Your pickup is coming up",
      body: time
        ? `Your order at ${hotel} is ready to collect from ${time}. Have your QR code handy.`
        : `Your pickup at ${hotel} starts soon. Have your QR code handy.`,
    };
  }
  return {
    title: "Your pickup window is open",
    body: `Your order at ${hotel} can be collected now. Show your QR code or OTP at the counter.`,
  };
}

module.exports = { reminderFor, reminderText, LOOKAHEAD_MS, WINDOW_MS, REMIND_BEFORE_MS };
