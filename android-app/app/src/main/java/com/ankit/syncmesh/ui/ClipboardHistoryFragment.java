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
import com.ankit.syncmesh.databinding.FragmentClipboardHistoryBinding;
import com.ankit.syncmesh.model.ClipboardEntry;
import com.ankit.syncmesh.ui.adapter.ClipboardHistoryAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ClipboardHistoryFragment extends Fragment implements ClipboardHistoryAdapter.Listener {
    private FragmentClipboardHistoryBinding binding;
    private AppRepository repository;
    private ClipboardHistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentClipboardHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());

        adapter = new ClipboardHistoryAdapter(this);
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerHistory.setAdapter(adapter);
        binding.buttonClearHistory.setOnClickListener(v -> confirmClearHistory());

        repository.getClipboardHistoryLiveData().observe(getViewLifecycleOwner(), entries -> {
            adapter.submitList(entries);
            binding.textHistoryEmpty.setVisibility(entries == null || entries.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onCopyEntry(ClipboardEntry entry) {
        ClipboardManager manager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("SyncMesh History", entry.text));
            Toast.makeText(requireContext(), R.string.toast_text_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_clear_history_title)
                .setMessage(R.string.dialog_clear_history_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_clear_history, (dialog, which) -> {
                    repository.clearClipboardHistory();
                    Toast.makeText(requireContext(), R.string.toast_history_cleared, Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
