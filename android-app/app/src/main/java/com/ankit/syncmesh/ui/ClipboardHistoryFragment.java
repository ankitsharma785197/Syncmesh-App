package com.ankit.syncmesh.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClipboardHistoryFragment extends Fragment implements ClipboardHistoryAdapter.Listener {
    private FragmentClipboardHistoryBinding binding;
    private AppRepository repository;
    private ClipboardHistoryAdapter adapter;

    // Presentation-only client-side view state over the already-loaded list.
    private final List<ClipboardEntry> allEntries = new ArrayList<>();
    private String searchQuery = "";
    private String directionFilter = "all";

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

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.getDefault());
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checkedId = binding.chipGroupFilter.getCheckedChipId();
            if (checkedId == R.id.chip_local) {
                directionFilter = "local";
            } else if (checkedId == R.id.chip_remote) {
                directionFilter = "remote";
            } else {
                directionFilter = "all";
            }
            applyFilters();
        });

        repository.getClipboardHistoryLiveData().observe(getViewLifecycleOwner(), entries -> {
            allEntries.clear();
            if (entries != null) {
                allEntries.addAll(entries);
            }
            applyFilters();
        });
    }

    private void applyFilters() {
        if (binding == null) {
            return;
        }
        List<ClipboardEntry> filtered = new ArrayList<>();
        for (ClipboardEntry entry : allEntries) {
            if (!"all".equals(directionFilter) && !directionFilter.equals(entry.direction)) {
                continue;
            }
            if (!searchQuery.isEmpty()) {
                String text = entry.text == null ? "" : entry.text.toLowerCase(Locale.getDefault());
                String source = entry.sourceDeviceName == null
                        ? "" : entry.sourceDeviceName.toLowerCase(Locale.getDefault());
                if (!text.contains(searchQuery) && !source.contains(searchQuery)) {
                    continue;
                }
            }
            filtered.add(entry);
        }
        adapter.submitList(filtered);

        boolean empty = filtered.isEmpty();
        binding.textHistoryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            boolean filtering = !searchQuery.isEmpty() || !"all".equals(directionFilter);
            binding.textHistoryEmpty.setText(filtering
                    ? R.string.empty_history_search
                    : R.string.empty_history);
        }
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
