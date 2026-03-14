/**
 * Firebase Configuration
 * ── All values come from env-config.js (not committed to Git) ───────────────
 * Make sure env-config.js is loaded BEFORE this file in every HTML page:
 *   <script src="../js/env-config.js"></script>
 *   <script src="../js/firebase-config.js"></script>
 */

const firebaseConfig = {
    apiKey:            ENV.FIREBASE_API_KEY,
    authDomain:        ENV.FIREBASE_AUTH_DOMAIN,
    databaseURL:       ENV.FIREBASE_DATABASE_URL,
    projectId:         ENV.FIREBASE_PROJECT_ID,
    storageBucket:     ENV.FIREBASE_STORAGE_BUCKET,
    messagingSenderId: ENV.FIREBASE_MESSAGING_SENDER,
    appId:             ENV.FIREBASE_APP_ID,
};

// Initialize Firebase
const app = firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const database = firebase.database();
const storage = firebase.storage();

// Auth State Listener
auth.onAuthStateChanged((user) => {
    if (user) {
        // User is signed in
        console.log("Logged in as:", user.email);
        // Check if user is hotel
        checkUserRole(user.uid);
    } else {
        // No user is signed in
        if (!window.location.href.includes("login.html") && !window.location.href.includes("register.html")) {
            window.location.href = "login.html";
        }
    }
});

function checkUserRole(uid) {
    database.ref('hotels/' + uid).once('value').then((snapshot) => {
        if (!snapshot.exists()) {
            // Not a hotel? Maybe admin or customer
            // Handle accordingly
        }
    });
}

function logout() {
    auth.signOut().then(() => {
        window.location.href = "login.html";
    });
}
