package com.ankit.syncmesh.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ankit.syncmesh.data.TransferRepository;
import com.ankit.syncmesh.databinding.ActivityTransferHistoryBinding;
import com.ankit.syncmesh.ui.adapter.TransferHistoryAdapter;

/** Searchable list of past file transfers (date, peer, status, size, duration). */
public class TransferHistoryActivity extends AppCompatActivity {

    private ActivityTransferHistoryBinding binding;
    private TransferRepository transferRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityTransferHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        applyInsets();

        transferRepository = TransferRepository.getInstance(this);

        TransferHistoryAdapter adapter = new TransferHistoryAdapter();
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHistory.setAdapter(adapter);

        transferRepository.getHistoryLiveData().observe(this, records -> {
            adapter.submitList(records);
            boolean empty = records == null || records.isEmpty();
            binding.textHistoryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                transferRepository.search(editable == null ? "" : editable.toString());
            }
        });

        transferRepository.search("");
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }
}
