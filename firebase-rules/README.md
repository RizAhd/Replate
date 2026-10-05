# Firebase security rules

| File | What it is |
|---|---|
| `database.rules.src.js` | Human-editable source of the Realtime Database rules (named building blocks like `ADMIN`, `APPROVED_HOTEL`). |
| `database.rules.json` | Generated from the source. This is what Firebase deploys. |
| `storage.rules` | Cloud Storage rules. |
| `tests/rules.test.js` | 100+ checks that run the rules in the Firebase emulator. |

After editing the source, regenerate and test:

```bash
npm install          # once
npm run rules:build  # rewrites database.rules.json
npm run rules:test   # needs Java; starts the database emulator and runs the checks
```

## Before you deploy (important)

The rules assume how the app and web panels use the data **today**. Do these first:

1. **Make sure an admin exists.** In the Firebase console, open the Realtime Database and set
   `users/<your admin uid>/role` to `"admin"`. Clients can never grant this role, so nothing else will.
   The admin must also sign in with `admin@replate.com` or `superadmin@replate.com`: the rules require
   the role **and** one of those two addresses (the same two as `web-panels/js/ui-shell.js` and
   `storage.rules`).
2. **Deploy the Cloud Functions first** (`firebase deploy --only functions`). Stock is now reduced only
   by `onOrderCreatedNotify`, because customers can no longer write `foodItems/*/quantity`. If the
   function is not deployed, stock will not go down when orders are placed.
3. **Admin email for Storage.** Storage rules cannot read the database, so they recognise the admin by
   sign-in email: `admin@replate.com` and `superadmin@replate.com` (the same two the admin panel login
   accepts). If your admin uses a different address, change `isAdmin()` in `storage.rules`.
4. Deploy: `firebase deploy --only database,storage`.
5. Smoke-test with a customer account, a hotel account and the admin panel (see the checklist below).
   You can roll back from the console's Rules tab, which keeps previous versions.

## Who can do what

- **Customers** read food, hotels, banners and reviews; read and create **their own** orders (the
  "my orders" query is the only way to list orders); cancel a pending order; review a completed one;
  read/write their own profile, notifications and chats.
- **Hotels** (approved by an admin) manage their own listings, update the status of their own orders,
  read **only their own** orders (a `hotelId` query, never the whole list), and chat with customers.
- **Admins** (role `admin` and one of the two admin addresses) approve hotels and manage categories,
  banners, users, notifications and logs. Suspending an account is done by the `setUserSuspended`
  Cloud Function; `users/<uid>/status` can only be written by an admin and a suspended account cannot
  clear it.
- Only an **approved** hotel can create or edit `foodItems`, and a listing must carry that hotel's own
  `hotelId`. Business licences live in `hotelLicenses/<hotelId>`, readable only by the owner and admins
  (older hotels: run `scripts/migrate-hotel-licenses.js` once).
- **Cloud Functions** use the admin SDK and bypass all of these rules.
- Nobody can promote themselves: a role is chosen once at sign-up (customer or hotel) and only an
  admin can change it. New hotels can only register as `pending`.
- Anything not listed in the rules (for example `otpVerifications`) is closed to clients.

## Known limitations (be aware, not blockers)

- **Payment is still decided by the app.** A customer can write `paymentStatus: "paid"` on their own
  order. Fixing this needs a PayHere `notify_url` handled by a Cloud Function; the rules cannot do it.
- **Customers can read the whole `hotels` node** (the map needs it), which includes each hotel's phone,
  email and FCM token. Moving private fields to a separate node would fix this.
- `avgRating` / `reviewCount` on food items are still written by the customer app (validated to a sane
  range). A Cloud Function that recomputes them from `reviews` would remove that trust.
- Storage cannot tell a hotel from a customer, so `food-images/` only checks sign-in, type and size.

## Smoke-test checklist after deploying

- Customer: sign in, browse food and the map, place a cash order, see it under My Orders, cancel a
  pending order, leave a review, send a chat message to a hotel, change profile photo.
- Hotel: sign in to the panel, add a listing with a photo, see the new order, mark it completed, reply
  to the chat.
- Admin: sign in, approve a pending hotel, add a banner with an image, send a notification.
- Check the stock of a listing drops by exactly the ordered quantity after one order.
