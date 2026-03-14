package com.replate.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

public class PayHereWebCheckoutActivity extends AppCompatActivity {

    public static final String EXTRA_CHECKOUT_HTML = "extra_checkout_html";
    public static final String EXTRA_ORDER_ID = "extra_order_id";
    public static final String EXTRA_PAYMENT_STATUS = "extra_payment_status";

    private static final String SUCCESS_URL = "https://replate.app/payment/success";
    private static final String CANCEL_URL = "https://replate.app/payment/cancel";

    private WebView webView;
    private ProgressBar progressBar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payhere_web_checkout);

        webView = findViewById(R.id.web_checkout);
        progressBar = findViewById(R.id.progress_checkout);

        String html = getIntent().getStringExtra(EXTRA_CHECKOUT_HTML);
        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(android.view.View.VISIBLE);
                handleRedirect(url, orderId);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(android.view.View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return handleRedirect(url, orderId);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleRedirect(url, orderId);
            }
        });

        if (html == null || html.trim().isEmpty()) {
            Intent result = new Intent();
            result.putExtra(EXTRA_PAYMENT_STATUS, "cancelled");
            setResult(RESULT_CANCELED, result);
            finish();
            return;
        }

        webView.loadDataWithBaseURL("https://sandbox.payhere.lk", html, "text/html", "UTF-8", null);
    }

    private boolean handleRedirect(String url, String orderId) {
        if (url == null) return false;

        if (url.startsWith(SUCCESS_URL)) {
            Intent result = new Intent();
            result.putExtra(EXTRA_ORDER_ID, orderId);
            result.putExtra(EXTRA_PAYMENT_STATUS, "paid");
            setResult(RESULT_OK, result);
            finish();
            return true;
        }

        if (url.startsWith(CANCEL_URL)) {
            Intent result = new Intent();
            result.putExtra(EXTRA_ORDER_ID, orderId);
            result.putExtra(EXTRA_PAYMENT_STATUS, "cancelled");
            setResult(RESULT_CANCELED, result);
            finish();
            return true;
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        Intent result = new Intent();
        result.putExtra(EXTRA_PAYMENT_STATUS, "cancelled");
        setResult(RESULT_CANCELED, result);
        super.onBackPressed();
    }
}
