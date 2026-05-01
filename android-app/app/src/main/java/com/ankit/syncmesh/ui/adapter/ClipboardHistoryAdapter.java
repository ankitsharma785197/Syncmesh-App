package com.ankit.syncmesh.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ItemClipboardHistoryBinding;
import com.ankit.syncmesh.model.ClipboardEntry;
import com.ankit.syncmesh.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;

public class ClipboardHistoryAdapter extends RecyclerView.Adapter<ClipboardHistoryAdapter.ViewHolder> {
    public interface Listener {
        void onCopyEntry(ClipboardEntry entry);
    }

    private final Listener listener;
    private final List<ClipboardEntry> items = new ArrayList<>();

    public ClipboardHistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<ClipboardEntry> entries) {
        items.clear();
        if (entries != null) {
            items.addAll(entries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemClipboardHistoryBinding binding = ItemClipboardHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ClipboardEntry entry = items.get(position);
        boolean isLocal = "local".equals(entry.direction);
        holder.binding.textSourceName.setText(DisplayUtils.safe(entry.sourceDeviceName, "Unknown source"));
        holder.binding.textDirection.setText(isLocal ? "Local" : "Remote");
        holder.binding.textDirection.setBackgroundResource(isLocal
                ? R.drawable.bg_chip_local
                : R.drawable.bg_chip_remote);
        holder.binding.textDirection.setTextColor(ContextCompat.getColor(
                holder.binding.getRoot().getContext(),
                isLocal ? R.color.syncmesh_primary : R.color.syncmesh_secondary));
        holder.binding.textTimestamp.setText(DisplayUtils.formatDateTime(entry.createdAt));
        holder.binding.textClipboardValue.setText(DisplayUtils.safe(entry.text, ""));
        holder.binding.getRoot().setOnClickListener(v -> listener.onCopyEntry(entry));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemClipboardHistoryBinding binding;

        ViewHolder(ItemClipboardHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
