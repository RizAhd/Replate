/**
 * Source of database.rules.json (Realtime Database security rules).
 *
 * The JSON format has no functions, so the long expressions are built here once and pasted in.
 * After editing this file run:   node firebase-rules/build-rules.js
 * and commit both files. See firebase-rules/README.md for the reasoning behind each rule.
 */

const SIGNED_IN = 'auth != null';

// users/{uid}/role is set to "admin" by hand in the Firebase console; clients can never grant it.
const ADMIN = "root.child('users').child(auth.uid).child('role').val() === 'admin'";

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
      '.indexOn': ['userId', 'createdAt', 'hotelId'],
      $orderId: {
        '.read': `auth != null && (data.child('userId').val() === auth.uid || ${ADMIN} || ${OWNS("data.child('hotelId').val()")})`,
        // A customer creates their own order, and may re-send it while it is still pending (a retry
        // after a flaky connection reuses the same order id). Afterwards only the field-level rules
        // below apply.
        '.write': `auth != null && (${ADMIN} || ((!data.exists() || (data.child('userId').val() === auth.uid && ${PENDING_ORDER})) && newData.child('userId').val() === auth.uid))`,
        '.validate': "newData.hasChildren(['userId', 'orderStatus', 'createdAt']) && newData.child('totalAmount').isNumber() && newData.child('totalAmount').val() >= 0",
        // Customer can only cancel; the hotel moves the order through its other states.
        orderStatus: {
          '.write': `auth != null && ((${ORDER_OWNER} && newData.val() === 'cancelled') || ${ORDER_HOTEL})`,
        },
        updatedAt: { '.write': `auth != null && (${ORDER_OWNER} || ${ORDER_HOTEL})` },
        cancelledAt: { '.write': `auth != null && ${ORDER_OWNER}` },
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
          '.write': 'auth != null && !data.exists() && newData.exists()',
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
