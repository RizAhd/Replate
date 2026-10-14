# REPLATE Android app

The customer app. People find discounted surplus meals from nearby hotels, reserve them, and collect
them with a QR code or a 6-digit pickup code. Java, Material 3 and Firebase, min SDK 24 (Android 7.0),
target SDK 34, portrait only.

[Back to the main README](../README.md)

<table>
  <tr>
    <td align="center" width="33%"><img src="../docs/screenshots/app-find.png" alt="Home screen with nearby deals, and search results with filters"><br><sub>Home and search</sub></td>
    <td align="center" width="33%"><img src="../docs/screenshots/app-details-map.png" alt="Dish details with call, SMS and chat, and the map with a route to the hotel"><br><sub>Dish details and map</sub></td>
    <td align="center" width="33%"><img src="../docs/screenshots/app-pickup-code.png" alt="Order details with a QR code and a 6-digit pickup code, and the order list"><br><sub>Pickup code and orders</sub></td>
  </tr>
</table>

## Features

| Area | What the app does |
|---|---|
| Sign-in | Email and password (the email must be verified), Google, Facebook and phone number; password reset |
| Home | Banner carousel managed from the admin panel, category chips, Hot deals (biggest discounts), your favourites, and nearby dishes sorted by distance; pull or shake to refresh |
| Search | Search with recent searches; sort by nearby, price, discount or rating; filter by category or available only |
| Map | Approved hotels as pins coloured by distance, search for a hotel or a place, a hotel card with its available dishes, and the driving route drawn from the Directions API |
| Dish details | Price, pickup window and distance; add to cart; save as a favourite; call, SMS or chat with the hotel; reviews; similar dishes |
| Checkout | Pickup at a chosen time inside the dish's pickup window, or delivery to a point picked on a map; cash or card through PayHere |
| Pickup | Each order gets a QR code and a 6-digit code, valid for one hour from the chosen pickup time, with a live countdown |
| Orders | Active, Completed and Cancelled tabs, live status timeline, cancel while pending, directions to the hotel, review after pickup |
| Chat | Real-time chat with a hotel, with the history kept on the phone |
| Notifications | Push notifications with a Mark as read action, an in-app popup, and an inbox with filter chips and swipe to delete |
| Favourites | Favourite dishes and favourite hotels in two tabs |
| Profile | Orders and money saved, an impact card (meals rescued, food saved and CO2e avoided, shown as estimates) and a savings chart for the last six orders |
| Settings | Edit the profile, take or pick a profile photo, notification switches including pickup reminders, clear cached data, share the app, photo credits, delete the account |
| Offline | Firebase offline persistence plus local SQLite and cached lists, so browsing works without a connection |
| Phone features | Light sensor brightens the screen for the QR code in the dark, shake to refresh, network and GPS receivers, a background sync worker every 15 minutes |

## Main screens

| Screen | Code |
|---|---|
| Splash and first-launch router | `SplashActivity` |
| Onboarding slides | `OnboardingActivity` |
| Sign-in and sign-up | `LoginActivity` |
| Main shell: bottom tabs, side drawer, shake to refresh | `MainActivity` |
| Home tab | `fragments/HomeFragment` |
| Map tab | `fragments/MapFragment` |
| Orders tab | `fragments/OrdersFragment` |
| Profile tab | `fragments/ProfileFragment` |
| Search | `SearchActivity`, `fragments/SearchFragment` |
| Dish details | `FoodDetailActivity` |
| Cart | `CartActivity` |
| Checkout | `CheckoutActivity` |
| Delivery or pickup point on a map | `LocationPickerActivity` |
| Order placed, QR code and review | `OrderSuccessActivity` |
| Order details, QR code and timeline | `OrderDetailActivity` |
| Chat list and chat | `MessagesActivity`, `ChatActivity` |
| Notifications inbox | `NotificationsActivity` |
| Favourites | `FavoritesActivity` |
| Settings | `SettingsActivity` |

The bottom tabs are Home, Map, Cart, Orders and Profile. The side drawer opens Favourites, Messages,
Notifications, Search, Settings and Help.

## Package structure

All code is under `src/main/java/com/replate/app/`.

| Package | Contents |
|---|---|
| (root) | The activities above and `ReplateApplication` (Firebase offline setup) |
| `fragments/` | The Home, Map, Orders, Profile and Search tabs |
| `adapters/` | RecyclerView and ViewPager2 adapters (food, orders, cart, chat, reviews, banners...) |
| `models/` | Data classes: `FoodItem`, `Order`, `CartItem`, `Category`, `Review`, `ChatMessage`... |
| `helpers/` | SQLite helpers (`CartDatabaseHelper`, `ReplateDatabaseHelper`, `ChatDatabaseHelper`), `BannerRepository`, `FoodItemCacheManager` |
| `map/` | `data/` loads hotels for the map (`FirebasePlacesDataSource`, `MapRules`); `routing/` asks Google Directions for routes (`DirectionsApiClient`, `RouteCache`) |
| `notifications/` | Notification categories, the in-app popup and the router that opens the right screen |
| `services/` | `MyFirebaseMessagingService` (FCM) and `OrderSyncWorker` (WorkManager) |
| `receivers/` | `NetworkChangeReceiver`, `GpsStateReceiver`, `NotificationActionReceiver` |
| `utils/` | Small, mostly pure helpers: pickup rules (`PickupSlot`, `PickupWindow`, `PickupCountdown`), money and savings, checkout checks, calls and SMS, impact estimates, account actions |
| `views/` | `PillPageIndicator`, the page dots for onboarding and banners |

## Setup

1. Open the repository root (not this folder) in Android Studio.
2. Download `google-services.json` from your Firebase project and put it in `app/`. It is not in git.
3. Create `local.properties` next to `settings.gradle` (also not in git):

   ```properties
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   GOOGLE_MAPS_API_KEY=...        # Maps SDK + Directions API key
   PAYHERE_MERCHANT_ID=...
   PAYHERE_NOTIFY_URL=...         # optional: https://asia-southeast1-<project>.cloudfunctions.net/payhereNotify
   GOOGLE_WEB_CLIENT_ID=...       # Firebase console > Authentication > Google > Web client ID
   ```

4. For Facebook login, replace `facebook_app_id`, `facebook_client_token` and
   `fb_login_protocol_scheme` in `src/main/res/values/strings.xml`.
5. Run the `app` configuration.

Notes:

- If `GOOGLE_WEB_CLIENT_ID` is empty, the app falls back to the web client ID from
  `google-services.json`. A Google or Facebook button whose ID is still a placeholder is hidden.
- Building a release APK or bundle fails without `GOOGLE_MAPS_API_KEY`, so a release never ships a
  blank map.
- A debug build only accepts a PayHere sandbox merchant ID (one starting with 1).
- `gradle.properties` points `org.gradle.java.home` at Android Studio's bundled JDK on Windows. On
  another machine, change that line or pass `-Dorg.gradle.java.home=<your JDK>` as the CI does.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

These are plain JUnit unit tests under `src/test/java/`. They cover the pure logic: pickup slots,
windows and countdowns, money and savings, checkout validation, payment safety, order numbers and
timelines, impact estimates, map rules, the route cache, banners and sign-in config. The HTML report is
written under `app/build/reports/tests/`. GitHub Actions runs the same tests on every push.
