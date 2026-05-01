package com.ankit.syncmesh.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ItemLogBinding;
import com.ankit.syncmesh.model.LogEntry;
import com.ankit.syncmesh.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {
    private final List<LogEntry> items = new ArrayList<>();

    public void submitList(List<LogEntry> entries) {
        items.clear();
        if (entries != null) {
            items.addAll(entries);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLogBinding binding = ItemLogBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogEntry entry = items.get(position);
        holder.binding.textLevel.setText(entry.level);
        holder.binding.textTag.setText(entry.tag);
        holder.binding.textTime.setText(DisplayUtils.formatDateTime(entry.createdAt));
        holder.binding.textMessage.setText(entry.message);

        int color;
        if ("ERROR".equals(entry.level)) {
            color = R.color.syncmesh_error;
        } else if ("WARN".equals(entry.level)) {
            color = R.color.syncmesh_warning;
        } else if ("INFO".equals(entry.level)) {
            color = R.color.syncmesh_success;
        } else {
            color = R.color.syncmesh_primary;
        }
        holder.binding.textLevel.setTextColor(ContextCompat.getColor(holder.binding.getRoot().getContext(), color));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemLogBinding binding;

        ViewHolder(ItemLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
