package com.replate.app.utils;

import java.util.Locale;

/**
 * Decides what a PayHere result means. Only a result the SDK reports as a success AND whose payment
 * status says "success" counts as paid; everything else is a cancel, a refusal or a failure.
 * (Pure Java so it can be unit tested; CheckoutActivity feeds it the SDK's values.)
 */
public final class PaymentOutcome {

    public enum Kind { SUCCESS, CANCELLED, UNAUTHORIZED, FAILED }

    /** PHResponse.STATUS_SUCCESS / STATUS_ERROR_CANCELED in the PayHere SDK. */
    public static final int RESPONSE_SUCCESS = 1;
    public static final int RESPONSE_CANCELED = -6;
    /** StatusResponse.Status.SUCCESS in the PayHere SDK. */
    public static final int PAYMENT_SUCCESS = 2;
    /** Used when the SDK did not send a value. */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    private PaymentOutcome() {}

    public static Kind classify(boolean resultOk, int responseStatus, int paymentStatus, String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        if (lower.contains("unauthorized")) return Kind.UNAUTHORIZED;
        if (resultOk && responseStatus == RESPONSE_SUCCESS && paymentStatus == PAYMENT_SUCCESS) return Kind.SUCCESS;
        if (responseStatus == RESPONSE_CANCELED || lower.contains("cancel")) return Kind.CANCELLED;
        return Kind.FAILED;
    }
}
