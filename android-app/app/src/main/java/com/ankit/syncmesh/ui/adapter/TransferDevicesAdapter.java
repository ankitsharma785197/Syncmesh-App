package com.ankit.syncmesh.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.util.DisplayUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Single-select paired-device list for the send flow. */
public class TransferDevicesAdapter
        extends RecyclerView.Adapter<TransferDevicesAdapter.ViewHolder> {

    public interface Listener {
        void onDeviceSelected(@Nullable PairedDevice device);
    }

    private final ArrayList<PairedDevice> devices = new ArrayList<>();
    private final Listener listener;
    private String selectedDeviceId;

    public TransferDevicesAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<PairedDevice> newDevices) {
        devices.clear();
        if (newDevices != null) {
            devices.addAll(newDevices);
        }
        if (selectedDeviceId != null && findById(selectedDeviceId) == null) {
            selectedDeviceId = null;
            listener.onDeviceSelected(null);
        }
        notifyDataSetChanged();
    }

    @Nullable
    public PairedDevice getSelectedDevice() {
        return findById(selectedDeviceId);
    }

    @Nullable
    private PairedDevice findById(@Nullable String deviceId) {
        if (deviceId == null) {
            return null;
        }
        for (PairedDevice device : devices) {
            if (deviceId.equals(device.deviceId)) {
                return device;
            }
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transfer_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PairedDevice device = devices.get(position);
        boolean selected = device.deviceId != null && device.deviceId.equals(selectedDeviceId);
        holder.name.setText(DisplayUtils.safe(device.deviceName, device.deviceId));
        holder.endpoint.setText(DisplayUtils.formatEndpoint(device.ipAddress, device.port));
        holder.check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        holder.card.setStrokeColor(selected
                ? com.google.android.material.color.MaterialColors.getColor(
                holder.card, com.google.android.material.R.attr.colorPrimary)
                : android.graphics.Color.TRANSPARENT);
        holder.itemView.setOnClickListener(v -> {
            selectedDeviceId = device.deviceId;
            notifyDataSetChanged();
            listener.onDeviceSelected(device);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView name;
        final TextView endpoint;
        final ImageView check;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_device);
            name = itemView.findViewById(R.id.text_device_name);
            endpoint = itemView.findViewById(R.id.text_device_endpoint);
            check = itemView.findViewById(R.id.image_selected);
        }
    }
}
