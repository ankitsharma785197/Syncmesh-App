package com.ankit.syncmesh.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

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
    private TourOverlayView tourOverlay;

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
            maybeShowOnboarding();
            // Redirect to the Play Store if a newer version is available (no-op off-Play).
            com.ankit.syncmesh.util.UpdateManager.checkForUpdate(this);
        }
    }

    private void maybeShowOnboarding() {
        boolean complete = com.ankit.syncmesh.data.AppRepository.getInstance(this)
                .getPreferences().isOnboardingComplete();
        if (!complete) {
            startActivity(new Intent(this, OnboardingActivity.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_top_app_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // The debug console stays hidden until unlocked via the version easter egg on Home.
        MenuItem debugItem = menu.findItem(R.id.action_debug);
        if (debugItem != null) {
            debugItem.setVisible(com.ankit.syncmesh.data.AppRepository.getInstance(this)
                    .getPreferences().isDebugUnlocked());
        }
        return super.onPrepareOptionsMenu(menu);
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
        if (item.getItemId() == R.id.action_theme) {
            showThemeChooser();
            return true;
        }
        if (item.getItemId() == R.id.action_tour) {
            startProductTour();
            return true;
        }
        if (item.getItemId() == R.id.action_check_updates) {
            com.ankit.syncmesh.util.UpdateManager.checkForUpdate(this, true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SyncCoordinator.getInstance(this).refreshSnapshot();
        maybeStartFirstRunTour();
        // Reflect any debug lock/unlock change made on another screen.
        invalidateOptionsMenu();
    }

    /** Auto-runs the guided tour exactly once, right after onboarding completes. */
    private void maybeStartFirstRunTour() {
        com.ankit.syncmesh.data.AppPreferences preferences =
                com.ankit.syncmesh.data.AppRepository.getInstance(this).getPreferences();
        if (preferences.isOnboardingComplete() && !preferences.isTourComplete()
                && tourOverlay == null) {
            startProductTour();
        }
    }

    /** One guided step: which tab to show, which control to spotlight, and the copy to display. */
    private static final class TourStep {
        final int navId;      // bottom-nav destination to switch to (0 = current)
        final int targetId;   // view id to spotlight
        final boolean inToolbar;
        final int titleRes;
        final int descRes;

        TourStep(int navId, int targetId, boolean inToolbar, int titleRes, int descRes) {
            this.navId = navId;
            this.targetId = targetId;
            this.inToolbar = inToolbar;
            this.titleRes = titleRes;
            this.descRes = descRes;
        }
    }

    private java.util.List<TourStep> buildTourSteps() {
        java.util.List<TourStep> steps = new java.util.ArrayList<>();
        // Home: how to start sync + keyboard setup
        steps.add(new TourStep(R.id.nav_home, R.id.button_start_stop, false,
                R.string.tour_sync_title, R.string.tour_sync_desc));
        steps.add(new TourStep(R.id.nav_home, R.id.button_enable_keyboard, false,
                R.string.tour_kb_enable_title, R.string.tour_kb_enable_desc));
        steps.add(new TourStep(R.id.nav_home, R.id.button_choose_keyboard, false,
                R.string.tour_kb_choose_title, R.string.tour_kb_choose_desc));
        // Pair: make QR, scan, connect manually
        steps.add(new TourStep(R.id.nav_pair, R.id.button_show_qr, false,
                R.string.tour_qr_show_title, R.string.tour_qr_show_desc));
        steps.add(new TourStep(R.id.nav_pair, R.id.button_scan_qr, false,
                R.string.tour_qr_scan_title, R.string.tour_qr_scan_desc));
        steps.add(new TourStep(R.id.nav_pair, R.id.button_pair_device, false,
                R.string.tour_connect_title, R.string.tour_connect_desc));
        // Devices + History + theme
        steps.add(new TourStep(R.id.nav_devices, R.id.nav_devices, false,
                R.string.tour_devices_title, R.string.tour_devices_desc));
        steps.add(new TourStep(R.id.nav_history, R.id.nav_history, false,
                R.string.tour_history_title, R.string.tour_history_desc));
        steps.add(new TourStep(R.id.nav_home, R.id.action_theme, true,
                R.string.tour_theme_title, R.string.tour_theme_desc));
        return steps;
    }

    /** Transfer sub-tour: spotlights the pick/choose/send controls on the Transfer tab. */
    private java.util.List<TourStep> buildTransferTourSteps() {
        java.util.List<TourStep> steps = new java.util.ArrayList<>();
        steps.add(new TourStep(R.id.nav_transfer, R.id.button_select_files, false,
                R.string.tour_transfer_pick_title, R.string.tour_transfer_pick_desc));
        steps.add(new TourStep(R.id.nav_transfer, R.id.recycler_devices, false,
                R.string.tour_transfer_device_title, R.string.tour_transfer_device_desc));
        steps.add(new TourStep(R.id.nav_transfer, R.id.button_send, false,
                R.string.tour_transfer_send_title, R.string.tour_transfer_send_desc));
        steps.add(new TourStep(R.id.nav_transfer, R.id.button_transfer_history, false,
                R.string.tour_transfer_history_title, R.string.tour_transfer_history_desc));
        return steps;
    }

    private void startProductTour() {
        startTour(buildTourSteps(), this::endProductTour);
    }

    /** Runs the transfer sub-tour once, the first time the Transfer tab is opened. */
    private void maybeStartTransferTour() {
        com.ankit.syncmesh.data.AppPreferences preferences =
                com.ankit.syncmesh.data.AppRepository.getInstance(this).getPreferences();
        if (!preferences.isTransferTourComplete() && tourOverlay == null) {
            startTour(buildTransferTourSteps(), () -> {
                preferences.setTransferTourComplete(true);
                endProductTour();
            });
        }
    }

    private void startTour(final java.util.List<TourStep> steps, final Runnable onFinished) {
        if (tourOverlay != null) {
            return;
        }
        final android.view.ViewGroup decor = (android.view.ViewGroup) getWindow().getDecorView();

        TourOverlayView overlay = new TourOverlayView(this, new TourOverlayView.Controller() {
            @Override
            public int stepCount() {
                return steps.size();
            }

            @Override
            public void prepareStep(int index, @NonNull TourOverlayView.ReadyCallback ready) {
                TourStep step = steps.get(index);
                // Switch tab if needed, then wait for layout before resolving the target view.
                if (step.navId != 0 && binding.bottomNavigation.getSelectedItemId() != step.navId
                        && !step.inToolbar) {
                    binding.bottomNavigation.setSelectedItemId(step.navId);
                }
                decor.postDelayed(() -> {
                    final View target;
                    boolean inFragment = false;
                    if (step.targetId == R.id.nav_home || step.targetId == R.id.nav_pair
                            || step.targetId == R.id.nav_devices || step.targetId == R.id.nav_history) {
                        target = binding.bottomNavigation.findViewById(step.targetId);
                    } else if (step.inToolbar) {
                        target = binding.toolbar.findViewById(step.targetId);
                    } else {
                        target = binding.fragmentContainer.findViewById(step.targetId);
                        inFragment = true;
                    }
                    // Buttons deeper in a scrolling screen (e.g. Enable/Choose keyboard) may be
                    // below the fold. Scroll them into view before spotlighting, since the dim
                    // overlay prevents the user from scrolling manually.
                    if (inFragment && target != null) {
                        scrollTargetIntoView(target);
                        decor.postDelayed(
                                () -> ready.onReady(target, step.titleRes, step.descRes), 120);
                    } else {
                        ready.onReady(target, step.titleRes, step.descRes);
                    }
                }, 260);
            }

            @Override
            public void onTourFinished(boolean completed) {
                onFinished.run();
            }
        });

        tourOverlay = overlay;
        decor.addView(overlay, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        // Delay start so the first fragment + toolbar menu are laid out.
        decor.postDelayed(() -> {
            if (tourOverlay != null) {
                tourOverlay.start();
            }
        }, 350);
    }

    /** Scrolls the nearest scrolling ancestor so {@code target} sits in the upper third. */
    private void scrollTargetIntoView(View target) {
        android.view.ViewParent parent = target.getParent();
        while (parent != null
                && !(parent instanceof androidx.core.widget.NestedScrollView)
                && !(parent instanceof android.widget.ScrollView)) {
            parent = parent.getParent();
        }
        if (!(parent instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup scroller = (android.view.ViewGroup) parent;
        int offset = 0;
        View v = target;
        while (v != null && v != scroller) {
            offset += v.getTop();
            android.view.ViewParent p = v.getParent();
            v = (p instanceof View) ? (View) p : null;
        }
        int destination = Math.max(0, offset - scroller.getHeight() / 3);
        scroller.scrollTo(0, destination);
    }

    private void endProductTour() {
        com.ankit.syncmesh.data.AppRepository.getInstance(this)
                .getPreferences().setTourComplete(true);
        if (tourOverlay != null) {
            ((android.view.ViewGroup) getWindow().getDecorView()).removeView(tourOverlay);
            tourOverlay = null;
        }
    }

    private void showThemeChooser() {
        final int[] modes = {
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        };
        String[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        com.ankit.syncmesh.data.AppPreferences preferences =
                com.ankit.syncmesh.data.AppRepository.getInstance(this).getPreferences();
        int current = preferences.getThemeMode();
        int selected = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                selected = i;
                break;
            }
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_dialog_title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    preferences.setThemeMode(modes[which]);
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(modes[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment;
        String title = item.getTitle().toString();
        int itemId = item.getItemId();
        boolean transferTab = false;
        if (itemId == R.id.nav_pair) {
            fragment = new PairFragment();
        } else if (itemId == R.id.nav_devices) {
            fragment = new PairedDevicesFragment();
        } else if (itemId == R.id.nav_history) {
            fragment = new ClipboardHistoryFragment();
        } else if (itemId == R.id.nav_transfer) {
            fragment = new FileTransferFragment();
            transferTab = true;
        } else {
            fragment = new HomeFragment();
        }

        binding.toolbar.setTitle(title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        if (transferTab) {
            // Kick off the one-time transfer sub-tour after the fragment lays out.
            binding.getRoot().postDelayed(this::maybeStartTransferTour, 400);
        }
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
