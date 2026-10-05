# REPLATE administrator guide

This guide is for the REPLATE team member who runs the platform: approving hotels, managing accounts,
categories and banners, watching orders and money, and sending announcements. You work in the **admin
web panel** in a browser.

## 1. Signing in

Open the admin panel's sign-in page and enter the admin email and password. Only the two admin
addresses set up for the platform (`admin@replate.com` and `superadmin@replate.com` by default) can sign
in; any other address is refused even if the password is right. The account also needs the `admin` role
on its user record in Firebase (see the Technical Guide, "First-time setup").

The menu on the left has: **Approve Hotels**, **Users**, **Transactions**, **Categories**, **Banners**,
**Analytics**, **Notifications** and **Logout**. On a small screen use the button at the top left.

## 2. Approve Hotels

Every hotel that registers appears here as a card with the hotel name, owner, email, phone, address, map
position and a **View licence** link (the business licence the hotel uploaded). Check that the licence
and details look genuine.

There are four tabs:
- **Pending**: waiting for you. **Approve** lets the hotel sign in and publish dishes. **Reject** asks for
  a reason, which the hotel sees.
- **Approved**: live hotels. **Revoke approval** (with a reason) hides the hotel again.
- **Rejected**: declined or revoked hotels. **Approve after all** reverses a decision.
- **Suspended**: hotels hidden because their owner's account is suspended. To bring one back, enable the
  owner on the **Users** page.

Hotels with no status or an older one (such as `active` or `declined`) are placed in the matching tab.
The hotel gets a notification when it is approved or rejected.

## 3. Users

A table of every account: name, email, phone, role (customer, hotel or admin), join date and status. Four
counters at the top show all users, customers, hotels and suspended accounts. Use the search box and the
**Role** and **Status** drop-downs to narrow the list; the page keeps your filter while new accounts
arrive.

**Suspend** an account when someone breaks the rules:
1. Click **Suspend** on the row and, if you wish, write a reason (only admins see it).
2. Confirm. The person is signed out and cannot sign in again. In the app they see **Account
   suspended**. If the account belongs to a hotel owner, the hotel and all its dishes disappear from the
   app too.
3. To undo it, click **Enable**. The account, the hotel and the dishes come back as they were.

You cannot suspend yourself or another admin. Suspending needs the Cloud Functions to be deployed; if it
fails the page says so.

## 4. Transactions

Every order across all hotels, newest first (the latest 500). Columns: order number, date, customer,
hotel, items, amount, payment (method and paid or pending) , review and status.

Narrow the list with the **Status** (pending, completed and delivered, cancelled), **Hotel** and **From /
To** date filters and the search box. Under the filters the page shows how many orders are in view and
the **completed revenue** of that view. Your search and page are kept when live updates arrive.

## 5. Categories

The categories customers browse in the app and hotels choose when adding a dish.
- **Add**: name (up to 40 characters, no duplicates, not "All") and a position.
- **Arrows** move a category up or down. The order is the order of the chips in the app.
- **Pencil** renames it. Dishes using the old name move to the new name automatically.
- **Bin** deletes it. The **Dishes** column shows how many dishes use it, and the warning tells you if
  some do (they stay on sale but no longer appear under that chip).
The app updates by itself. If you delete every category the app falls back to its built-in six.

## 6. Banners

The photos that slide across the top of the app's Home screen.
- **Title**, **Subtitle** and **Badge** (short label such as HOT DEAL).
- **Image**: a wide picture, about 2:1 (for example 1080 by 540).
- **Tap action** (optional): `category:Bakery` opens a category, `tab:map` opens a tab, or a full
  `https://` link opens in the browser.
- **Order**, an **Active** switch, and optional **Show from / Show until** dates.

## 7. Analytics

Live numbers for the whole platform:
- **Revenue (completed orders)**: money from orders that were completed or delivered. Orders that are
  still waiting or were cancelled do not count.
- **Total Orders**, **Registered Customers** (accounts with the customer role) and **Food Rescued
  (collected)**: dishes in completed and delivered orders.
- Charts: **orders per week**, **order status**, **revenue per week**, **revenue by category**,
  **payment methods**, **top hotels by orders** and the hotel counts (approved, pending, rejected).
The revenue and orders tiles show the change against last week. If the data cannot be loaded a message appears instead of a
blank chart.

## 8. Notifications

The page has three tabs: **Live Activity**, **Send Notifications** and **Send History**.
- **Live Activity** is a real-time feed: new orders, order status changes, hotel registrations and status
  changes, new listings and new users. It reports a real status change only, not every small edit.
- **Send Notifications** has three forms:
  - **Broadcast**: to **All Users**, **Customers**, **Hotels**, **Admins** or the **Deals Topic**.
  - **Targeted**: to specific people. Enter their user IDs (shown in Firebase) separated by commas.
  - **Deal Campaign**: tell every customer about a deal.
- **Send History** lists what was sent and the result.

If the push service (the Cloud Function) cannot be reached, the page now warns you that the message was
saved in the people's in-app notification lists only and **no phone push was sent**. Check that the
functions are deployed.

## 9. Good practice

- Look at **Approve Hotels** daily; a pending hotel cannot sell until you act.
- Always give a reason when rejecting, revoking or suspending; the hotel or your future self will need it.
- Check **Transactions** for orders stuck in pending for a long time.
- Keep the admin passwords private; only the two admin addresses can use this panel.

## 10. If something goes wrong

| Problem | What to do |
|---|---|
| "This email is not an admin account" | Only the two admin addresses can sign in. |
| Sign-in works but pages send me back to login | The account is missing the `admin` role in Firebase. |
| "Could not be loaded" on a page | Check your connection and that you are signed in as admin; reload. |
| Suspend or Enable fails | The `setUserSuspended` Cloud Function is not deployed or you are not an admin. |
| A push says "saved in in-app lists only" | Deploy the Cloud Functions. |
