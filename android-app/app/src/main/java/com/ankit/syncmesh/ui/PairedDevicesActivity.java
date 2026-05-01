package com.ankit.syncmesh.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ActivityPairedDevicesBinding;

public class PairedDevicesActivity extends AppCompatActivity {
    private ActivityPairedDevicesBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityPairedDevicesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        applyWindowInsets();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.paired_devices_fragment_container, new PairedDevicesFragment())
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

        final int containerStart = binding.pairedDevicesFragmentContainer.getPaddingStart();
        final int containerTop = binding.pairedDevicesFragmentContainer.getPaddingTop();
        final int containerEnd = binding.pairedDevicesFragmentContainer.getPaddingEnd();
        final int containerBottom = binding.pairedDevicesFragmentContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.pairedDevicesFragmentContainer, (view, windowInsets) -> {
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
