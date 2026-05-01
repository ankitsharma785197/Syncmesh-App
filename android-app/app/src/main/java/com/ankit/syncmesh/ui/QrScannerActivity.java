package com.ankit.syncmesh.ui;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ankit.syncmesh.databinding.ActivityQrScannerBinding;
import com.journeyapps.barcodescanner.CaptureManager;

public class QrScannerActivity extends AppCompatActivity {
    private ActivityQrScannerBinding binding;
    private CaptureManager captureManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityQrScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        applyWindowInsets();
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        captureManager = new CaptureManager(this, binding.barcodeScannerView);
        captureManager.initializeFromIntent(getIntent(), savedInstanceState);
        captureManager.decode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (captureManager != null) {
            captureManager.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (captureManager != null) {
            captureManager.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (captureManager != null) {
            captureManager.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (captureManager != null) {
            captureManager.onSaveInstanceState(outState);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return binding.barcodeScannerView.onKeyDown(keyCode, event)
                || super.onKeyDown(keyCode, event);
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

        final int contentStart = binding.scannerContent.getPaddingStart();
        final int contentTop = binding.scannerContent.getPaddingTop();
        final int contentEnd = binding.scannerContent.getPaddingEnd();
        final int contentBottom = binding.scannerContent.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scannerContent, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    contentStart + systemBars.left,
                    contentTop,
                    contentEnd + systemBars.right,
                    contentBottom + systemBars.bottom
            );
            return windowInsets;
        });

        ViewCompat.requestApplyInsets(binding.getRoot());
    }
}
