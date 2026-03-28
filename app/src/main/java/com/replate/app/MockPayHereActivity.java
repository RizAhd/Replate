package com.replate.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.replate.app.utils.ToastUtils;

import java.util.Locale;

public class MockPayHereActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "extra_amount";

    private TextInputEditText etCardNumber, etExpiry, etCvc, etCardName;
    private Button btnPayNow;
    private FrameLayout progressOverlay;
    private TextView tvAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_payhere);

        initViews();
        setupFormatters();

        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0.0);
        tvAmount.setText(String.format(Locale.US, "LKR %.2f", amount));

        btnPayNow.setOnClickListener(v -> processPayment());
    }

    private void initViews() {
        etCardNumber = findViewById(R.id.etCardNumber);
        etExpiry = findViewById(R.id.etExpiry);
        etCvc = findViewById(R.id.etCvv); // XML id is etCvv, java was etCvc
        etCardName = findViewById(R.id.etCardName);
        btnPayNow = findViewById(R.id.btnPay); // XML id changed to btnPay
        // progressOverlay = findViewById(R.id.progressOverlay); // Removed from new XML
        tvAmount = findViewById(R.id.tvAmount);
    }

    private void setupFormatters() {
        // Simple expiry date formatter (MM/YY)
        etExpiry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 2 && !s.toString().contains("/")) {
                    s.append("/");
                }
            }
        });
    }

    private void processPayment() {
        String cardNum = etCardNumber.getText().toString();
        String expiry = etExpiry.getText().toString();
        String cvc = etCvc.getText().toString();
        String name = etCardName.getText().toString();

        if (cardNum.length() < 16) {
            etCardNumber.setError("Invalid Card Number");
            return;
        }
        if (expiry.length() < 5) {
            etExpiry.setError("Invalid Expiry");
            return;
        }
        if (cvc.length() < 3) {
            etCvc.setError("Invalid CVC");
            return;
        }
        if (name.isEmpty()) {
            etCardName.setError("Name Required");
            return;
        }

        // Simulate Network Call
        // progressOverlay.setVisibility(View.VISIBLE);
        btnPayNow.setEnabled(false);

        new Handler().postDelayed(() -> {
            ToastUtils.showCustomToast(MockPayHereActivity.this, "Payment Approved");
            setResult(RESULT_OK);
            finish();
        }, 2000);
    }
}
