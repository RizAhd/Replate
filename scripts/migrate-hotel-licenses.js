/**
 * One-off migration. Moves each hotel's licence link from hotels/{key} (which every signed-in user
 * can read) to hotelLicenses/{key} (readable only by that hotel's owner and admins).
 * New registrations already use hotelLicenses; this only fixes hotels registered before.
 *
 * Run it once, after deploying the new database rules:
 *   set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\service-account.json
 *   set FIREBASE_DATABASE_URL=https://<project>-default-rtdb.<region>.firebasedatabase.app
 *   node scripts/migrate-hotel-licenses.js            (shows what it would do)
 *   node scripts/migrate-hotel-licenses.js --apply    (does it)
 */
const path = require('path');
const admin = require(path.join(__dirname, '..', 'firebase-functions', 'node_modules', 'firebase-admin'));

const apply = process.argv.includes('--apply');
const databaseURL = process.env.FIREBASE_DATABASE_URL;
if (!databaseURL) {
    console.error('Set FIREBASE_DATABASE_URL first (see the comment at the top of this file).');
    process.exit(1);
}

admin.initializeApp({ databaseURL });
const db = admin.database();

(async () => {
    const hotels = (await db.ref('hotels').get()).val() || {};
    const updates = {};
    let moved = 0;

    for (const [key, hotel] of Object.entries(hotels)) {
        const url = hotel.licenseUrl || hotel.license;
        if (!url) continue;
        const existing = (await db.ref(`hotelLicenses/${key}`).get()).val();
        if (!existing) {
            updates[`hotelLicenses/${key}`] = {
                userId: hotel.userId || key,
                licenseUrl: url,
                uploadedAt: hotel.createdAt || Date.now(),
            };
        }
        updates[`hotels/${key}/licenseUrl`] = null;
        updates[`hotels/${key}/license`] = null;
        moved++;
    }

    console.log(`${moved} hotel(s) have a licence link on the hotel record.`);
    if (!apply) {
        console.log('Dry run: nothing was changed. Run again with --apply to move them.');
    } else if (moved > 0) {
        await db.ref().update(updates);
        console.log('Done. The links now live in hotelLicenses/.');
    }
    process.exit(0);
})().catch((err) => { console.error(err); process.exit(1); });
