package com.replate.app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.replate.app.LoginActivity;
import com.replate.app.MainActivity;
import com.replate.app.R;
import com.replate.app.SplashActivity;

/**
 * Logout and account deletion flows shared by the Profile tab and the Settings screen,
 * so both behave the same way.
 */
public final class AccountActions {

    private static final String TAG = "AccountActions";

    private AccountActions() {}

    // Asks the user to confirm before signing out.
    public static void confirmLogout(@NonNull Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.profile_logout_2)
                .setMessage(R.string.profile_are_you_sure_you_want)
                .setPositiveButton(R.string.profile_logout_2, (dialog, which) -> logout(activity))
                .setNegativeButton(R.string.profile_cancel, null)
                .show();
    }

    // Signs out and clears local data, then opens Login.
    private static void logout(@NonNull Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).logoutToLogin();
            return;
        }

        try {
            FcmTokenManager.clearTokenForCurrentUser();
        } catch (Exception ignored) {
            // Best-effort token cleanup; the user still logs out
        }
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception e) {
            Log.e(TAG, "Sign out failed", e);
            ToastUtils.showCustomToast(activity, R.string.profile_logout_failed);
            return;
        }

        LocalDataCleaner.clearAll(activity);

        // Keep the device-level onboarding flag across logouts, like MainActivity.logoutToLogin does
        SharedPreferences prefs = activity.getSharedPreferences("REPLATE_PREFS", Context.MODE_PRIVATE);
        boolean onboardingSeen = prefs.getBoolean("onboarding_seen", false);
        prefs.edit().clear().apply();
        if (onboardingSeen) {
            prefs.edit().putBoolean("onboarding_seen", true).apply();
        }

        Intent intent = new Intent(activity, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    // Asks the user to confirm before deleting the account.
    public static void confirmDelete(@NonNull Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.delete_account_confirm_title)
                .setMessage(R.string.delete_account_confirm_message)
                .setPositiveButton(R.string.delete_account_confirm_btn, (d, w) -> {
                    // Google, Facebook and phone accounts have no password to confirm
                    if (AccountDeletionManager.requiresPassword(FirebaseAuth.getInstance().getCurrentUser())) {
                        askPassword(activity);
                    } else {
                        performDeletion(activity, null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Asks for the password before deleting the account.
    private static void askPassword(@NonNull Activity activity) {
        EditText etPassword = new EditText(activity);
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setHint(R.string.delete_account_password_hint);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        etPassword.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.delete_account_password_title)
                .setView(etPassword)
                .setPositiveButton(R.string.delete_account_confirm_btn, (d, w) -> {
                    String password = etPassword.getText() == null ? "" : etPassword.getText().toString();
                    if (password.isEmpty()) {
                        ToastUtils.showCustomToast(activity, R.string.delete_account_password_hint);
                        return;
                    }
                    performDeletion(activity, password);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Deletes the account while a progress dialog is shown.
    private static void performDeletion(@NonNull Activity activity, @Nullable String password) {
        final Context app = activity.getApplicationContext();
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setMessage(R.string.delete_account_deleting)
                .setCancelable(false)
                .create();
        progress.show();

        AccountDeletionManager.deleteAccount(app, password, new AccountDeletionManager.DeletionCallback() {
            // Signs out and opens the Login screen.
            @Override
            public void onSuccess() {
                dismiss(progress);
                try {
                    FirebaseAuth.getInstance().signOut();
                } catch (Exception ignored) {
                    // The account is already gone; signing out is just tidying up
                }
                ToastUtils.showCustomToast(app, R.string.delete_account_success);
                try {
                    Intent intent = new Intent(app, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    app.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Navigation error after deletion", e);
                }
            }

            // Tells the user and asks for the password again.
            @Override
            public void onWrongPassword() {
                dismiss(progress);
                ToastUtils.showCustomToast(app, R.string.delete_account_wrong_password);
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    askPassword(activity);
                }
            }

            // Tells the user to sign in again first.
            @Override
            public void onRecentLoginRequired() {
                dismiss(progress);
                ToastUtils.showCustomToast(app, R.string.delete_account_recent_login);
            }

            // Tells the user the deletion failed.
            @Override
            public void onFailure(@NonNull String reason) {
                dismiss(progress);
                ToastUtils.showCustomToast(app, R.string.delete_account_failed);
                Log.e(TAG, "Deletion failed: " + reason);
            }
        });
    }

    // Closes the dialog without crashing.
    private static void dismiss(@NonNull AlertDialog dialog) {
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (IllegalArgumentException ignored) {
            // The host activity was destroyed while deletion was running
        }
    }
}
