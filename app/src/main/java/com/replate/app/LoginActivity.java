package com.replate.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.utils.FcmTokenManager;
import com.replate.app.utils.NotificationPermissionHelper;
import com.replate.app.utils.ToastUtils;
import com.replate.app.utils.UserRoleHelper;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    // UI Components
    private TabLayout tabLayout;
    private LinearLayout loginForm, registerForm;
    private ProgressBar progressBar;

    // Login form fields
    private TextInputLayout tilLoginEmail, tilLoginPassword;
    private TextInputEditText etLoginEmail, etLoginPassword;
    private Button btnLogin;
    private TextView tvForgotPassword;
    private com.google.android.material.button.MaterialButton btnGoogleLogin;

    // Register form fields
    private TextInputLayout tilRegisterName, tilRegisterEmail, tilRegisterPhone;
    private TextInputLayout tilRegisterPassword, tilRegisterConfirmPassword;
    private TextInputEditText etRegisterName, etRegisterEmail, etRegisterPhone;
    private TextInputEditText etRegisterPassword, etRegisterConfirmPassword;
    private Button btnRegister;
    private com.google.android.material.button.MaterialButton btnGoogleRegister;

    // Social login buttons
    private com.google.android.material.button.MaterialButton btnFacebookLogin, btnFacebookRegister;
    private com.google.android.material.button.MaterialButton btnPhoneLogin, btnPhoneRegister;

    // Google Sign-In
    private GoogleSignInClient mGoogleSignInClient;

    // Facebook Sign-In
    private CallbackManager callbackManager;

    // Phone Auth
    private String phoneVerificationId;
    private PhoneAuthProvider.ForceResendingToken phoneResendToken;

    private static final int REQ_POST_NOTIFICATIONS = 1201;
    private static final int RC_GOOGLE_SIGN_IN = 9001;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // The top of the page is the green brand section, so the status bar takes its colour (no hard edge)
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.green_primary));

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize Google Sign-In
        String webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId.isEmpty() ? getString(R.string.default_web_client_id) : webClientId)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize Facebook Sign-In
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        firebaseAuthWithFacebook(loginResult.getAccessToken().getToken());
                    }

                    @Override
                    public void onCancel() {
                        showLoading(false);
                    }

                    @Override
                    public void onError(@NonNull FacebookException error) {
                        showLoading(false);
                        ToastUtils.showCustomToast(LoginActivity.this,
                                R.string.login_facebook_sign_in_failed_please);
                    }
                });

        // Initialize views
        initializeViews();

        // Setup tab switching
        setupTabLayout();

        // Setup click listeners
        setupClickListeners();
        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (!NotificationPermissionHelper.isRuntimePermissionRequired()
                || NotificationPermissionHelper.hasPermission(this)) {
            return;
        }

        if (NotificationPermissionHelper.shouldShowRationale(this)) {
            NotificationPermissionHelper.showRationaleDialog(this,
                    () -> NotificationPermissionHelper.request(this, REQ_POST_NOTIFICATIONS));
            return;
        }

        NotificationPermissionHelper.request(this, REQ_POST_NOTIFICATIONS);
    }

    private void initializeViews() {
        tabLayout = findViewById(R.id.tabLayout);
        loginForm = findViewById(R.id.loginForm);
        registerForm = findViewById(R.id.registerForm);
        progressBar = findViewById(R.id.progressBar);

        // Login fields
        tilLoginEmail = findViewById(R.id.tilLoginEmail);
        tilLoginPassword = findViewById(R.id.tilLoginPassword);
        etLoginEmail = findViewById(R.id.etLoginEmail);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        btnFacebookLogin = findViewById(R.id.btnFacebookLogin);
        btnPhoneLogin = findViewById(R.id.btnPhoneLogin);

        // Register fields
        tilRegisterName = findViewById(R.id.tilRegisterName);
        tilRegisterEmail = findViewById(R.id.tilRegisterEmail);
        tilRegisterPhone = findViewById(R.id.tilRegisterPhone);
        tilRegisterPassword = findViewById(R.id.tilRegisterPassword);
        tilRegisterConfirmPassword = findViewById(R.id.tilRegisterConfirmPassword);
        etRegisterName = findViewById(R.id.etRegisterName);
        etRegisterEmail = findViewById(R.id.etRegisterEmail);
        etRegisterPhone = findViewById(R.id.etRegisterPhone);
        etRegisterPassword = findViewById(R.id.etRegisterPassword);
        etRegisterConfirmPassword = findViewById(R.id.etRegisterConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogleRegister = findViewById(R.id.btnGoogleRegister);
        btnFacebookRegister = findViewById(R.id.btnFacebookRegister);
        btnPhoneRegister = findViewById(R.id.btnPhoneRegister);
    }

    private void setupTabLayout() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Smooth height/fade change between the two forms instead of an instant swap
                ViewGroup forms = findViewById(R.id.formsContainer);
                if (forms != null) {
                    AutoTransition transition = new AutoTransition();
                    transition.setDuration(200);
                    TransitionManager.beginDelayedTransition(forms, transition);
                }
                if (tab.getPosition() == 0) {
                    // Show Login form
                    loginForm.setVisibility(View.VISIBLE);
                    registerForm.setVisibility(View.GONE);
                } else {
                    // Show Register form
                    loginForm.setVisibility(View.GONE);
                    registerForm.setVisibility(View.VISIBLE);
                }
                clearErrors();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupClickListeners() {
        // Login button
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        // Register button
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });

        // Forgot password
        tvForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showForgotPasswordDialog();
            }
        });

        // Google Sign-In buttons
        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
        }
        if (btnGoogleRegister != null) {
            btnGoogleRegister.setOnClickListener(v -> signInWithGoogle());
        }

        // Facebook Sign-In buttons
        if (btnFacebookLogin != null) {
            btnFacebookLogin.setOnClickListener(v -> signInWithFacebook());
        }
        if (btnFacebookRegister != null) {
            btnFacebookRegister.setOnClickListener(v -> signInWithFacebook());
        }

        // Phone Sign-In buttons
        if (btnPhoneLogin != null) {
            btnPhoneLogin.setOnClickListener(v -> showPhoneAuthDialog());
        }
        if (btnPhoneRegister != null) {
            btnPhoneRegister.setOnClickListener(v -> showPhoneAuthDialog());
        }
    }

    private void loginUser() {
        String email = etLoginEmail.getText().toString().trim();
        String password = etLoginPassword.getText().toString().trim();

        // Validate inputs
        if (!validateLoginInputs(email, password)) {
            return;
        }

        // Show loading
        showLoading(true);

        // Firebase Authentication
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (!task.isSuccessful()) {
                        showLoading(false);
                        Exception ex = task.getException();
                        if (ex instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                            // Email not registered
                            showNotRegisteredDialog(email);
                        } else if (ex instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            // Wrong password
                            tilLoginPassword.setError("Incorrect password. Please try again.");
                        } else {
                            String errorMessage = "Login failed. Please check your credentials.";
                            if (ex != null && ex.getMessage() != null) {
                                errorMessage = ex.getMessage();
                            }
                            ToastUtils.showCustomToast(LoginActivity.this, errorMessage);
                        }
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showLoading(false);
                        ToastUtils.showCustomToast(LoginActivity.this,
                            R.string.login_login_failed_please_try_again);
                        return;
                    }

                    // Reload user to get fresh emailVerified status from Firebase
                    user.reload().addOnCompleteListener(reloadTask -> {
                        FirebaseUser freshUser = mAuth.getCurrentUser();
                        if (freshUser == null) {
                            showLoading(false);
                            ToastUtils.showCustomToast(LoginActivity.this,
                                R.string.login_login_failed_please_try_again);
                            return;
                        }

                        // Enforce verified email before allowing app access.
                        if (!freshUser.isEmailVerified()) {
                            showLoading(false);
                            mAuth.signOut();
                            showEmailNotVerifiedDialog(freshUser.getEmail());
                            return;
                        }

                        resolveUserRoleAndProceed(freshUser);
                    });
                }
            });
    }

    private void resolveUserRoleAndProceed(FirebaseUser user) {
        mDatabase.child("users").child(user.getUid())
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String role = snapshot.exists()
                            ? UserRoleHelper.fromSnapshot(snapshot)
                            : UserRoleHelper.ROLE_CUSTOMER;
                    if (!snapshot.exists()) {
                        Map<String, Object> fallback = new HashMap<>();
                        fallback.put("name", user.getDisplayName() == null ? "Customer" : user.getDisplayName());
                        fallback.put("email", user.getEmail());
                        fallback.put("phone", "");
                        fallback.put("userType", UserRoleHelper.ROLE_CUSTOMER);
                        fallback.put("role", UserRoleHelper.ROLE_CUSTOMER);
                        fallback.put("createdAt", System.currentTimeMillis());
                        fallback.put("profileImage", "");
                        fallback.put("emailVerified", true);
                        mDatabase.child("users").child(user.getUid()).setValue(fallback);
                    }

                    showLoading(false);
                    saveUserSession(user, role);
                    createLoginNotification(user.getUid(), role);
                    com.replate.app.notifications.InAppNotificationCenter.publish(
                            new com.replate.app.notifications.InAppNotificationCenter.InAppNotificationMessage(
                                    "Welcome back",
                                    "Login successful. You are now signed in.",
                                    com.replate.app.notifications.NotificationCategories.LOGIN_SUCCESS,
                                    "home",
                                    "",
                                    "",
                                    "",
                                    null
                            )
                    );
                    ToastUtils.showCustomToast(LoginActivity.this, R.string.login_welcome_back);
                    goToHomeByRole(role, user.getUid());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    showLoading(false);
                    saveUserSession(user, UserRoleHelper.ROLE_CUSTOMER);
                    goToHomeByRole(UserRoleHelper.ROLE_CUSTOMER, user.getUid());
                }
            });
    }

    private void createLoginNotification(String userId, String role) {
        if (userId == null || userId.trim().isEmpty()) return;

        Map<String, Object> notif = new HashMap<>();
        notif.put("title", "Welcome back");
        notif.put("body", "You logged in successfully.");
        notif.put("type", "login");
        notif.put("category", com.replate.app.notifications.NotificationCategories.LOGIN_SUCCESS);
        notif.put("route", "home");
        notif.put("role", role == null ? "customer" : role);
        notif.put("read", false);
        notif.put("timestamp", System.currentTimeMillis());

        mDatabase.child("notifications").child(userId).push().setValue(notif);
    }

    private void registerUser() {
        String name = etRegisterName.getText().toString().trim();
        String email = etRegisterEmail.getText().toString().trim();
        String phone = etRegisterPhone.getText().toString().trim();
        String password = etRegisterPassword.getText().toString().trim();
        String confirmPassword = etRegisterConfirmPassword.getText().toString().trim();

        if (!validateRegisterInputs(name, email, phone, password, confirmPassword)) {
            return;
        }

        // Use Firebase email verification instead of custom OTP endpoint.
        performFirebaseRegistration(name, email, phone, password);
    }

    private void performFirebaseRegistration(String name, String email, String phone, String password) {
        // Show loading
        showLoading(true);

        // Create user with Firebase
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (!task.isSuccessful()) {
                        showLoading(false);
                        String errorMessage = "Registration failed.";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        ToastUtils.showCustomToast(LoginActivity.this, errorMessage);
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showLoading(false);
                        ToastUtils.showCustomToast(LoginActivity.this,
                            R.string.login_registration_failed_user_not_available);
                        return;
                    }

                    // Save profile first, then send Firebase verification email.
                    saveUserToDatabase(user.getUid(), name, email, phone, false, true);
                }
            });
    }

    /**
     * Save user data to Firebase Realtime Database
     */
    private void saveUserToDatabase(String userId, String name, String email, String phone,
                                    boolean emailVerified, boolean sendVerificationEmail) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("userType", "customer");
        userData.put("role", "customer");
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("profileImage", "");
        userData.put("emailVerified", emailVerified);

        mDatabase.child("users").child(userId).setValue(userData)
            .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (!task.isSuccessful()) {
                        showLoading(false);
                        ToastUtils.showCustomToast(LoginActivity.this,
                            R.string.login_failed_to_save_user_data);
                        return;
                    }

                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                    if (firebaseUser == null) {
                        showLoading(false);
                        ToastUtils.showCustomToast(LoginActivity.this,
                            R.string.login_verification_mail_could_not_be);
                        return;
                    }

                    if (!sendVerificationEmail) {
                        showLoading(false);
                        saveUserSession(firebaseUser, "customer");
                        ToastUtils.showCustomToast(LoginActivity.this,
                            R.string.login_account_created_successfully);
                        goToHomeByRole("customer", firebaseUser.getUid());
                        return;
                    }

                    // Send branded verification email via Cloud Function
                    sendBrandedVerificationEmail(email,
                        etRegisterName.getText() != null ? etRegisterName.getText().toString().trim() : "",
                        firebaseUser);
                }
            });
    }

    /**
     * Send branded verification email via our Cloud Function.
     * Falls back to Firebase default sendEmailVerification if Cloud Function fails.
     */
    private void sendBrandedVerificationEmail(String email, String name, FirebaseUser firebaseUser) {
        new Thread(() -> {
            boolean cloudSuccess = false;
            try {
                String projectId = getFirebaseProjectId();
                if (projectId == null || projectId.isEmpty()) {
                    throw new Exception("Project ID not available");
                }
                java.net.URL url = new java.net.URL(
                    "https://asia-southeast1-" + projectId + ".cloudfunctions.net/sendCustomVerificationEmail");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // Build the body with JSONObject so quotes or backslashes in a name can't break it
                org.json.JSONObject body = new org.json.JSONObject();
                body.put("email", email);
                body.put("name", name != null ? name : "");
                try (java.io.OutputStream out = conn.getOutputStream()) {
                    out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                cloudSuccess = (responseCode >= 200 && responseCode < 300);
                conn.disconnect();
            } catch (Exception e) {
                // Cloud Function call failed; the Firebase default email below is the fallback
                android.util.Log.w("LoginActivity", "Branded verification email failed, using fallback", e);
            }

            final boolean success = cloudSuccess;
            runOnUiThread(() -> {
                if (success) {
                    showLoading(false);
                    mAuth.signOut();
                    showVerificationSentDialog(email);
                } else {
                    // Fallback: use Firebase default verification
                    if (firebaseUser != null) {
                        firebaseUser.sendEmailVerification().addOnCompleteListener(mailTask -> {
                            showLoading(false);
                            mAuth.signOut();
                            showVerificationSentDialog(email);
                        });
                    } else {
                        showLoading(false);
                        mAuth.signOut();
                        showVerificationSentDialog(email);
                    }
                }
            });
        }).start();
    }

    private String getFirebaseProjectId() {
        try {
            com.google.firebase.FirebaseApp app = com.google.firebase.FirebaseApp.getInstance();
            if (app != null && app.getOptions() != null) {
                return app.getOptions().getProjectId();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Show a clean dialog after signup telling the user to verify their email.
     */
    private void showVerificationSentDialog(String email) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.login_verify_your_email)
            .setMessage("We've sent a verification link to:\n\n" + (email != null ? email : "") +
                "\n\nPlease check your inbox (and spam folder) and click the link to activate your account.\n\nOnce verified, come back and log in.")
            .setPositiveButton(R.string.login_got_it, (dialog, which) -> {
                tabLayout.selectTab(tabLayout.getTabAt(0));
            })
            .setCancelable(false)
            .show();
    }

    /**
     * Validate login form inputs
     */
    private boolean validateLoginInputs(String email, String password) {
        boolean isValid = true;

        // Validate email
        if (TextUtils.isEmpty(email)) {
            tilLoginEmail.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilLoginEmail.setError("Enter a valid email");
            isValid = false;
        } else {
            tilLoginEmail.setError(null);
        }

        // Validate password
        if (TextUtils.isEmpty(password)) {
            tilLoginPassword.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            tilLoginPassword.setError("Password must be at least 6 characters");
            isValid = false;
        } else {
            tilLoginPassword.setError(null);
        }

        return isValid;
    }

    private boolean validateRegisterInputs(String name, String email, String phone,
                                           String password, String confirmPassword) {
        boolean isValid = true;

        // Validate name
        if (TextUtils.isEmpty(name)) {
            tilRegisterName.setError("Name is required");
            isValid = false;
        } else if (name.length() < 2) {
            tilRegisterName.setError("Name is too short");
            isValid = false;
        } else {
            tilRegisterName.setError(null);
        }

        // Validate email
        if (TextUtils.isEmpty(email)) {
            tilRegisterEmail.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilRegisterEmail.setError("Enter a valid email");
            isValid = false;
        } else {
            tilRegisterEmail.setError(null);
        }

        // Validate phone
        if (TextUtils.isEmpty(phone)) {
            tilRegisterPhone.setError("Phone is required");
            isValid = false;
        } else if (phone.length() < 10) {
            tilRegisterPhone.setError("Enter a valid phone number");
            isValid = false;
        } else {
            tilRegisterPhone.setError(null);
        }

        // Validate password
        if (TextUtils.isEmpty(password)) {
            tilRegisterPassword.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            tilRegisterPassword.setError("Password must be at least 6 characters");
            isValid = false;
        } else {
            tilRegisterPassword.setError(null);
        }

        // Validate confirm password
        if (TextUtils.isEmpty(confirmPassword)) {
            tilRegisterConfirmPassword.setError("Confirm your password");
            isValid = false;
        } else if (!confirmPassword.equals(password)) {
            tilRegisterConfirmPassword.setError("Passwords do not match");
            isValid = false;
        } else {
            tilRegisterConfirmPassword.setError(null);
        }

        return isValid;
    }

    /**
     * Show dialog when user tries to log in with an unregistered email.
     */
    private void showNotRegisteredDialog(String email) {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.auth_not_registered_title))
            .setMessage(getString(R.string.auth_not_registered_message))
            .setPositiveButton(R.string.login_register_now, (dialog, which) -> {
                // Switch to Register tab and pre-fill the email
                tabLayout.selectTab(tabLayout.getTabAt(1));
                if (etRegisterEmail != null && email != null) {
                    etRegisterEmail.setText(email);
                }
            })
            .setNegativeButton(R.string.login_ok, null)
            .setCancelable(true)
            .show();
    }

    private void showEmailNotVerifiedDialog(String email) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.login_email_not_verified)
            .setMessage("Your email address (" + (email != null ? email : "") + ") has not been verified yet.\n\nPlease check your inbox (and spam folder) for the verification link, then try logging in again.")
            .setPositiveButton(R.string.login_resend_email, (dialog, which) -> {
                // Re-authenticate briefly to resend
                showLoading(true);
                String enteredEmail = etLoginEmail.getText() != null ? etLoginEmail.getText().toString().trim() : "";
                String enteredPass = etLoginPassword.getText() != null ? etLoginPassword.getText().toString().trim() : "";
                if (!enteredEmail.isEmpty() && !enteredPass.isEmpty()) {
                    mAuth.signInWithEmailAndPassword(enteredEmail, enteredPass)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                                FirebaseUser u = mAuth.getCurrentUser();
                                sendBrandedVerificationEmail(enteredEmail, null, u);
                            } else {
                                showLoading(false);
                                ToastUtils.showCustomToast(this, R.string.login_could_not_resend_please_check);
                            }
                        });
                } else {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, R.string.login_enter_your_email_and_password);
                }
            })
            .setNegativeButton(R.string.login_ok, null)
            .setCancelable(true)
            .show();
    }

    private void signInWithGoogle() {
        if (mGoogleSignInClient == null) {
            ToastUtils.showCustomToast(this, R.string.login_google_sign_in_is_not);
            return;
        }
        // Sign out first so the account chooser always appears
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Facebook callback
        if (callbackManager != null) {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                int code = e.getStatusCode();
                if (code == com.google.android.gms.common.api.CommonStatusCodes.CANCELED
                        || code == 12501) {
                    // User cancelled - do nothing
                } else if (code == com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR
                        || code == 7) {
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.login_network_error)
                        .setMessage(R.string.login_could_not_connect_to_google)
                        .setPositiveButton(R.string.login_retry, (d, w) -> signInWithGoogle())
                        .setNegativeButton(R.string.login_cancel, null)
                        .show();
                } else {
                    ToastUtils.showCustomToast(this, R.string.login_google_sign_in_failed_please);
                }
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        showLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, R.string.login_google_sign_in_failed_please);
                    return;
                }
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, R.string.login_sign_in_failed_please_try);
                    return;
                }
                // Check if user profile exists; if not, create it
                mDatabase.child("users").child(user.getUid())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (!snapshot.exists()) {
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("name", user.getDisplayName() != null ? user.getDisplayName() : "Customer");
                                userData.put("email", user.getEmail());
                                userData.put("phone", "");
                                userData.put("userType", "customer");
                                userData.put("role", "customer");
                                userData.put("createdAt", System.currentTimeMillis());
                                userData.put("profileImage", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
                                userData.put("emailVerified", true);
                                mDatabase.child("users").child(user.getUid()).setValue(userData);
                            }
                            String role = snapshot.exists() ? UserRoleHelper.fromSnapshot(snapshot) : UserRoleHelper.ROLE_CUSTOMER;
                            showLoading(false);
                            saveUserSession(user, role);
                            createLoginNotification(user.getUid(), role);
                            ToastUtils.showCustomToast(LoginActivity.this, "Welcome, " + (user.getDisplayName() != null ? user.getDisplayName() : "there") + "!");
                            goToHomeByRole(role, user.getUid());
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            showLoading(false);
                            saveUserSession(user, UserRoleHelper.ROLE_CUSTOMER);
                            goToHomeByRole(UserRoleHelper.ROLE_CUSTOMER, user.getUid());
                        }
                    });
            });
    }

    // ─── Facebook Sign-In ───────────────────────────────────────────────
    private void signInWithFacebook() {
        showLoading(true);
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
    }

    private void firebaseAuthWithFacebook(String token) {
        showLoading(true);
        AuthCredential credential = FacebookAuthProvider.getCredential(token);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, R.string.login_facebook_sign_in_failed_please);
                    return;
                }
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, R.string.login_sign_in_failed_please_try);
                    return;
                }
                handleSocialAuthUser(user);
            });
    }

    // ─── Phone Sign-In ──────────────────────────────────────────────────
    private void showPhoneAuthDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_phone_auth, null);

        TextInputEditText etCountryCode = dialogView.findViewById(R.id.etCountryCode);
        TextInputEditText etPhoneNumber = dialogView.findViewById(R.id.etPhoneNumber);
        com.google.android.material.button.MaterialButton btnSendOtp = dialogView.findViewById(R.id.btnSendOtp);
        LinearLayout otpSection = dialogView.findViewById(R.id.otpSection);
        TextInputEditText etOtpCode = dialogView.findViewById(R.id.etOtpCode);
        com.google.android.material.button.MaterialButton btnVerifyOtp = dialogView.findViewById(R.id.btnVerifyOtp);
        TextView tvResendOtp = dialogView.findViewById(R.id.tvResendOtp);
        ProgressBar phoneAuthProgress = dialogView.findViewById(R.id.phoneAuthProgress);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            // Rounded card, inset 16dp from the screen edge like a standard dialog
            int inset = (int) (16 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(
                    ContextCompat.getDrawable(this, R.drawable.card_background_shape), inset));
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        btnSendOtp.setOnClickListener(v -> {
            String code = etCountryCode.getText() != null ? etCountryCode.getText().toString().trim() : "+94";
            String phone = etPhoneNumber.getText() != null ? etPhoneNumber.getText().toString().trim() : "";
            if (phone.isEmpty()) {
                ToastUtils.showCustomToast(this, R.string.login_please_enter_your_phone_number);
                return;
            }
            String fullPhone = code + phone;

            btnSendOtp.setEnabled(false);
            phoneAuthProgress.setVisibility(View.VISIBLE);

            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                    .setPhoneNumber(fullPhone)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override
                        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                            phoneAuthProgress.setVisibility(View.GONE);
                            dialog.dismiss();
                            signInWithPhoneCredential(credential);
                        }

                        @Override
                        public void onVerificationFailed(@NonNull com.google.firebase.FirebaseException e) {
                            phoneAuthProgress.setVisibility(View.GONE);
                            btnSendOtp.setEnabled(true);
                            ToastUtils.showCustomToast(LoginActivity.this,
                                    "Verification failed: " + e.getMessage());
                        }

                        @Override
                        public void onCodeSent(@NonNull String verificationId,
                                               @NonNull PhoneAuthProvider.ForceResendingToken token) {
                            phoneVerificationId = verificationId;
                            phoneResendToken = token;
                            phoneAuthProgress.setVisibility(View.GONE);

                            // Show OTP section (animated, so the dialog does not jump)
                            TransitionManager.beginDelayedTransition((ViewGroup) dialogView);
                            btnSendOtp.setVisibility(View.GONE);
                            otpSection.setVisibility(View.VISIBLE);
                            ToastUtils.showCustomToast(LoginActivity.this, R.string.login_verification_code_sent);
                        }
                    })
                    .build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });

        btnVerifyOtp.setOnClickListener(v -> {
            String otp = etOtpCode.getText() != null ? etOtpCode.getText().toString().trim() : "";
            if (otp.length() != 6) {
                ToastUtils.showCustomToast(this, R.string.login_please_enter_the_6_digit);
                return;
            }
            if (phoneVerificationId == null) {
                ToastUtils.showCustomToast(this, R.string.login_please_request_a_code_first);
                return;
            }

            phoneAuthProgress.setVisibility(View.VISIBLE);
            btnVerifyOtp.setEnabled(false);

            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(phoneVerificationId, otp);
            dialog.dismiss();
            signInWithPhoneCredential(credential);
        });

        tvResendOtp.setOnClickListener(v -> {
            // Reset UI to send again
            TransitionManager.beginDelayedTransition((ViewGroup) dialogView);
            otpSection.setVisibility(View.GONE);
            btnSendOtp.setVisibility(View.VISIBLE);
            btnSendOtp.setEnabled(true);
        });

        dialog.show();
    }

    private void signInWithPhoneCredential(PhoneAuthCredential credential) {
        showLoading(true);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, R.string.login_phone_verification_failed_please_try);
                    return;
                }
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    showLoading(false);
                    ToastUtils.showCustomToast(this, R.string.login_sign_in_failed_please_try);
                    return;
                }
                handleSocialAuthUser(user);
            });
    }

    /**
     * Common handler for social/phone auth users. Creates profile if new, then navigates.
     */
    private void handleSocialAuthUser(FirebaseUser user) {
        mDatabase.child("users").child(user.getUid())
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("name", user.getDisplayName() != null ? user.getDisplayName() : "Customer");
                        userData.put("email", user.getEmail() != null ? user.getEmail() : "");
                        userData.put("phone", user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
                        userData.put("userType", "customer");
                        userData.put("role", "customer");
                        userData.put("createdAt", System.currentTimeMillis());
                        userData.put("profileImage", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
                        userData.put("emailVerified", true);
                        mDatabase.child("users").child(user.getUid()).setValue(userData);
                    }
                    String role = snapshot.exists() ? UserRoleHelper.fromSnapshot(snapshot) : UserRoleHelper.ROLE_CUSTOMER;
                    showLoading(false);
                    saveUserSession(user, role);
                    createLoginNotification(user.getUid(), role);
                    String displayName = user.getDisplayName() != null ? user.getDisplayName() :
                            (user.getPhoneNumber() != null ? user.getPhoneNumber() : "there");
                    ToastUtils.showCustomToast(LoginActivity.this, "Welcome, " + displayName + "!");
                    goToHomeByRole(role, user.getUid());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    showLoading(false);
                    saveUserSession(user, UserRoleHelper.ROLE_CUSTOMER);
                    goToHomeByRole(UserRoleHelper.ROLE_CUSTOMER, user.getUid());
                }
            });
    }

    private void showForgotPasswordDialog() {
        // Create input field
        final TextInputEditText input = new TextInputEditText(this);
        input.setHint(R.string.login_enter_your_email);
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setPadding(50, 30, 50, 30);

        // Create dialog
        new AlertDialog.Builder(this)
            .setTitle(R.string.login_reset_password)
            .setMessage(R.string.login_we_ll_send_you_an)
            .setView(input)
            .setPositiveButton(R.string.login_send, (dialog, which) -> {
                String email = input.getText().toString().trim();
                if (!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    sendPasswordResetEmail(email);
                } else {
                    ToastUtils.showCustomToast(this, R.string.login_please_enter_a_valid_email);
                }
            })
            .setNegativeButton(R.string.login_cancel, null)
            .show();
    }

    private void sendPasswordResetEmail(String email) {
        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener(task -> {
                showLoading(false);
                if (task.isSuccessful()) {
                    ToastUtils.showCustomToast(this,
                        R.string.login_password_reset_email_sent_check);
                } else {
                    ToastUtils.showCustomToast(this,
                        R.string.login_failed_to_send_reset_email);
                }
            });
    }

    private void saveUserSession(FirebaseUser user, String role) {
        if (user != null) {
            SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit()
                .putString("userId", user.getUid())
                .putString("userEmail", user.getEmail())
                .putString("role", role)
                .putString("userType", role)
                .putBoolean("isLoggedIn", true);

            // Ask location only once, right after successful customer auth.
            if ("customer".equalsIgnoreCase(role)) {
                editor.putBoolean("pending_location_permission_prompt", true);
            }

            editor.apply();
            FcmTokenManager.syncTokenForCurrentUser(this, role);
        }
    }

    private void clearErrors() {
        tilLoginEmail.setError(null);
        tilLoginPassword.setError(null);
        tilRegisterName.setError(null);
        tilRegisterEmail.setError(null);
        tilRegisterPhone.setError(null);
        tilRegisterPassword.setError(null);
        tilRegisterConfirmPassword.setError(null);
    }

    private void showLoading(boolean show) {
        // INVISIBLE (not GONE) so the card does not jump when the bar appears
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        btnLogin.setEnabled(!show);
        btnRegister.setEnabled(!show);
        if (btnGoogleLogin != null) btnGoogleLogin.setEnabled(!show);
        if (btnGoogleRegister != null) btnGoogleRegister.setEnabled(!show);
        if (btnFacebookLogin != null) btnFacebookLogin.setEnabled(!show);
        if (btnFacebookRegister != null) btnFacebookRegister.setEnabled(!show);
        if (btnPhoneLogin != null) btnPhoneLogin.setEnabled(!show);
        if (btnPhoneRegister != null) btnPhoneRegister.setEnabled(!show);
    }

    private void goToHomeByRole(String role, String userId) {
        // Show onboarding for first-time users before entering the app
        SharedPreferences prefs = getSharedPreferences("REPLATE_PREFS", MODE_PRIVATE);
        boolean onboardingSeen = prefs.getBoolean("onboarding_seen", false);
        if (!onboardingSeen) {
            Intent onboardingIntent = new Intent(LoginActivity.this, OnboardingActivity.class);
            onboardingIntent.putExtra("post_auth", true);
            onboardingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(onboardingIntent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }

        if ("hotel".equals(role)) {
            checkHotelApprovalStatus(userId);
            return;
        }

        if ("admin".equals(role)) {
            try {
                Class<?> adminClass = Class.forName("com.replate.app.admin.AdminDashboardActivity");
                Intent adminIntent = new Intent(LoginActivity.this, adminClass);
                adminIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(adminIntent);
                finish();
                return;
            } catch (ClassNotFoundException ignored) {
                // Fall through to customer shell when admin module is not bundled.
            }
        }

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void checkHotelApprovalStatus(String userId) {
        // Attempt hotel dashboard if present, otherwise fallback to main customer shell.
        try {
            Class<?> hotelClass = Class.forName("com.replate.app.hotel.HotelDashboardActivity");
            startActivity(new Intent(LoginActivity.this, hotelClass));
        } catch (ClassNotFoundException e) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
        }
        finish();
    }

}
