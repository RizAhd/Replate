# Production Manual Setup Checklist

## 1) Firebase Cloud Messaging (FCM)

1. In Firebase Console -> Project Settings -> Cloud Messaging:
   - Ensure **Firebase Cloud Messaging API (V1)** is enabled.
2. Verify Android app config:
   - `google-services.json` is from the same Firebase project used in production.
3. Android notification channel alignment:
   - App channel id: `replate_notifications_v3`
   - Functions channel id: `replate_notifications_v3`
4. If you use topics from admin/backend:
   - Supported app topics include: `all_users`, `customers`, `hotels`, `admins`, `deals_customers`.

## 2) Firebase Realtime Database Rules (minimum)

- Allow users to read/write their own notification and token nodes only.
- Ensure admin/service account can write broadcast/targeted notifications.
- Ensure hotel/admin role checks for backend-triggered actions.

## 3) Firebase Functions deployment

Run from `firebase-functions` folder:

```bash
npm install
firebase login
firebase use <your-project-id>
firebase deploy --only functions
```

## 4) Environment variables for Functions (OTP mail)

Set runtime env vars for the OTP function:

```bash
firebase functions:config:set otp.email="YOUR_EMAIL" otp.email_app_password="YOUR_APP_PASSWORD"
```

If your current code reads `process.env`, configure equivalent env in your Functions runtime/deployment pipeline.

## 5) Google Maps + Directions

1. In Google Cloud Console:
   - Enable Maps SDK for Android
   - Enable Directions API
2. Restrict your API key to your Android app package/signing cert and required APIs.
3. Ensure app resource key is valid in `@string/google_maps_key`.

## 6) Android 13+ notification permission

- Keep runtime permission prompt for `POST_NOTIFICATIONS`.
- Verify app settings allow notifications and the channel is not blocked.

## 7) Required database fields consistency

Ensure hotel and food nodes contain consistent keys:

- Hotels: `latitude`, `longitude`, `status`, `hotelName`, `address`
- Food items: `hotelId`, `status`, `quantity`, `foodName`, `discountedPrice`

## 8) Push payload contract (recommended)

For admin/backend triggered push payloads, include:

- `title`, `body`
- `category` (e.g. `pickup_ready`, `order_accepted`, `hot_deals`)
- `route` (`map`, `order`, `home`, `notifications`)
- Optional: `orderId`, `hotelId`, `deepLink`

This guarantees correct click navigation in-app.

## 9) Pre-release verification pass (manual)

1. Login as customer -> allow/deny/permanent deny location flow.
2. Open map -> verify live location + nearby hotels.
3. Select hotel -> verify route polyline + distance + ETA + camera fit.
4. Trigger push types from admin/functions and confirm tap navigation.
5. Cold open app -> verify cached home/search data appears quickly.
6. Validate search filters (nearest, price, rating, category, availability).


