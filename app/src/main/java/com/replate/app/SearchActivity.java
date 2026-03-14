package com.replate.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Search Activity - Redirects to search tab in MainActivity
 */
public class SearchActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Redirect to MainActivity search tab
        finish();
    }
}
