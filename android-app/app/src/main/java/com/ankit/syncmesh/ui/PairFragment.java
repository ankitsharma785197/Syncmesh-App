package com.ankit.syncmesh.ui;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.databinding.DialogPairQrBinding;
import com.ankit.syncmesh.databinding.FragmentPairBinding;
import com.ankit.syncmesh.model.DiscoveredDevice;
import com.ankit.syncmesh.model.ServiceSnapshot;
import com.ankit.syncmesh.sync.SyncCoordinator;
import com.ankit.syncmesh.sync.TcpServer;
import com.ankit.syncmesh.ui.adapter.NearbyDevicesAdapter;
import com.ankit.syncmesh.util.DisplayUtils;
import com.ankit.syncmesh.util.NetworkUtils;
import com.ankit.syncmesh.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONObject;

import java.util.Collections;

public class PairFragment extends Fragment implements NearbyDevicesAdapter.Listener {
    private static final int RC_CAMERA = 601;

    private FragmentPairBinding binding;
    private AppRepository repository;
    private SyncCoordinator coordinator;
    private NearbyDevicesAdapter nearbyDevicesAdapter;
    private int nearbyDeviceCount;
    private boolean isServiceRunning;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPairBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        coordinator = SyncCoordinator.getInstance(requireContext());

        nearbyDevicesAdapter = new NearbyDevicesAdapter(this);
        binding.recyclerNearbyDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerNearbyDevices.setAdapter(nearbyDevicesAdapter);

        binding.editPort.setText(String.valueOf(TcpServer.PORT));
        binding.buttonShowQr.setOnClickListener(v -> showPairQr());
        binding.buttonScanQr.setOnClickListener(v -> startQrScanOrRequestPermission());
        binding.buttonPairDevice.setOnClickListener(v -> submitPairRequest());

        repository.getNearbyDevicesLiveData().observe(getViewLifecycleOwner(), devices -> {
            nearbyDeviceCount = devices == null ? 0 : devices.size();
            nearbyDevicesAdapter.submitList(devices);
            binding.textNearbyEmpty.setVisibility(nearbyDeviceCount == 0 ? View.VISIBLE : View.GONE);
            updateNearbyEmptyState();
        });

        repository.getServiceSnapshotLiveData().observe(getViewLifecycleOwner(), this::bindLocalDeviceCard);
        coordinator.refreshSnapshot();
    }

    @Override
    public void onResume() {
        super.onResume();
        coordinator.refreshSnapshot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onUseDevice(DiscoveredDevice device) {
        binding.editIp.setText(device.ipAddress);
        binding.editPort.setText(String.valueOf(device.port));
        binding.inputLayoutIp.setError(null);
        String pairingCode = binding.editCode.getText() == null
                ? ""
                : binding.editCode.getText().toString().trim();
        if (pairingCode.isEmpty()) {
            binding.editCode.requestFocus();
            Toast.makeText(requireContext(), R.string.toast_nearby_device_selected, Toast.LENGTH_SHORT).show();
        } else {
            submitPairRequest();
        }
    }

    private void bindLocalDeviceCard(ServiceSnapshot snapshot) {
        if (binding == null || snapshot == null) {
            return;
        }
        isServiceRunning = snapshot.serviceRunning;
        binding.textMyDeviceName.setText(repository.getLocalDeviceName());
        binding.textMyDeviceId.setText(getString(R.string.format_device_identity, snapshot.deviceIdShort));
        binding.textMyDeviceIp.setText(getString(R.string.format_device_ip,
                DisplayUtils.safe(snapshot.localIpAddress, getString(R.string.status_not_connected))));
        binding.textMyDeviceCode.setText(getString(R.string.format_pairing_code, snapshot.pairingCode));
        binding.textPairServiceStatus.setText(snapshot.serviceRunning
                ? R.string.pair_sync_running
                : R.string.pair_sync_stopped);
        binding.textPairServiceStatus.setBackgroundResource(snapshot.serviceRunning
                ? R.drawable.bg_status_badge_active
                : R.drawable.bg_status_badge_idle);
        binding.textPairServiceStatus.setTextColor(ContextCompat.getColor(requireContext(),
                snapshot.serviceRunning ? R.color.syncmesh_primary : R.color.syncmesh_text_secondary));
        binding.buttonShowQr.setEnabled(snapshot.serviceRunning);
        updateNearbyEmptyState();
    }

    private void showPairQr() {
        if (!isServiceRunning) {
            Toast.makeText(requireContext(), R.string.toast_show_qr_requires_sync, Toast.LENGTH_SHORT).show();
            return;
        }
        String ipAddress = NetworkUtils.getLocalIpv4Address();
        if (TextUtils.isEmpty(ipAddress)) {
            Toast.makeText(requireContext(), R.string.toast_network_required, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "syncmesh_pair_qr");
            payload.put("deviceId", repository.getLocalDeviceId());
            payload.put("deviceName", repository.getLocalDeviceName());
            payload.put("ipAddress", ipAddress);
            payload.put("port", TcpServer.PORT);
            payload.put("pairingCode", repository.getLocalPairingCode());

            BitMatrix matrix = new MultiFormatWriter().encode(
                    payload.toString(), BarcodeFormat.QR_CODE, 720, 720);
            Bitmap bitmap = new BarcodeEncoder().createBitmap(matrix);

            DialogPairQrBinding dialogBinding = DialogPairQrBinding.inflate(getLayoutInflater());
            dialogBinding.imageQr.setImageBitmap(bitmap);
            dialogBinding.textQrDeviceName.setText(repository.getLocalDeviceName());
            dialogBinding.textQrSummary.setText(
                    DisplayUtils.formatEndpoint(ipAddress, TcpServer.PORT) + " • Code " +
                            repository.getLocalPairingCode()
            );

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_qr_title)
                    .setView(dialogBinding.getRoot())
                    .setPositiveButton(R.string.action_close, null)
                    .show();
        } catch (Exception exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startQrScanOrRequestPermission() {
        if (!PermissionHelper.isCameraPermissionGranted(requireContext())) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, RC_CAMERA);
            return;
        }
        IntentIntegrator integrator = IntentIntegrator.forSupportFragment(this);
        integrator.setDesiredBarcodeFormats(Collections.singletonList("QR_CODE"));
        integrator.setCaptureActivity(QrScannerActivity.class);
        integrator.setPrompt(getString(R.string.action_scan_qr));
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    private void submitPairRequest() {
        String ipAddress = binding.editIp.getText() == null ? "" : binding.editIp.getText().toString().trim();
        String portValue = binding.editPort.getText() == null ? "" : binding.editPort.getText().toString().trim();
        String pairingCode = binding.editCode.getText() == null ? "" : binding.editCode.getText().toString().trim();

        if (ipAddress.isEmpty() || portValue.isEmpty() || pairingCode.isEmpty()) {
            Toast.makeText(requireContext(), R.string.toast_pair_fields_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(NetworkUtils.getLocalIpv4Address())) {
            Toast.makeText(requireContext(), R.string.toast_network_required, Toast.LENGTH_SHORT).show();
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            binding.inputLayoutPort.setError(getString(R.string.pair_hint_port));
            return;
        }
        binding.inputLayoutIp.setError(null);
        binding.inputLayoutPort.setError(null);
        binding.inputLayoutCode.setError(null);
        binding.buttonPairDevice.setEnabled(false);
        coordinator.sendPairRequest(ipAddress, port, pairingCode, (success, message) -> {
            binding.buttonPairDevice.setEnabled(true);
            Toast.makeText(requireContext(),
                    success
                            ? getString(R.string.toast_pair_success)
                            : (message == null ? getString(R.string.toast_pair_failed) : message),
                    Toast.LENGTH_LONG).show();
            if (!success && message != null) {
                if (message.toLowerCase().contains("pairing code")) {
                    binding.inputLayoutCode.setError(message);
                    binding.inputLayoutIp.setError(null);
                } else {
                    binding.inputLayoutIp.setError(message);
                    binding.inputLayoutCode.setError(null);
                }
            } else {
                binding.inputLayoutCode.setError(null);
                binding.inputLayoutIp.setError(null);
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).navigateTo(R.id.nav_devices);
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String contents = result.getContents();
            if (contents != null) {
                applyQrContents(contents);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void applyQrContents(String contents) {
        try {
            JSONObject payload = new JSONObject(contents);
            if (!"syncmesh_pair_qr".equals(payload.optString("type"))) {
                Toast.makeText(requireContext(), R.string.toast_pair_invalid_qr, Toast.LENGTH_SHORT).show();
                return;
            }
            binding.editIp.setText(payload.optString("ipAddress"));
            binding.editPort.setText(String.valueOf(payload.optInt("port", TcpServer.PORT)));
            binding.editCode.setText(payload.optString("pairingCode"));
            binding.inputLayoutCode.setError(null);
            Toast.makeText(requireContext(), R.string.toast_qr_autofilled, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(requireContext(), R.string.toast_pair_invalid_qr, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_CAMERA) {
            if (PermissionHelper.isCameraPermissionGranted(requireContext())) {
                startQrScanOrRequestPermission();
            } else {
                Toast.makeText(requireContext(), R.string.toast_qr_camera_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateNearbyEmptyState() {
        if (binding == null || nearbyDeviceCount > 0) {
            return;
        }
        binding.textNearbyEmpty.setText(isServiceRunning
                ? R.string.empty_nearby
                : R.string.empty_nearby_sync_stopped);
    }
}
