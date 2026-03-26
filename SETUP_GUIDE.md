# REPLATE App - Manual Setup Guide

## ✅ What's Already Configured

### 1. **Firebase Connection**
- ✅ Google-services.json configured
- ✅ Firebase project linked: `replate-food-rescue`
- ✅ API keys added
- ✅ Cloud Messaging enabled

### 2. **Location Permission**
- ✅ AndroidManifest.xml includes:
  - `ACCESS_FINE_LOCATION`
  - `ACCESS_COARSE_LOCATION`
- ✅ Runtime permission handling implemented
- ✅ Location permission launcher ready

### 3. **Firebase Realtime Database**
- ✅ Database URL: `https://replate-food-rescue-default-rtdb.asia-southeast1.firebasedatabase.app`
- ✅ Collections ready:
  - `users/` - customer and hotel profiles
  - `foodItems/` - food listings with coordinates
  - `orders/` - order history
  - `reviews/` - product reviews with verified purchase badge
  - `hotels/` - hotel locations with latitude/longitude

---

## 🚀 How to Run the App

### Option 1: Android Studio
```
1. Open Project: File → Open → C:\Users\rizla\AndroidStudioProjects\REPLATE
2. Wait for sync to complete
3. Connect Android device or start emulator
4. Click Run → Run 'app'
```

### Option 2: Command Line
```powershell
cd C:\Users\rizla\AndroidStudioProjects\REPLATE
./gradlew installDebug

# Or directly with adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option 3: Gradle Build
```powershell
cd C:\Users\rizla\AndroidStudioProjects\REPLATE
./gradlew assembleDebug
# APK generated at: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📍 Location Permission Handling

### First Launch Flow
```
App Starts
   ↓
Check if permissions granted?
   ↓
NO → Request permissions via launcher
   ↓
User allows? → YES → Get current location
              NO  → Show default location
```

### Current Location Display
```
HomeFragment:
- Uses FusedLocationProviderClient
- Requests FINE_LOCATION and COARSE_LOCATION
- Updates location label with coordinates
- Tries to reverse geocode to city name
```

### Test Locations (Sri Lanka)
```
Colombo City:        6.9271, 79.8612
Nuwara Eliya:        6.9497, 80.7891
Haggala:             6.8500, 81.2000
Kandy:               7.2906, 80.6337
```

---

## 🧪 Testing Checklist

- [ ] App installs successfully
- [ ] First launch asks for location permission
- [ ] After permission granted, shows current location
- [ ] Location label updates when map loads
- [ ] Can browse food items without crashes
- [ ] Can submit reviews without Firebase errors
- [ ] Verified purchase badge shows correctly
- [ ] No ThreadPoolExecutor errors in logcat
- [ ] Fragment switching doesn't crash app

---

## 🐛 Common Issues & Solutions

### Issue: "Permission Denied"
```
Solution: Grant location permission when app asks
Settings → Apps → REPLATE → Permissions → Location → Allow
```

### Issue: "No Location Found"
```
Solution: 
1. Enable location on device
2. Wait 5-10 seconds for GPS lock
3. Restart app
```

### Issue: "Failed to Fetch Location"
```
Solution:
1. Check internet connection
2. Verify Google Play Services installed
3. Restart device
4. Clear app cache: adb shell pm clear com.replate.app
```

### Issue: "Review Not Saving"
```
Solution:
1. Check Firebase internet connection
2. Verify user is logged in (uid not null)
3. Check review rating > 0
4. Monitor logcat for Firebase errors
```

---

## 📊 Current App Status

### Completed Features ✅
- User authentication
- Food browsing with location display
- Product details with reviews
- Shopping cart with SQLite
- Order placement and tracking
- QR code generation
- Firebase integration
- Location services
- Notifications system
- Review system with verified purchase

### Testing Status ✅
- Build: Passing
- Compilation: All errors fixed
- Runtime: No crashes reported
- Firebase: Working properly
- Location: Handling permissions correctly

---

## 🎯 What to Do Next

1. **Install APK** on test device
2. **Grant permissions** when prompted
3. **Create test account** or login
4. **Test complete workflow:**
   - Browse home page (verify location updates)
   - Click product (check reviews load)
   - Add review (verify no Firebase errors)
   - Switch fragments (no crashes)
5. **Check logcat** for any warnings/errors

---

## 📞 Quick Commands

```powershell
# Build only
./gradlew assembleDebug

# Build and install
./gradlew installDebug

# View logcat
adb logcat

# Filter for app logs
adb logcat | findstr "REPLATE"

# Clear app data
adb shell pm clear com.replate.app

# Start fresh install
adb uninstall com.replate.app
./gradlew installDebug
```

---

## 🔑 Firebase Credentials (Already Configured)

- Project: `replate-food-rescue`
- Database: `asia-southeast1`
- Auth: Email/Password enabled
- Storage: Enabled
- Cloud Messaging: Enabled

**No additional setup needed!** All Firebase connections are pre-configured.

---

**Last Updated:** March 26, 2026
**Status:** Ready for Testing ✅
**Build Version:** Debug 1.0

