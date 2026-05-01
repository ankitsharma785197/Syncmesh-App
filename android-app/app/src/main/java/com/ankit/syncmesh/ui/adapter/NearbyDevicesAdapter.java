package com.ankit.syncmesh.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.databinding.ItemNearbyDeviceBinding;
import com.ankit.syncmesh.model.DiscoveredDevice;
import com.ankit.syncmesh.util.DisplayUtils;

import java.util.ArrayList;
import java.util.List;

public class NearbyDevicesAdapter extends RecyclerView.Adapter<NearbyDevicesAdapter.ViewHolder> {
    public interface Listener {
        void onUseDevice(DiscoveredDevice device);
    }

    private final Listener listener;
    private final List<DiscoveredDevice> items = new ArrayList<>();

    public NearbyDevicesAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<DiscoveredDevice> devices) {
        items.clear();
        if (devices != null) {
            items.addAll(devices);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNearbyDeviceBinding binding = ItemNearbyDeviceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final DiscoveredDevice device = items.get(position);
        holder.binding.textDeviceName.setText(DisplayUtils.safe(device.deviceName, "Unknown device"));
        holder.binding.textDeviceEndpoint.setText(DisplayUtils.formatEndpoint(device.ipAddress, device.port));
        holder.binding.textDeviceSeen.setText("Seen " + DisplayUtils.formatRelativeTime(device.lastSeen));
        holder.binding.buttonUse.setOnClickListener(v -> listener.onUseDevice(device));
        holder.binding.getRoot().setOnClickListener(v -> listener.onUseDevice(device));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemNearbyDeviceBinding binding;

        ViewHolder(ItemNearbyDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
