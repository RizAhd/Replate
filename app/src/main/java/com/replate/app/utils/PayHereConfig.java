package com.replate.app.utils;

/**
 * PayHere chooses its server from the first digit of the merchant id (this is done inside the SDK,
 * not by the app): 1xxxxxx is the sandbox, 2xxxxxx is the live service where real cards are charged.
 */
public final class PayHereConfig {

    private PayHereConfig() {}

    public static boolean isConfigured(String merchantId) {
        return merchantId != null && !merchantId.trim().isEmpty();
    }

    public static boolean isSandbox(String merchantId) {
        return isConfigured(merchantId) && merchantId.trim().charAt(0) == '1';
    }

    public static boolean isLive(String merchantId) {
        return isConfigured(merchantId) && merchantId.trim().charAt(0) == '2';
    }

    /** A debug build must never be able to charge a real card, so it only runs against the sandbox. */
    public static boolean mayStartPayment(String merchantId, boolean debugBuild) {
        if (!isConfigured(merchantId)) return false;
        return !debugBuild || isSandbox(merchantId);
    }
}
