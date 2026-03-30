# REPLATE FINAL PROJECT

## Overview
This repository contains the complete source code for the REPLATE application, including Android app, Firebase backend functions, and web admin/hotel panels.

---

## Project Structure

- **app/**: Android application source code (Java/Kotlin, Gradle build)
- **firebase-functions/**: Cloud Functions for Firebase backend (Node.js)
- **web-panels/**: Web admin and hotel panels (HTML, CSS, JS)
- **assets/**, **css/**, **js/**: Shared static resources for web panels
- **build.gradle**, **settings.gradle**, **gradlew**: Android/Gradle build scripts
- **firebase.json**, **google-services.json**: Firebase configuration files

---

## Getting Started

### Prerequisites
- [Android Studio](https://developer.android.com/studio)
- [Node.js & npm](https://nodejs.org/)
- [Firebase CLI](https://firebase.google.com/docs/cli)

### Android App
1. Open the `app/` folder in Android Studio.
2. Sync Gradle and build the project.
3. Run on an emulator or connected device.

#### Google Maps API Key (Safety Notice)
For security, the Google Maps API key is **not** included in the repository. To use map features:

1. Obtain your Google Maps API key from the [Google Cloud Console](https://console.cloud.google.com/).
2. Add the following line to your `local.properties` file (do **not** commit this file):
   ```
   GOOGLE_MAPS_API_KEY=YOUR_API_KEY_HERE
   ```
3. The build system will automatically use this key for map features.

**Dependency:**
The project uses the Maps SDK for Android:
```
implementation 'com.google.android.gms:play-services-maps:18.2.0'
```

**Never commit your API key to version control.**

### Firebase Functions
1. Navigate to `firebase-functions/`.
2. Install dependencies:
   ```bash
   npm install
   ```
3. Deploy functions:
   ```bash
   firebase deploy --only functions
   ```

### Web Panels
- Open HTML files in `web-panels/admin-panel/` or `web-panels/hotel-panel/` in your browser.
- Ensure Firebase config in `js/firebase-config.js` is set up correctly.

---

## Features
- **Android App**: User registration, hotel/food listings, order management, notifications, analytics.
- **Admin Panel**: Hotel approval, analytics, transaction management, notifications.
- **Hotel Panel**: Listing management, order tracking, chat, analytics.
- **Firebase Backend**: Authentication, real-time database, cloud functions for business logic.

---

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

---

## License
This project is licensed. See LICENSE file for details.
