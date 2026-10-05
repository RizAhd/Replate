# REPLATE final report: what to change so it matches the project

The final report (`2REP.pdf`, "Final Project Complete Document") was checked line by line against the
code in this repository. This file lists every statement that is **wrong, out of date or now
different**, with the page, the current wording and a replacement you can paste in. Page numbers are
the PDF page numbers (the table of contents uses the printed page numbers, which are one lower).

How to use it: work from top to bottom, edit the Word source, then re-export the PDF. Anything marked
**Retake** needs a fresh screenshot (see the end of this file).

Three things in the report can only be confirmed by you and are **not changed here**: the devices in
Table 10, the dates, and the test results ("Pass") of any new test case you add. Do not list a phone
you did not test on.

---

## Chapter 2: Background

### Page 13, section 2.3 (QR code paragraph) and Table 2
**Currently says:** "the ZXing (Zebra Crossing) library was used within the mobile app to generate QR
codes".
**Replace with:** keep the sentence, and fix Table 2 (page 27) as shown below.

---

## Chapter 4: Design

### Page 19, section 4.1 (System Architecture)
**Currently says:** "The REPLATE system is made up of four connected components ... there is no need
for a separate backend server or REST API."
**Replace with:**
> The REPLATE system is made up of four connected components: the Android application for customers,
> the hotel web panel, the admin dashboard, and the Firebase backend. The apps talk to Firebase
> directly for reading and writing data. A small set of Firebase Cloud Functions (Node.js 20, region
> asia-southeast1) handles the work that must not run on a client: sending push notifications,
> emailing OTP codes, reducing stock when an order is placed, and suspending accounts.

**Currently says:** "The MainActivity serves as the host for five main fragments—Home, Map, Cart,
Orders, and Profile".
**Replace with:**
> MainActivity hosts four fragments (Home, Map, Orders and Profile) behind a BottomNavigationView. The
> Cart tab opens CartActivity, and a DrawerLayout gives a side menu with Search, Notifications,
> Messages, Settings and Help.

### Page 21, Table 5 (Firebase node structure)
**Currently says:** the node is `food_items/{foodId}`.
**Replace with:** `foodItems/{foodId}` (the app, the web panels, the security rules and Figure 2 all use
`foodItems`). Replace the table with:

| Node | Purpose | Key fields |
|---|---|---|
| `users/{uid}` | Registered user profiles | name, email, role, status, fcmToken |
| `hotels/{hotelId}` | Hotel registration and approval | status, latitude, longitude, hotelName |
| `hotelLicenses/{hotelId}` | Business licence link (owner and admin only) | userId, licenseUrl |
| `foodItems/{foodId}` | Listings created by hotels | discountedPrice, quantity, status, category, pickupTimeStart |
| `orders/{orderId}` | Orders with payment and pickup codes | qrCode, otpCode, orderStatus, pickupExpiresAt, cancelledBy |
| `reviews/{reviewId}` | Dish reviews | foodId, userId, rating, comment |
| `categories/{catId}` | Admin-managed categories | name, order |
| `banners/{bannerId}` | Home screen banners | title, imageUrl, order |
| `chats/{chatId}` and `conversations/{uid}` | Chat messages and the inbox lists | messages, lastMessage |
| `notifications/{uid}` | Per-user notification inbox | title, body, read |
| `notificationLogs` | Admin send history | action, target, result |

### Page 21, Table 6 and Table 7 (SQLite)
**Currently says:** "The application uses two SQLite databases for offline data."
**Replace with:** "The application uses three SQLite databases: `ReplateCartDB` (the cart),
`ReplateLocalDB` (cache) and `ReplateChatDB` (chat history)."
Table 6 is correct (table `cart_items`). In Table 7 add these rows and keep the others:

| Table | Purpose | Columns |
|---|---|---|
| `user_profile` | Profile shown instantly while Firebase loads | user_id, name, email, phone, photo_url, address |
| `favorite_hotels` | Hotels the customer saved with the heart | hotel_id, hotel_name, address, added_at |

### Page 20, Figure 9 (App Navigation Flow Diagram)
**Retake / redraw.** The diagram shows `CartFragment`, `EditProfileActivity`, `SettingsFragment` and
`SavedAddressesFragment`. None of these exist. The real screens are listed in Appendix A below; the
cart is `CartActivity`, settings is `SettingsActivity` (profile editing is inside it), and saved
addresses are part of Settings.

---

## Chapter 5: Implementation

### Page 27, Table 2 (Technology stack)
| Row | Currently | Replace with |
|---|---|---|
| QR Code (app) | ZXing + zxing-android-embedded, core 3.5.2, embedded 4.3.0 | ZXing core 3.5.2 (QR images are drawn with `MultiFormatWriter`; `zxing-android-embedded` is not used) |
| Charts (web) | Chart.js, CDN latest | Chart.js 4.4.1 (pinned) |
| Charts (app) | MPAndroidChart v3.1.0 | Correct now. The Profile screen has a "Your savings" bar chart |
| (new row) | | Cloud Functions: Node 20, firebase-functions 5.1.1, firebase-admin 12.7.0, nodemailer 6.9.16 |
| (new row) | | DataTables 1.11.5 (admin tables), html5-qrcode 2.3.8 |

### Page 28, section 5.2.1 (Startup)
**Currently says:** "After a brief delay (900ms for returning users and 1800ms for new users)".
**Replace with:**
> The splash screen plays a short animated intro (an overshoot scale and a glowing ring fade-in).
> Returning users with a saved session go straight to MainActivity; first-time users see the splash for
> about 1.4 seconds, then the onboarding slides.

(The three onboarding slides, the `onboarding_seen` flag and the `REPLATE_PREFS` session check are
correct as written.)

### Page 30, section 5.2.3 (Home Screen)
**Currently says:** "It retrieves data from Firebase's food_items node ... a grid layout showing all
available food items."
**Replace with:**
> HomeFragment reads the `foodItems` node with a live listener and shows four sections: an
> auto-scrolling banner (ViewPager2), a row of category chips, a "Hot Deals" row with the highest
> discounts (sorted by discount percentage, then price), and the full feed of available dishes. The
> category chips come from the admin-managed `categories` node, so a category added, renamed or
> reordered in the admin panel appears in the app; the built-in six (Meals, Bakery, Drinks,
> Groceries, Desserts) are used only when the list is empty or the phone is offline.

### Page 31, section 5.2.4 (Map-Based Discovery)
**Currently says:** "reads data from the food_items and hotels nodes" and "a 'View Deals' button".
**Replace with:** "reads the `foodItems` and `hotels` nodes ... a bottom sheet with the hotel name,
address, the number of deals and two buttons: **View deals** (opens Search filtered to that hotel) and
**Show route** (draws the Directions API polyline with distance and time). The sheet also has a heart
to save the hotel as a favourite. A banner at the top tells the user when location or GPS is off, and
it updates the moment GPS is switched on or off (a `GpsStateReceiver` listens for the change)."
Update test case TC-08 to say "Tap a marker → **Show route**".

### Pages 32-33, section 5.2.5 (Food Detail and Cart)
**Currently says:** "the ReviewAdapter shows star ratings ... retrieved from the
orders/{orderId}/reviewRating and reviewComment fields."
**Replace with:**
> Reviews are stored in the `reviews` node (one record per review with the dish id, rating and
> comment) and are read with a query on `foodId`. When a customer reviews a completed order, the same
> rating is also saved on the order (`reviewRating`, `reviewComment`) so the hotel panel can show it.

Add: "Next to the hotel name there is a heart that saves the hotel to the Favourites screen; the Call,
SMS and Chat buttons contact the hotel."

### Page 34, section 5.2.6 (Checkout and Payment)
**Currently says:** "Creates a random 6-digit OTP using the Random class" and the QR and OTP are
created "when a successful payment callback is received".
**Replace with:**
> When the order is saved, the app creates the QR value (the order id) and a six-digit OTP using
> `SecureRandom`. Cash orders get the same QR and OTP. The order also stores `pickupExpiresAt`, the end
> of the one-hour pickup slot the customer chose, because the QR code and OTP stop working after that
> time. Each dish line also stores its category, so analytics can group revenue by category.

Table 9, TC-12: the stored value is `paymentMethod = "Cash"` (capital C).

### Page 35, section 5.2.7 (Order Detail)
The "Navigate to Hotel" button and the Call/SMS buttons are now correct. Replace the Call/SMS bullet:
> **Call hotel** asks for the CALL_PHONE permission when tapped and then places the call directly
> (`TelephonyUtils.callNow`); if the user refuses, it opens the dialer instead. **SMS hotel** opens the
> messaging app with a prepared text. **Navigate to hotel** opens turn-by-turn directions in Google
> Maps, falls back to any maps app, and finally to the in-app route.

Add: "A step timeline under the status banner shows how far the order has got (Placed, Confirmed, Preparing,
Ready or On the way, Collected or Delivered); it is hidden for cancelled orders. The order-placed screen
also says what the order achieved, for example how much was saved and how many meals were rescued, and
dish cards show "Only N left" when three or fewer portions remain."
Add: "When the pickup slot has passed, the screen says "Pickup time passed" and hides the QR code and
OTP. If the hotel cancels an order it says "Cancelled by the hotel"."
**Light sensor:** replace "below a defined threshold" with "below 50 lux".

### Page 36, section 5.2.8 (Shake to Refresh)
**Currently says:** "In MainActivity, a SensorManager is registered ... with a custom ShakeDetector
utility class ... 2.7G ... two shakes within 3 seconds ... refreshData()".
**Replace with:**
> Shake detection works in two places. HomeFragment registers the `ShakeDetector` utility (it counts two
> shakes above 2.7 g within three seconds) and re-reads the `foodItems` node. MainActivity also has a
> simpler listener (acceleration above 12 m/s², with a two-second cooldown) that refreshes whichever tab
> is showing.

### Pages 36-37, section 5.2.9 (Notifications)
**Currently says:** a "new FCM token" sentence with no start, and a **code example titled "Push
Notifications (FCM)" that is a copy of the Google Maps code** from page 31.
**Replace the code example** with this shortened version of the real `onMessageReceived`
(`services/MyFirebaseMessagingService.java`). Note the system notification is always shown; the
branded popup is added on top while the app is open:
```java
@Override
public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
    super.onMessageReceived(remoteMessage);
    Map<String, String> data = new HashMap<>(remoteMessage.getData());
    String title = data.containsKey("title") ? data.get("title") : "REPLATE";
    String body = data.containsKey("body") ? data.get("body") : "You have a new notification";
    data.put("category", NotificationCategories.normalize(value(data, "category", value(data, "type", "general"))));

    // Keep it in the in-app list (Firebase + SQLite); the key lets "Mark as read" find it later
    String notificationKey = saveNotification(title, body, data);
    if (notificationKey != null) data.put("notificationKey", notificationKey);

    // Turned off in Settings: stay in the list but do not pop up or ring
    if (!NotificationPrefs.isAllowed(getApplicationContext(), data.get("category"))) return;

    if (isAppInForeground()) {
        InAppNotificationCenter.publish(/* title, body, route, orderId ... */);  // branded popup
    }
    showNotification(title, body, data);   // system notification with a "Mark as read" action
}
```
**Also fix steps 3 and 4** of the list above the code: the branded popup is shown while the app is open,
and the system notification is posted in every case (not only in the background); a notification type
turned off in Settings is kept in the list but does not pop up or ring.
**Add (this is also in the proposal):** "A scheduled Cloud Function (`sendPickupReminders`, every 15
minutes) sends each pickup customer one reminder about 30 minutes before their one-hour pickup slot
starts. New-deal pushes are sent by a database trigger when a hotel publishes a dish, and a
`sendScheduledEngagementNotifications` function sends a periodic trending-deals nudge."
**Add:** "A system notification carries a **Mark as read** action. It is handled by
`NotificationActionReceiver`, a manifest-declared BroadcastReceiver that marks the notification read in
Firebase and removes it from the status bar." And: "`OrderSyncWorker` (WorkManager, every 15 minutes)
updates `lastActive`" is correct.

### Page 38, section 5.2.10 (Profile, Settings, Photo)
**Currently says:** the photo is uploaded to "profile_images/{uid}.jpg" and the URL saved to
`users/{uid}/profileImageUrl`.
**Replace with:** "uploaded to Firebase Storage under `profileImages/{uid}/`, and the download URL is
saved to `users/{uid}/profileImage` (older accounts may have `profileImageUrl`; the app reads both)."
**Add:** "The Profile screen shows the number of orders, the lifetime savings, and a **Your savings**
bar chart (MPAndroidChart) of the last six orders." "Settings also has notification switches (order
updates, new deals, pickup reminders), Help and FAQ, Contact support, Rate and Share, an open-source
licences screen, a data-and-privacy screen, Clear cache, Log out and Delete my account."
Replace "Enable or disable push notifications using a SwitchMaterial" with "three notification
switches".

### Pages 38-39, section 5.2.11 (Additional Features)
**Currently says:** FavoritesActivity shows saved dishes from the favourites table.
**Replace with:** "FavoritesActivity has two tabs, **Dishes** and **Hotels**. Both are stored in the
local SQLite database (`favorites` and `favorite_hotels`). Swipe a dish to remove it; tap the heart on a
hotel to remove it. A hotel row has a **View deals** button."
**Currently says:** "NetworkChangeReceiver is implemented as a BroadcastReceiver registered in
MainActivity".
**Replace with (correct again, plus two more):**
> The app uses three BroadcastReceivers in the `receivers` package. `NetworkChangeReceiver` is
> registered in MainActivity for `CONNECTIVITY_ACTION` and shows a toast when the connection is lost or
> back. `GpsStateReceiver` is registered in the Map screen for `PROVIDERS_CHANGED_ACTION` and updates
> the location banner. `NotificationActionReceiver` is declared in the manifest and handles the Mark as
> read button on a notification.

Chat call button: now correct (`TelephonyUtils.callNow()` with the CALL_PHONE permission requested at
the moment the user taps).

### Page 39, section 5.3 intro
**Currently says:** "The hotel web panel is made up of eight dedicated HTML pages".
**Replace with:** "nine HTML pages" (login, register, dashboard, add-food, manage-listings, orders,
analytics, settings, chat). Appendix A already lists the nine.

### Page 39, section 5.3.1 (Hotel Registration)
**Replace the last sentence with:** "The licence file is uploaded to Firebase Storage and its link is kept
in `hotelLicenses/{hotelId}`, which only the owner and administrators can read, so the link is not
visible to other users. A hotel that is not approved cannot sign in (the sign-in page says "Waiting for
approval" or shows the rejection reason). As a second check, the Orders, Manage Listings, Analytics, Chat
and Add Food pages confirm the approval again, and the database rules refuse listings from a hotel that
is not approved."

### Page 39, section 5.3.2 (Dashboard)
Replace the revenue description with: "Total Revenue is the sum of completed and delivered orders;
Pending Orders counts every order that is neither finished nor cancelled (pending, confirmed,
preparing, ready, out for delivery)."

### Pages 40-41, section 5.3.3 (Order Management)
**Replace the first bullet (All Orders) with:**
> **All Orders** lists the hotel's orders. Each row has one button for the next step (Confirm, then
> Preparing, then Ready; for delivery orders Confirm, Preparing, Dispatch, Delivered), a **Done** button
> for pickup orders, and **Cancel**. Every status change sends the customer exactly one push notification
> from the server.
**Replace the Scan QR and Enter OTP bullets' last sentence with:** "A match opens a confirmation sheet
with the customer, the items and the total; pressing Confirm sets the order to completed. A code whose
pickup slot has ended is refused with a clear message (the hotel can still use **Done** if it decides to
hand the food over). Orders whose slot has ended show a **Slot ended** badge."

### Page 41, section 5.3.4 (Add Food Listing)
Add: "The photo is resized in the browser (longest side 1280 px, JPEG) before upload, so large phone
photos become a few hundred kilobytes. When the listing is saved, a Cloud Function
(`onFoodItemCreatedNotify`) sends the new-deal push to customers; the web page no longer sends it."

### Page 42, section 5.3.5 (Manage Listings and Other Pages)
**Currently says:** "a table, with options to edit or delete" (edit did not exist before). It now does:
"**Edit** opens a dialog for the name, category, description, prices, quantity, pickup times and an
optional new photo."
**Currently says:** "The analytics.html page uses Chart.js to present ... revenue trends (orders per
week), top-selling items, and the distribution of items across categories."
**Replace with:** "analytics.html shows revenue for the last 7 days, orders per day for the last 14
days, an order-status chart, the top-selling items and the recent orders. Revenue counts completed and
delivered orders only."
**Currently says:** settings.html lets the hotel "view its current approval status". This is now true:
the page shows the approval status (with the rejection reason if there is one) and the registered
address (read-only).

### Page 42, section 5.4 intro
**Currently says:** "The admin dashboard consists of six pages".
**Replace with:** "eight pages: Login, Approve Hotels, Users, Transactions, Categories, Banners,
Analytics and Notifications."

### Pages 42-43, section 5.4.1 (Hotel Approvals)
**Replace the first paragraph's last sentence and add:**
> The tabs are Pending, Approved, Rejected and Suspended; hotels with a blank or older status
> (`active`, `declined`) are placed in the matching tab. An approved hotel can be **revoked** with a
> reason, and a rejected one can be approved after all. The licence is opened from `hotelLicenses`.

### New section to add after 5.4.1: "5.4.1a User Management"
> The Users page lists every account with its role, phone, join date and status, with filters for
> role and status and a search box. **Suspend** (with an optional reason) calls the `setUserSuspended`
> Cloud Function, which disables the Firebase Authentication account and revokes its sessions, so the
> person cannot sign in until an admin presses **Enable**. Suspending a hotel owner also hides the hotel
> and its dishes from the app. The app shows "Account suspended" to a suspended user who tries to log
> in. Admin accounts cannot be suspended, and nobody can suspend themselves.

### Page 43, section 5.4.2 (Analytics)
All bullets are now true once the following wording is used:
- "Total Revenue (calculated as the sum of all completed orders)" → "**completed and delivered** orders".
- "Total number of customers (unique users)" → "**Registered customers** (accounts with the customer
  role)".
- "Food rescued (total number of items sold)" → "items in completed and delivered orders".
- "Revenue by category, presented as a pie chart" is now a doughnut chart of price × quantity per
  category (category read from the order line or, for older orders, from the dish).
Add: "Every chart and counter shows an error state if the data cannot be loaded."

### Page 44, section 5.4.3 (Categories, Transactions, Notifications)
Now true as written. Add detail: "Categories can be renamed (the dishes in that category move to the new
name), reordered with the arrows, and a duplicate name is refused. The list shows how many dishes use
each category and warns before deleting one that is in use. Transactions shows the items and the payment
method and status of each order, with filters for status, hotel and date range."
**Add to the notifications paragraph:** "If the Cloud Function cannot be reached, the page now says so
and warns that only the in-app copies were saved and no phone push was sent."

### Page 45, section 5.5 (Firebase Integration), rules bullets
**Replace the bullet list with the rules that are actually deployed:**
- Any signed-in user can read `foodItems`, `hotels`, `categories` and `banners`.
- A user can read and write only their own `users/{uid}` record; the `role` can be chosen once at
  sign-up (customer or hotel) and only an admin can change it, and `status` (active or suspended) is
  set only by an admin.
- A hotel can change only its own name, owner name, phone and notification token; `status` is admin-only.
- Only an **approved** hotel can create or change listings, and a listing must point at that hotel.
- A customer creates and reads their own orders and can only cancel one that is still pending; a hotel
  reads only its own orders (by query) and moves them through their statuses.
- Business licences are in `hotelLicenses`, readable only by the owner and admins.
- Admin actions (categories, banners, approvals, user management) require `role = admin` **and** one of
  the two admin email addresses.
Add: "These rules are covered by 127 automated tests that run in the Firebase emulator
(`npm run rules:test`)."

### Page 46, section 5.6 (Challenges)
Optional extra rows that are true of this build: "Notifications were sent twice (from the browser and
from the server)" → "Every status change now notifies from one place, a Cloud Function"; "Anyone
signed in could read a hotel's licence link through the open `hotels` node" → "Licence links moved to
`hotelLicenses`".

---

## Chapter 6: Results and Evaluation

### Page 47-49, Table 9 (functional test cases)
- **TC-08:** step "Tap a marker → **Show route**" (View deals now opens Search for that hotel).
- **TC-12:** "paymentMethod = Cash".
- **TC-26** (network change toast) and **TC-27** (admin categories) are true as written.
- **Add these rows.** Run each one on your phone, then write the real result; do not copy "Pass"
  without trying it.

| TC | Feature | Steps | Expected result |
|---|---|---|---|
| TC-28 | GPS receiver | Open Map, switch GPS off, then on | The banner changes immediately (no refresh) |
| TC-29 | Notification action | Receive a push in the background, tap **Mark as read** | The notification disappears and is read in the app |
| TC-30 | Direct call | Order detail, **Call hotel**, allow the permission | The call starts without the dialer; if refused, the dialer opens |
| TC-31 | SMS | Order detail, **SMS hotel** | The SMS app opens with the message ready |
| TC-32 | External navigation | Order detail, **Navigate to hotel** | Google Maps opens with directions |
| TC-33 | Savings chart | Profile with 2+ completed orders | A bar per order with the saved amount |
| TC-34 | Favourite hotel | Tap the heart on a hotel, open Favorites, **Hotels** | The hotel is listed; **View deals** shows its dishes |
| TC-35 | QR/OTP expiry | Wait until the pickup slot ends, then scan the QR | The app hides the code; the hotel panel refuses it |
| TC-36 | Suspend user | Admin, Users, **Suspend**, then log in as that user | "Account suspended" is shown; **Enable** restores access |
| TC-37 | Order steps | Hotel panel: Confirm, Preparing, Ready, Done | The customer gets one notification per change |
| TC-38 | Category rename | Admin renames a category | The app chip and the hotel form show the new name |
| TC-39 | Approved-only listings | A pending hotel tries to add a dish | The page redirects; direct database writes are refused |
| TC-40 | Hot deals order | Home screen | Hot Deals shows the highest discounts first |

### Page 51, Table 10 (device compatibility)
Keep only the devices you actually tested on. If a phone in the table was not used, remove it.

### Pages 51-52, Table 11 (objectives)
Add rows with Status "Fully met" for the proposal items: "Upcoming-pickup push notifications", "Dashboard
with daily earnings and most popular items", "User suspension". Add rows with Status "Added feature": "User suspension (admin)", "Favourite hotels", "QR/OTP expiry after
the pickup slot", "Three BroadcastReceivers", "Hotel order steps (confirm, prepare, ready, cancel)",
"Savings chart (MPAndroidChart)". The row "Sound on payment success: MediaPlayer in CheckoutActivity"
is correct (the success screen plays it too).

---

## Chapter 7: Future Work

### Page 53, item 6 "Firebase Functions Backend"
**Currently says:** "Currently, push notifications are sent directly from the web panel using the
Firebase HTTP API. Migrating notification handling ... to Firebase Cloud Functions would create a more
robust serverless backend".
**This is no longer true: Cloud Functions already exist.** Replace the item with:
> **6. Moving more logic to Cloud Functions.** Push notifications, OTP emails, stock reduction and
> account suspension already run in Cloud Functions. The next step is to move payment confirmation
> (a PayHere `notify_url` handler) and analytics aggregation to the server, so that totals are not
> recomputed in every browser and a customer can no longer write `paymentStatus` on their own order.

Also consider adding "Per-hotel sign-in with custom claims" and "Aggregated hotel rating on the map"
(the map shows a hotel's rating only when it has one).

---

## Appendices

### Pages 57-58, Appendix A (Complete Screen List)
- **Remove** `CartFragment` and `EditProfileActivity` (they do not exist; the cart is `CartActivity`).
- **Add** `MessagesActivity` (the conversations list), `SearchFragment` (the search screen, hosted by
  `SearchActivity`), and two admin web pages: Admin Users (`admin-panel/users.html`) and Admin Banners
  (`admin-panel/banners.html`). `OrdersFragment` is already listed and is correct.
- The app has 16 activities: Splash, Onboarding, Login, Main, FoodDetail, Favorites, Cart, Checkout,
  Notifications, Settings, Search, OrderDetail, OrderSuccess, Chat, Messages, LocationPicker.

### Pages 58-60, Appendix C (Key Classes)
- **Remove** the `CartFragment` row.
- `MyFirebaseMessagingService` package is `services` (correct).
- `NetworkChangeReceiver` package `receivers` is correct. **Add:** `GpsStateReceiver` (receivers: reacts
  to GPS on/off), `NotificationActionReceiver` (receivers: Mark as read), `CallHelper` (utils: asks the
  CALL_PHONE permission when the user taps, then calls), `PickupWindow` (utils: one-hour pickup slot and
  expiry), `HotelNavigation` (utils: Google Maps links), `Savings` (utils: savings per order),
  `FavoriteHotelToggle` (utils: the heart on a hotel).

### Appendix B (Table 8, delivery schedule)
No change needed.

---

## Things in the proposal (`1 REP.pdf`) that the project still does not do

If an examiner compares the proposal with the final system, these are the differences. Either leave
them out of the final report or mention them under Future Work:
- **Promo-code box in the cart.** It appeared only in the proposal's wireframes ("final UI may vary").
- **Real-time delivery tracking.** Delivery orders exist, but there is no live courier map.
Everything else the proposal promises is now implemented: QR/OTP that expire after the pickup window,
admin user suspension, and favourite hotels.

## Figures to retake

The screenshots in Figures 10-20 show the older screens. After the changes above, retake: Figure 12
(Home), 13 (Map), 14 (Food Detail), 18 (Order Detail), 19 (Notifications), 20 (Profile and Settings),
and for the web panels Figures 21-26, and add one for the new Users page. Store them in
`docs/screenshots/`.
