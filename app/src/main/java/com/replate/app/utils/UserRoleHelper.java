package com.replate.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;

import java.util.Locale;

/**
 * Central place for role normalization and legacy role/userType fallback handling.
 */
public final class UserRoleHelper {

    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_HOTEL = "hotel";
    public static final String ROLE_ADMIN = "admin";

    private static final String PREFS = "REPLATE_PREFS";

    private UserRoleHelper() {
    }

    // Turns any role text into a known role.
    @NonNull
    public static String normalize(@Nullable String rawRole) {
        if (rawRole == null) return ROLE_CUSTOMER;
        String normalized = rawRole.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) return ROLE_CUSTOMER;
        if (ROLE_HOTEL.equals(normalized) || ROLE_ADMIN.equals(normalized) || ROLE_CUSTOMER.equals(normalized)) {
            return normalized;
        }
        if (normalized.contains("hotel")) return ROLE_HOTEL;
        if (normalized.contains("admin")) return ROLE_ADMIN;
        return ROLE_CUSTOMER;
    }

    // Reads the saved role from the phone's storage.
    @NonNull
    public static String fromPrefs(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return fromPrefs(prefs);
    }

    // Reads the saved role from the given preferences.
    @NonNull
    public static String fromPrefs(@Nullable SharedPreferences prefs) {
        if (prefs == null) return ROLE_CUSTOMER;
        String role = prefs.getString("role", null);
        String userType = prefs.getString("userType", null);
        return !isBlank(role) ? normalize(role) : normalize(userType);
    }

    // Reads the role from a Firebase user record.
    @NonNull
    public static String fromSnapshot(@Nullable DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return ROLE_CUSTOMER;
        String role = snapshot.child("role").getValue(String.class);
        String userType = snapshot.child("userType").getValue(String.class);
        return !isBlank(role) ? normalize(role) : normalize(userType);
    }

    // Picks the role, using the older userType if needed.
    @NonNull
    public static String choose(@Nullable String role, @Nullable String userType) {
        return !isBlank(role) ? normalize(role) : normalize(userType);
    }

    // True for a hotel account.
    public static boolean isHotel(@Nullable String role) {
        return ROLE_HOTEL.equals(normalize(role));
    }

    // True for an admin account.
    public static boolean isAdmin(@Nullable String role) {
        return ROLE_ADMIN.equals(normalize(role));
    }

    // True when the text is null or empty.
    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}

