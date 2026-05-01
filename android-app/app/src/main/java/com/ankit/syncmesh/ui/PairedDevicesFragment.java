package com.ankit.syncmesh.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.databinding.FragmentPairedDevicesBinding;
import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.sync.SyncCoordinator;
import com.ankit.syncmesh.ui.adapter.PairedDevicesAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PairedDevicesFragment extends Fragment implements PairedDevicesAdapter.Listener {
    private FragmentPairedDevicesBinding binding;
    private AppRepository repository;
    private SyncCoordinator coordinator;
    private PairedDevicesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPairedDevicesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        coordinator = SyncCoordinator.getInstance(requireContext());

        adapter = new PairedDevicesAdapter(this);
        binding.recyclerPairedDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerPairedDevices.setAdapter(adapter);

        repository.getPairedDevicesLiveData().observe(getViewLifecycleOwner(), devices -> {
            adapter.submitList(devices);
            binding.textDevicesEmpty.setVisibility(devices == null || devices.isEmpty() ? View.VISIBLE : View.GONE);
        });
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
    public void onPingDevice(PairedDevice device) {
        coordinator.pingDevice(device, (success, message) -> Toast.makeText(requireContext(),
                success
                        ? getString(R.string.toast_ping_success)
                        : (message == null ? getString(R.string.toast_ping_failed) : message),
                Toast.LENGTH_LONG).show());
    }

    @Override
    public void onRemoveDevice(PairedDevice device) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_remove_device_title)
                .setMessage(R.string.dialog_remove_device_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_remove, (dialog, which) -> {
                    repository.removePairedDevice(device.deviceId);
                    Toast.makeText(requireContext(), R.string.toast_device_removed, Toast.LENGTH_SHORT).show();
                    coordinator.refreshSnapshot();
                })
                .show();
    }
}
