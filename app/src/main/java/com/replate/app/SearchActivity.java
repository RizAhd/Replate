package com.replate.app;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.replate.app.fragments.SearchFragment;

/**
 * Search Activity - Dedicated searchable listing screen.
 */
public class SearchActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);
        int containerId = R.id.search_fragment_container;
        container.setId(containerId);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        container.setBackgroundColor(getColor(R.color.background_cream));
        setContentView(container);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(containerId, new SearchFragment())
                    .commit();
        }
    }
}
