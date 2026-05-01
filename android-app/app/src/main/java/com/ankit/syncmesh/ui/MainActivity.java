package com.ankit.syncmesh.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ActivityMainBinding;
import com.ankit.syncmesh.sync.SyncCoordinator;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_START_DESTINATION = "com.ankit.syncmesh.extra.START_DESTINATION";

    private ActivityMainBinding binding;

    public static Intent createLaunchIntent(Context context, int destinationId) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_START_DESTINATION, destinationId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        applyWindowInsets();

        binding.bottomNavigation.setOnItemSelectedListener(this::onNavigationItemSelected);
        if (savedInstanceState == null) {
            binding.bottomNavigation.setSelectedItemId(resolveStartDestination(getIntent()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SyncCoordinator.getInstance(this).refreshSnapshot();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_top_app_bar_menu, menu);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        navigateTo(resolveStartDestination(intent));
    }

    public void navigateTo(int itemId) {
        if (itemId == R.id.action_debug) {
            openDebugScreen();
            return;
        }
        binding.bottomNavigation.setSelectedItemId(itemId);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_debug) {
            openDebugScreen();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment;
        String title = item.getTitle().toString();
        int itemId = item.getItemId();
        if (itemId == R.id.nav_pair) {
            fragment = new PairFragment();
        } else if (itemId == R.id.nav_devices) {
            fragment = new PairedDevicesFragment();
        } else if (itemId == R.id.nav_history) {
            fragment = new ClipboardHistoryFragment();
        } else {
            fragment = new HomeFragment();
        }

        binding.toolbar.setTitle(title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        return true;
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

        final int bottomStart = binding.bottomNavigation.getPaddingStart();
        final int bottomTop = binding.bottomNavigation.getPaddingTop();
        final int bottomEnd = binding.bottomNavigation.getPaddingEnd();
        final int bottomBottom = binding.bottomNavigation.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    bottomStart + systemBars.left,
                    bottomTop,
                    bottomEnd + systemBars.right,
                    bottomBottom + systemBars.bottom
            );
            return windowInsets;
        });

        final int containerStart = binding.fragmentContainer.getPaddingStart();
        final int containerTop = binding.fragmentContainer.getPaddingTop();
        final int containerEnd = binding.fragmentContainer.getPaddingEnd();
        final int containerBottom = binding.fragmentContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer, (view, windowInsets) -> {
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

    private int resolveStartDestination(Intent intent) {
        if (intent == null) {
            return R.id.nav_home;
        }
        int destination = intent.getIntExtra(EXTRA_START_DESTINATION, R.id.nav_home);
        if (destination == R.id.nav_pair
                || destination == R.id.nav_devices
                || destination == R.id.nav_history) {
            return destination;
        }
        return R.id.nav_home;
    }

    public void openDebugScreen() {
        startActivity(new Intent(this, DebugActivity.class));
    }
}
