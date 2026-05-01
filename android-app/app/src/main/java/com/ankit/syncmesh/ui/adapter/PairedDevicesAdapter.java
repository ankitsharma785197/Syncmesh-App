package com.ankit.syncmesh.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.databinding.ItemPairedDeviceBinding;
import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.util.DisplayUtils;
import com.ankit.syncmesh.util.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class PairedDevicesAdapter extends RecyclerView.Adapter<PairedDevicesAdapter.ViewHolder> {
    public interface Listener {
        void onPingDevice(PairedDevice device);

        void onRemoveDevice(PairedDevice device);
    }

    private final Listener listener;
    private final List<PairedDevice> items = new ArrayList<>();

    public PairedDevicesAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<PairedDevice> devices) {
        items.clear();
        if (devices != null) {
            items.addAll(devices);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPairedDeviceBinding binding = ItemPairedDeviceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final PairedDevice device = items.get(position);
        holder.binding.textDeviceName.setText(DisplayUtils.safe(device.deviceName, "Unknown device"));
        holder.binding.textDeviceId.setText("ID " + NetworkUtils.shortenDeviceId(device.deviceId));
        holder.binding.textDeviceAddress.setText(DisplayUtils.formatEndpoint(device.ipAddress, device.port));
        holder.binding.textLastSeen.setText("Last seen " + DisplayUtils.formatRelativeTime(device.lastSeen));
        boolean hasError = device.lastError != null && !device.lastError.trim().isEmpty();
        holder.binding.textLastError.setText(hasError ? device.lastError : "No recorded errors");
        holder.binding.textLastError.setTextColor(ContextCompat.getColor(
                holder.binding.getRoot().getContext(),
                hasError ? R.color.syncmesh_error : R.color.syncmesh_text_secondary));
        holder.binding.buttonPing.setOnClickListener(v -> listener.onPingDevice(device));
        holder.binding.buttonRemove.setOnClickListener(v -> listener.onRemoveDevice(device));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemPairedDeviceBinding binding;

        ViewHolder(ItemPairedDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
