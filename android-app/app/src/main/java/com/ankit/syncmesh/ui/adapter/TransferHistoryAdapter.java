package com.ankit.syncmesh.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.model.TransferRecord;
import com.ankit.syncmesh.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;

public class TransferHistoryAdapter
        extends RecyclerView.Adapter<TransferHistoryAdapter.ViewHolder> {

    private final ArrayList<TransferRecord> records = new ArrayList<>();

    public void submitList(List<TransferRecord> newRecords) {
        records.clear();
        if (newRecords != null) {
            records.addAll(newRecords);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transfer_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TransferRecord record = records.get(position);
        boolean sent = TransferRecord.DIRECTION_SENT.equals(record.direction);
        android.content.Context context = holder.itemView.getContext();

        holder.direction.setImageResource(sent ? R.drawable.ic_upload : R.drawable.ic_download);
        holder.peer.setText(context.getString(sent
                        ? R.string.transfer_notif_sent_done
                        : R.string.transfer_notif_received_done,
                DisplayUtils.safe(record.peerDeviceName, record.peerDeviceId)));
        holder.files.setText(record.fileNames == null
                ? "" : record.fileNames.replace('\n', ',').replace(",", ", "));

        String status;
        int statusColor;
        int statusBackground;
        switch (record.status == null ? "" : record.status) {
            case TransferRecord.STATUS_COMPLETED:
                status = context.getString(R.string.transfer_status_completed);
                statusColor = R.color.syncmesh_primary;
                statusBackground = R.drawable.bg_status_badge_active;
                break;
            case TransferRecord.STATUS_CANCELLED:
                status = context.getString(R.string.transfer_status_cancelled);
                statusColor = R.color.syncmesh_text_secondary;
                statusBackground = R.drawable.bg_status_badge_idle;
                break;
            case TransferRecord.STATUS_REJECTED:
                status = context.getString(R.string.transfer_action_reject);
                statusColor = R.color.syncmesh_text_secondary;
                statusBackground = R.drawable.bg_status_badge_idle;
                break;
            default:
                status = context.getString(R.string.transfer_status_failed);
                statusColor = R.color.syncmesh_error;
                statusBackground = R.drawable.bg_status_badge_idle;
                break;
        }
        holder.status.setText(status);
        holder.status.setTextColor(ContextCompat.getColor(context, statusColor));
        holder.status.setBackgroundResource(statusBackground);

        String duration = record.durationMs > 0
                ? DisplayUtils.formatEta(record.durationMs / 1000) : null;
        StringBuilder meta = new StringBuilder(DisplayUtils.formatDateTime(record.startedAt));
        meta.append(" · ").append(record.fileCount).append(' ')
                .append(context.getString(R.string.transfer_files_label));
        meta.append(" · ").append(DisplayUtils.formatBytes(record.totalSize));
        if (duration != null) {
            meta.append(" · ").append(duration);
        }
        holder.meta.setText(meta);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView direction;
        final TextView peer;
        final TextView files;
        final TextView status;
        final TextView meta;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            direction = itemView.findViewById(R.id.image_record_direction);
            peer = itemView.findViewById(R.id.text_record_peer);
            files = itemView.findViewById(R.id.text_record_files);
            status = itemView.findViewById(R.id.text_record_status);
            meta = itemView.findViewById(R.id.text_record_meta);
        }
    }
}
