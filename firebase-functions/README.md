# REPLATE Notifications Backend (Firebase Functions)

## Implemented endpoints
- `sendBroadcastNotification` (admin)
- `sendTargetedNotification` (admin/hotel)
- `sendDealNotification` (admin/hotel)

## Implemented automatic triggers
- `onUserProfileCreatedNotify` -> welcome notification
- `onOrderCreatedNotify` -> order placed notifications for customer + hotel
- `onOrderStatusChangedNotify` -> accepted/cancelled/pickup-ready updates
- `onFoodItemCreatedNotify` -> new deal topic notification
- `sendScheduledEngagementNotifications` -> hot deals reminder every 2 hours

## Notification categories in production contract
- `welcome`
- `new_deals`
- `hot_deals`
- `order_placed`
- `order_accepted`
- `order_rejected`
- `order_cancelled`
- `pickup_ready`
- `pickup_reminder`
- `admin_announcement`
- `admin_broadcast`
- `hotel_new_order`
- `profile_updated`
- `login_success`

## Manual setup required
1. Enable Firebase Cloud Messaging API v1 in Google Cloud.
2. Deploy functions:
   - `firebase login`
   - `firebase use replate-food-rescue`
   - `firebase deploy --only functions`
3. If OTP mail is used, configure env vars:
   - `firebase functions:config:set otp.email="..." otp.email_app_password="..."`
4. In web panels env config, set `FUNCTIONS_BASE_URL` to your deployed region URL.
5. Keep Android notification channel `replate_notifications_v2` enabled on device.
