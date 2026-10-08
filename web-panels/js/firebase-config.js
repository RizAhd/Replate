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

const app = firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const database = firebase.database();
// Only pages that load firebase-storage-compat have this; everywhere else it is null (and never used)
const storage = typeof firebase.storage === 'function' ? firebase.storage() : null;

// True for the login and register pages.
function isAuthPage(pathname) {
    return pathname.includes('login.html') || pathname.includes('register.html');
}

// Reads the user's role from the database.
async function resolveUserRole(uid) {
    const userSnap = await database.ref('users/' + uid).once('value');
    if (!userSnap.exists()) return null;
    const val = userSnap.val() || {};
    return (val.role || val.userType || '').toLowerCase();
}

// Finds the role and remembers the hotel id for hotel owners.
async function checkUserRole(uid) {
    try {
        const role = await resolveUserRole(uid);
        if (role === 'hotel') {
            const hotelByUser = await database.ref('hotels').orderByChild('userId').equalTo(uid).once('value');
            if (hotelByUser.exists()) {
                const [hotelId] = Object.keys(hotelByUser.val());
                sessionStorage.setItem('hotelId', hotelId);
                return 'hotel';
            }
            const legacy = await database.ref('hotels/' + uid).once('value');
            if (legacy.exists()) {
                sessionStorage.setItem('hotelId', uid);
                return 'hotel';
            }
        }
        return role;
    } catch (e) {
        return null;
    }
}

// Send signed-out visitors to the login page (every page except login/register itself)
auth.onAuthStateChanged(async (user) => {
    if (user) {
        await checkUserRole(user.uid);
    } else if (!isAuthPage(window.location.pathname)) {
        window.location.href = 'login.html';
    }
});

// The one logout used by every page and by the sidebar link
function logout() {
    // Stop live listeners first so they do not report permission errors after sign-out
    try { firebase.database().goOffline(); } catch (e) { /* ignore */ }
    auth.signOut().finally(() => {
        sessionStorage.removeItem('hotelId');
        window.location.href = 'login.html';
    });
}
