const crypto = require("crypto");
const admin = require("firebase-admin");
const functions = require("firebase-functions");
const nodemailer = require("nodemailer");

admin.initializeApp();

const db = admin.database();

const OTP_EXPIRY_MINUTES = 10;
const OTP_MAX_ATTEMPTS = 5;

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

exports.sendOtpEmail = functions.https.onRequest(async (req, res) => {
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

exports.verifyOtp = functions.https.onRequest(async (req, res) => {
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

