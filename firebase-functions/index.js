const crypto = require("crypto");
const admin = require("firebase-admin");
const functions = require("firebase-functions");
const nodemailer = require("nodemailer");
const { reminderFor, reminderText, LOOKAHEAD_MS } = require("./pickup-reminder");
const { createStock } = require("./stock");
const {
  MAX_TARGETED_USERS,
  RATE_LIMIT_PER_EMAIL,
  RATE_LIMIT_PER_IP,
  escapeHtml,
  plainName,
  generateOtp,
  failureResponse,
  rateLimitDecision,
  isReservedDataKey,
  limitUserIds,
  colomboHour,
} = require("./safe");

admin.initializeApp();

const db = admin.database();
const { adjustStockForOrder, restoreStockForOrder } = createStock(db);
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

/**
 * Public endpoints (they send email) are limited per address and per caller IP so they cannot be used
 * to flood someone's inbox or to burn the mail quota. Counters live in rateLimits/ (no client access).
 */
async function allowRequest(req, email, kind) {
  const forwarded = String(req.headers["x-forwarded-for"] || "").split(",")[0].trim();
  const ip = forwarded || String(req.ip || "unknown");
  const checks = [
    [`${kind}_email_${sha256(String(email).toLowerCase().trim()).slice(0, 32)}`, RATE_LIMIT_PER_EMAIL],
    [`${kind}_ip_${sha256(ip).slice(0, 32)}`, RATE_LIMIT_PER_IP],
  ];
  for (const [key, limit] of checks) {
    let allowed = false;
    await db.ref(`rateLimits/${key}`).transaction((record) => {
      const decision = rateLimitDecision(record, Date.now(), { limit });
      allowed = decision.allowed;
      return decision.next;
    });
    if (!allowed) return false;
  }
  return true;
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
        if (!isReservedDataKey(key)) acc[key] = String(extraData[key]);
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
      data: payload.data,
      android: {
        priority: "high",
        notification: {
          title: payload.title || "",
          body: payload.body || "",
          channelId: "replate_notifications_v4",
          icon: "replate",
          color: "#1A6B3C",
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
    data: payload.data,
    android: {
      priority: "high",
      notification: {
        title: payload.title || "",
        body: payload.body || "",
        channelId: "replate_notifications_v4",
        icon: "replate",
        color: "#1A6B3C",
        defaultSound: true,
        defaultVibrateTimings: true,
      },
    },
  };

  const messageId = await admin.messaging().send(message);
  return { messageId };
}

// ── Custom branded verification email ──────────────────────────────
exports.sendCustomVerificationEmail = functions.region(REGION).https.onRequest(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const { email, name } = req.body || {};

    if (!email || !/^\S+@\S+\.\S+$/.test(email)) {
      return res.status(400).json({ success: false, message: "Valid email is required" });
    }

    if (!(await allowRequest(req, email, "verifyMail"))) {
      return res.status(429).json({ success: false, message: "Too many requests. Please wait a few minutes and try again." });
    }
    const textName = plainName(name);
    const safeName = escapeHtml(textName);

    // Generate the Firebase verification link
    const actionCodeSettings = {
      url: `https://replate-app.web.app/verified?email=${encodeURIComponent(email)}`,
      handleCodeInApp: false,
    };
    const verificationLink = await admin.auth().generateEmailVerificationLink(email, actionCodeSettings);

    const transporter = getTransporter();
    await transporter.sendMail({
      from: `"REPLATE" <${process.env.OTP_EMAIL}>`,
      to: email,
      subject: "Verify your REPLATE account",
      text: `Hi ${textName},\n\nWelcome to REPLATE! Please verify your email address by clicking the link below:\n\n${verificationLink}\n\nThis link will expire in 24 hours.\n\nIf you didn't create this account, you can safely ignore this email.\n\n— The REPLATE Team`,
      html: `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Verify your REPLATE account</title>
</head>
<body style="margin:0;padding:0;background:#f5f5f5;font-family:'Segoe UI',Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:32px 16px;">
    <tr>
      <td align="center">
        <table width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
          <!-- Header -->
          <tr>
            <td style="background:linear-gradient(135deg,#1A6B3C 0%,#25A55A 100%);padding:40px 32px;text-align:center;">
              <div style="font-size:36px;margin-bottom:8px;">🍃</div>
              <div style="color:#ffffff;font-size:28px;font-weight:700;letter-spacing:1px;">REPLATE</div>
              <div style="color:rgba(255,255,255,0.8);font-size:13px;margin-top:4px;">Because Every Meal Matters</div>
            </td>
          </tr>
          <!-- Body -->
          <tr>
            <td style="padding:36px 32px 28px;">
              <p style="margin:0 0 8px;font-size:20px;font-weight:600;color:#1C1C1E;">Welcome, ${safeName}! 👋</p>
              <p style="margin:0 0 24px;font-size:14px;color:#6C6C6E;line-height:1.7;">
                Thank you for joining REPLATE. To get started and unlock all features, please verify your email address by clicking the button below.
              </p>
              <!-- CTA Button -->
              <div style="text-align:center;margin-bottom:28px;">
                <a href="${verificationLink}" style="display:inline-block;padding:14px 40px;background:linear-gradient(135deg,#1A6B3C,#25A55A);color:#ffffff;font-size:16px;font-weight:600;text-decoration:none;border-radius:12px;letter-spacing:0.5px;box-shadow:0 4px 12px rgba(26,107,60,0.3);">
                  Verify My Email
                </a>
              </div>
              <p style="margin:0 0 16px;font-size:13px;color:#9E9E9E;line-height:1.6;">
                Or copy and paste this link into your browser:
              </p>
              <div style="background:#f0f9f4;border:1px solid #E0F5E8;border-radius:10px;padding:12px 16px;margin-bottom:24px;word-break:break-all;">
                <a href="${verificationLink}" style="font-size:12px;color:#1A6B3C;text-decoration:none;">${verificationLink}</a>
              </div>
              <p style="margin:0 0 8px;font-size:13px;color:#6C6C6E;line-height:1.6;">
                This link will expire in <strong>24 hours</strong>. If you didn't create a REPLATE account, you can safely ignore this email.
              </p>
            </td>
          </tr>
          <!-- Footer -->
          <tr>
            <td style="background:#f9f9f9;padding:20px 32px;border-top:1px solid #EAEAEA;text-align:center;">
              <p style="margin:0 0 4px;font-size:12px;color:#9E9E9E;">© ${new Date().getFullYear()} REPLATE · Reducing Food Waste Together</p>
              <p style="margin:0;font-size:11px;color:#BDBDBD;">This is an automated email. Please do not reply.</p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`,
    });

    return res.status(200).json({ success: true, message: "Verification email sent" });
  } catch (err) {
    console.error("sendCustomVerificationEmail error", err);
    return res.status(500).json({ success: false, message: "We could not send the email right now. Please try again later." });
  }
});

exports.sendOtpEmail = functions.region(REGION).https.onRequest(async (req, res) => {
  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const { email, name } = req.body || {};

    if (!email || !/^\S+@\S+\.\S+$/.test(email)) {
      return res.status(400).json({ success: false, message: "Valid email is required" });
    }

    if (!(await allowRequest(req, email, "otpMail"))) {
      return res.status(429).json({ success: false, message: "Too many requests. Please wait a few minutes and try again." });
    }
    const textName = plainName(name);
    const safeName = escapeHtml(textName);

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
      from: `"REPLATE" <${process.env.OTP_EMAIL}>`,
      to: email,
      subject: "Your REPLATE Verification Code",
      text: `Hi ${textName},\n\nYour REPLATE verification code is: ${otp}\nThis code expires in ${OTP_EXPIRY_MINUTES} minutes.\n\nIf you did not request this, you can ignore this email.`,
      html: `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>REPLATE Verification</title>
</head>
<body style="margin:0;padding:0;background:#f5f5f5;font-family:'Segoe UI',Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:32px 16px;">
    <tr>
      <td align="center">
        <table width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
          <!-- Header -->
          <tr>
            <td style="background:linear-gradient(135deg,#1A6B3C 0%,#25A55A 100%);padding:36px 32px;text-align:center;">
              <div style="font-size:32px;margin-bottom:8px;">🍃</div>
              <div style="color:#ffffff;font-size:26px;font-weight:700;letter-spacing:1px;">REPLATE</div>
              <div style="color:rgba(255,255,255,0.8);font-size:13px;margin-top:4px;">Because Every Meal Matters</div>
            </td>
          </tr>
          <!-- Body -->
          <tr>
            <td style="padding:36px 32px 28px;">
              <p style="margin:0 0 8px;font-size:18px;font-weight:600;color:#1C1C1E;">Hi ${safeName} 👋</p>
              <p style="margin:0 0 28px;font-size:14px;color:#6C6C6E;line-height:1.6;">
                Here is your one-time verification code to complete your REPLATE registration.
              </p>
              <!-- OTP Box -->
              <div style="background:#f0f9f4;border:2px dashed #25A55A;border-radius:16px;padding:28px 24px;text-align:center;margin-bottom:28px;">
                <div style="font-size:44px;font-weight:800;letter-spacing:12px;color:#1A6B3C;font-family:'Courier New',monospace;">${otp}</div>
                <div style="margin-top:10px;font-size:13px;color:#6C6C6E;">⏱ Expires in <strong>${OTP_EXPIRY_MINUTES} minutes</strong></div>
              </div>
              <p style="margin:0 0 16px;font-size:13px;color:#6C6C6E;line-height:1.6;">
                For your security, do not share this code with anyone. REPLATE staff will never ask for your OTP.
              </p>
              <p style="margin:0;font-size:13px;color:#9E9E9E;">
                If you didn't request this code, you can safely ignore this email.
              </p>
            </td>
          </tr>
          <!-- Footer -->
          <tr>
            <td style="background:#f9f9f9;padding:20px 32px;border-top:1px solid #EAEAEA;text-align:center;">
              <p style="margin:0;font-size:12px;color:#9E9E9E;">© ${new Date().getFullYear()} REPLATE · Reducing Food Waste Together</p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`,
    });

    return res.status(200).json({ success: true, message: "OTP sent to your email" });
  } catch (err) {
    console.error("sendOtpEmail error", err);
    return res.status(500).json({ success: false, message: "We could not send the email right now. Please try again later." });
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

    // Count this guess first, atomically, so guesses sent in parallel cannot slip past the limit
    const counted = await ref.child("attempts").transaction((n) => (Number(n) || 0) + 1);
    if (Number(counted.snapshot.val()) > OTP_MAX_ATTEMPTS) {
      await ref.remove();
      return res.status(429).json({ success: false, message: "Too many attempts. Request a new OTP." });
    }

    const isMatch = sha256(String(otp).trim()) === String(record.otpHash || "");
    if (!isMatch) {
      return res.status(400).json({ success: false, message: "Invalid OTP" });
    }

    await ref.update({ verified: true, verifiedAt: Date.now() });

    return res.status(200).json({ success: true, message: "OTP verified" });
  } catch (err) {
    console.error("verifyOtp error", err);
    return res.status(500).json({ success: false, message: "We could not check the code right now. Please try again." });
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
      // Normalize audience target: "customers" → "customer", "hotels" → "hotel", "admins" → "admin"
      let roleTarget = null;
      if (target !== "all_users") {
        roleTarget = normalizeRole(target.replace(/s$/, "")); // strip trailing 's'
      }
      const users = await loadUsersByRole(roleTarget);
      // Resolve tokens more thoroughly — check both fcmToken and notification.fcmToken
      const userRecords = users.map((u) => ({
        uid: u.uid,
        token: String(u.fcmToken || (u.notification && u.notification.fcmToken) || "").trim(),
        cleanupPaths: [`users/${u.uid}/fcmToken`, `users/${u.uid}/notification/fcmToken`],
      }));
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
    const failure = failureResponse(err, "Failed to send notification");
    return res.status(failure.status).json({ success: false, message: failure.message });
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
    if (Array.isArray(body.userIds) && body.userIds.length > MAX_TARGETED_USERS) {
      return res.status(400).json({ success: false, message: `You can notify at most ${MAX_TARGETED_USERS} people at once` });
    }
    const userIds = limitUserIds(body.userIds);
    if (userIds.length === 0) {
      return res.status(400).json({ success: false, message: "userIds is required" });
    }

    // An admin may notify anyone. A hotel may only notify customers who ordered from it, so a
    // hotel account can never be used to message the whole user base.
    let allowedUserIds = userIds.map((uid) => String(uid).trim());
    if (requester.role === "hotel") {
      const profile = (await db.ref(`users/${requester.uid}`).get()).val() || {};
      const myHotelIds = new Set(await hotelIdsForUser(requester.uid, profile));
      const checked = await Promise.all(allowedUserIds.map(async (customerUid) => {
        const orders = await db.ref("orders").orderByChild("userId").equalTo(customerUid).get();
        let isMine = false;
        orders.forEach((order) => { if (myHotelIds.has(String((order.val() || {}).hotelId || ""))) isMine = true; });
        return isMine ? customerUid : null;
      }));
      allowedUserIds = checked.filter(Boolean);
      if (allowedUserIds.length === 0) {
        return res.status(403).json({ success: false, message: "You can only notify customers who ordered from your hotel" });
      }
    }

    const payload = buildPayload({
      category: body.category || "admin_broadcast",
      ...body,
    });

    const users = (await Promise.all(allowedUserIds.map((uid) => loadUserNotificationRecord(uid)))).filter(Boolean);
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
    const failure = failureResponse(err, "Failed to send notification");
    return res.status(failure.status).json({ success: false, message: failure.message });
  }
});

// ── USER SUSPENSION (admin panel > Users) ─────────────────
// Disabling the Firebase Auth account is what really blocks the person: they cannot sign in and
// their refresh tokens are revoked. users/{uid}/status is for display. A suspended hotel owner's
// hotel and dishes are hidden from the app until the account is enabled again.
async function hotelIdsForUser(uid, userProfile) {
  const ids = new Set();
  if (userProfile && userProfile.hotelId) ids.add(String(userProfile.hotelId));
  const byUser = await db.ref("hotels").orderByChild("userId").equalTo(uid).get();
  byUser.forEach((child) => { ids.add(child.key); });
  if ((await db.ref(`hotels/${uid}`).get()).exists()) ids.add(uid);
  return Array.from(ids);
}

async function setHotelVisibility(hotelIds, suspended) {
  const updates = {};
  for (const hotelId of hotelIds) {
    const hotelSnap = await db.ref(`hotels/${hotelId}`).get();
    if (hotelSnap.exists()) {
      const hotel = hotelSnap.val() || {};
      if (suspended && hotel.status !== "suspended") {
        updates[`hotels/${hotelId}/statusBeforeSuspension`] = hotel.status || "approved";
        updates[`hotels/${hotelId}/status`] = "suspended";
      } else if (!suspended && hotel.status === "suspended") {
        updates[`hotels/${hotelId}/status`] = hotel.statusBeforeSuspension || "approved";
        updates[`hotels/${hotelId}/statusBeforeSuspension`] = null;
      }
    }

    const foods = await db.ref("foodItems").orderByChild("hotelId").equalTo(hotelId).get();
    foods.forEach((child) => {
      const food = child.val() || {};
      if (suspended && !food.suspendedByAdmin) {
        updates[`foodItems/${child.key}/statusBeforeSuspension`] = food.status || null;
        updates[`foodItems/${child.key}/suspendedByAdmin`] = true;
        updates[`foodItems/${child.key}/status`] = "inactive";
      } else if (!suspended && food.suspendedByAdmin) {
        updates[`foodItems/${child.key}/status`] = food.statusBeforeSuspension || "available";
        updates[`foodItems/${child.key}/statusBeforeSuspension`] = null;
        updates[`foodItems/${child.key}/suspendedByAdmin`] = null;
      }
    });
  }
  if (Object.keys(updates).length > 0) await db.ref().update(updates);
}

exports.setUserSuspended = functions.region(REGION).https.onRequest(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    let requester;
    try {
      requester = await verifyRequestAuth(req, ["admin"]);
    } catch (authErr) {
      return res.status(403).json({ success: false, message: "Only an admin can suspend or enable accounts" });
    }

    const uid = String((req.body && req.body.uid) || "").trim();
    const suspended = !!(req.body && req.body.suspended === true);
    const reason = String((req.body && req.body.reason) || "").trim().slice(0, 300);

    if (!uid) return res.status(400).json({ success: false, message: "uid is required" });
    if (uid === requester.uid) {
      return res.status(400).json({ success: false, message: "You cannot suspend your own account" });
    }

    const userSnap = await db.ref(`users/${uid}`).get();
    if (!userSnap.exists()) {
      return res.status(404).json({ success: false, message: "User not found" });
    }
    const profile = userSnap.val() || {};
    const role = roleFromUser(profile);
    if (role === "admin") {
      return res.status(403).json({ success: false, message: "Admin accounts cannot be suspended here" });
    }

    await admin.auth().updateUser(uid, { disabled: suspended });
    if (suspended) {
      await admin.auth().revokeRefreshTokens(uid);
    }

    await db.ref(`users/${uid}`).update(suspended
      ? { status: "suspended", suspendedAt: Date.now(), suspendedBy: requester.uid, suspensionReason: reason }
      : { status: "active", suspendedAt: null, suspendedBy: null, suspensionReason: null });

    if (role === "hotel") {
      await setHotelVisibility(await hotelIdsForUser(uid, profile), suspended);
    }

    return res.status(200).json({ success: true, status: suspended ? "suspended" : "active" });
  } catch (err) {
    console.error("setUserSuspended error", err);
    const failure = failureResponse(err, "Could not update the account");
    return res.status(failure.status).json({ success: false, message: failure.message });
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
    const failure = failureResponse(err, "Failed to send self-test notification");
    return res.status(failure.status).json({ success: false, message: failure.message });
  }
};

// ── NEW LISTING ────────────────────────────────────────────
// Tells customers who follow the deals topic when a hotel publishes a dish. This runs on the
// server so it works for every approved hotel; the hotel panel no longer sends this itself.
exports.onFoodItemCreatedNotify = functions
  .region(REGION)
  .database.ref("/foodItems/{foodId}")
  .onCreate(async (snapshot, context) => {
    const item = snapshot.val() || {};
    const status = String(item.status || "available").trim().toLowerCase();
    if (item.isAvailable === false || (status !== "available" && status !== "active")) return null;

    const hotelName = String(item.hotelName || "A hotel").trim();
    const foodName = String(item.foodName || "a new dish").trim();
    const discount = Number(item.discountPercent || item.discountPercentage || 0);
    const payload = buildPayload({
      title: "New deal nearby",
      body: `${hotelName} added ${foodName}${discount > 0 ? ` at ${discount}% off` : ""}.`,
      type: "new_deal",
      category: discount >= 50 ? "hot_deals" : "new_deals",
      route: "deals",
      hotelId: String(item.hotelId || ""),
      data: { foodId: context.params.foodId },
    });

    try {
      await sendToTopic("deals_customers", payload);
    } catch (err) {
      console.warn("[onFoodItemCreatedNotify] Topic send failed:", err.message);
    }
    return null;
  });

// Canonical endpoint used by Android app:
// https://asia-southeast1-replate-food-rescue.cloudfunctions.net/sendSelfTestPush
exports.sendSelfTestPush = functions.region(REGION).https.onRequest(sendSelfTestPushHandler);


// ── DEAL NOTIFICATION ──────────────────────────────────────
exports.sendDealNotification = functions.region(REGION).https.onRequest(async (req, res) => {
  setCorsHeaders(res);
  if (req.method === "OPTIONS") return res.status(204).send("");

  try {
    if (req.method !== "POST") {
      return res.status(405).json({ success: false, message: "Method not allowed" });
    }

    const requester = await verifyRequestAuth(req, ["admin"]);
    const body = req.body || {};

    const payload = buildPayload({
      title: body.title || "Hot Deal Nearby",
      body: body.body || "Check out new food rescue deals!",
      type: body.type || "new_deal",
      category: body.category || "new_deals",
      route: body.route || "deals",
      data: body.data || {},
    });

    // Send to deals topic
    let topicResult = null;
    try {
      topicResult = await sendToTopic("deals_customers", payload);
    } catch (topicErr) {
      console.warn("[sendDealNotification] Topic send failed:", topicErr.message);
    }

    // Also send to all customers directly for broader reach
    const customers = await loadUsersByRole("customer");
    const userRecords = customers.map((u) => ({
      uid: u.uid,
      token: String(u.fcmToken || (u.notification && u.notification.fcmToken) || "").trim(),
      cleanupPaths: [`users/${u.uid}/fcmToken`, `users/${u.uid}/notification/fcmToken`],
    }));
    const directResult = await sendToUserRecords(userRecords, payload);
    await persistNotificationForUsers(customers.map((u) => u.uid), payload, "deal_campaign");

    const result = {
      topicMessageId: topicResult?.messageId || null,
      directSuccess: directResult.successCount,
      directFailure: directResult.failureCount,
      totalReach: customers.length,
    };

    await db.ref("notificationLogs").push({
      senderUid: requester.uid,
      senderRole: requester.role,
      action: "deal",
      target: "deals_customers + all customers",
      payload,
      result,
      createdAt: Date.now(),
    });

    return res.status(200).json({ success: true, result });
  } catch (err) {
    console.error("sendDealNotification error", err);
    const failure = failureResponse(err, "Failed to send deal notification");
    return res.status(failure.status).json({ success: false, message: failure.message });
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

async function notifyOrderCreated(orderId, order) {
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
}

exports.onOrderCreatedNotify = functions
  .region(REGION)
  .database.ref("/orders/{orderId}")
  .onCreate(async (snapshot, context) => {
    const order = snapshot.val() || {};
    const orderId = context.params.orderId;
    console.log(`[onOrderCreatedNotify] TRIGGER FIRED for orderId=${orderId}`);

    // Stock first: a push that fails must never stop the stock from being taken
    try {
      await adjustStockForOrder(orderId, order);
    } catch (stockErr) {
      console.error("[onOrderCreatedNotify] Error adjusting stock", stockErr);
    }

    try {
      await notifyOrderCreated(orderId, order);
    } catch (notifyErr) {
      console.error("[onOrderCreatedNotify] Error sending notifications", notifyErr);
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

    // A cancelled order gives its portions back (once), before any push can fail
    if (String(after.orderStatus || "").toLowerCase() === "cancelled") {
      try {
        await restoreStockForOrder(orderId, after);
      } catch (restoreErr) {
        console.error("[onOrderStatusChangedNotify] Error restoring stock", restoreErr);
      }
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
        title: "Pickup confirmed",
        body: "Your order was picked up. Enjoy your meal!",
        category: "pickup_ready",
      },
      confirmed: {
        title: "Order confirmed",
        body: "The hotel confirmed your order and will start preparing it.",
        category: "order_accepted",
      },
      preparing: {
        title: "Preparing your order",
        body: "The hotel is preparing your order now.",
        category: "order_accepted",
      },
      out_for_delivery: {
        title: "Order out for delivery",
        body: "Your order is on its way!",
        category: "order_accepted",
      },
      delivered: {
        title: "Order delivered",
        body: "Your order has been delivered. Enjoy your meal!",
        category: "pickup_ready",
      },
    };

    // A hotel that cancels says so; no value means the customer cancelled their own order
    const cancelledByHotel = status === "cancelled" && String(after.cancelledBy || "") === "hotel";
    if (cancelledByHotel) {
      statusMap.cancelled = {
        title: "Order cancelled by the hotel",
        body: `${after.hotelName || "The hotel"} had to cancel your order. Please contact them if you have questions.`,
        category: "order_cancelled",
      };
    }

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

    // Only a customer's own cancellation needs to tell the hotel
    if (status === "cancelled" && hotelId && !cancelledByHotel) {
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

// A hotel that is no longer approved (revoked or rejected) must not keep selling: its dishes are hidden
// from the app, and shown again (as they were) when it is approved again.
async function setHotelDishesHidden(hotelId, hidden) {
  const updates = {};
  const foods = await db.ref("foodItems").orderByChild("hotelId").equalTo(hotelId).get();
  foods.forEach((child) => {
    const food = child.val() || {};
    if (hidden && !food.suspendedByAdmin) {
      updates[`foodItems/${child.key}/statusBeforeSuspension`] = food.status || null;
      updates[`foodItems/${child.key}/suspendedByAdmin`] = true;
      updates[`foodItems/${child.key}/status`] = "inactive";
    } else if (!hidden && food.suspendedByAdmin) {
      updates[`foodItems/${child.key}/status`] = food.statusBeforeSuspension || "available";
      updates[`foodItems/${child.key}/statusBeforeSuspension`] = null;
      updates[`foodItems/${child.key}/suspendedByAdmin`] = null;
    }
  });
  if (Object.keys(updates).length > 0) await db.ref().update(updates);
}

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

    // Revoking or rejecting hides the dishes; approving brings them back (suspension handles its own)
    if (beforeStatus !== "suspended" && afterStatus !== "suspended") {
      try {
        if (beforeStatus === "approved" && afterStatus !== "approved") await setHotelDishesHidden(hotelId, true);
        else if (afterStatus === "approved" && beforeStatus !== "approved") await setHotelDishesHidden(hotelId, false);
      } catch (visibilityErr) {
        console.error("[onHotelStatusChangedNotify] Could not update dish visibility", visibilityErr);
      }
    }

    // Suspending or re-enabling the owner's account (admin panel > Users) is not a new approval
    if (beforeStatus === "suspended" || afterStatus === "suspended") {
      console.log(`[onHotelStatusChangedNotify] Suspension change, skipping`);
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

// ── UPCOMING PICKUP REMINDERS ──────────────────────────────
// Every 15 minutes: customers whose one-hour pickup slot starts within 30 minutes (or is already
// open) get one reminder. `pickupReminderSent` on the order makes sure it is only sent once, and
// the app's "Pickup reminders" switch in Settings decides whether it pops up.
exports.sendPickupReminders = functions
  .region(REGION)
  .pubsub.schedule("every 15 minutes")
  .timeZone("Asia/Colombo")
  .onRun(async () => {
    const now = Date.now();
    const snapshot = await db.ref("orders")
      .orderByChild("pickupExpiresAt")
      .startAt(now)
      .endAt(now + LOOKAHEAD_MS)
      .get();

    const due = [];
    snapshot.forEach((child) => {
      const order = child.val() || {};
      const phase = reminderFor(order, now);
      if (phase && order.userId) due.push({ id: child.key, order, phase });
    });

    let sent = 0;
    for (const { id, order, phase } of due) {
      try {
        // Mark first: a retry must never send the same reminder twice
        await db.ref(`orders/${id}/pickupReminderSent`).set(true);

        const customer = await loadUserNotificationRecord(String(order.userId));
        if (!customer) continue;

        const text = reminderText(order, phase);
        const payload = buildPayload({
          ...text,
          type: "pickup_reminder",
          category: "pickup_reminder",
          route: "order",
          orderId: id,
          hotelId: String(order.hotelId || ""),
          data: { phase },
        });
        if (customer.token) {
          await sendToUserRecords([customer], payload);
        }
        await persistNotificationForUsers([customer.uid], payload, "pickup_reminder");
        sent += 1;
      } catch (err) {
        console.error(`[sendPickupReminders] order ${id} failed`, err);
      }
    }

    console.log(`[sendPickupReminders] ${sent} of ${due.length} reminder(s) sent`);
    return null;
  });

exports.sendScheduledEngagementNotifications = functions
  .region(REGION)
  .pubsub.schedule("every 2 hours")
  .timeZone("Asia/Colombo")
  .onRun(async () => {
    const hour = colomboHour();
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
