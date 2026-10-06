package com.replate.app.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * A card payment that went through but whose order has not been saved yet. It is written to the
 * phone the moment PayHere says "paid", so closing the app (or a crash) cannot lose the order;
 * the next checkout offers to save it. Text form: v1|field|field|..., every field URL-encoded.
 */
public final class PendingPayment {

    public final String userId;
    public final String orderId;
    public final String otp;
    public final String method;
    public final long paymentRef;
    public final boolean delivery;
    public final String customerName;
    public final String customerPhone;
    public final String pickupTime;
    public final String address;
    public final double lat;
    public final double lng;

    public PendingPayment(String userId, String orderId, String otp, String method, long paymentRef,
                          boolean delivery, String customerName, String customerPhone,
                          String pickupTime, String address, double lat, double lng) {
        this.userId = nz(userId);
        this.orderId = nz(orderId);
        this.otp = nz(otp);
        this.method = nz(method);
        this.paymentRef = paymentRef;
        this.delivery = delivery;
        this.customerName = nz(customerName);
        this.customerPhone = nz(customerPhone);
        this.pickupTime = nz(pickupTime);
        this.address = nz(address);
        this.lat = lat;
        this.lng = lng;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return "";
        }
    }

    public String encode() {
        return "v1|" + enc(userId) + "|" + enc(orderId) + "|" + enc(otp) + "|" + enc(method) + "|" + paymentRef
                + "|" + (delivery ? "1" : "0") + "|" + enc(customerName) + "|" + enc(customerPhone)
                + "|" + enc(pickupTime) + "|" + enc(address) + "|" + lat + "|" + lng;
    }

    /** Returns null for anything that is not a complete, well-formed record. */
    public static PendingPayment decode(String text) {
        if (text == null) return null;
        String[] p = text.split("[|]", -1);
        if (p.length != 13 || !"v1".equals(p[0])) return null;
        try {
            PendingPayment out = new PendingPayment(dec(p[1]), dec(p[2]), dec(p[3]), dec(p[4]),
                    Long.parseLong(p[5]), "1".equals(p[6]), dec(p[7]), dec(p[8]), dec(p[9]), dec(p[10]),
                    Double.parseDouble(p[11]), Double.parseDouble(p[12]));
            return out.userId.isEmpty() || out.orderId.isEmpty() ? null : out;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
