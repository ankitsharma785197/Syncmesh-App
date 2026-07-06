package com.ankit.syncmesh.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.model.TransferFileInfo;
import com.ankit.syncmesh.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;

/** Simple name+size list used in the send confirmation and the incoming dialog. */
public class TransferFilesAdapter extends RecyclerView.Adapter<TransferFilesAdapter.ViewHolder> {

    private final ArrayList<TransferFileInfo> files = new ArrayList<>();

    public void submitList(List<TransferFileInfo> newFiles) {
        files.clear();
        if (newFiles != null) {
            files.addAll(newFiles);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transfer_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TransferFileInfo file = files.get(position);
        holder.name.setText(file.name);
        holder.size.setText(DisplayUtils.formatBytes(file.size));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView size;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_file_name);
            size = itemView.findViewById(R.id.text_file_size);
        }
    }
}
