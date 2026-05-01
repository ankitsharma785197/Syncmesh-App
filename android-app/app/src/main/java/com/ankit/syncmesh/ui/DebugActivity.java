package com.ankit.syncmesh.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ActivityDebugBinding;

public class DebugActivity extends AppCompatActivity {
    private ActivityDebugBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityDebugBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        applyWindowInsets();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.debug_fragment_container, new DebugFragment())
                    .commit();
        }
    }

    private void applyWindowInsets() {
        final int toolbarStart = binding.toolbar.getPaddingStart();
        final int toolbarTop = binding.toolbar.getPaddingTop();
        final int toolbarEnd = binding.toolbar.getPaddingEnd();
        final int toolbarBottom = binding.toolbar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    toolbarStart + systemBars.left,
                    toolbarTop + systemBars.top,
                    toolbarEnd + systemBars.right,
                    toolbarBottom
            );
            return windowInsets;
        });

        final int containerStart = binding.debugFragmentContainer.getPaddingStart();
        final int containerTop = binding.debugFragmentContainer.getPaddingTop();
        final int containerEnd = binding.debugFragmentContainer.getPaddingEnd();
        final int containerBottom = binding.debugFragmentContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.debugFragmentContainer, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int keyboardPadding = Math.max(0, ime.bottom - systemBars.bottom);
            view.setPadding(
                    containerStart + systemBars.left,
                    containerTop,
                    containerEnd + systemBars.right,
                    containerBottom + keyboardPadding
            );
            return windowInsets;
        });

        ViewCompat.requestApplyInsets(binding.getRoot());
    }
}
