/**
 * Source of database.rules.json (Realtime Database security rules).
 *
 * The JSON format has no functions, so the long expressions are built here once and pasted in.
 * After editing this file run:   node firebase-rules/build-rules.js
 * and commit both files. See firebase-rules/README.md for the reasoning behind each rule.
 */

const SIGNED_IN = 'auth != null';

// users/{uid}/role is set to "admin" by hand in the Firebase console; clients can never grant it.
// The role alone is not enough: the signed-in address must also be one of the two admin addresses
// (the same two as js/ui-shell.js and storage.rules), so a role set by mistake cannot grant admin.
const ADMIN =
  "(root.child('users').child(auth.uid).child('role').val() === 'admin'" +
  " && (auth.token.email === 'admin@replate.com' || auth.token.email === 'superadmin@replate.com'))";

// The hotels/{key} record owned by the signed-in user (hotel panel stores the key at users/{uid}/hotelId)
const MY_HOTEL_KEY = "root.child('users').child(auth.uid).child('hotelId').val()";

// A hotel account the admin has approved. Covers the normal layout (users/{uid}/hotelId ->
// hotels/{key}.userId) and the legacy layout where the hotel record is stored at hotels/{uid}.
const APPROVED_HOTEL =
  `(${MY_HOTEL_KEY} != null && root.child('hotels').child(${MY_HOTEL_KEY}).child('userId').val() === auth.uid` +
  ` && root.child('hotels').child(${MY_HOTEL_KEY}).child('status').val() === 'approved')` +
  " || root.child('hotels').child(auth.uid).child('status').val() === 'approved'";

// True when the signed-in user owns the hotel whose key is the given expression
const OWNS = (hotelKeyExpr) =>
  `(${hotelKeyExpr} != null && (root.child('hotels').child(${hotelKeyExpr}).child('userId').val() === auth.uid || ${hotelKeyExpr} === auth.uid))`;

// Chat conversation ids look like "<hotelKey>_<customerUid>". A participant is the customer, the
// owner of that hotel, the legacy "<uid>_..." owner, or an admin.
const CHAT_PARTICIPANT = (conv) =>
  `auth != null && (${conv}.endsWith('_' + auth.uid) || ${conv}.beginsWith(auth.uid + '_') || ${ADMIN}` +
  ` || (${MY_HOTEL_KEY} != null && ${conv}.beginsWith(${MY_HOTEL_KEY} + '_')` +
  ` && root.child('hotels').child(${MY_HOTEL_KEY}).child('userId').val() === auth.uid))`;

const ORDER_OWNER = "data.parent().child('userId').val() === auth.uid";
const ORDER_HOTEL = OWNS("data.parent().child('hotelId').val()");

const PENDING_ORDER =
  "(data.child('orderStatus').val() === 'pending_pickup' || data.child('orderStatus').val() === 'pending_delivery')";

// Orders that can no longer change: nobody (except an admin) may move them to another state
const FINAL_ORDER_STATUS = "(data.val() === 'completed' || data.val() === 'delivered' || data.val() === 'cancelled')";
const VALID_ORDER_STATUS =
  "(newData.val() === 'pending_pickup' || newData.val() === 'pending_delivery' || newData.val() === 'pending'" +
  " || newData.val() === 'confirmed' || newData.val() === 'accepted' || newData.val() === 'preparing'" +
  " || newData.val() === 'ready' || newData.val() === 'ready_for_pickup' || newData.val() === 'out_for_delivery'" +
  " || newData.val() === 'completed' || newData.val() === 'delivered' || newData.val() === 'cancelled'" +
  " || newData.val() === 'rejected')";

// What a customer may send when creating an order (or re-sending one that is still pending after a
// flaky connection): their own order, starting in a pending state, and none of the fields that only
// the server or the hotel set. Without this the customer could write the order as already "completed".
const NEW_ORDER_FROM_CUSTOMER =
  "newData.child('userId').val() === auth.uid" +
  " && (newData.child('orderStatus').val() === 'pending_pickup' || newData.child('orderStatus').val() === 'pending_delivery')" +
  " && !newData.child('stockAdjusted').exists() && !newData.child('stockRestored').exists()" +
  " && !newData.child('pickupReminderSent').exists() && !newData.child('completedAt').exists()" +
  " && !newData.child('dispatchedAt').exists() && !newData.child('cancelledAt').exists()" +
  " && !newData.child('cancelledBy').exists()";

// "paid" has to come with the PayHere payment number the app got back, so it cannot simply be typed in
const PAYMENT_FIELDS_OK =
  "(!newData.child('paymentStatus').exists() || newData.child('paymentStatus').val() === 'pending'" +
  " || (newData.child('paymentStatus').val() === 'paid' && newData.child('paymentMethod').val() === 'PayHere Online'" +
  " && newData.child('paymentRef').isNumber() && newData.child('paymentRef').val() > 0))";

// Older orders (before paymentRef existed) keep working: only a change to the payment fields is checked
const PAYMENT_UNCHANGED =
  "(data.exists() && data.child('paymentStatus').val() === newData.child('paymentStatus').val()" +
  " && data.child('paymentMethod').val() === newData.child('paymentMethod').val()" +
  " && data.child('paymentRef').val() === newData.child('paymentRef').val())";

const rules = {
  rules: {
    // ---------------------------------------------------------------- users
    users: {
      '.read': ADMIN,
      '.indexOn': ['createdAt'],
      $uid: {
        '.read': `auth != null && auth.uid === $uid`,
        '.write': `auth != null && (auth.uid === $uid || ${ADMIN})`,
        // A suspended account cannot lift its own suspension by rewriting its profile. (The real
        // block is the disabled Firebase Auth account; this keeps the displayed status honest.)
        '.validate': `${ADMIN} || data.child('status').val() !== 'suspended' || newData.child('status').val() === 'suspended'`,
        // active / suspended is set by the setUserSuspended Cloud Function or an admin, never by the user.
        status: { '.validate': ADMIN },
        // Nobody can promote themselves: a role is chosen once at sign-up (customer or hotel) and
        // can only be changed by an admin.
        role: {
          '.validate':
            `${ADMIN} || (newData.isString() && (data.exists() ? newData.val() === data.val() : (newData.val() === 'customer' || newData.val() === 'hotel')))`,
        },
        userType: {
          '.validate':
            `${ADMIN} || (newData.isString() && (data.exists() ? newData.val() === data.val() : (newData.val() === 'customer' || newData.val() === 'hotel')))`,
        },
      },
    },

    // --------------------------------------------------------------- hotels
    hotels: {
      // The customer map reads every hotel. (This also exposes each hotel's contact fields; moving
      // private fields such as fcmToken to a separate node is a future improvement.)
      '.read': SIGNED_IN,
      '.indexOn': ['userId', 'createdAt'],
      $hotelId: {
        // Anyone signed in can register a hotel, but only as "pending" and only for themselves.
        // Approving / rejecting (status, rejectionReason) is admin-only.
        '.write': `${ADMIN} || (auth != null && !data.exists() && newData.child('userId').val() === auth.uid && newData.child('status').val() === 'pending')`,
        hotelName: { '.write': `auth != null && (data.parent().child('userId').val() === auth.uid || $hotelId === auth.uid)` },
        ownerName: { '.write': `auth != null && (data.parent().child('userId').val() === auth.uid || $hotelId === auth.uid)` },
        phone: { '.write': `auth != null && (data.parent().child('userId').val() === auth.uid || $hotelId === auth.uid)` },
        fcmToken: { '.write': `auth != null && (data.parent().child('userId').val() === auth.uid || $hotelId === auth.uid)` },
        notification: { '.write': `auth != null && (data.parent().child('userId').val() === auth.uid || $hotelId === auth.uid)` },
      },
    },

    // Business licences are kept apart from `hotels` because every signed-in user can read that node
    // (the map needs it) and a licence link must not be public. Only the owner and admins read it.
    hotelLicenses: {
      $hotelId: {
        '.read': `auth != null && (${ADMIN} || data.child('userId').val() === auth.uid)`,
        '.write': `auth != null && (${ADMIN} || (!data.exists() && newData.child('userId').val() === auth.uid))`,
        '.validate': "newData.child('licenseUrl').isString() && newData.child('licenseUrl').val().beginsWith('https://')",
      },
    },

    // ----------------------------------------------------------- foodItems
    foodItems: {
      '.read': SIGNED_IN,
      '.indexOn': ['hotelId', 'createdAt'],
      $foodId: {
        // The hotel that owns a listing (hotelUserId) manages it, but only while an admin has the
        // hotel approved (a pending, rejected or suspended hotel cannot publish), and a listing can
        // only point at the owner's own hotel. Deleting is allowed for the owner. Quantity/status are
        // also changed by the onOrderCreatedNotify Cloud Function, which bypasses these rules.
        '.write': `${ADMIN} || (auth != null && (data.exists() ? data.child('hotelUserId').val() : newData.child('hotelUserId').val()) === auth.uid` +
          ` && (${APPROVED_HOTEL})` +
          ` && (!newData.exists() || newData.child('hotelId').val() === ${MY_HOTEL_KEY} || newData.child('hotelId').val() === auth.uid))`,
        // Prices and stock must be real numbers inside sensible limits.
        originalPrice: { '.validate': 'newData.isNumber() && newData.val() >= 0 && newData.val() <= 1000000' },
        discountedPrice: { '.validate': 'newData.isNumber() && newData.val() >= 0 && newData.val() <= 1000000' },
        quantity: { '.validate': 'newData.isNumber() && newData.val() >= 0 && newData.val() <= 1000' },
        // Customers rate food, so they may update the aggregate - but only with sane numbers.
        avgRating: {
          '.write': SIGNED_IN,
          '.validate': 'newData.isNumber() && newData.val() >= 0 && newData.val() <= 5',
        },
        reviewCount: {
          '.write': SIGNED_IN,
          '.validate': 'newData.isNumber() && newData.val() >= 0',
        },
      },
    },

    // --------------------------------------------------------------- orders
    orders: {
      // Only admins can read the whole node. Everyone else must run a query for their own orders:
      //   customers: orderByChild('userId').equalTo(<my uid>)
      //   approved hotels: orderByChild('hotelId').equalTo(<my hotel key or uid>)
      // A single order can also be read directly (see $orderId below).
      '.read': `auth != null && (${ADMIN} || (query.orderByChild === 'userId' && query.equalTo === auth.uid)` +
        ` || ((${APPROVED_HOTEL}) && query.orderByChild === 'hotelId' && (query.equalTo === auth.uid || query.equalTo === ${MY_HOTEL_KEY})))`,
      '.indexOn': ['userId', 'createdAt', 'hotelId', 'pickupExpiresAt'],
      $orderId: {
        '.read': `auth != null && (data.child('userId').val() === auth.uid || ${ADMIN} || ${OWNS("data.child('hotelId').val()")})`,
        // A customer creates their own order, and may re-send it while it is still pending (a retry
        // after a flaky connection reuses the same order id). Afterwards only the field-level rules
        // below apply.
        // A re-send is only possible before the server has processed the order (stockAdjusted).
        '.write': `auth != null && (${ADMIN} || (${NEW_ORDER_FROM_CUSTOMER} && (!data.exists() || (data.child('userId').val() === auth.uid && ${PENDING_ORDER} && !data.child('stockAdjusted').exists()))))`,
        '.validate': `newData.hasChildren(['userId', 'orderStatus', 'createdAt']) && newData.child('totalAmount').isNumber() && newData.child('totalAmount').val() >= 0 && (${PAYMENT_FIELDS_OK} || ${PAYMENT_UNCHANGED})`,
        // Customer can only cancel; the hotel (once approved) moves the order through its other states.
        // A finished order (completed, delivered, cancelled) can never be moved again.
        orderStatus: {
          '.write': `auth != null && !${FINAL_ORDER_STATUS} && ((${ORDER_OWNER} && newData.val() === 'cancelled') || (${ORDER_HOTEL} && (${APPROVED_HOTEL})))`,
          '.validate': VALID_ORDER_STATUS,
        },
        updatedAt: { '.write': `auth != null && (${ORDER_OWNER} || ${ORDER_HOTEL})` },
        cancelledAt: { '.write': `auth != null && ${ORDER_OWNER}` },
        // Who cancelled it, so the right person is told. The customer app does not set it (no value
        // means the customer cancelled); a hotel that cancels sets "hotel".
        cancelledBy: {
          '.write': `auth != null && (${ORDER_OWNER} || ${ORDER_HOTEL})`,
          '.validate': "newData.val() === 'customer' || newData.val() === 'hotel'",
        },
        completedAt: { '.write': `auth != null && ${ORDER_HOTEL}` },
        dispatchedAt: { '.write': `auth != null && ${ORDER_HOTEL}` },
        reviewRating: { '.write': `auth != null && ${ORDER_OWNER}` },
        reviewComment: { '.write': `auth != null && ${ORDER_OWNER}` },
        reviewAt: { '.write': `auth != null && ${ORDER_OWNER}` },
        hasReview: { '.write': `auth != null && ${ORDER_OWNER}` },
        // Account deletion strips personal data from the user's old orders.
        userId: { '.write': `auth != null && ${ORDER_OWNER} && newData.val() === '[deleted]'` },
        customerName: { '.write': `auth != null && ${ORDER_OWNER} && newData.val() === '[deleted]'` },
        customerPhone: { '.write': `auth != null && ${ORDER_OWNER} && newData.val() === '[deleted]'` },
        userName: { '.write': `auth != null && ${ORDER_OWNER} && newData.val() === '[deleted]'` },
        userEmail: { '.write': `auth != null && ${ORDER_OWNER} && newData.val() === '[deleted]'` },
        userPhone: { '.write': `auth != null && ${ORDER_OWNER} && newData.val() === '[deleted]'` },
        accountDeleted: { '.write': `auth != null && ${ORDER_OWNER} && newData.val() === true` },
      },
    },

    // -------------------------------------------------------------- reviews
    reviews: {
      '.read': SIGNED_IN,
      '.indexOn': ['foodId'],
      $reviewId: {
        '.write': `auth != null && ((!data.exists() && newData.child('userId').val() === auth.uid) || (data.exists() && data.child('userId').val() === auth.uid && (!newData.exists() || newData.child('userId').val() === auth.uid)))`,
        rating: { '.validate': 'newData.isNumber() && newData.val() >= 0 && newData.val() <= 5' },
        comment: { '.validate': 'newData.isString() && newData.val().length <= 1000' },
      },
    },

    // -------------------------------------------------------- notifications
    notifications: {
      $uid: {
        '.read': `auth != null && auth.uid === $uid`,
        // You manage your own inbox...
        '.write': `auth != null && (auth.uid === $uid || ${ADMIN})`,
        // ...and any signed-in user may drop a NEW notification into someone else's inbox (a
        // customer notifying the hotel about an order or a review). They cannot edit or delete one.
        $notificationId: {
          '.write': `auth != null && !data.exists() && newData.exists() && (auth.uid === $uid || ${ADMIN} || (newData.child('read').val() === false && newData.child('orderId').isString() && root.child('orders').child(newData.child('orderId').val()).child('userId').val() === auth.uid))`,
          '.validate': "newData.hasChildren(['title', 'body', 'timestamp']) && newData.child('title').isString() && newData.child('title').val().length <= 200 && newData.child('body').isString() && newData.child('body').val().length <= 1000",
        },
      },
    },

    // ---------------------------------------------------------------- chats
    chats: {
      // Only the two participants (and admins) can read a chat. There is no node-level read for
      // hotels, so one hotel can never read another hotel's customer conversations.
      '.read': `auth != null && ${ADMIN}`,
      $convId: {
        '.read': CHAT_PARTICIPANT('$convId'),
        '.write': CHAT_PARTICIPANT('$convId'),
        messages: {
          '.indexOn': ['timestamp'],
          $messageId: {
            '.validate': "newData.hasChildren(['senderId', 'message', 'timestamp']) && newData.child('message').isString() && newData.child('message').val().length <= 2000",
          },
        },
      },
    },

    conversations: {
      $ownerId: {
        '.indexOn': ['lastTimestamp'],
        '.read': `auth != null && (auth.uid === $ownerId || ${ADMIN} || (${MY_HOTEL_KEY} === $ownerId && root.child('hotels').child($ownerId).child('userId').val() === auth.uid))`,
        // Both sides of a chat write the summary rows (customer -> hotel inbox and vice versa).
        $convId: {
          '.write': `auth != null && (auth.uid === $ownerId || ${CHAT_PARTICIPANT('$convId')})`,
        },
      },
    },

    // --------------------------------------------------- admin-managed data
    categories: {
      '.read': SIGNED_IN,
      '.write': `auth != null && ${ADMIN}`,
      '.indexOn': ['order'],
    },
    banners: {
      '.read': SIGNED_IN,
      '.write': `auth != null && ${ADMIN}`,
      '.indexOn': ['order'],
      $bannerId: {
        title: { '.validate': 'newData.isString() && newData.val().length <= 80' },
        subtitle: { '.validate': 'newData.isString() && newData.val().length <= 160' },
        imageUrl: { '.validate': "newData.isString() && newData.val().beginsWith('https://')" },
        deepLink: { '.validate': 'newData.isString() && newData.val().length <= 300' },
      },
    },
    notificationLogs: {
      '.read': `auth != null && ${ADMIN}`,
      '.write': `auth != null && ${ADMIN}`,
      '.indexOn': ['createdAt'],
    },

    // ------------------------------------------------------------ per user
    favorites: {
      $uid: {
        '.read': 'auth != null && auth.uid === $uid',
        '.write': 'auth != null && auth.uid === $uid',
      },
    },
    indexes: {
      foodByHotel: {
        $hotelKey: {
          '.write': `auth != null && (${ADMIN} || ${OWNS('$hotelKey')})`,
        },
      },
      ordersByUser: {
        $uid: {
          '.read': 'auth != null && auth.uid === $uid',
          '.write': 'auth != null && auth.uid === $uid',
        },
      },
    },

    // otpVerifications and anything not listed above: no client access (Cloud Functions use the admin SDK)
  },
};

module.exports = rules;
