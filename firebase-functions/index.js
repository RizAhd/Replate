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
  "hotel_approved",
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
  if (candidate.includes("approved") && candidate.includes("hotel")) return "hotel_approved";
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

async function loadUserNotificationRecord(uid) {
  if (!uid) return null;

  const userSnap = await db.ref(`users/${uid}`).get();
  if (!userSnap.exists()) return null;

  const user = userSnap.val() || {};
  const role = roleFromUser(user);
  let token = String(user.fcmToken || user.notification?.fcmToken || "").trim();
  const cleanupPaths = [
    `users/${uid}/fcmToken`,
    `users/${uid}/notification/fcmToken`,
  ];

  if (role === "hotel") {
    const hotelsSnap = await db.ref("hotels").orderByChild("userId").equalTo(uid).get();
    if (hotelsSnap.exists()) {
      hotelsSnap.forEach((child) => {
        const hotel = child.val() || {};
        cleanupPaths.push(`hotels/${child.key}/fcmToken`);
        cleanupPaths.push(`hotels/${child.key}/notification/fcmToken`);
        if (!token) {
          token = String(hotel.fcmToken || hotel.notification?.fcmToken || "").trim();
        }
      });
    }
  }

  return { uid, role, token, cleanupPaths, user };
}

async function loadHotelNotificationRecord(hotelId) {
  if (!hotelId) return null;

  const directHotelSnap = await db.ref(`hotels/${hotelId}`).get();
  if (directHotelSnap.exists()) {
    const hotel = directHotelSnap.val() || {};
    const userId = String(hotel.userId || hotel.ownerUid || hotel.uid || hotelId).trim();
    const userRecord = await loadUserNotificationRecord(userId);
    if (userRecord) return userRecord;

    const token = String(hotel.fcmToken || hotel.notification?.fcmToken || "").trim();
    return {
      uid: userId,
      role: "hotel",
      token,
      cleanupPaths: [
        `hotels/${hotelId}/fcmToken`,
        `hotels/${hotelId}/notification/fcmToken`,
      ],
      user: hotel,
    };
  }

  return loadUserNotificationRecord(String(hotelId).trim());
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
    if (!record || !Array.isArray(record.cleanupPaths)) {
      return;
    }

    record.cleanupPaths.forEach((path) => {
      if (!path) return;
      removals.push(db.ref(path).remove());
    });
  });

  if (removals.length > 0) {
    await Promise.allSettled(removals);
  }
}

async function sendToUserRecords(userRecords, payload) {
  const valid = userRecords.filter((u) => u && u.token);
  console.log(`[sendToUserRecords] Total records=${userRecords.length}, valid (with token)=${valid.length}`);
  if (valid.length === 0) {
    console.warn("[sendToUserRecords] No valid tokens found, skipping send");
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
          channelId: "replate_notifications_v3",
          priority: "high",
          defaultSound: true,
          defaultVibrateTimings: true,
        },
      },
    });

    successCount += response.successCount;
    failureCount += response.failureCount;
    console.log(`[sendToUserRecords] Batch: success=${response.successCount}, failure=${response.failureCount}`);

    const failures = response.responses
      .map((r, index) => ({ ...r, index }))
      .filter((r) => !r.success);

    if (failures.length > 0) {
      failures.forEach((f) => {
        console.error(`[sendToUserRecords] Token failed: index=${f.index}, error=${f.error?.code || f.error?.message || "unknown"}`);
      });
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
        channelId: "replate_notifications_v3",
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

    const users = (await Promise.all(userIds.map((uid) => loadUserNotificationRecord(String(uid).trim())))).filter(Boolean);
    const result = await sendToUserRecords(users, payload);
    await persistNotificationForUsers(users.map((u) => u.uid), payload, requester.role === "hotel" ? "hotel_targeted" : "admin_targeted");

    await db.ref("notificationLogs").push({
      senderUid: requester.uid,
      senderRole: requester.role,
      action: "targeted",
      userIds: users.map((u) => u.uid),
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

const sendSelfTestPushHandler = async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const requester = await verifyRequestAuth(req, ["admin", "hotel", "customer"]);
    const record = await loadUserNotificationRecord(requester.uid);
    if (!record || !record.token) {
      return res.status(400).json({ success: false, message: "Missing FCM token for current user" });
    }

    const payload = buildPayload({
      title: req.body?.title || "REPLATE Test Push",
      body: req.body?.body || "If you can see this popup, push notifications are working.",
      category: req.body?.category || "admin_announcement",
      route: req.body?.route || "notifications",
      ...req.body,
    });

    const result = await sendToUserRecords([record], payload);
    await persistNotificationForUsers([requester.uid], payload, "self_test");

    await db.ref("notificationLogs").push({
      senderUid: requester.uid,
      senderRole: requester.role,
      action: "self_test",
      payload,
      result,
      createdAt: Date.now(),
    });

    return res.status(200).json({ success: true, result });
  } catch (err) {
    console.error("sendSelfTestPush error", err);
    return res.status(500).json({ success: false, message: err.message || "Failed to send self-test notification" });
  }
};

// Canonical endpoint used by Android app:
// https://asia-southeast1-replate-food-rescue.cloudfunctions.net/sendSelfTestPush
exports.sendSelfTestPush = functions.region(REGION).https.onRequest(sendSelfTestPushHandler);


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
    console.log(`[onOrderCreatedNotify] TRIGGER FIRED for orderId=${orderId}`);

    const customerId = String(order.userId || "").trim();
    const hotelId = String(order.hotelId || "").trim();
    const customerName = String(order.customerName || order.userName || "Customer");
    const hotelName = String(order.hotelName || "Hotel");
    console.log(`[onOrderCreatedNotify] customerId=${customerId}, hotelId=${hotelId}, customerName=${customerName}, hotelName=${hotelName}`);

    if (customerId) {
      const customerRecord = await loadUserNotificationRecord(customerId);
      console.log(`[onOrderCreatedNotify] Customer record: uid=${customerRecord?.uid}, hasToken=${!!customerRecord?.token}`);
      if (customerRecord) {
        const payload = buildPayload({
          title: "Order placed successfully",
          body: `Your order at ${hotelName} has been placed.`,
          type: "order_placed",
          category: "order_placed",
          route: "order",
          orderId,
          hotelId,
        });

        if (customerRecord.token) {
          const result = await sendToUserRecords([customerRecord], payload);
          console.log(`[onOrderCreatedNotify] FCM send to customer result: ${JSON.stringify(result)}`);
        } else {
          console.warn(`[onOrderCreatedNotify] Customer ${customerId} has no FCM token, skipping push`);
        }
        // Note: Android app already persists in-app notification for customer, so skip here to avoid duplicates
      }
    } else {
      console.warn("[onOrderCreatedNotify] No customerId in order, skipping customer notification");
    }

    if (hotelId) {
      const hotelRecord = await loadHotelNotificationRecord(hotelId);
      console.log(`[onOrderCreatedNotify] Hotel record: uid=${hotelRecord?.uid}, hasToken=${!!hotelRecord?.token}`);
      if (hotelRecord && hotelRecord.uid) {
        const payload = buildPayload({
          title: "New order received",
          body: `${customerName} placed a new order.`,
          type: "order_placed",
          category: "hotel_new_order",
          route: "order",
          orderId,
          hotelId,
        });

        if (hotelRecord.token) {
          const result = await sendToUserRecords([hotelRecord], payload);
          console.log(`[onOrderCreatedNotify] FCM send to hotel result: ${JSON.stringify(result)}`);
        } else {
          console.warn(`[onOrderCreatedNotify] Hotel ${hotelId} owner has no FCM token, skipping push`);
        }
        // Note: Android app already persists in-app notification for hotel, so skip here to avoid duplicates
      } else {
        console.warn(`[onOrderCreatedNotify] Could not resolve hotel record for hotelId=${hotelId}`);
      }
    } else {
      console.warn("[onOrderCreatedNotify] No hotelId in order, skipping hotel notification");
    }

    console.log(`[onOrderCreatedNotify] COMPLETED for orderId=${orderId}`);

    // Server-side safety net: decrement food quantity & auto-deactivate if sold out
    try {
      const items = order.items || [];
      for (const item of items) {
        const foodId = String(item.foodId || "").trim();
        const orderedQty = Number(item.quantity) || 1;
        if (!foodId) continue;

        const foodRef = db.ref(`foodItems/${foodId}`);
        const foodSnap = await foodRef.get();
        if (!foodSnap.exists()) continue;

        const currentQty = Number(foodSnap.child("quantity").val()) || 0;
        const newQty = Math.max(0, currentQty - orderedQty);
        await foodRef.child("quantity").set(newQty);

        if (newQty <= 0) {
          await foodRef.child("status").set("sold_out");
          await foodRef.child("isAvailable").set(false);
          console.log(`[onOrderCreatedNotify] Food ${foodId} auto-deactivated (sold out)`);
        } else {
          console.log(`[onOrderCreatedNotify] Food ${foodId} quantity updated: ${currentQty} -> ${newQty}`);
        }
      }
    } catch (qtyErr) {
      console.error("[onOrderCreatedNotify] Error decrementing food quantity", qtyErr);
    }

    return null;
  });

exports.onOrderStatusChangedNotify = functions
  .region(REGION)
  .database.ref("/orders/{orderId}")
  .onUpdate(async (change, context) => {
    const before = change.before.val() || {};
    const after = change.after.val() || {};
    const orderId = context.params.orderId;
    console.log(`[onOrderStatusChangedNotify] TRIGGER FIRED for orderId=${orderId}, before.orderStatus=${before.orderStatus}, after.orderStatus=${after.orderStatus}`);

    if (before.orderStatus === after.orderStatus) {
      console.log(`[onOrderStatusChangedNotify] No status change, skipping`);
      return null;
    }

    const userId = String(after.userId || "").trim();
    const hotelId = String(after.hotelId || "").trim();
    const status = String(after.orderStatus || "updated").toLowerCase();
    console.log(`[onOrderStatusChangedNotify] userId=${userId}, hotelId=${hotelId}, newStatus=${status}`);

    const statusMap = {
      pending_pickup: {
        title: "Order accepted",
        body: "Your order is accepted and waiting for pickup.",
        category: "order_accepted",
      },
      accepted: {
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
      ready_for_pickup: {
        title: "Pickup ready",
        body: "Your meal is ready for pickup.",
        category: "pickup_ready",
      },
      pending_delivery: {
        title: "Order accepted",
        body: "Your order is accepted and out for delivery.",
        category: "order_accepted",
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
    console.log(`[onOrderStatusChangedNotify] Resolved content: ${JSON.stringify(content)}`);

    const customerPayload = buildPayload({
      ...content,
      type: "order_status",
      route: "order",
      orderId,
      hotelId,
      data: { newStatus: status },
    });

    if (userId) {
      const customerRecord = await loadUserNotificationRecord(userId);
      console.log(`[onOrderStatusChangedNotify] Customer record: uid=${customerRecord?.uid}, hasToken=${!!customerRecord?.token}`);
      if (customerRecord) {
        if (customerRecord.token) {
          const result = await sendToUserRecords([customerRecord], customerPayload);
          console.log(`[onOrderStatusChangedNotify] FCM send to customer result: ${JSON.stringify(result)}`);
        } else {
          console.warn(`[onOrderStatusChangedNotify] Customer ${userId} has no FCM token`);
        }
        await persistNotificationForUsers([customerRecord.uid], customerPayload, "order_status_update");
      }
    }

    if (status === "cancelled" && hotelId) {
      const hotelRecord = await loadHotelNotificationRecord(hotelId);
      console.log(`[onOrderStatusChangedNotify] Hotel record for cancellation: uid=${hotelRecord?.uid}, hasToken=${!!hotelRecord?.token}`);
      if (hotelRecord && hotelRecord.uid) {
        const hotelPayload = buildPayload({
          title: "Order cancelled",
          body: `${after.customerName || after.userName || "A customer"} cancelled an order.`,
          type: "order_cancelled",
          category: "order_cancelled",
          route: "order",
          orderId,
          hotelId,
          data: { newStatus: status },
        });

        if (hotelRecord.token) {
          const result = await sendToUserRecords([hotelRecord], hotelPayload);
          console.log(`[onOrderStatusChangedNotify] FCM send to hotel result: ${JSON.stringify(result)}`);
        }
        await persistNotificationForUsers([hotelRecord.uid], hotelPayload, "order_status_update");
      }
    }

    console.log(`[onOrderStatusChangedNotify] COMPLETED for orderId=${orderId}`);
    return null;
  });

exports.onHotelStatusChangedNotify = functions
  .region(REGION)
  .database.ref("/hotels/{hotelId}")
  .onUpdate(async (change, context) => {
    const before = change.before.val() || {};
    const after = change.after.val() || {};
    const beforeStatus = String(before.status || "").trim().toLowerCase();
    const afterStatus = String(after.status || "").trim().toLowerCase();
    const hotelId = context.params.hotelId;
    console.log(`[onHotelStatusChangedNotify] TRIGGER FIRED for hotelId=${hotelId}, before=${beforeStatus}, after=${afterStatus}`);

    if (!afterStatus || beforeStatus === afterStatus) {
      console.log(`[onHotelStatusChangedNotify] No status change, skipping`);
      return null;
    }

    const hotelRecord = await loadHotelNotificationRecord(hotelId);
    console.log(`[onHotelStatusChangedNotify] Hotel record: uid=${hotelRecord?.uid}, hasToken=${!!hotelRecord?.token}`);
    if (!hotelRecord || !hotelRecord.uid) return null;

    let payload = null;
    if (afterStatus === "approved") {
      payload = buildPayload({
        title: "Hotel approved",
        body: "Your hotel account is approved. You can now manage orders and listings.",
        type: "hotel_approved",
        category: "hotel_approved",
        route: "hotel_dashboard",
        hotelId,
        data: { status: afterStatus },
      });
    } else if (afterStatus === "rejected") {
      payload = buildPayload({
        title: "Hotel status updated",
        body: "Your hotel registration needs attention. Please contact support or review your submitted details.",
        type: "admin_announcement",
        category: "admin_announcement",
        route: "profile",
        hotelId,
        data: { status: afterStatus },
      });
    }

    if (!payload) {
      console.log(`[onHotelStatusChangedNotify] No payload for status=${afterStatus}, skipping`);
      return null;
    }

    if (hotelRecord.token) {
      const result = await sendToUserRecords([hotelRecord], payload);
      console.log(`[onHotelStatusChangedNotify] FCM send result: ${JSON.stringify(result)}`);
    } else {
      console.warn(`[onHotelStatusChangedNotify] Hotel ${hotelId} owner has no FCM token`);
    }
    await persistNotificationForUsers([hotelRecord.uid], payload, "hotel_status_update");
    console.log(`[onHotelStatusChangedNotify] COMPLETED for hotelId=${hotelId}`);
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
