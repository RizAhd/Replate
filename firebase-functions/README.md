# REPLATE Cloud Functions

Server-side work the clients must not be trusted with: sending push notifications and emails, changing
stock, suspending accounts and confirming PayHere payments. Node 20, `firebase-functions` 5 (first
generation API), `firebase-admin` 12 and Nodemailer. Every function runs in `asia-southeast1`.

[Back to the main README](../README.md)

## Files

| File | What it holds |
|---|---|
| `index.js` | Every exported function |
| `stock.js` | Taking and restoring stock for an order, in transactions |
| `pickup-reminder.js` | When a pickup reminder is due, kept free of Firebase so it can be tested |
| `safe.js` | Small helpers: HTML escaping, rate limits, quantity caps, error mapping, PayHere signature check |

## Exported functions

### HTTPS endpoints

| Function | What it does |
|---|---|
| `sendCustomVerificationEmail` | Emails a branded email verification link; rate-limited per address and per caller IP |
| `sendOtpEmail` | Creates a 6-digit code, stores only its hash for 10 minutes and emails it; rate-limited |
| `verifyOtp` | Checks a typed code against the stored hash; at most 5 attempts per code |
| `sendBroadcastNotification` | Admin only: push to all users, one role, or a topic |
| `sendTargetedNotification` | Admin or hotel: push to chosen users (at most 500); a hotel may only reach customers who ordered from it |
| `sendDealNotification` | Admin only: deal push to the `deals_customers` topic and to every customer directly, saved in their inbox |
| `sendSelfTestPush` | Any signed-in user: a test push to their own device |
| `setUserSuspended` | Admin only: disables or re-enables an Auth account, signs it out, and hides or restores the owner's hotel and dishes |
| `payhereNotify` | Called by PayHere after a payment: checks the signature and the amount, then sets `paymentVerified` on the order |

The admin, hotel and self-test endpoints need a Firebase ID token in `Authorization: Bearer <token>`.
An admin call also needs one of the admin email addresses (see
[the admin panel README](../web-panels/admin-panel/README.md#how-an-account-becomes-admin)).

### Realtime Database triggers

| Function | Trigger | What it does |
|---|---|---|
| `onFoodItemCreatedNotify` | create `/foodItems/{foodId}` | New-deal push to the `deals_customers` topic |
| `onUserProfileCreatedNotify` | create `/users/{userId}` | Welcome notification |
| `onOrderCreatedNotify` | create `/orders/{orderId}` | Takes the stock, then tells the customer and the hotel |
| `onOrderStatusChangedNotify` | update `/orders/{orderId}` | One push per status change; gives the stock back when an order is cancelled |
| `onHotelStatusChangedNotify` | update `/hotels/{hotelId}` | Tells the owner when the hotel is approved or rejected; hides its dishes when approval is withdrawn and shows them again on approval |
| `onChatMessageNotify` | create `/chats/{convId}/messages/{messageId}` | Pushes a new chat message to the other side (hotel owner or customer) |

### Scheduled

| Function | Schedule | What it does |
|---|---|---|
| `sendPickupReminders` | every 15 minutes (Asia/Colombo) | One reminder per pickup order, 30 minutes before its one-hour slot starts (or at the next run if the slot is already open); `pickupReminderSent` stops repeats |
| `sendScheduledEngagementNotifications` | every 2 hours (Asia/Colombo) | Breakfast, lunch or dinner deals reminder to the `deals_customers` topic; nothing between 22:00 and 05:59 |

## Configuration

The functions read these environment variables. Put them in `firebase-functions/.env`, which is
git-ignored:

```properties
OTP_EMAIL=...                  # Gmail address that sends the verification and OTP emails
OTP_EMAIL_APP_PASSWORD=...     # an app password for that Gmail account
PAYHERE_MERCHANT_SECRET=...    # needed by payhereNotify to check PayHere's signature
```

Other settings live in `index.js`:

- `REGION` (`asia-southeast1`). The app's sign-up screen and the panels' `FUNCTIONS_BASE_URL` use the
  same region, so change them together.
- `ADMIN_EMAILS`, the two admin addresses, shared with the panels and the security rules.
- The continue link in the verification email, a hosting address in `sendCustomVerificationEmail`.
  Point it at your own project.

To have PayHere call `payhereNotify`, put its address in the app's `local.properties` as
`PAYHERE_NOTIFY_URL` (see [app/README.md](../app/README.md#setup)).

## Deploy

```bash
cd firebase-functions
npm install
firebase deploy --only functions      # or: npm run deploy
```

Deploy the functions **before** the security rules: the rules stop customers from writing stock, so
stock only goes down once `onOrderCreatedNotify` is live. Deploying Cloud Functions needs the Firebase
Blaze (pay as you go) plan.

`npm run serve` starts the functions emulator.

## Tests

From the repository root:

```bash
npm run functions:test                   # pickup reminder decisions and the safe.js helpers
npm install --prefix firebase-functions  # once, before the stock test
npm run stock:test                       # stock.js against the Realtime Database emulator (JDK 21+)
```

GitHub Actions runs all three, plus `node --check firebase-functions/index.js`.
