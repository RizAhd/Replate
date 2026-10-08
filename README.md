# REPLATE

REPLATE is a food-rescue marketplace for Sri Lanka. Hotels list surplus meals at a discount, customers
find them on a map, order for pickup or delivery, and collect with a QR code and a one-time code.

This repository holds all four parts of the system:

| Folder | What it is |
|---|---|
| `app/` | The Android customer app (Java, Material 3, Firebase) |
| `web-panels/` | Browser panels for **hotels** (`hotel-panel/`) and **admins** (`admin-panel/`) |
| `firebase-functions/` | Cloud Functions (push notifications, emails, stock updates) |
| `firebase-rules/` | Realtime Database and Cloud Storage security rules, with an automated test suite |

Also: `docs/brand/` (logo files) and `docs/IMAGE_CREDITS.md` (photo credits).

## Documentation

| File | For |
|---|---|
| [docs/USER_MANUAL_CUSTOMER.md](docs/USER_MANUAL_CUSTOMER.md) | Customers using the app |
| [docs/USER_MANUAL_HOTEL.md](docs/USER_MANUAL_HOTEL.md) | Hotel staff using the hotel panel |
| [docs/USER_MANUAL_ADMIN.md](docs/USER_MANUAL_ADMIN.md) | The platform administrator |
| [docs/TECHNICAL_GUIDE.md](docs/TECHNICAL_GUIDE.md) | Architecture, setup, deployment, tests, where each feature lives |
| [docs/REPORT_CORRECTIONS.md](docs/REPORT_CORRECTIONS.md) | Page-by-page changes for the final project report |

## The Android app

- Customer sign-in: email/password (with verified email), Google, Facebook and phone.
- Home with promo banners (managed from the admin panel), categories, hot deals and nearby food.
- Map of approved hotels with live distance, directions and search.
- Cart, checkout (cash or PayHere card payment, pickup or delivery), order tracking, QR + OTP pickup.
- Chat with the hotel, push notifications, reviews, favorites, profile and settings.
- Works offline for browsing: Firebase offline persistence plus a local cache of listings.

### Set up

Prerequisites: [Android Studio](https://developer.android.com/studio),
[Node.js and npm](https://nodejs.org/) and the [Firebase CLI](https://firebase.google.com/docs/cli).

1. Open this folder in Android Studio.
2. Add your Firebase config: download `google-services.json` from the Firebase console
   (project `replate-food-rescue`) into `app/`. It is deliberately **not** in git.
3. Create `local.properties` (also not in git) next to `settings.gradle` and add:

   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   GOOGLE_MAPS_API_KEY=...        # Maps SDK + Directions API key
   PAYHERE_MERCHANT_ID=...
   PAYHERE_NOTIFY_URL=...         # optional: https://asia-southeast1-<project>.cloudfunctions.net/payhereNotify
   GOOGLE_WEB_CLIENT_ID=...       # for Google sign-in
   ```

4. In `app/src/main/res/values/strings.xml`, replace the Facebook placeholders
   (`facebook_app_id`, `facebook_client_token`, `fb_login_protocol_scheme`) if you use Facebook login.
5. Run the `app` configuration. A release build refuses to start without `GOOGLE_MAPS_API_KEY`.

### Tests

```bash
./gradlew testDebugUnitTest      # Android unit tests (pure logic: validators, route cache, banners, map rules)
npm install && npm run rules:test  # security rules, in the Firebase emulator (needs JDK 21 or newer)
npm run web:check                  # every script and link in web-panels/ parses and resolves
npm run web:test                   # the shared order rules the panels use (revenue, pending, expiry)
```

Both run automatically on every push through GitHub Actions (`.github/workflows/ci.yml`).

## Firebase

Cloud Functions:

```bash
cd firebase-functions
npm install
firebase deploy --only functions
```

Read **`firebase-rules/README.md` before deploying rules**. In short:

1. Give your admin account the role: in the Realtime Database set `users/<admin uid>/role` to `"admin"`.
2. `firebase deploy --only functions` first (stock is reduced only by a Cloud Function).
3. `firebase deploy --only database,storage` for the rules.

Banners: add them in the admin panel (Banners page). Without any, the app shows its four built-in photos.

## Admin and hotel panels

Static HTML in `web-panels/`: open the pages in `admin-panel/` or `hotel-panel/` in a browser, or host the
folder anywhere static (for example Firebase Hosting). Both panels read their Firebase settings from
`web-panels/js/env-config.js` (git-ignored; copy the values from your Firebase project's web app
settings) through `js/firebase-config.js`.

## Brand

Logo files, in SVG and PNG, live in `docs/brand/`. The mark is a cloche (serving dome) with a heart, a
two-leaf sprout and a plate line, in REPLATE green and orange. Android icons are vector drawables, so
they stay sharp at every size and support Android 13 themed icons.

## Security notes

- Secrets that must stay out of the app: FCM server key, payment secrets. Anything compiled into an
  APK can be extracted.
- The PayHere merchant secret is no longer compiled into the app (the SDK never used it). Move payment
  verification to a Cloud Function with a PayHere `notify_url` (it will need the secret) before going live
  with real payments. A debug build refuses a live merchant id (one starting with 2).
- Restrict the Google Maps key to this app (package + SHA-1) and the two APIs it needs, and set a
  billing alert in Google Cloud Console.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like
to change. Keep commits small, and make sure `./gradlew testDebugUnitTest` and `npm run rules:test` pass.

## License

No open-source licence has been added yet, so all rights are reserved by the owner. Add a `LICENSE` file
to grant others permission to use or contribute to the code.

## Roadmap (known gaps)

- Server-side payment verification and server-generated pickup codes.
- Translations (Sinhala, Tamil): the screen text is now in `strings.xml`; add `values-si` and `values-ta` and have a native speaker review them.
- Dark theme (the app is intentionally light-only for now).
- Customers can still read each hotel's phone, email and push token from the `hotels` node (the map needs the rest); split the private fields out.
