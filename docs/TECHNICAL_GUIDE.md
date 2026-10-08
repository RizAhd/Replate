# REPLATE technical guide

For developers, examiners and anyone who has to set the project up, deploy it or change it. For the
people who use the system, see the three user manuals in this folder.

| Manual | For |
|---|---|
| [USER_MANUAL_CUSTOMER.md](USER_MANUAL_CUSTOMER.md) | Customers using the Android app |
| [USER_MANUAL_HOTEL.md](USER_MANUAL_HOTEL.md) | Hotel staff using the hotel panel |
| [USER_MANUAL_ADMIN.md](USER_MANUAL_ADMIN.md) | The platform administrator |
| [REPORT_CORRECTIONS.md](REPORT_CORRECTIONS.md) | What to change in the final report |

## 1. System overview

```
 Customer app (Android, Java)        Hotel panel (HTML/JS)        Admin panel (HTML/JS)
        |                                   |                              |
        +---------------+-------------------+------------------------------+
                        |  Firebase SDKs (Auth, Realtime Database, Storage, FCM)
                        v
        +-------------------------------------------------------------+
        | Firebase: Authentication | Realtime Database | Storage | FCM  |
        |            Security rules (database.rules.json, storage.rules)|
        +----------------------------+--------------------------------+
                                     |  database triggers + HTTPS endpoints
                                     v
                    Cloud Functions (Node 20, asia-southeast1)
                     push notifications, OTP email, stock, suspension
```

The three user-facing parts talk to Firebase directly and are protected by the security rules. Work that
a client must not be trusted with (sending pushes, reducing stock, disabling an account) runs in Cloud
Functions with the Admin SDK, which bypasses the rules.

| Folder | Contents |
|---|---|
| `app/` | Android app: min SDK 24, target/compile SDK 34, Java 11, AGP 8.2.2, Gradle 8.5 |
| `web-panels/` | `hotel-panel/` and `admin-panel/` (Bootstrap 5.3.0, Firebase JS SDK 9.23.0 compat), shared code in `js/` |
| `firebase-functions/` | `index.js`, Node 20, firebase-functions 5.1.1, firebase-admin 12.7.0 |
| `firebase-rules/` | `database.rules.src.js` (source), `database.rules.json` (generated), `storage.rules`, `tests/` |
| `scripts/` | `check-web.js`, `test-schema.js`, `migrate-hotel-licenses.js` |
| `docs/` | These guides, brand files, screenshots |

## 2. First-time setup

1. **Firebase project** (`replate-food-rescue`). Enable Authentication (Email/Password, Google,
   Facebook, Phone), Realtime Database (region as in `FIREBASE_DATABASE_URL`), Storage and Cloud
   Messaging.
2. **Android**: put `google-services.json` in `app/`. Create `local.properties` next to
   `settings.gradle` (never commit it):
   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   GOOGLE_MAPS_API_KEY=...      # Maps SDK + Directions API
   PAYHERE_MERCHANT_ID=...
   PAYHERE_NOTIFY_URL=...         # optional: https://asia-southeast1-<project>.cloudfunctions.net/payhereNotify
   GOOGLE_WEB_CLIENT_ID=...     # Firebase console > Authentication > Google > Web client ID
   ```
   `GOOGLE_WEB_CLIENT_ID` is required for Google Sign-In; without it the button explains that Google
   Sign-In is not configured. Facebook login needs `facebook_app_id`, `facebook_client_token` and
   `fb_login_protocol_scheme` in `strings.xml`. A release build refuses to start without
   `GOOGLE_MAPS_API_KEY`.
3. **Web panels**: create `web-panels/js/env-config.js` (git-ignored) with the `ENV` object: the Firebase
   web config values plus `FUNCTIONS_BASE_URL`
   (`https://asia-southeast1-<project>.cloudfunctions.net`).
4. **Cloud Functions**: `cd firebase-functions && npm install`. The OTP email function reads
   `OTP_EMAIL` and `OTP_EMAIL_APP_PASSWORD` from environment variables (a `firebase-functions/.env`
   file that is not committed).
5. **First admin**: in the Realtime Database set `users/<admin uid>/role` to `"admin"`. The admin must
   sign in with one of the two admin addresses (see 6.4). Clients can never grant this role.
6. **Deploy in this order** (the order matters):
   ```bash
   firebase deploy --only functions          # stock and pushes are server-side
   firebase deploy --only database,storage   # the rules
   firebase deploy --only hosting            # the panels (public dir: web-panels)
   ```
7. If hotels registered before this version, run the one-off licence migration (section 6.6).

## 3. The Android app

### 3.1 Screens and packages

`com.replate.app`
- Activities (16): `SplashActivity`, `OnboardingActivity`, `LoginActivity`, `MainActivity` (bottom
  navigation host + drawer), `FoodDetailActivity`, `FavoritesActivity`, `CartActivity`,
  `CheckoutActivity`, `OrderSuccessActivity`, `OrderDetailActivity`, `NotificationsActivity`,
  `SettingsActivity`, `SearchActivity`, `ChatActivity`, `MessagesActivity`, `LocationPickerActivity`.
- `fragments/`: `HomeFragment`, `MapFragment`, `OrdersFragment`, `ProfileFragment`, `SearchFragment`.
- `adapters/`, `models/` (`FoodItem`, `Order`, `CartItem`, `Category`, `FavoriteHotel`...).
- `helpers/`: `CartDatabaseHelper`, `ReplateDatabaseHelper`, `ChatDatabaseHelper` (SQLite),
  `BannerRepository`, `FoodItemCacheManager`.
- `receivers/`: `NetworkChangeReceiver`, `GpsStateReceiver`, `NotificationActionReceiver`.
- `services/`: `MyFirebaseMessagingService` (FCM), `OrderSyncWorker` (WorkManager).
- `map/`: `data/FirebasePlacesDataSource`, `routing/DirectionsApiClient`, `model/FoodPlace`.
- `notifications/`: categories, router, in-app popup.
- `utils/`: `TelephonyUtils`, `CallHelper`, `ShakeDetector`, `PickupWindow`, `HotelNavigation`, `Savings`,
  `FavoriteHotelToggle`, `CategoryIcons`, `CheckoutValidator`, `Money`, `NetworkUtils`, `LocationUtils`.

### 3.2 Where each marked feature is implemented

| Course item | Where |
|---|---|
| UI design | 16 activities, 5 fragments, Material 3 components; design tokens in `res/values/colors.xml`, `dimens.xml`, `styles.xml` |
| Event handling and animations | `res/anim/*`, `ViewPropertyAnimator` (splash, Map sheet), layout animations, `ItemTouchHelper` swipe in Favorites and Notifications, pull-to-refresh |
| Data storage | SharedPreferences `REPLATE_PREFS` (session, onboarding, settings); SQLite: `ReplateCartDB` (`cart_items`), `ReplateLocalDB` (search_history, favorites, favorite_hotels, notification_cache, order_cache, food_cache, user_profile), `ReplateChatDB`; internal storage (`profile_photo.jpg`, `receipt_*.txt`) |
| Network connection | Firebase SDKs; `HttpURLConnection` for the Directions API and the OTP endpoint; `NetworkUtils.isOnline` |
| Notifications | `MyFirebaseMessagingService`, channels, `NotificationCompat`, in-app popup, "Mark as read" action |
| Broadcast receivers and integration | `NetworkChangeReceiver` (`CONNECTIVITY_ACTION`), `GpsStateReceiver` (`PROVIDERS_CHANGED_ACTION`), `NotificationActionReceiver` (manifest, `ACTION_MARK_READ`); integration: Google Maps navigation intent, dialer/SMS, share sheet, camera |
| Multitasking | WorkManager `OrderSyncWorker` (every 15 min); executors and handlers for parsing and cache writes |
| Advanced components | RecyclerView, ViewPager2, DrawerLayout, BottomNavigationView, TabLayout, BottomSheet-style card, SwipeRefreshLayout, ChipGroup, MPAndroidChart bar chart |
| Telephony | `TelephonyUtils.callNow` (`ACTION_CALL`), `CallHelper` (asks `CALL_PHONE` when tapped, falls back to the dialer), `openSms`; Firebase Phone Auth for sign-in |
| Multimedia | Camera (`ACTION_IMAGE_CAPTURE`) for the profile photo; `MediaPlayer` success sound in `CheckoutActivity` and `OrderSuccessActivity`; Glide image loading |
| Sensors | Accelerometer shake (`ShakeDetector` 2.7 g in Home; 12 m/s² in `MainActivity`), light sensor brightness on `OrderDetailActivity` (below 50 lux) |
| Google Map with Directions API | `MapFragment`, `FirebasePlacesDataSource`, `DirectionsApiClient` (polyline), Haversine in `LocationUtils` |

### 3.3 Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Firebase and the connectivity receiver |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Distances, map, nearest-first ordering |
| `CAMERA` | Profile photo |
| `POST_NOTIFICATIONS` | Android 13+ notifications |
| `CALL_PHONE` | Direct call; requested only when the user taps Call |
| `VIBRATE` | Notification vibration |
Telephony is declared `required="false"` so the app still installs on tablets without a modem.

### 3.4 Order lifecycle and money

Statuses: `pending_pickup` / `pending_delivery` -> `confirmed` -> `preparing` -> `ready` (pickup) or
`out_for_delivery` (delivery) -> `completed` (pickup) or `delivered` (delivery); `cancelled` from any open
state. The customer can cancel while pending; the hotel can cancel at any open state and sets
`cancelledBy: "hotel"`.

One definition is used everywhere (`web-panels/js/data-schema.js`, tested by `scripts/test-schema.js`):
- **Revenue and food rescued** count only `completed` and `delivered` orders.
- **Pending** is every open state.
- Cancelled orders count for nothing.

Pickup codes: at checkout the order gets `qrCode` (the order id), a six-digit `otpCode`
(`SecureRandom`) and `pickupExpiresAt` = the chosen pickup time plus one hour (`PickupWindow`). The app
hides the code after that time and the hotel panel refuses it; the hotel can still complete the order
from the table.

## 4. Firebase

### 4.1 Realtime Database nodes

| Node | Written by | Notes |
|---|---|---|
| `users/{uid}` | the user; admin; Cloud Function (`status`) | `role` set once; `status` is admin-only |
| `hotels/{hotelId}` | owner at registration (pending only); admin (status); Cloud Function | readable by all signed-in users (the map) |
| `hotelLicenses/{hotelId}` | owner at registration | readable by the owner and admins only |
| `foodItems/{foodId}` | an approved hotel; the stock function | `hotelUserId` owns it; `hotelId` must be the owner's hotel |
| `orders/{orderId}` | the customer; the hotel (status); functions | listed only through queries on `userId` or `hotelId` |
| `reviews`, `favorites`, `chats`, `conversations`, `notifications/{uid}` | the participants | per-user rules |
| `categories`, `banners` | admin | read by everyone signed in |
| `notificationLogs` | admin; functions | admin read only |
| `indexes`, `otpVerifications` | functions / hotel indexes | `otpVerifications` is closed to clients |

Indexes: `orders` on `userId`, `createdAt`, `hotelId`; `foodItems` on `hotelId`, `createdAt`; `hotels` on
`userId`, `createdAt`; `categories` and `banners` on `order`.

Storage paths: `profileImages/{uid}/`, `profile_photos/` (older fallback), `food-images/{uid}/` (one folder per uploading account), `licenses/`,
`banners/` (see `firebase-rules/storage.rules`).

### 4.2 Security rules

`database.rules.src.js` is the source; run `npm run rules:build` after editing and commit both files. An
**admin** is a user whose record has `role = admin` **and** whose sign-in email is one of the two admin
addresses (`admin@replate.com`, `superadmin@replate.com`). The same two addresses appear in
`web-panels/js/ui-shell.js` (`ADMIN_EMAILS`) and `storage.rules`; change all three together.

Highlights: a hotel registers only as `pending` and cannot change its own status; only an approved hotel
can create or edit listings; customers cannot write stock; a suspended account cannot clear its own
`status`; orders can only be listed by a query on the caller's own id.
See `firebase-rules/README.md` for the full table and the known limitations.

### 4.3 Cloud Functions (`firebase-functions/index.js`)

| Function | Trigger | Purpose |
|---|---|---|
| `sendCustomVerificationEmail`, `sendOtpEmail`, `verifyOtp` | HTTPS | Email verification and OTP |
| `sendBroadcastNotification` | HTTPS, admin | Push to all, a role or a topic |
| `sendTargetedNotification` | HTTPS, admin or hotel | Push to chosen users; a hotel may only reach customers who ordered from it |
| `sendDealNotification` | HTTPS, admin | Deal campaign to every customer |
| `sendSelfTestPush` | HTTPS | Settings > test push |
| `setUserSuspended` | HTTPS, admin | Disables or enables an Auth account, sets `users/{uid}/status`, hides or restores a hotel and its dishes |
| `onFoodItemCreatedNotify` | DB create `/foodItems/{id}` | New-deal push to the `deals_customers` topic |
| `onOrderCreatedNotify` | DB create `/orders/{id}` | Reduces stock; tells the customer and the hotel |
| `onOrderStatusChangedNotify` | DB update `/orders/{id}` | One push per status change (confirmed, preparing, ready, out for delivery, delivered, completed, cancelled; says who cancelled) |
| `onHotelStatusChangedNotify` | DB update `/hotels/{id}` | Approved or rejected notice (not for suspension changes) |
| `onUserProfileCreatedNotify` | DB create `/users/{uid}` | Welcome notification |
| `sendPickupReminders` | schedule, every 15 minutes | One reminder per pickup order, 30 minutes before its one-hour slot starts (or when the slot is already open); `pickupReminderSent` prevents repeats; the app's "Pickup reminders" switch decides whether it pops up |
| `sendScheduledEngagementNotifications` | schedule, every 2 hours | "Trending deals" nudge to the deals topic |

All HTTPS functions require a Firebase ID token in `Authorization: Bearer ...` and check the role.

### 4.4 Tests

| Command | What it checks |
|---|---|
| `./gradlew testDebugUnitTest` | Android unit tests (validators, `Savings`, `PickupWindow`, `HotelNavigation`, `CategoryIcons`, map rules, banners...) |
| `npm run rules:test` | 127 security-rule checks in the Firebase emulator (needs **JDK 21+** on the PATH) |
| `npm run web:check` | Every script and link in `web-panels/` parses and resolves |
| `npm run web:test` | The shared order rules (revenue, pending, expiry, hotel status, daily earnings, popular items) |
| `npm run functions:test` | The pickup-reminder decisions and the safe helpers (escaping, rate limit, quantity cap, error mapping) |
| `npm run stock:test` | Order stock logic (take, cap, other-hotel guard, restore on cancel) against the database emulator |
All of these run in GitHub Actions (`.github/workflows/ci.yml`).

## 5. The web panels

Each page loads, in order, the Firebase SDKs, `env-config.js`, `firebase-config.js` (initialises Firebase,
exposes `auth`, `database`, `storage`), `data-schema.js` (`ReplateSchema`), `ui-shell.js` (`ReplateUI`)
and the page's shell config.

- `ReplateUI.requireRole('hotel' | 'admin')` sends visitors with the wrong role to the sign-in page.
  `ReplateUI.requireApprovedHotel(user)` additionally requires an approved hotel.
- `ReplateUI.esc` escapes every dynamic value put into HTML; `safeUrl` only allows http(s) links.
- `ReplateSchema` holds the order and hotel status rules and the shared money definitions.
- `ReplateUI.compressImage` shrinks photos before upload.
- `notifications-api.js` calls the HTTPS functions with the signed-in user's token.

Hotel panel (9 pages): `login`, `register`, `dashboard`, `add-food`, `manage-listings`, `orders`,
`analytics`, `chat`, `settings`. Admin panel (8 pages): `login`, `approve-hotels`, `users`, `transactions`,
`categories`, `banners`, `analytics`, `notifications`.

## 6. Operations

### 6.1 Suspending a user
Admin panel > Users > Suspend calls `setUserSuspended`. The Auth account is disabled and its refresh
tokens revoked (the person is signed out within about an hour at the latest; new sign-ins fail at once).
A hotel owner's hotel becomes `suspended` and each of its dishes gets `status: "inactive"` with
`suspendedByAdmin: true`; enabling restores the previous values.

### 6.2 Category flow
Admin edits `/categories`; the hotel add-food and edit forms and the app's Home chips read it. Renaming
also rewrites `category` on the dishes that used the old name.

### 6.3 Notifications flow
Hotel actions only change the order's `orderStatus`. `onOrderStatusChangedNotify` sends the single push
and saves the inbox copy. The panels do not send order pushes themselves.

### 6.4 Changing the admin addresses
Edit `ADMIN_EMAILS` in `web-panels/js/ui-shell.js`, the `isAdmin()` list in `firebase-rules/storage.rules`
and the `ADMIN` expression in `firebase-rules/database.rules.src.js`; run `npm run rules:build` and
`npm run rules:test`; redeploy rules and hosting.

### 6.5 Monitoring
Firebase Console: Functions logs for the triggers above, Realtime Database usage, Crashlytics for the app
(crash reports and basic usage statistics are enabled; mention both in the privacy policy).

### 6.6 One-off licence migration
Hotels registered before the `hotelLicenses` node still carry `licenseUrl` on the hotel record, which every
signed-in user can read. After deploying the rules run, with a service account key:
```bash
set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\service-account.json
set FIREBASE_DATABASE_URL=https://<project>-default-rtdb.<region>.firebasedatabase.app
node scripts/migrate-hotel-licenses.js            # shows what would move
node scripts/migrate-hotel-licenses.js --apply    # moves the links
```

## 7. Known limitations

- **Payment is reported by the app.** The rules only accept `paymentStatus: "paid"` together with a PayHere
  payment number, but a determined customer could still invent one. Real proof is a PayHere `notify_url`
  handled by a Cloud Function that checks PayHere's signature; do this before taking real payments.
- The PayHere merchant secret is no longer compiled into the app (the SDK never used it). Keep it for the
  server-side verification only (Functions config).
- Customers can read each hotel's phone, email and push token from `hotels` (the map needs the rest).
  Split the private fields into their own node.
- The Hot Deals row and category chips are computed on the phone from the full `foodItems` list; at large
  scale use a geohash query (GeoFire) and server-side filtering.
- Delivery has no live courier tracking.
- The app is portrait-only and light-theme only.
