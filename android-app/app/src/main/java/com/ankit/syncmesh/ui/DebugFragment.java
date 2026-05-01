package com.ankit.syncmesh.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import com.ankit.syncmesh.databinding.FragmentDebugBinding;
import com.ankit.syncmesh.model.ServiceSnapshot;
import com.ankit.syncmesh.sync.SyncCoordinator;
import com.ankit.syncmesh.ui.adapter.LogAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DebugFragment extends Fragment {
    private FragmentDebugBinding binding;
    private AppRepository repository;
    private SyncCoordinator coordinator;
    private LogAdapter logAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDebugBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        coordinator = SyncCoordinator.getInstance(requireContext());

        logAdapter = new LogAdapter();
        binding.recyclerLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerLogs.setAdapter(logAdapter);

        binding.buttonClearLogs.setOnClickListener(v -> confirmClearLogs());
        binding.buttonCopyLogs.setOnClickListener(v -> copyLogs());

        repository.getServiceSnapshotLiveData().observe(getViewLifecycleOwner(), this::bindSnapshot);
        repository.getLogsLiveData().observe(getViewLifecycleOwner(), logs -> {
            logAdapter.submitList(logs);
            binding.textLogsEmpty.setVisibility(logs == null || logs.isEmpty() ? View.VISIBLE : View.GONE);
        });
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

    private void bindSnapshot(ServiceSnapshot snapshot) {
        if (binding == null || snapshot == null) {
            return;
        }
        binding.textDebugServiceStatus.setText(snapshot.serviceRunning
                ? getString(R.string.label_running)
                : getString(R.string.label_stopped));
        binding.textDebugTcpStatus.setText(snapshot.tcpRunning
                ? getString(R.string.label_running)
                : getString(R.string.label_stopped));
        binding.textDebugUdpStatus.setText(snapshot.udpRunning
                ? getString(R.string.label_running)
                : getString(R.string.label_stopped));
        binding.textDebugIp.setText(snapshot.localIpAddress == null
                ? getString(R.string.status_not_connected)
                : snapshot.localIpAddress);
    }

    private void confirmClearLogs() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_clear_logs_title)
                .setMessage(R.string.dialog_clear_logs_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_clear_logs, (dialog, which) -> {
                    repository.clearLogs();
                    Toast.makeText(requireContext(), R.string.toast_logs_cleared, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void copyLogs() {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("SyncMesh Logs", repository.buildLogExportText()));
            Toast.makeText(requireContext(), R.string.toast_logs_copied, Toast.LENGTH_SHORT).show();
        }
    }
}
