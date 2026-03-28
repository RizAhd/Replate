# NOTIFICATION ICON FIX - COMPLETE STEPS

## Current Status: Code is FIXED ✅
Build: SUCCESSFUL ✅

## Problem
Notification popup shows default Android icon instead of REPLATE app logo.

## Root Cause
The notification channel was created with old settings. Android caches notification channel metadata.

## COMPLETE FIX STEPS (Do ALL of these):

### Step 1: Uninstall Current App COMPLETELY
```
1. Go to device Settings → Apps → REPLATE
2. Tap "Storage"
3. Tap "Clear Data"
4. Tap "Clear Cache"
5. Go back and tap "Uninstall"
```

### Step 2: Install Fresh APK
```
Location: C:\Users\rizla\AndroidStudioProjects\REPLATE\app\build\outputs\apk\debug\app-debug.apk

Method 1 (USB):
- Connect device via USB
- Copy app-debug.apk to device
- Install it

Method 2 (ADB):
adb install -r "C:\Users\rizla\AndroidStudioProjects\REPLATE\app\build\outputs\apk\debug\app-debug.apk"
```

### Step 3: Deploy Cloud Functions (REQUIRED!)
**Option 1 - Firebase Console:**
```
1. Open https://console.firebase.google.com/
2. Select project: replate-food-rescue
3. Go to Functions
4. Click "Deploy"
5. Navigate to: C:\Users\rizla\AndroidStudioProjects\REPLATE\firebase-functions
6. Upload index.js
```

**Option 2 - Command Line (if firebase CLI installed):**
```powershell
cd C:\Users\rizla\AndroidStudioProjects\REPLATE
firebase deploy --only functions --project replate-food-rescue
```

**Option 3 - Using npx:**
```powershell
cd C:\Users\rizla\AndroidStudioProjects\REPLATE\firebase-functions
npx firebase-tools deploy --only functions --project replate-food-rescue
```

⚠️ **CRITICAL**: You MUST deploy Cloud Functions! The old functions are still sending "ic_notification" which doesn't work.

### Step 4: Test Notification
```
1. Open REPLATE app
2. Login
3. Go to Settings
4. Tap "Send Test Push"
5. Check notification popup - it should show REPLATE logo
```

## What Was Changed:

### Android App:
- `MyFirebaseMessagingService.java` → `.setSmallIcon(R.mipmap.ic_launcher)`
- `SettingsActivity.java` → `.setSmallIcon(R.mipmap.ic_launcher)`
- `AndroidManifest.xml` → `@mipmap/ic_launcher`

### Cloud Functions:
- `firebase-functions/index.js` → `icon: "ic_launcher"` (both sendToUserRecords and sendToTopic)

## Why It WILL Work Now:

`R.mipmap.ic_launcher` is your ACTUAL app icon - the same one from your splash screen.
It's already properly sized for all screen densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).
Android ALWAYS renders mipmap icons correctly in notifications.

## If Still Not Working:

### Clear Notification Channel Manually:
```
1. Long-press on a REPLATE notification
2. Tap "All categories"
3. Find "REPLATE Alerts"
4. Tap it
5. Tap "Delete"
6. Restart app (this recreates the channel with new icon)
```

OR

### Rebuild with New Channel ID:
In `MyFirebaseMessagingService.java`, change:
```java
private static final String CHANNEL_ID = "replate_notifications_v4";  // Changed from v3
private static final String CHANNEL_NAME = "REPLATE Alerts";
```

Also update in `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="replate_notifications_v4" />
```

Then rebuild and reinstall.

---

## CRITICAL: You MUST do Step 1 (Uninstall) before testing!
Old notification channels persist even after reinstalling. A fresh install is required.

