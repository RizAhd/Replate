/**
 * REPLATE Web Panel — Environment Configuration
 *
 * ⚠️  DO NOT commit this file to Git!
 *     Add  web-panels/js/env-config.js  to your .gitignore
 *
 * In production replace these values with your real credentials
 * or load them from a proper secrets manager.
 */

const ENV = Object.freeze({
    // ── Firebase ─────────────────────────────────────────────────────────
    FIREBASE_API_KEY:           "AIzaSyBdepQADh5GTEK0ysRv75v-llnc3ziy8TQ",
    FIREBASE_AUTH_DOMAIN:       "replate-food-rescue.firebaseapp.com",
    FIREBASE_DATABASE_URL:      "https://replate-food-rescue-default-rtdb.asia-southeast1.firebasedatabase.app",
    FIREBASE_PROJECT_ID:        "replate-food-rescue",
    FIREBASE_STORAGE_BUCKET:    "replate-food-rescue.firebasestorage.app",
    FIREBASE_MESSAGING_SENDER:  "50353677588",
    FIREBASE_APP_ID:            "1:50353677588:web:75306663",

    // ── FCM Server Key (used for sending push notifications server-side) ──
    FIREBASE_FCM_SERVER_KEY:    "AIzaSyCpUxdo15c3j4JojViG3pwy2PioPkte7DM",
});

