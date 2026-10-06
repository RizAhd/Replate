/**
 * Small decisions the Cloud Functions make that must never go wrong, kept free of Firebase so they can
 * be tested (scripts/test-functions.js).
 */
const crypto = require("crypto");

/** The most portions of one dish a single order line may take from the stock. */
const MAX_ORDER_LINE_QUANTITY = 50;
/** The most people one targeted notification may name. */
const MAX_TARGETED_USERS = 500;

const RATE_WINDOW_MS = 10 * 60 * 1000;
const RATE_LIMIT_PER_EMAIL = 5;
const RATE_LIMIT_PER_IP = 20;

/** Makes text safe to place inside an HTML email (names are typed by users). */
function escapeHtml(value) {
  return String(value === undefined || value === null ? "" : value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** A name for the plain-text part of an email: no line breaks (they could inject headers or fake lines). */
function plainName(value, fallback = "there") {
  const cleaned = String(value || "").replace(/[\r\n]+/g, " ").trim().slice(0, 60);
  return cleaned || fallback;
}

/** Six digits from a secure random source (Math.random is predictable). */
function generateOtp() {
  return String(crypto.randomInt(100000, 1000000));
}

/** A whole number from 1 to MAX_ORDER_LINE_QUANTITY, or 0 when the value is not a usable quantity. */
function clampQuantity(value, max = MAX_ORDER_LINE_QUANTITY) {
  const n = Math.floor(Number(value));
  if (!Number.isFinite(n) || n < 1) return 0;
  return Math.min(n, max);
}

/**
 * What the caller sees when a function fails. Sign-in and permission problems get 401/403 with a fixed
 * sentence; anything else is a 500 with the generic fallback, so internal error text never reaches the client.
 */
function failureResponse(err, fallbackMessage) {
  const message = String((err && err.message) || "");
  const code = String((err && err.code) || "");
  if (message === "Missing Bearer token" || code.startsWith("auth/")) {
    return { status: 401, message: "Please sign in again." };
  }
  if (message === "User profile not found" || message === "Insufficient permissions") {
    return { status: 403, message: "You do not have permission to do this." };
  }
  return { status: 500, message: fallbackMessage };
}

/**
 * Fixed-window rate limit. Given the stored record ({count, windowStart}) it says whether one more
 * request is allowed and what to store next.
 */
function rateLimitDecision(record, now, { limit, windowMs = RATE_WINDOW_MS }) {
  const expired = !record || !Number.isFinite(record.windowStart) || now - record.windowStart >= windowMs;
  const count = expired ? 0 : Number(record.count) || 0;
  const windowStart = expired ? now : record.windowStart;
  if (count >= limit) {
    return { allowed: false, next: { count, windowStart } };
  }
  return { allowed: true, next: { count: count + 1, windowStart } };
}

/** Keys the FCM service reserves; sending one makes the whole message fail. */
function isReservedDataKey(key) {
  const k = String(key || "").trim().toLowerCase();
  if (!k) return true;
  return k.startsWith("google") || k.startsWith("gcm") ||
    ["from", "notification", "message_type", "collapse_key"].includes(k);
}

/** Keeps at most `max` distinct, non-empty user ids. */
function limitUserIds(userIds, max = MAX_TARGETED_USERS) {
  const seen = new Set();
  const out = [];
  for (const raw of Array.isArray(userIds) ? userIds : []) {
    const uid = String(raw === undefined || raw === null ? "" : raw).trim();
    // Realtime Database keys cannot contain these characters
    if (!uid || /[.#$\[\]\/]/.test(uid) || seen.has(uid)) continue;
    seen.add(uid);
    out.push(uid);
    if (out.length >= max) break;
  }
  return out;
}

/** The hour (0-23) in Sri Lanka, whatever time zone the server runs in. */
function colomboHour(date = new Date()) {
  const text = new Intl.DateTimeFormat("en-GB", { hour: "numeric", hourCycle: "h23", timeZone: "Asia/Colombo" }).format(date);
  return Number(text) % 24;
}

module.exports = {
  MAX_ORDER_LINE_QUANTITY,
  MAX_TARGETED_USERS,
  RATE_LIMIT_PER_EMAIL,
  RATE_LIMIT_PER_IP,
  RATE_WINDOW_MS,
  escapeHtml,
  plainName,
  generateOtp,
  clampQuantity,
  failureResponse,
  rateLimitDecision,
  isReservedDataKey,
  limitUserIds,
  colomboHour,
};
