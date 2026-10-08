package com.replate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps the one unsaved {@link PendingPayment} (if any) in private app storage. */
public final class PendingPaymentStore {

    private static final String PREFS = "REPLATE_PENDING_PAYMENT";
    private static final String KEY = "record";

    private PendingPaymentStore() {}

    // Opens the private storage file.
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // Writes the unsaved payment to the phone.
    public static void save(Context context, PendingPayment payment) {
        // commit(), not apply(): the record must be on disk before the app can be closed
        prefs(context).edit().putString(KEY, payment.encode()).commit();
    }

    /** The saved payment, but only for the signed-in user (a record from another account is ignored). */
    public static PendingPayment load(Context context, String userId) {
        PendingPayment payment = PendingPayment.decode(prefs(context).getString(KEY, null));
        return payment != null && payment.userId.equals(userId) ? payment : null;
    }

    // Deletes the saved payment.
    public static void clear(Context context) {
        prefs(context).edit().remove(KEY).commit();
    }
}
