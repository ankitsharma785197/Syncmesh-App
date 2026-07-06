package com.ankit.syncmesh.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.databinding.FragmentHomeBinding;
import com.ankit.syncmesh.model.ServiceSnapshot;
import com.ankit.syncmesh.sync.SyncCoordinator;
import com.ankit.syncmesh.sync.SyncForegroundService;
import com.ankit.syncmesh.util.NetworkUtils;
import com.ankit.syncmesh.util.PermissionHelper;

import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.settings.SettingsActivity;

public class HomeFragment extends Fragment {
    private static final int RC_NOTIFICATIONS = 501;

    private FragmentHomeBinding binding;
    private AppRepository repository;
    private SyncCoordinator coordinator;
    private boolean pendingStartAfterNotifications;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        coordinator = SyncCoordinator.getInstance(requireContext());

        binding.buttonStartStop.setOnClickListener(v -> toggleService());
        binding.buttonEnableKeyboard.setOnClickListener(v -> openInputMethodSettings());
        binding.buttonChooseKeyboard.setOnClickListener(v -> showInputMethodPicker());
        binding.buttonKeyboardSettings.setOnClickListener(v -> openKeyboardSettings());
        binding.switchKeyboardAutoSend.setOnCheckedChangeListener(this::onKeyboardAutoSendChanged);
        binding.buttonGoPair.setOnClickListener(v -> navigateTo(R.id.nav_pair));
        binding.buttonGoDevices.setOnClickListener(v -> navigateTo(R.id.nav_devices));
        binding.buttonGoHistory.setOnClickListener(v -> navigateTo(R.id.nav_history));
        binding.buttonGoTransfer.setOnClickListener(v -> navigateTo(R.id.nav_transfer));
        setupVersionEasterEgg();

        repository.getServiceSnapshotLiveData().observe(getViewLifecycleOwner(), this::bindSnapshot);
        // Read-only observers used purely to present existing data on the home screen.
        repository.getNearbyDevicesLiveData().observe(getViewLifecycleOwner(), this::bindNearbyCount);
        repository.getClipboardHistoryLiveData().observe(getViewLifecycleOwner(), this::bindRecentClip);
        binding.cardRecentClip.setOnClickListener(v -> navigateTo(R.id.nav_history));
        coordinator.refreshSnapshot();
        updateKeyboardCard();
    }

    private void bindNearbyCount(java.util.List<com.ankit.syncmesh.model.DiscoveredDevice> devices) {
        if (binding == null) {
            return;
        }
        binding.textNearbyCount.setText(String.valueOf(devices == null ? 0 : devices.size()));
    }

    private void bindRecentClip(java.util.List<com.ankit.syncmesh.model.ClipboardEntry> entries) {
        if (binding == null) {
            return;
        }
        if (entries == null || entries.isEmpty()) {
            binding.textRecentClipValue.setText(getString(R.string.home_recent_clip_empty));
            binding.textRecentClipMeta.setText(getString(R.string.tab_history));
            return;
        }
        com.ankit.syncmesh.model.ClipboardEntry entry = entries.get(0);
        binding.textRecentClipValue.setText(
                com.ankit.syncmesh.util.DisplayUtils.safe(entry.text, getString(R.string.home_recent_clip_empty)));
        boolean isLocal = "local".equals(entry.direction);
        String origin = isLocal
                ? getString(R.string.status_local)
                : com.ankit.syncmesh.util.DisplayUtils.safe(entry.sourceDeviceName, getString(R.string.status_remote));
        binding.textRecentClipMeta.setText(origin + " · "
                + com.ankit.syncmesh.util.DisplayUtils.formatRelativeTime(entry.createdAt));
    }

    @Override
    public void onResume() {
        super.onResume();
        updateKeyboardCard();
        coordinator.refreshSnapshot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void bindSnapshot(ServiceSnapshot snapshot) {
        if (binding == null || snapshot == null) {
            return;
        }
        boolean serviceRunning = snapshot.serviceRunning;
        binding.textServiceState.setText(serviceRunning
                ? getString(R.string.label_running)
                : getString(R.string.label_stopped));
        binding.textServiceState.setBackgroundResource(serviceRunning
                ? R.drawable.bg_status_badge_active
                : R.drawable.bg_status_badge_idle);
        binding.textServiceState.setTextColor(ContextCompat.getColor(requireContext(),
                serviceRunning ? R.color.syncmesh_primary : R.color.syncmesh_text_secondary));
        binding.textCurrentIp.setText(snapshot.localIpAddress == null
                ? getString(R.string.status_not_connected)
                : snapshot.localIpAddress);
        binding.textDeviceId.setText(snapshot.deviceIdShort);
        binding.textPairingCode.setText(snapshot.pairingCode);
        binding.textPairedCount.setText(String.valueOf(snapshot.pairedDeviceCount));
        binding.buttonStartStop.setText(serviceRunning
                ? R.string.action_stop_sync
                : R.string.action_start_sync);
        binding.textServiceDetails.setText(serviceRunning
                ? R.string.home_service_running_detail
                : R.string.home_service_stopped_detail);
    }

    private void updateKeyboardCard() {
        if (binding == null) {
            return;
        }
        boolean keyboardEnabled = NetworkUtils.isInputMethodEnabled(
                requireContext(), LatinIME.class);
        boolean keyboardSelected = keyboardEnabled && NetworkUtils.isInputMethodSelected(
                requireContext(), LatinIME.class);
        binding.textKeyboardStatus.setText(keyboardEnabled
                ? (keyboardSelected ? getString(R.string.label_selected) : getString(R.string.label_enabled))
                : getString(R.string.label_not_enabled));
        binding.textKeyboardStatus.setBackgroundResource(keyboardEnabled
                ? R.drawable.bg_status_badge_active
                : R.drawable.bg_status_badge_idle);
        binding.textKeyboardStatus.setTextColor(ContextCompat.getColor(requireContext(),
                keyboardEnabled ? R.color.syncmesh_primary : R.color.syncmesh_text_secondary));
        boolean autoSendEnabled = repository.getPreferences().isKeyboardAutoSendEnabled();
        binding.switchKeyboardAutoSend.setOnCheckedChangeListener(null);
        binding.switchKeyboardAutoSend.setChecked(autoSendEnabled);
        binding.switchKeyboardAutoSend.setOnCheckedChangeListener(this::onKeyboardAutoSendChanged);
        binding.textKeyboardAutoSendValue.setText(autoSendEnabled
                ? R.string.label_on
                : R.string.label_off);
    }

    private void toggleService() {
        if (coordinator.isRuntimeRunning()) {
            requireContext().startService(SyncForegroundService.createStopIntent(requireContext()));
            Toast.makeText(requireContext(), R.string.toast_service_stopped, Toast.LENGTH_SHORT).show();
        } else {
            if (!PermissionHelper.isNotificationPermissionGranted(requireContext())
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pendingStartAfterNotifications = true;
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIFICATIONS);
                return;
            }
            startSyncService();
        }
    }

    private void openInputMethodSettings() {
        startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
    }

    private void showInputMethodPicker() {
        InputMethodManager inputMethodManager = (InputMethodManager) requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null) {
            Toast.makeText(requireContext(), R.string.toast_keyboard_picker_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        inputMethodManager.showInputMethodPicker();
    }

    private void openKeyboardSettings() {
        startActivity(new Intent(requireContext(), SettingsActivity.class));
    }

    private void startSyncService() {
        ContextCompat.startForegroundService(requireContext(),
                SyncForegroundService.createStartIntent(requireContext()));
        Toast.makeText(requireContext(), R.string.toast_service_started, Toast.LENGTH_SHORT).show();
    }

    private void onKeyboardAutoSendChanged(CompoundButton buttonView, boolean isChecked) {
        repository.getPreferences().setKeyboardAutoSendEnabled(isChecked);
        if (binding != null) {
            binding.textKeyboardAutoSendValue.setText(isChecked
                    ? R.string.label_on
                    : R.string.label_off);
        }
    }

    private void navigateTo(int menuId) {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).navigateTo(menuId);
        }
    }

    // ---- Version footer + hidden debug easter egg ----
    private static final int DEBUG_UNLOCK_TAPS = 7;
    private int versionTapCount;
    private long lastVersionTapAt;

    private void setupVersionEasterEgg() {
        String version;
        try {
            version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception exception) {
            version = "";
        }
        binding.textAppVersion.setText(getString(R.string.home_version_format, version));
        binding.textAppVersion.setOnClickListener(v -> onVersionTapped());
    }

    private void onVersionTapped() {
        // Tapping the version only unlocks debug; it never opens the console (that lives in
        // the toolbar menu once unlocked).
        if (repository.getPreferences().isDebugUnlocked()) {
            return;
        }
        long now = System.currentTimeMillis();
        versionTapCount = (now - lastVersionTapAt < 1500) ? versionTapCount + 1 : 1;
        lastVersionTapAt = now;

        int remaining = DEBUG_UNLOCK_TAPS - versionTapCount;
        if (remaining <= 0) {
            repository.getPreferences().setDebugUnlocked(true);
            com.ankit.syncmesh.util.SyncLog.setPersistToDb(true);
            versionTapCount = 0;
            Toast.makeText(requireContext(), R.string.toast_debug_unlocked, Toast.LENGTH_LONG).show();
            requireActivity().invalidateOptionsMenu();
        } else if (remaining <= 3) {
            com.ankit.syncmesh.util.Toasts.brief(requireContext(),
                    getString(R.string.toast_debug_unlock_progress, remaining));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_NOTIFICATIONS) {
            boolean granted = PermissionHelper.isNotificationPermissionGranted(requireContext());
            if (pendingStartAfterNotifications && granted) {
                startSyncService();
            } else if (!granted) {
                Toast.makeText(requireContext(), R.string.toast_notifications_required,
                        Toast.LENGTH_SHORT).show();
            }
            pendingStartAfterNotifications = false;
        }
        updateKeyboardCard();
    }
}
