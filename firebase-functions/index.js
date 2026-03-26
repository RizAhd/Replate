const crypto = require("crypto");
const admin = require("firebase-admin");
const functions = require("firebase-functions");
const nodemailer = require("nodemailer");

admin.initializeApp();

const db = admin.database();
const REGION = "asia-southeast1";
const MAX_MULTICAST = 500;
const OTP_EXPIRY_MINUTES = 10;
const OTP_MAX_ATTEMPTS = 5;
const VALID_NOTIFICATION_CATEGORIES = new Set([
  "welcome",
  "new_deals",
  "hot_deals",
  "order_placed",
  "order_accepted",
  "order_rejected",
  "order_cancelled",
  "pickup_ready",
  "pickup_reminder",
  "admin_announcement",
  "admin_broadcast",
  "hotel_new_order",
  "profile_updated",
  "login_success",
]);

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function keyFromEmail(email) {
  return Buffer.from(email.toLowerCase().trim()).toString("base64url");
}

function generateOtp() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function getTransporter() {
  const email = process.env.OTP_EMAIL;
  const pass = process.env.OTP_EMAIL_APP_PASSWORD;

  if (!email || !pass) {
    throw new Error("Missing OTP_EMAIL or OTP_EMAIL_APP_PASSWORD env variables.");
  }

  return nodemailer.createTransport({
    service: "gmail",
    auth: { user: email, pass },
  });
}

function setCorsHeaders(res) {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
}

function normalizeRole(role) {
  const value = String(role || "customer").trim().toLowerCase();
  return value || "customer";
}

function roleFromUser(user) {
  return normalizeRole((user && (user.role || user.userType)) || "customer");
}

async function verifyRequestAuth(req, allowedRoles = ["admin"]) {
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    throw new Error("Missing Bearer token");
  }

  const idToken = authHeader.replace("Bearer ", "").trim();
  const decoded = await admin.auth().verifyIdToken(idToken);
  const uid = decoded.uid;

  const roleFromClaim = normalizeRole(decoded.role || (decoded.roles && decoded.roles[0]));
  if (allowedRoles.includes(roleFromClaim)) {
    return { uid, role: roleFromClaim };
  }

  const userSnap = await db.ref(`users/${uid}`).get();
  if (!userSnap.exists()) {
    throw new Error("User profile not found");
  }

  const role = roleFromUser(userSnap.val());
  if (!allowedRoles.includes(role)) {
    throw new Error("Insufficient permissions");
  }

  return { uid, role };
}

function normalizeCategory(rawCategory, fallback = "admin_broadcast") {
  const candidate = String(rawCategory || fallback).trim().toLowerCase();
  if (VALID_NOTIFICATION_CATEGORIES.has(candidate)) return candidate;

  if (candidate.includes("deal")) return candidate.includes("hot") ? "hot_deals" : "new_deals";
  if (candidate.includes("reject")) return "order_rejected";
  if (candidate.includes("accept")) return "order_accepted";
  if (candidate.includes("cancel")) return "order_cancelled";
  if (candidate.includes("pickup") && candidate.includes("remind")) return "pickup_reminder";
  if (candidate.includes("pickup") || candidate.includes("ready")) return "pickup_ready";
  if (candidate.includes("hotel") || candidate.includes("new_order")) return "hotel_new_order";
  if (candidate.includes("place") || candidate.includes("create") || candidate.includes("order")) return "order_placed";
  if (candidate.includes("profile")) return "profile_updated";
  if (candidate.includes("login")) return "login_success";
  if (candidate.includes("welcome")) return "welcome";
  if (candidate.includes("announce") || candidate.includes("broadcast") || candidate.includes("admin")) {
    return "admin_announcement";
  }
  return fallback;
}

function buildPayload(input = {}) {
  const title = String(input.title || "REPLATE").trim();
  const body = String(input.body || "You have a new update").trim();
  const type = String(input.type || "general").trim();
  const route = String(input.route || "notifications").trim();
  const deepLink = String(input.deepLink || "").trim();
  const orderId = String(input.orderId || "").trim();
  const hotelId = String(input.hotelId || "").trim();
  const category = normalizeCategory(input.category || input.type || "admin_broadcast");
  const extraData = input.data && typeof input.data === "object" ? input.data : {};

  return {
    title,
    body,
    type,
    category,
    route,
    deepLink,
    orderId,
    hotelId,
    data: {
      title,
      body,
      type,
      category,
      route,
      deepLink,
      orderId,
      hotelId,
      ...Object.keys(extraData).reduce((acc, key) => {
        acc[key] = String(extraData[key]);
        return acc;
      }, {}),
    },
  };
}

function chunk(arr, size) {
  const out = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

async function loadUsersByRole(targetRole) {
  const usersSnap = await db.ref("users").get();
  if (!usersSnap.exists()) return [];

  const users = [];
  usersSnap.forEach((child) => {
    const value = child.val() || {};
    const role = roleFromUser(value);

    if (!targetRole || targetRole === "all_users" || role === targetRole) {
      users.push({ uid: child.key, ...value, role });
    }
  });

  return users;
}

async function persistNotificationForUsers(userIds, payload, source = "system") {
  if (!Array.isArray(userIds) || userIds.length === 0) return;

  const now = Date.now();
  const updates = {};
  userIds.forEach((uid) => {
    const key = db.ref(`notifications/${uid}`).push().key;
    updates[`notifications/${uid}/${key}`] = {
      title: payload.title,
      body: payload.body,
      category: normalizeCategory(payload.category, "admin_broadcast"),
      type: payload.type,
      route: payload.route,
      deepLink: payload.deepLink,
      orderId: payload.orderId,
      hotelId: payload.hotelId,
      source,
      read: false,
      timestamp: now,
    };
  });
  await db.ref().update(updates);
}

async function cleanupInvalidTokens(records, failures) {
  const removals = [];
  failures.forEach((failure) => {
    const code = failure.error && failure.error.code ? failure.error.code : "";
    if (!code.includes("registration-token-not-registered") && !code.includes("invalid-registration-token")) {
      return;
    }

    const record = records[failure.index];
    if (record && record.uid) {
      removals.push(db.ref(`users/${record.uid}/fcmToken`).remove());
    }
  });

  if (removals.length > 0) {
    await Promise.allSettled(removals);
  }
}

async function sendToUserRecords(userRecords, payload) {
  const valid = userRecords.filter((u) => u && u.token);
  if (valid.length === 0) {
    return { successCount: 0, failureCount: 0 };
  }

  let successCount = 0;
  let failureCount = 0;

  const groups = chunk(valid, MAX_MULTICAST);
  for (const group of groups) {
    const tokens = group.map((u) => u.token);

    const response = await admin.messaging().sendEachForMulticast({
      tokens,
      notification: {
        title: payload.title,
        body: payload.body,
      },
      data: payload.data,
      android: {
        priority: "high",
        notification: {
          channelId: "replate_notifications_v2",
          priority: "high",
          defaultSound: true,
          defaultVibrateTimings: true,
        },
      },
    });

    successCount += response.successCount;
    failureCount += response.failureCount;

    const failures = response.responses
      .map((r, index) => ({ ...r, index }))
      .filter((r) => !r.success);

    if (failures.length > 0) {
      await cleanupInvalidTokens(group, failures);
    }
  }

  return { successCount, failureCount };
}

async function sendToTopic(topic, payload) {
  const message = {
    topic,
    notification: {
      title: payload.title,
      body: payload.body,
    },
    data: payload.data,
    android: {
      priority: "high",
      notification: {
        channelId: "replate_notifications_v2",
        priority: "high",
        defaultSound: true,
        defaultVibrateTimings: true,
      },
    },
  };

  const messageId = await admin.messaging().send(message);
  return { messageId };
}

exports.sendOtpEmail = functions.region(REGION).https.onRequest(async (req, res) => {
  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const { email, name } = req.body || {};

    if (!email || !/^\S+@\S+\.\S+$/.test(email)) {
      return res.status(400).json({ success: false, message: "Valid email is required" });
    }

    const otp = generateOtp();
    const otpHash = sha256(otp);
    const now = Date.now();
    const expiresAt = now + OTP_EXPIRY_MINUTES * 60 * 1000;

    const emailKey = keyFromEmail(email);

    await db.ref(`otpVerifications/${emailKey}`).set({
      email: email.toLowerCase().trim(),
      otpHash,
      expiresAt,
      createdAt: now,
      attempts: 0,
      verified: false,
    });

    const transporter = getTransporter();
    await transporter.sendMail({
      from: process.env.OTP_EMAIL,
      to: email,
      subject: "REPLATE Verification Code",
      text:
        `Hi ${name || "there"},\n\n` +
        `Your REPLATE verification code is: ${otp}\n` +
        `This code expires in ${OTP_EXPIRY_MINUTES} minutes.\n\n` +
        "If you did not request this, you can ignore this email.",
    });

    return res.status(200).json({ success: true, message: "OTP sent to your email" });
  } catch (err) {
    console.error("sendOtpEmail error", err);
    return res.status(500).json({ success: false, message: err.message || "Server error" });
  }
});

exports.verifyOtp = functions.region(REGION).https.onRequest(async (req, res) => {
  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const { email, otp } = req.body || {};

    if (!email || !otp) {
      return res.status(400).json({ success: false, message: "Email and OTP are required" });
    }

    const emailKey = keyFromEmail(email);
    const ref = db.ref(`otpVerifications/${emailKey}`);
    const snap = await ref.get();

    if (!snap.exists()) {
      return res.status(404).json({ success: false, message: "No OTP found. Please request again." });
    }

    const record = snap.val();

    if (record.verified) {
      return res.status(200).json({ success: true, message: "Already verified" });
    }

    if (Date.now() > Number(record.expiresAt || 0)) {
      await ref.remove();
      return res.status(400).json({ success: false, message: "OTP expired. Please request a new one." });
    }

    const attempts = Number(record.attempts || 0);
    if (attempts >= OTP_MAX_ATTEMPTS) {
      await ref.remove();
      return res.status(429).json({ success: false, message: "Too many attempts. Request a new OTP." });
    }

    const isMatch = sha256(String(otp).trim()) === String(record.otpHash || "");
    if (!isMatch) {
      await ref.update({ attempts: attempts + 1 });
      return res.status(400).json({ success: false, message: "Invalid OTP" });
    }

    await ref.update({ verified: true, verifiedAt: Date.now() });

    return res.status(200).json({ success: true, message: "OTP verified" });
  } catch (err) {
    console.error("verifyOtp error", err);
    return res.status(500).json({ success: false, message: err.message || "Server error" });
  }
});

exports.sendBroadcastNotification = functions.region(REGION).https.onRequest(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const requester = await verifyRequestAuth(req, ["admin"]);
    const body = req.body || {};
    const payload = buildPayload({
      category: "admin_broadcast",
      ...body,
    });
    const target = String(body.target || "all_users").trim();

    let result;
    let targetUserIds = [];

    if (target.startsWith("topic:")) {
      const topic = target.replace("topic:", "").trim();
      if (!topic) return res.status(400).json({ success: false, message: "Invalid topic target" });
      result = await sendToTopic(topic, payload);
    } else {
      const roleTarget = target === "all_users" ? null : normalizeRole(target);
      const users = await loadUsersByRole(roleTarget);
      const userRecords = users.map((u) => ({ uid: u.uid, token: u.fcmToken || "" }));
      targetUserIds = users.map((u) => u.uid);
      result = await sendToUserRecords(userRecords, payload);
      await persistNotificationForUsers(targetUserIds, payload, "admin_broadcast");
    }

    await db.ref("notificationLogs").push({
      senderUid: requester.uid,
      senderRole: requester.role,
      action: "broadcast",
      target,
      payload,
      result,
      createdAt: Date.now(),
    });

    return res.status(200).json({ success: true, result });
  } catch (err) {
    console.error("sendBroadcastNotification error", err);
    return res.status(500).json({ success: false, message: err.message || "Failed to send notification" });
  }
});

exports.sendTargetedNotification = functions.region(REGION).https.onRequest(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const requester = await verifyRequestAuth(req, ["admin", "hotel"]);
    const body = req.body || {};
    const userIds = Array.isArray(body.userIds) ? body.userIds.filter(Boolean) : [];
    if (userIds.length === 0) {
      return res.status(400).json({ success: false, message: "userIds is required" });
    }

    const payload = buildPayload({
      category: body.category || "admin_broadcast",
      ...body,
    });

    const users = await Promise.all(
      userIds.map(async (uid) => {
        const snap = await db.ref(`users/${uid}`).get();
        if (!snap.exists()) return null;
        const value = snap.val() || {};
        return { uid, token: value.fcmToken || "" };
      })
    );

    const filtered = users.filter(Boolean);
    const result = await sendToUserRecords(filtered, payload);
    await persistNotificationForUsers(userIds, payload, requester.role === "hotel" ? "hotel_targeted" : "admin_targeted");

    await db.ref("notificationLogs").push({
      senderUid: requester.uid,
      senderRole: requester.role,
      action: "targeted",
      userIds,
      payload,
      result,
      createdAt: Date.now(),
    });

    return res.status(200).json({ success: true, result });
  } catch (err) {
    console.error("sendTargetedNotification error", err);
    return res.status(500).json({ success: false, message: err.message || "Failed to send notification" });
  }
});

exports.sendDealNotification = functions.region(REGION).https.onRequest(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const requester = await verifyRequestAuth(req, ["admin", "hotel"]);
    const payload = buildPayload({
      type: "deal",
      category: req.body && req.body.isHotDeal ? "hot_deals" : "new_deals",
      route: "deals",
      ...req.body,
    });

    const result = await sendToTopic("deals_customers", payload);

    await db.ref("notificationLogs").push({
      senderUid: requester.uid,
      senderRole: requester.role,
      action: "deal_broadcast",
      payload,
      result,
      createdAt: Date.now(),
    });

    return res.status(200).json({ success: true, result });
  } catch (err) {
    console.error("sendDealNotification error", err);
    return res.status(500).json({ success: false, message: err.message || "Failed to send deal notification" });
  }
});

exports.onUserProfileCreatedNotify = functions
  .region(REGION)
  .database.ref("/users/{userId}")
  .onCreate(async (snapshot, context) => {
    const user = snapshot.val() || {};
    const uid = context.params.userId;
    const token = String(user.fcmToken || "").trim();

    const payload = buildPayload({
      title: "Welcome to REPLATE",
      body: "Because every meal matters. Start exploring rescued food deals near you.",
      type: "welcome",
      category: "welcome",
      route: "home",
    });

    if (token) {
      await sendToUserRecords([{ uid, token }], payload);
    }
    await persistNotificationForUsers([uid], payload, "user_created");
    return null;
  });

exports.onOrderCreatedNotify = functions
  .region(REGION)
  .database.ref("/orders/{orderId}")
  .onCreate(async (snapshot, context) => {
    const order = snapshot.val() || {};
    const orderId = context.params.orderId;

    const customerId = String(order.userId || "").trim();
    const hotelId = String(order.hotelId || "").trim();
    const customerName = String(order.customerName || "Customer");
    const hotelName = String(order.hotelName || "Hotel");

    if (customerId) {
      const userSnap = await db.ref(`users/${customerId}`).get();
      if (userSnap.exists()) {
        const token = userSnap.val().fcmToken || "";
        const payload = buildPayload({
          title: "Order placed successfully",
          body: `Your order at ${hotelName} has been placed.`,
          type: "order_placed",
          category: "order_placed",
          route: "order",
          orderId,
          hotelId,
        });

        if (token) {
          await sendToUserRecords([{ uid: customerId, token }], payload);
        }
        await persistNotificationForUsers([customerId], payload, "order_created");
      }
    }

    if (hotelId) {
      const hotelSnap = await db.ref(`hotels/${hotelId}`).get();
      if (hotelSnap.exists()) {
        const hotelVal = hotelSnap.val() || {};
        const hotelUserId = String(hotelVal.userId || "").trim();
        if (hotelUserId) {
          const hotelUserSnap = await db.ref(`users/${hotelUserId}`).get();
          const hotelToken = hotelUserSnap.exists() ? (hotelUserSnap.val().fcmToken || "") : "";

          const payload = buildPayload({
            title: "New order received",
            body: `${customerName} placed a new order.`,
            type: "order_placed",
            category: "hotel_new_order",
            route: "order",
            orderId,
            hotelId,
          });

          if (hotelToken) {
            await sendToUserRecords([{ uid: hotelUserId, token: hotelToken }], payload);
          }
          await persistNotificationForUsers([hotelUserId], payload, "order_created");
        }
      }
    }

    return null;
  });

exports.onOrderStatusChangedNotify = functions
  .region(REGION)
  .database.ref("/orders/{orderId}")
  .onUpdate(async (change, context) => {
    const before = change.before.val() || {};
    const after = change.after.val() || {};

    if (before.orderStatus === after.orderStatus) return null;

    const orderId = context.params.orderId;
    const userId = String(after.userId || "").trim();
    if (!userId) return null;

    const userSnap = await db.ref(`users/${userId}`).get();
    if (!userSnap.exists()) return null;

    const token = userSnap.val().fcmToken || "";
    const status = String(after.orderStatus || "updated").toLowerCase();

    const statusMap = {
      pending_pickup: {
        title: "Order accepted",
        body: "Your order is accepted and waiting for pickup.",
        category: "order_accepted",
      },
      rejected: {
        title: "Order rejected",
        body: "Your order was rejected by the hotel.",
        category: "order_rejected",
      },
      cancelled: {
        title: "Order cancelled",
        body: "Your order has been cancelled.",
        category: "order_cancelled",
      },
      ready: {
        title: "Pickup ready",
        body: "Your meal is ready for pickup.",
        category: "pickup_ready",
      },
      completed: {
        title: "Order completed",
        body: "Order marked completed. Enjoy your meal!",
        category: "pickup_ready",
      },
    };

    const content = statusMap[status] || {
      title: "Order updated",
      body: `Your order status is now ${status.replace(/_/g, " ")}.`,
      category: "order_accepted",
    };

    const payload = buildPayload({
      ...content,
      type: "order_status",
      route: "order",
      orderId,
      hotelId: after.hotelId || "",
      data: { newStatus: status },
    });

    if (token) {
      await sendToUserRecords([{ uid: userId, token }], payload);
    }
    await persistNotificationForUsers([userId], payload, "order_status_update");

    return null;
  });

exports.onFoodItemCreatedNotify = functions
  .region(REGION)
  .database.ref("/foodItems/{foodId}")
  .onCreate(async (snapshot) => {
    const food = snapshot.val() || {};
    const status = String(food.status || "").toLowerCase();
    if (status && status !== "available") return null;

    const payload = buildPayload({
      title: "New deal near you",
      body: `${food.hotelName || "A hotel"} added ${food.foodName || "a meal"} at discount.`,
      type: "new_deal",
      category: "new_deals",
      route: "deals",
      hotelId: food.hotelId || "",
      data: {
        foodId: food.foodId || "",
        discountPercentage: String(food.discountPercentage || food.discountPercent || ""),
      },
    });

    await sendToTopic("deals_customers", payload);
    return null;
  });

exports.sendScheduledEngagementNotifications = functions
  .region(REGION)
  .pubsub.schedule("every 2 hours")
  .timeZone("Asia/Colombo")
  .onRun(async () => {
    const hour = new Date().getHours();
    let segment = "meal";

    if (hour >= 6 && hour < 11) segment = "breakfast";
    else if (hour >= 11 && hour < 16) segment = "lunch";
    else if (hour >= 16 && hour < 22) segment = "dinner";

    const payload = buildPayload({
      title: "Trending food rescue deals",
      body: `Fresh ${segment} deals are live near you. Grab them before they are gone!`,
      type: "engagement_reminder",
      category: "hot_deals",
      route: "deals",
      data: { segment },
    });

    await sendToTopic("deals_customers", payload);
    return null;
  });
