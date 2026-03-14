// Firebase Configuration
// COPY FROM YOUR FIREBASE CONSOLE
const firebaseConfig = {
    apiKey: "AIzaSyBdepQADh5GTEK0ysRv75v-llnc3ziy8TQ",
    authDomain: "replate-food-rescue.firebaseapp.com",
    databaseURL: "https://replate-food-rescue-default-rtdb.asia-southeast1.firebasedatabase.app",
    projectId: "replate-food-rescue",
    storageBucket: "replate-food-rescue.firebasestorage.app",
    messagingSenderId: "50353677588",
    appId: "1:50353677588:web:75306663" // Placeholder, ideally get from Firebase Console > Project Settings > General > Your Apps > Web App
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

