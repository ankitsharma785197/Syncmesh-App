package com.ankit.syncmesh.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ActivityIncomingTransferBinding;
import com.ankit.syncmesh.transfer.FileTransferManager;
import com.ankit.syncmesh.transfer.TransferState;
import com.ankit.syncmesh.ui.adapter.TransferFilesAdapter;
import com.ankit.syncmesh.util.DisplayUtils;

/**
 * Modal incoming-transfer request. Shows sender, per-file name/size and totals; the user
 * must explicitly Accept before any bytes are received. Reject declines; Cancel just closes
 * this screen (the notification stays actionable until the request times out).
 */
public class IncomingTransferActivity extends AppCompatActivity {

    private ActivityIncomingTransferBinding binding;
    private FileTransferManager transferManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityIncomingTransferBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        transferManager = FileTransferManager.getInstance(this);

        TransferFilesAdapter adapter = new TransferFilesAdapter();
        binding.recyclerIncomingFiles.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerIncomingFiles.setAdapter(adapter);

        binding.buttonIncomingAccept.setOnClickListener(v -> {
            transferManager.acceptIncoming();
            // Hand off to the progress screen.
            startActivity(new android.content.Intent(this, FileTransferActivity.class));
            finish();
        });
        binding.buttonIncomingReject.setOnClickListener(v -> {
            transferManager.rejectIncoming();
            finish();
        });
        binding.buttonIncomingCancel.setOnClickListener(v -> finish());

        transferManager.getTransferStateLiveData().observe(this, state -> {
            if (state == null || state.phase != TransferState.Phase.INCOMING_REQUEST) {
                // Decided elsewhere (notification action) or timed out.
                if (!isFinishing()) {
                    finish();
                }
                return;
            }
            binding.textIncomingSender.setText(getString(R.string.transfer_incoming_from,
                    DisplayUtils.safe(state.peerDeviceName, state.peerDeviceId)));
            adapter.submitList(state.files);
            binding.textIncomingTotal.setText(getString(R.string.transfer_selected_summary,
                    state.files.size(), DisplayUtils.formatBytes(state.totalSize)));
        });
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }
}
