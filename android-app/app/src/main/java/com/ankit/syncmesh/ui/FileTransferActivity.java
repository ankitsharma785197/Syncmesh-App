package com.ankit.syncmesh.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ActivityFileTransferBinding;

/**
 * Standalone host for {@link FileTransferFragment}, used for notification deep-links into an
 * active transfer. The same fragment also backs the bottom-nav Transfer tab in MainActivity.
 */
public class FileTransferActivity extends AppCompatActivity {

    private ActivityFileTransferBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityFileTransferBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        applyInsets();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.transfer_fragment_container, new FileTransferFragment())
                    .commit();
        }
    }

    private void applyInsets() {
        final int toolbarStart = binding.toolbar.getPaddingStart();
        final int toolbarTop = binding.toolbar.getPaddingTop();
        final int toolbarEnd = binding.toolbar.getPaddingEnd();
        final int toolbarBottom = binding.toolbar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(toolbarStart + systemBars.left, toolbarTop + systemBars.top,
                    toolbarEnd + systemBars.right, toolbarBottom);
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(binding.transferFragmentContainer,
                (view, windowInsets) -> {
                    Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }
}
