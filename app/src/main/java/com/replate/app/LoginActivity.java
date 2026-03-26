package com.replate.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.replate.app.utils.FcmTokenManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login/Register Activity with Firebase Authentication
 * Allows users to login or create new accounts
 */
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

    // Register form fields
    private TextInputLayout tilRegisterName, tilRegisterEmail, tilRegisterPhone;
    private TextInputLayout tilRegisterPassword, tilRegisterConfirmPassword;
    private TextInputEditText etRegisterName, etRegisterEmail, etRegisterPhone;
    private TextInputEditText etRegisterPassword, etRegisterConfirmPassword;
    private Button btnRegister;

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private static final String OTP_FALLBACK_US = "https://us-central1-replate-food-rescue.cloudfunctions.net";
    private static final String OTP_FALLBACK_ASIA = "https://asia-southeast1-replate-food-rescue.cloudfunctions.net";
    private static final int REQ_POST_NOTIFICATIONS = 1201;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize views
        initializeViews();

        // Setup tab switching
        setupTabLayout();

        // Setup click listeners
        setupClickListeners();
        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_POST_NOTIFICATIONS
        );
    }

    /**
     * Initialize all views from layout
     */
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
    }

    /**
     * Setup tab layout to switch between Login and Register
     */
    private void setupTabLayout() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
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

    /**
     * Setup click listeners for buttons
     */
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
    }

    /**
     * Login user with email and password
     */
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
                        String errorMessage = "Login failed. Please check your credentials.";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this,
                            "Login failed. Please try again.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Enforce verified email before allowing app access.
                    if (!user.isEmailVerified()) {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this,
                            "Please verify your email first. Verification mail sent.",
                            Toast.LENGTH_LONG).show();
                        user.sendEmailVerification();
                        mAuth.signOut();
                        return;
                    }

                    resolveUserRoleAndProceed(user);
                }
            });
    }

    private void resolveUserRoleAndProceed(FirebaseUser user) {
        mDatabase.child("users").child(user.getUid())
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String role = "customer";
                    if (snapshot.exists()) {
                        String roleVal = snapshot.child("role").getValue(String.class);
                        String typeVal = snapshot.child("userType").getValue(String.class);
                        if (roleVal != null && !roleVal.trim().isEmpty()) {
                            role = roleVal.trim().toLowerCase();
                        } else if (typeVal != null && !typeVal.trim().isEmpty()) {
                            role = typeVal.trim().toLowerCase();
                        }
                    } else {
                        // Backfill missing profile for old auth-only users.
                        Map<String, Object> fallback = new HashMap<>();
                        fallback.put("name", user.getDisplayName() == null ? "Customer" : user.getDisplayName());
                        fallback.put("email", user.getEmail());
                        fallback.put("phone", "");
                        fallback.put("userType", "customer");
                        fallback.put("role", "customer");
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
                    Toast.makeText(LoginActivity.this, "Welcome back!", Toast.LENGTH_SHORT).show();
                    goToHomeByRole(role, user.getUid());
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    showLoading(false);
                    saveUserSession(user, "customer");
                    goToHomeByRole("customer", user.getUid());
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

    /**
     * Register new user
     */
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
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this,
                            "Registration failed. User not available.", Toast.LENGTH_LONG).show();
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
                        Toast.makeText(LoginActivity.this,
                            "Failed to save user data.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser firebaseUser = mAuth.getCurrentUser();
                    if (firebaseUser == null) {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this,
                            "Verification mail could not be sent.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!sendVerificationEmail) {
                        showLoading(false);
                        saveUserSession(firebaseUser, "customer");
                        Toast.makeText(LoginActivity.this,
                            "Account created successfully!", Toast.LENGTH_SHORT).show();
                        goToHomeByRole("customer", firebaseUser.getUid());
                        return;
                    }

                    firebaseUser.sendEmailVerification().addOnCompleteListener(mailTask -> {
                        showLoading(false);
                        if (mailTask.isSuccessful()) {
                            Toast.makeText(LoginActivity.this,
                                "Verification email sent. Please check your inbox before login.",
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(LoginActivity.this,
                                "Account created, but email send failed. You can retry from login.",
                                Toast.LENGTH_LONG).show();
                        }
                        mAuth.signOut();
                        tabLayout.selectTab(tabLayout.getTabAt(0));
                    });
                }
            });
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

    /**
     * Validate registration form inputs
     */
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
     * Show forgot password dialog
     */
    private void showForgotPasswordDialog() {
        // Create input field
        final TextInputEditText input = new TextInputEditText(this);
        input.setHint("Enter your email");
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setPadding(50, 30, 50, 30);

        // Create dialog
        new AlertDialog.Builder(this)
            .setTitle("Reset Password")
            .setMessage("We'll send you an email to reset your password.")
            .setView(input)
            .setPositiveButton("Send", (dialog, which) -> {
                String email = input.getText().toString().trim();
                if (!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    sendPasswordResetEmail(email);
                } else {
                    Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Send password reset email
     */
    private void sendPasswordResetEmail(String email) {
        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener(task -> {
                showLoading(false);
                if (task.isSuccessful()) {
                    Toast.makeText(this,
                        "Password reset email sent. Check your inbox.",
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                        "Failed to send reset email. Please try again.",
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    /**
     * Save user session to SharedPreferences
     */
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

    /**
     * Clear all form errors
     */
    private void clearErrors() {
        tilLoginEmail.setError(null);
        tilLoginPassword.setError(null);
        tilRegisterName.setError(null);
        tilRegisterEmail.setError(null);
        tilRegisterPhone.setError(null);
        tilRegisterPassword.setError(null);
        tilRegisterConfirmPassword.setError(null);
    }

    /**
     * Show or hide loading indicator
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
        btnRegister.setEnabled(!show);
    }

    /**
     * Navigate to Home screen
     */
    private void goToHomeByRole(String role, String userId) {
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

    /**
     * Check hotel approval status and navigate accordingly
     */
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

    private JSONObject postJson(String endpoint, JSONObject payload) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.toString().getBytes());
            }

            int code = connection.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }

            String body = sb.toString().trim();
            if (body.isEmpty()) {
                JSONObject fallback = new JSONObject();
                fallback.put("success", code >= 200 && code < 300);
                fallback.put("message", "Empty server response");
                return fallback;
            }

            String bodyLower = body.toLowerCase();
            if (bodyLower.startsWith("<!doctype") || bodyLower.startsWith("<html") || bodyLower.contains("<head") || bodyLower.contains("<body")) {
                throw new IllegalStateException("Endpoint returned HTML instead of JSON. Deploy OTP functions or fix endpoint URL.");
            }

            if (!body.startsWith("{")) {
                throw new IllegalStateException("Endpoint returned non-JSON response: " + body.substring(0, Math.min(120, body.length())));
            }

            try {
                return new JSONObject(body);
            } catch (Exception parseEx) {
                throw new IllegalStateException("Invalid JSON from endpoint: " + parseEx.getMessage());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
    }
}
