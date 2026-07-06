package com.ankit.syncmesh.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.databinding.FragmentFileTransferBinding;
import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.model.TransferFileInfo;
import com.ankit.syncmesh.transfer.FileTransferManager;
import com.ankit.syncmesh.transfer.TransferState;
import com.ankit.syncmesh.ui.adapter.TransferDevicesAdapter;
import com.ankit.syncmesh.ui.adapter.TransferFilesAdapter;
import com.ankit.syncmesh.util.DisplayUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * The transfer experience hosted by the bottom-nav Transfer tab (and by
 * {@link FileTransferActivity} for notification deep-links): pick files → confirm → choose a
 * paired device → live progress with pause/resume/cancel/retry. All state lives in
 * {@link FileTransferManager}, so the screen survives rotation and tab switches while a
 * background transfer keeps running under the foreground service.
 */
public class FileTransferFragment extends Fragment implements TransferDevicesAdapter.Listener {

    private FragmentFileTransferBinding binding;
    private FileTransferManager transferManager;
    private AppRepository repository;
    private TransferFilesAdapter filesAdapter;
    private TransferDevicesAdapter devicesAdapter;
    private ArrayList<TransferFileInfo> selectedFiles = new ArrayList<>();

    private final ActivityResultLauncher<String[]> pickFiles =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(),
                    this::onFilesPicked);

    private final ActivityResultLauncher<Uri> pickFolder =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
                    this::onFolderPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFileTransferBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        transferManager = FileTransferManager.getInstance(requireContext());
        repository = AppRepository.getInstance(requireContext());

        filesAdapter = new TransferFilesAdapter();
        binding.recyclerSelectedFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSelectedFiles.setAdapter(filesAdapter);

        devicesAdapter = new TransferDevicesAdapter(this);
        binding.recyclerDevices.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerDevices.setAdapter(devicesAdapter);

        binding.buttonSelectFiles.setOnClickListener(v -> pickFiles.launch(new String[]{"*/*"}));
        binding.buttonClearFiles.setOnClickListener(v -> {
            selectedFiles = new ArrayList<>();
            renderSelection();
        });
        binding.buttonSend.setOnClickListener(v -> startSend());
        binding.buttonTransferHistory.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), TransferHistoryActivity.class)));
        binding.buttonChangeLocation.setOnClickListener(v -> pickFolder.launch(null));
        binding.buttonPauseResume.setOnClickListener(v -> togglePause());
        binding.buttonCancel.setOnClickListener(v -> transferManager.cancel());
        binding.buttonRetry.setOnClickListener(v -> {
            if (!transferManager.retryLast()) {
                Toast.makeText(requireContext(), R.string.transfer_error_busy,
                        Toast.LENGTH_SHORT).show();
            }
        });
        binding.buttonDone.setOnClickListener(v -> {
            transferManager.resetIfFinished();
            selectedFiles = new ArrayList<>();
            renderSelection();
        });

        binding.buttonPairNow.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).navigateTo(R.id.nav_pair);
            }
        });

        repository.getPairedDevicesLiveData().observe(getViewLifecycleOwner(), devices -> {
            devicesAdapter.submitList(devices);
            boolean empty = devices == null || devices.isEmpty();
            binding.groupNoDevices.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerDevices.setVisibility(empty ? View.GONE : View.VISIBLE);
            updateSendEnabled();
        });
        transferManager.getTransferStateLiveData().observe(getViewLifecycleOwner(), this::render);
        renderSelection();
        updateSaveLocationLabel();
    }

    private void onFolderPicked(@Nullable Uri treeUri) {
        if (binding == null || treeUri == null) {
            return;
        }
        try {
            requireContext().getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            repository.getPreferences().setTransferSaveUri(treeUri.toString());
            updateSaveLocationLabel();
            Toast.makeText(requireContext(), R.string.transfer_location_updated,
                    Toast.LENGTH_SHORT).show();
        } catch (SecurityException security) {
            Toast.makeText(requireContext(), R.string.transfer_error_dialog_title,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSaveLocationLabel() {
        if (binding == null) {
            return;
        }
        String savedUri = repository.getPreferences().getTransferSaveUri();
        if (savedUri == null) {
            binding.textSaveLocation.setText(R.string.transfer_save_location_default);
            return;
        }
        String label = null;
        try {
            androidx.documentfile.provider.DocumentFile dir =
                    androidx.documentfile.provider.DocumentFile.fromTreeUri(
                            requireContext(), Uri.parse(savedUri));
            if (dir != null && dir.getName() != null) {
                label = dir.getName();
            }
        } catch (Exception ignored) {
            // fall through to the raw path below
        }
        if (label == null) {
            label = Uri.parse(savedUri).getLastPathSegment();
        }
        binding.textSaveLocation.setText(DisplayUtils.safe(label,
                getString(R.string.transfer_save_location_default)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDeviceSelected(@Nullable PairedDevice device) {
        updateSendEnabled();
    }

    private void onFilesPicked(List<Uri> uris) {
        if (binding == null || uris == null || uris.isEmpty()) {
            return;
        }
        try {
            selectedFiles = transferManager.prepareFiles(uris);
            renderSelection();
        } catch (IllegalArgumentException invalid) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.transfer_error_dialog_title)
                    .setMessage(invalid.getMessage())
                    .setPositiveButton(R.string.action_close, null)
                    .show();
        }
    }

    private void startSend() {
        PairedDevice device = devicesAdapter.getSelectedDevice();
        if (device == null || selectedFiles.isEmpty()) {
            return;
        }
        long total = 0L;
        for (TransferFileInfo file : selectedFiles) {
            total += file.size;
        }
        String summary = getString(R.string.transfer_selected_summary,
                selectedFiles.size(), DisplayUtils.formatBytes(total));
        final ArrayList<TransferFileInfo> files = selectedFiles;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.transfer_confirm_title)
                .setMessage(summary + "\n→ " + DisplayUtils.safe(device.deviceName, device.deviceId))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.transfer_send_button, (dialog, which) -> {
                    if (!transferManager.sendFiles(device, files)) {
                        Toast.makeText(requireContext(), R.string.transfer_error_busy,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void togglePause() {
        TransferState state = transferManager.getTransferStateLiveData().getValue();
        if (state != null && state.phase == TransferState.Phase.PAUSED) {
            transferManager.resume();
        } else {
            transferManager.pause();
        }
    }

    private void renderSelection() {
        if (binding == null) {
            return;
        }
        filesAdapter.submitList(selectedFiles);
        boolean hasFiles = !selectedFiles.isEmpty();
        binding.cardSelectedFiles.setVisibility(hasFiles ? View.VISIBLE : View.GONE);
        if (hasFiles) {
            long total = 0L;
            for (TransferFileInfo file : selectedFiles) {
                total += file.size;
            }
            binding.textSelectedSummary.setText(getString(R.string.transfer_selected_summary,
                    selectedFiles.size(), DisplayUtils.formatBytes(total)));
        }
        updateSendEnabled();
        TransferState state = transferManager.getTransferStateLiveData().getValue();
        if (state == null || state.phase == TransferState.Phase.IDLE) {
            showSetup(true);
        }
    }

    private void updateSendEnabled() {
        if (binding == null) {
            return;
        }
        binding.buttonSend.setEnabled(!selectedFiles.isEmpty()
                && devicesAdapter.getSelectedDevice() != null);
    }

    private void showSetup(boolean setup) {
        binding.groupSetup.setVisibility(setup ? View.VISIBLE : View.GONE);
        binding.groupProgress.setVisibility(setup ? View.GONE : View.VISIBLE);
    }

    private void render(TransferState state) {
        if (binding == null) {
            return;
        }
        if (state == null || state.phase == TransferState.Phase.IDLE
                || state.phase == TransferState.Phase.INCOMING_REQUEST) {
            showSetup(true);
            return;
        }
        showSetup(false);

        binding.imageDirection.setImageResource(
                state.outgoing ? R.drawable.ic_upload : R.drawable.ic_download);
        binding.textPeerName.setText(DisplayUtils.safe(state.peerDeviceName, state.peerDeviceId));
        binding.textFileCounter.setText(getString(R.string.transfer_file_counter,
                Math.min(state.completedFiles + 1, state.files.size()), state.files.size()));
        binding.textCurrentFile.setText(DisplayUtils.safe(state.currentFileName, "—"));

        int overallPercent = state.totalSize > 0
                ? (int) (state.transferredBytes * 100 / state.totalSize) : 0;
        int filePercent = state.currentFileSize > 0
                ? (int) (state.currentFileBytes * 100 / state.currentFileSize) : 0;
        binding.progressOverall.setProgress(overallPercent);
        binding.progressCurrentFile.setProgress(filePercent);
        binding.textOverallPercent.setText(overallPercent + "%");
        binding.textSpeed.setText(DisplayUtils.formatSpeed(state.speedBps));
        binding.textEta.setText(DisplayUtils.formatEta(state.etaSeconds));
        binding.textTransferred.setText(DisplayUtils.formatBytes(state.transferredBytes));
        binding.textRemaining.setText(DisplayUtils.formatBytes(
                Math.max(0, state.totalSize - state.transferredBytes)));

        String statusText;
        boolean terminal = false;
        switch (state.phase) {
            case CONNECTING:
                statusText = getString(R.string.transfer_status_connecting);
                break;
            case WAITING_FOR_ACCEPT:
                statusText = getString(R.string.transfer_status_waiting_accept);
                break;
            case PAUSED:
                statusText = getString(R.string.transfer_status_paused);
                break;
            case COMPLETED:
                statusText = getString(R.string.transfer_status_completed);
                terminal = true;
                break;
            case FAILED:
                statusText = getString(R.string.transfer_status_failed);
                terminal = true;
                break;
            case CANCELLED:
                statusText = getString(R.string.transfer_status_cancelled);
                terminal = true;
                break;
            default:
                statusText = getString(state.outgoing
                        ? R.string.transfer_status_transferring
                        : R.string.transfer_status_receiving);
                break;
        }
        binding.textTransferStatus.setText(statusText);
        binding.textConnection.setText(terminal
                ? statusText : getString(R.string.transfer_connection_ok));

        boolean paused = state.phase == TransferState.Phase.PAUSED;
        binding.buttonPauseResume.setText(paused
                ? R.string.transfer_action_resume : R.string.transfer_action_pause);
        boolean pausable = state.phase == TransferState.Phase.TRANSFERRING || paused;
        binding.buttonPauseResume.setEnabled(pausable);
        binding.buttonCancel.setEnabled(!terminal);
        binding.buttonPauseResume.setVisibility(terminal ? View.GONE : View.VISIBLE);
        binding.buttonCancel.setVisibility(terminal ? View.GONE : View.VISIBLE);
        binding.rowFinishedActions.setVisibility(terminal ? View.VISIBLE : View.GONE);
        binding.buttonRetry.setVisibility(terminal && state.outgoing
                && state.phase != TransferState.Phase.COMPLETED && transferManager.canRetry()
                ? View.VISIBLE : View.GONE);

        if (state.message != null && !state.message.isEmpty()) {
            binding.textTransferMessage.setText(state.message);
            binding.textTransferMessage.setVisibility(View.VISIBLE);
        } else if (state.phase == TransferState.Phase.WAITING_FOR_ACCEPT) {
            binding.textTransferMessage.setText(R.string.transfer_status_waiting_accept);
            binding.textTransferMessage.setVisibility(View.VISIBLE);
        } else {
            binding.textTransferMessage.setVisibility(View.GONE);
        }
    }
}
