/**
 * Security-rules tests for the Realtime Database. Run with:   npm run rules:test
 * (starts the Firebase emulator, loads firebase-rules/database.rules.json and checks who can do what).
 * Needs Java on the PATH for the emulator.
 */
const fs = require('fs');
const path = require('path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { ref, get, set, update, remove, push, query, orderByChild, equalTo } = require('firebase/database');

const RULES = fs.readFileSync(path.join(__dirname, '..', 'database.rules.json'), 'utf8');

let passed = 0, failed = 0;
async function check(name, promise) {
  try { await promise; passed++; console.log('  ok   ', name); }
  catch (e) { failed++; console.log('  FAIL ', name, '->', (e.message || e).toString().split('\n')[0]); }
}

(async () => {
  const env = await initializeTestEnvironment({
    projectId: 'demo-replate',
    database: { rules: RULES },
  });

  // ----- seed data with rules disabled
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.database();
    await set(ref(db, 'users/admin1'), { role: 'admin', name: 'Admin' });
    await set(ref(db, 'users/cust1'), { role: 'customer', name: 'Cust One' });
    await set(ref(db, 'users/cust2'), { role: 'customer', name: 'Cust Two' });
    await set(ref(db, 'users/hotelOwner1'), { role: 'hotel', hotelId: 'hotelA' });
    await set(ref(db, 'users/hotelOwner2'), { role: 'hotel', hotelId: 'hotelB' });
    await set(ref(db, 'users/pendingOwner'), { role: 'hotel', hotelId: 'hotelP' });
    await set(ref(db, 'users/stillPending'), { role: 'hotel', hotelId: 'hotelQ' });
    await set(ref(db, 'users/suspendedUser'), { role: 'customer', name: 'Sus', status: 'suspended' });
    await set(ref(db, 'users/suspendedOwner'), { role: 'hotel', hotelId: 'hotelS', status: 'suspended' });
    await set(ref(db, 'hotels/hotelS'), { userId: 'suspendedOwner', status: 'suspended', hotelName: 'S' });
    await set(ref(db, 'hotelLicenses/hotelA'), { userId: 'hotelOwner1', licenseUrl: 'https://x/license.pdf' });
    await set(ref(db, 'hotels/hotelQ'), { userId: 'stillPending', status: 'pending', hotelName: 'Q' });
    await set(ref(db, 'hotels/hotelA'), { userId: 'hotelOwner1', status: 'approved', hotelName: 'A' });
    await set(ref(db, 'hotels/hotelB'), { userId: 'hotelOwner2', status: 'approved', hotelName: 'B' });
    await set(ref(db, 'hotels/hotelP'), { userId: 'pendingOwner', status: 'pending', hotelName: 'P' });
    await set(ref(db, 'foodItems/f1'), { hotelId: 'hotelA', hotelUserId: 'hotelOwner1', quantity: 5, foodName: 'Rice' });
    await set(ref(db, 'orders/o1'), { userId: 'cust1', hotelId: 'hotelA', orderStatus: 'pending_pickup', totalAmount: 500, createdAt: 1 });
    await set(ref(db, 'orders/o2'), { userId: 'cust2', hotelId: 'hotelB', orderStatus: 'pending_pickup', totalAmount: 300, createdAt: 2 });
    await set(ref(db, 'orders/oDone'), { userId: 'cust1', hotelId: 'hotelA', orderStatus: 'completed', totalAmount: 100, createdAt: 3 });
    await set(ref(db, 'orders/o3'), { userId: 'cust1', hotelId: 'hotelA', orderStatus: 'pending_pickup', totalAmount: 400, createdAt: 4 });
    await set(ref(db, 'orders/oProcessed'), { userId: 'cust1', hotelId: 'hotelA', orderStatus: 'pending_pickup', totalAmount: 400, createdAt: 5, stockAdjusted: true });
    await set(ref(db, 'orders/oCancelled'), { userId: 'cust1', hotelId: 'hotelA', orderStatus: 'cancelled', totalAmount: 90, createdAt: 6 });
    await set(ref(db, 'orders/oLegacy'), { userId: 'cust1', hotelId: 'hotelA', orderStatus: 'pending_pickup', totalAmount: 90, createdAt: 8, paymentStatus: 'paid', paymentMethod: 'PayHere Online' });
    await set(ref(db, 'orders/oQ'), { userId: 'cust2', hotelId: 'hotelQ', orderStatus: 'pending_pickup', totalAmount: 90, createdAt: 7 });
    await set(ref(db, 'banners/b1'), { title: 'T', imageUrl: 'https://x/y.jpg', order: 1, active: true });
    await set(ref(db, 'chats/hotelA_cust1/messages/m1'), { senderId: 'cust1', message: 'hi', timestamp: 1 });
    await set(ref(db, 'notifications/cust1/n1'), { title: 't', body: 'b', timestamp: 1, read: false });
  });

  const anon = env.unauthenticatedContext().database();
  const cust1 = env.authenticatedContext('cust1').database();
  const cust2 = env.authenticatedContext('cust2').database();
  const owner1 = env.authenticatedContext('hotelOwner1').database();
  const owner2 = env.authenticatedContext('hotelOwner2').database();
  const pending = env.authenticatedContext('pendingOwner').database();
  const admin = env.authenticatedContext('admin1', { email: 'admin@replate.com' }).database();
  const adminWrongEmail = env.authenticatedContext('admin1', { email: 'someone@else.com' }).database();
  const newUser = env.authenticatedContext('newbie').database();

  console.log('\nUnauthenticated');
  await check('cannot read hotels', assertFails(get(ref(anon, 'hotels'))));
  await check('cannot read foodItems', assertFails(get(ref(anon, 'foodItems'))));
  await check('cannot read banners', assertFails(get(ref(anon, 'banners'))));

  console.log('\nUsers');
  await check('user reads own profile', assertSucceeds(get(ref(cust1, 'users/cust1'))));
  await check('user cannot read another profile', assertFails(get(ref(cust1, 'users/cust2'))));
  await check('user cannot list all users', assertFails(get(ref(cust1, 'users'))));
  await check('admin can list all users', assertSucceeds(get(ref(admin, 'users'))));
  await check('admin role with a non-admin email gets no admin access', assertFails(get(ref(adminWrongEmail, 'users'))));
  await check('new customer registers with role customer', assertSucceeds(set(ref(newUser, 'users/newbie'), { role: 'customer', userType: 'customer', name: 'N' })));
  await check('new hotel registers with role hotel', assertSucceeds(set(ref(env.authenticatedContext('newhotel').database(), 'users/newhotel'), { role: 'hotel', userType: 'hotel', hotelId: 'hX' })));
  await check('cannot register as admin', assertFails(set(ref(env.authenticatedContext('evil').database(), 'users/evil'), { role: 'admin' })));
  await check('customer cannot promote themselves to admin', assertFails(update(ref(cust1, 'users/cust1'), { role: 'admin' })));
  await check('customer cannot switch to hotel later', assertFails(update(ref(cust1, 'users/cust1'), { role: 'hotel' })));
  await check('customer updates own name', assertSucceeds(update(ref(cust1, 'users/cust1'), { name: 'New name' })));
  await check('customer rewrites profile keeping the same role', assertSucceeds(set(ref(cust1, 'users/cust1'), { role: 'customer', name: 'X' })));
  await check('cannot write someone else profile', assertFails(update(ref(cust1, 'users/cust2'), { name: 'hax' })));
  await check('customer can delete own profile (account deletion)', assertSucceeds(remove(ref(env.authenticatedContext('newbie').database(), 'users/newbie'))));

  await check('customer cannot give themselves a status', assertFails(set(ref(cust1, 'users/cust1/status'), 'active')));
  await check('admin suspends a user', assertSucceeds(update(ref(admin, 'users/cust2'), { status: 'suspended', suspensionReason: 'spam' })));
  await check('suspended user cannot clear the suspension by rewriting the profile', assertFails(set(ref(env.authenticatedContext('suspendedUser').database(), 'users/suspendedUser'), { role: 'customer', name: 'Sus' })));
  await check('suspended user cannot set status to active', assertFails(set(ref(env.authenticatedContext('suspendedUser').database(), 'users/suspendedUser/status'), 'active')));
  await check('suspended user may still update other profile fields', assertSucceeds(update(ref(env.authenticatedContext('suspendedUser').database(), 'users/suspendedUser'), { name: 'Still Sus' })));
  await check('admin enables a user again', assertSucceeds(update(ref(admin, 'users/suspendedUser'), { status: 'active' })));

  console.log('\nHotel licences');
  await check('owner reads own licence', assertSucceeds(get(ref(owner1, 'hotelLicenses/hotelA'))));
  await check('other signed-in user cannot read a licence', assertFails(get(ref(cust1, 'hotelLicenses/hotelA'))));
  await check('other hotel cannot read a licence', assertFails(get(ref(owner2, 'hotelLicenses/hotelA'))));
  await check('cannot list all licences', assertFails(get(ref(cust1, 'hotelLicenses'))));
  await check('a hotel owner cannot list all licences', assertFails(get(ref(owner1, 'hotelLicenses'))));
  await check('admin lists all licences (approval page)', assertSucceeds(get(ref(admin, 'hotelLicenses'))));
  await check('admin reads licences', assertSucceeds(get(ref(admin, 'hotelLicenses/hotelA'))));
  await check('new hotel owner stores their licence', assertSucceeds(set(ref(newUser, 'hotelLicenses/newHotelKey'), { userId: 'newbie', licenseUrl: 'https://x/l.pdf' })));
  await check('cannot store a licence under another user', assertFails(set(ref(newUser, 'hotelLicenses/other2'), { userId: 'cust1', licenseUrl: 'https://x/l.pdf' })));
  await check('owner cannot overwrite an existing licence', assertFails(set(ref(owner1, 'hotelLicenses/hotelA'), { userId: 'hotelOwner1', licenseUrl: 'https://x/new.pdf' })));
  await check('licence link must be https', assertFails(set(ref(env.authenticatedContext('another').database(), 'hotelLicenses/k3'), { userId: 'another', licenseUrl: 'http://x/l.pdf' })));

  console.log('\nHotels');
  await check('signed-in user reads all hotels (map)', assertSucceeds(get(ref(cust1, 'hotels'))));
  await check('hotel registration as pending for self', assertSucceeds(set(ref(newUser, 'hotels/newHotelKey'), { userId: 'newbie', status: 'pending', hotelName: 'New' })));
  await check('cannot self-approve at registration', assertFails(set(ref(env.authenticatedContext('sneaky').database(), 'hotels/sneakyKey'), { userId: 'sneaky', status: 'approved' })));
  await check('cannot register hotel for another user', assertFails(set(ref(newUser, 'hotels/other'), { userId: 'cust1', status: 'pending' })));
  await check('owner edits own hotel name/phone', assertSucceeds(update(ref(owner1, 'hotels/hotelA'), { hotelName: 'A2', phone: '0771234567' })));
  await check('owner cannot change own status', assertFails(update(ref(owner1, 'hotels/hotelA'), { status: 'approved' })));
  await check('pending owner cannot self-approve', assertFails(update(ref(pending, 'hotels/hotelP'), { status: 'approved' })));
  await check('owner cannot edit another hotel', assertFails(update(ref(owner1, 'hotels/hotelB'), { hotelName: 'hax' })));
  await check('owner stores fcm token on own hotel', assertSucceeds(update(ref(owner1, 'hotels/hotelA'), { fcmToken: 'tok' })));
  await check('admin approves a hotel', assertSucceeds(update(ref(admin, 'hotels/hotelP'), { status: 'approved' })));

  console.log('\nFood items');
  await check('customer reads food', assertSucceeds(get(ref(cust1, 'foodItems'))));
  await check('customer cannot change quantity', assertFails(set(ref(cust1, 'foodItems/f1/quantity'), 99)));
  await check('customer cannot change status', assertFails(set(ref(cust1, 'foodItems/f1/status'), 'sold_out')));
  await check('customer updates avgRating within 0-5', assertSucceeds(update(ref(cust1, 'foodItems/f1'), { avgRating: 4.5, reviewCount: 3 })));
  await check('customer cannot set avgRating to 50', assertFails(set(ref(cust1, 'foodItems/f1/avgRating'), 50)));
  await check('owner hotel edits own food', assertSucceeds(update(ref(owner1, 'foodItems/f1'), { isAvailable: false, status: 'paused' })));
  await check('other hotel cannot edit it', assertFails(update(ref(owner2, 'foodItems/f1'), { status: 'x' })));
  await check('hotel creates new listing for itself', assertSucceeds(set(ref(owner1, 'foodItems/f2'), { hotelId: 'hotelA', hotelUserId: 'hotelOwner1', foodName: 'Soup', quantity: 3 })));
  await check('hotel cannot create listing for another owner', assertFails(set(ref(owner1, 'foodItems/f3'), { hotelId: 'hotelB', hotelUserId: 'hotelOwner2', foodName: 'Fake' })));
  await check('listing price must be a number', assertFails(set(ref(owner1, 'foodItems/f1/discountedPrice'), '100')));
  await check('listing price cannot be negative', assertFails(set(ref(owner1, 'foodItems/f1/discountedPrice'), -1)));
  await check('listing quantity cannot exceed 1000', assertFails(set(ref(owner1, 'foodItems/f1/quantity'), 5000)));
  await check('owner sets a valid price and quantity', assertSucceeds(update(ref(owner1, 'foodItems/f1'), { discountedPrice: 180, originalPrice: 300, quantity: 12 })));
  await check('owner deletes own listing', assertSucceeds(remove(ref(owner1, 'foodItems/f2'))));
  await check('pending hotel cannot publish a listing', assertFails(set(ref(env.authenticatedContext('stillPending').database(), 'foodItems/fp'), { hotelId: 'hotelQ', hotelUserId: 'stillPending', foodName: 'Early' })));
  await check('suspended hotel cannot publish a listing', assertFails(set(ref(env.authenticatedContext('suspendedOwner').database(), 'foodItems/fs'), { hotelId: 'hotelS', hotelUserId: 'suspendedOwner', foodName: 'Nope' })));
  await check('hotel cannot point its listing at another hotel key', assertFails(set(ref(owner1, 'foodItems/f4'), { hotelId: 'hotelB', hotelUserId: 'hotelOwner1', foodName: 'Wrong hotel' })));

  console.log('\nOrders');
  const order = { userId: 'cust1', hotelId: 'hotelA', orderStatus: 'pending_pickup', totalAmount: 250, createdAt: Date.now(), items: [{ foodId: 'f1' }] };
  await check('customer creates own order', assertSucceeds(set(ref(cust1, 'orders/new1'), order)));
  await check('customer retries the same order id while pending (idempotent)', assertSucceeds(set(ref(cust1, 'orders/new1'), order)));
  await check('customer cannot create order for someone else', assertFails(set(ref(cust1, 'orders/new2'), { ...order, userId: 'cust2' })));
  await check('order without required fields rejected', assertFails(set(ref(cust1, 'orders/new3'), { userId: 'cust1' })));
  await check('negative total rejected', assertFails(set(ref(cust1, 'orders/new4'), { ...order, totalAmount: -5 })));
  await check('customer reads own orders by query', assertSucceeds(get(query(ref(cust1, 'orders'), orderByChild('userId'), equalTo('cust1')))));
  await check('customer cannot list all orders', assertFails(get(ref(cust1, 'orders'))));
  await check('customer cannot query someone else orders', assertFails(get(query(ref(cust1, 'orders'), orderByChild('userId'), equalTo('cust2')))));
  await check('customer reads own order by id', assertSucceeds(get(ref(cust1, 'orders/o1'))));
  await check('customer cannot read another order', assertFails(get(ref(cust1, 'orders/o2'))));
  await check('customer cancels own pending order', assertSucceeds(update(ref(cust1, 'orders/o1'), { orderStatus: 'cancelled', updatedAt: 5, cancelledAt: 5 })));
  await check('hotel cancels its order and records that it did', assertSucceeds(update(ref(owner2, 'orders/o2'), { orderStatus: 'cancelled', cancelledBy: 'hotel', updatedAt: 8 })));
  await check('cancelledBy only accepts customer or hotel', assertFails(update(ref(owner1, 'orders/oDone'), { cancelledBy: 'admin' })));
  await check('another hotel cannot set cancelledBy', assertFails(update(ref(owner2, 'orders/oDone'), { cancelledBy: 'hotel' })));
  await check('customer cannot mark order completed', assertFails(set(ref(cust1, 'orders/o2/orderStatus'), 'completed')));
  await check('customer cannot rewrite a completed order', assertFails(set(ref(cust1, 'orders/oDone'), { ...order, orderStatus: 'pending_pickup' })));
  await check('customer reviews a completed order', assertSucceeds(update(ref(cust1, 'orders/oDone'), { reviewRating: 5, reviewComment: 'great', reviewAt: 9, hasReview: true })));
  await check('customer anonymizes own order on account deletion', assertSucceeds(update(ref(cust1, 'orders/oDone'), { userId: '[deleted]', customerName: '[deleted]', accountDeleted: true })));
  await check('hotel cannot list all orders', assertFails(get(ref(owner1, 'orders'))));
  await check('hotel queries orders by its hotel key', assertSucceeds(get(query(ref(owner1, 'orders'), orderByChild('hotelId'), equalTo('hotelA')))));
  await check('hotel queries orders by its owner uid (legacy)', assertSucceeds(get(query(ref(owner1, 'orders'), orderByChild('hotelId'), equalTo('hotelOwner1')))));
  await check('hotel cannot query another hotel orders', assertFails(get(query(ref(owner1, 'orders'), orderByChild('hotelId'), equalTo('hotelB')))));
  await check('hotel cannot query orders by customer', assertFails(get(query(ref(owner1, 'orders'), orderByChild('userId'), equalTo('cust1')))));
  await check('pending hotel cannot query orders', assertFails(get(query(ref(env.authenticatedContext('stillPending').database(), 'orders'), orderByChild('hotelId'), equalTo('hotelQ')))));
  await check('hotel reads one of its orders', assertSucceeds(get(ref(owner1, 'orders/o1'))));
  await check('hotel A updates status of its own order', assertSucceeds(update(ref(owner1, 'orders/o3'), { orderStatus: 'completed', completedAt: 7, updatedAt: 7 })));
  await check('hotel A cannot update hotel B order', assertFails(update(ref(owner1, 'orders/o2'), { orderStatus: 'completed' })));
  await check('admin reads all orders', assertSucceeds(get(ref(admin, 'orders'))));
  await check('customer cannot write stockAdjusted', assertFails(set(ref(cust1, 'orders/o1/stockAdjusted'), true)));

  console.log('\nOrders: tampering and payment');
  const fresh = { ...order, createdAt: 1 };
  await check('customer cannot rewrite their own pending order as completed', assertFails(set(ref(cust1, 'orders/oSelf1'), { ...fresh, orderStatus: 'completed' })));
  await check('customer cannot create an order that already has stockAdjusted', assertFails(set(ref(cust1, 'orders/oSelf2'), { ...fresh, stockAdjusted: true })));
  await check('customer cannot create an order with a completion time', assertFails(set(ref(cust1, 'orders/oSelf3'), { ...fresh, completedAt: 5 })));
  await check('customer cannot re-send an order the server already processed', assertFails(set(ref(cust1, 'orders/oProcessed'), fresh)));
  await check('customer cannot re-send a pending order as completed', assertFails(set(ref(cust1, 'orders/oSelf1'), { ...fresh, orderStatus: 'pending_pickup' }).then(() => set(ref(cust1, 'orders/oSelf1'), { ...fresh, orderStatus: 'completed' }))));
  await check('cash order is created as pending payment', assertSucceeds(set(ref(cust1, 'orders/oPay1'), { ...fresh, paymentStatus: 'pending', paymentMethod: 'Cash' })));
  await check('card order with a PayHere payment number is accepted', assertSucceeds(set(ref(cust1, 'orders/oPay2'), { ...fresh, paymentStatus: 'paid', paymentMethod: 'PayHere Online', paymentRef: 320005678 })));
  await check('paid without a payment number is rejected', assertFails(set(ref(cust1, 'orders/oPay3'), { ...fresh, paymentStatus: 'paid', paymentMethod: 'PayHere Online' })));
  await check('paid by cash is rejected', assertFails(set(ref(cust1, 'orders/oPay4'), { ...fresh, paymentStatus: 'paid', paymentMethod: 'Cash', paymentRef: 5 })));
  await check('unknown payment status is rejected', assertFails(set(ref(cust1, 'orders/oPay5'), { ...fresh, paymentStatus: 'free' })));
  await check('hotel cannot complete a cancelled order', assertFails(update(ref(owner1, 'orders/oCancelled'), { orderStatus: 'completed', completedAt: 9 })));
  await check('hotel cannot move a completed order back', assertFails(update(ref(owner1, 'orders/oDone'), { orderStatus: 'preparing' })));
  await check('customer cannot cancel a completed order', assertFails(update(ref(cust1, 'orders/oDone'), { orderStatus: 'cancelled' })));
  await check('hotel can still progress an older paid order that has no payment number', assertSucceeds(update(ref(owner1, 'orders/oLegacy'), { orderStatus: 'confirmed', updatedAt: 12 })));
  await check('an unknown order status is rejected', assertFails(update(ref(owner1, 'orders/oProcessed'), { orderStatus: 'banana' })));
  await check('hotel moves an order through preparing', assertSucceeds(update(ref(owner1, 'orders/oProcessed'), { orderStatus: 'preparing', updatedAt: 11 })));
  await check('a hotel still waiting for approval cannot change order status', assertFails(update(ref(env.authenticatedContext('stillPending').database(), 'orders/oQ'), { orderStatus: 'confirmed' })));

  console.log('\nReviews');
  await check('customer creates own review', assertSucceeds(set(ref(cust1, 'reviews/r1'), { userId: 'cust1', foodId: 'f1', rating: 4, comment: 'ok' })));
  await check('customer cannot create review as someone else', assertFails(set(ref(cust1, 'reviews/r2'), { userId: 'cust2', foodId: 'f1', rating: 4 })));
  await check('customer edits own review', assertSucceeds(update(ref(cust1, 'reviews/r1'), { rating: 5 })));
  await check('other customer cannot edit it', assertFails(update(ref(cust2, 'reviews/r1'), { rating: 1 })));
  await check('rating above 5 rejected', assertFails(set(ref(cust1, 'reviews/r3'), { userId: 'cust1', foodId: 'f1', rating: 9 })));
  await check('customer deletes own review', assertSucceeds(remove(ref(cust1, 'reviews/r1'))));
  await check('customer queries reviews by food', assertSucceeds(get(query(ref(cust1, 'reviews'), orderByChild('foodId'), equalTo('f1')))));

  console.log('\nNotifications');
  await check('user reads own inbox', assertSucceeds(get(ref(cust1, 'notifications/cust1'))));
  await check('user cannot read another inbox', assertFails(get(ref(cust2, 'notifications/cust1'))));
  await check('user marks own notification read', assertSucceeds(update(ref(cust1, 'notifications/cust1/n1'), { read: true })));
  await check('user deletes own notification', assertSucceeds(remove(ref(cust1, 'notifications/cust1/n1'))));
  await check('customer notifies the hotel owner about their own order', assertSucceeds(set(push(ref(cust1, 'notifications/hotelOwner1')), { title: 'New order', body: 'x', timestamp: 1, read: false, orderId: 'oProcessed' })));
  await check('a notification into another inbox needs an order of the sender', assertFails(set(push(ref(cust1, 'notifications/hotelOwner1')), { title: 'Admin notice', body: 'x', timestamp: 1, read: false })));
  await check('a notification cannot point at someone else\'s order', assertFails(set(push(ref(cust1, 'notifications/hotelOwner1')), { title: 'x', body: 'x', timestamp: 1, read: false, orderId: 'o2' })));
  await check('a notification into another inbox must start unread', assertFails(set(push(ref(cust1, 'notifications/hotelOwner1')), { title: 'x', body: 'x', timestamp: 1, read: true, orderId: 'oProcessed' })));
  await check('customer cannot edit an existing notification in another inbox', assertFails(set(ref(cust1, 'notifications/cust2/n9/read'), true)));
  await check('oversized notification body rejected', assertFails(set(push(ref(cust1, 'notifications/hotelOwner1')), { title: 't', body: 'x'.repeat(2000), timestamp: 1 })));

  console.log('\nChats');
  await check('customer reads own conversation', assertSucceeds(get(ref(cust1, 'chats/hotelA_cust1/messages'))));
  await check('customer sends a message', assertSucceeds(set(ref(cust1, 'chats/hotelA_cust1/messages/m2'), { senderId: 'cust1', message: 'hello', timestamp: 2 })));
  await check('hotel owner reads its conversation', assertSucceeds(get(ref(owner1, 'chats/hotelA_cust1/messages'))));
  await check('hotel owner replies', assertSucceeds(set(ref(owner1, 'chats/hotelA_cust1/messages/m3'), { senderId: 'hotelOwner1', message: 'welcome', timestamp: 3 })));
  await check('other customer cannot read it', assertFails(get(ref(cust2, 'chats/hotelA_cust1/messages'))));
  await check('other hotel cannot read it', assertFails(get(ref(owner2, 'chats/hotelA_cust1/messages'))));
  await check('hotel cannot scan all chats', assertFails(get(ref(owner1, 'chats'))));
  await check('admin can read all chats', assertSucceeds(get(ref(admin, 'chats'))));
  await check('customer cannot scan all chats', assertFails(get(ref(cust1, 'chats'))));
  await check('message over 2000 chars rejected', assertFails(set(ref(cust1, 'chats/hotelA_cust1/messages/m4'), { senderId: 'cust1', message: 'x'.repeat(2500), timestamp: 4 })));
  await check('customer writes conversation summary into hotel inbox', assertSucceeds(set(ref(cust1, 'conversations/hotelA/hotelA_cust1'), { lastMessage: 'hi', lastTimestamp: 2 })));
  await check('hotel writes summary into customer inbox', assertSucceeds(set(ref(owner1, 'conversations/cust1/hotelA_cust1'), { lastMessage: 'yo', lastTimestamp: 3 })));
  await check('hotel reads its conversations list', assertSucceeds(get(ref(owner1, 'conversations/hotelA'))));
  await check('stranger cannot write a conversation they are not in', assertFails(set(ref(cust2, 'conversations/hotelA/hotelA_cust1'), { lastMessage: 'spam' })));

  console.log('\nAdmin-managed data');
  await check('signed-in user reads banners', assertSucceeds(get(ref(cust1, 'banners'))));
  await check('customer cannot write banners', assertFails(set(push(ref(cust1, 'banners')), { title: 'x', imageUrl: 'https://a/b.png' })));
  await check('admin creates banner', assertSucceeds(set(push(ref(admin, 'banners')), { title: 'x', imageUrl: 'https://a/b.png', order: 1, active: true })));
  await check('banner with http image rejected', assertFails(set(push(ref(admin, 'banners')), { title: 'x', imageUrl: 'http://a/b.png' })));
  await check('admin writes categories', assertSucceeds(set(push(ref(admin, 'categories')), { name: 'Meals', order: 1 })));
  await check('customer cannot write categories', assertFails(set(push(ref(cust1, 'categories')), { name: 'x', order: 1 })));
  await check('hotel reads categories', assertSucceeds(get(ref(owner1, 'categories'))));
  await check('only admin reads notificationLogs', assertFails(get(ref(cust1, 'notificationLogs'))));
  await check('admin reads notificationLogs', assertSucceeds(get(ref(admin, 'notificationLogs'))));
  await check('otpVerifications fully locked', assertFails(get(ref(admin, 'otpVerifications'))));

  console.log('\nPer-user data');
  await check('user writes own favorites', assertSucceeds(set(ref(cust1, 'favorites/cust1/f1'), true)));
  await check('user cannot read others favorites', assertFails(get(ref(cust2, 'favorites/cust1'))));
  await check('hotel writes its food index', assertSucceeds(set(ref(owner1, 'indexes/foodByHotel/hotelA/f1'), true)));
  await check('other hotel cannot write that index', assertFails(set(ref(owner2, 'indexes/foodByHotel/hotelA/f1'), true)));
  await check('user removes own ordersByUser index', assertSucceeds(remove(ref(cust1, 'indexes/ordersByUser/cust1'))));

  console.log(`\n${passed} passed, ${failed} failed`);
  await env.cleanup();
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error('Harness error:', e); process.exit(2); });
