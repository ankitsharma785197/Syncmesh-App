package com.ankit.syncmesh.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.model.ClipboardEntry;
import com.ankit.syncmesh.model.DiscoveredDevice;
import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.model.ServiceSnapshot;
import com.ankit.syncmesh.util.NetworkUtils;
import com.ankit.syncmesh.util.SyncLog;

import org.json.JSONObject;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncCoordinator {
    public interface ActionCallback {
        void onComplete(boolean success, String message);
    }

    private static final String TAG = "SyncCoordinator";
    private static volatile SyncCoordinator instance;

    private final Context appContext;
    private final AppRepository repository;
    private final ClipboardSyncManager clipboardSyncManager;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean runtimeRunning = new AtomicBoolean(false);
    private final ClipboardSyncManager.LocalClipboardListener localClipboardListener =
            new ClipboardSyncManager.LocalClipboardListener() {
                @Override
                public void onLocalClipboardChanged(String text, String eventId, long timestamp) {
                    handleLocalClipboardChanged(text, eventId, timestamp);
                }
            };

    private TcpServer tcpServer;
    private UdpDiscoveryManager udpDiscoveryManager;

    private SyncCoordinator(Context context) {
        appContext = context.getApplicationContext();
        repository = AppRepository.getInstance(appContext);
        clipboardSyncManager = ClipboardSyncManager.getInstance(appContext);
    }

    public static SyncCoordinator getInstance(Context context) {
        if (instance == null) {
            synchronized (SyncCoordinator.class) {
                if (instance == null) {
                    instance = new SyncCoordinator(context);
                }
            }
        }
        return instance;
    }

    public void startRuntime() {
        if (runtimeRunning.compareAndSet(false, true)) {
            tcpServer = new TcpServer(new TcpServer.MessageHandler() {
                @Override
                public String onMessage(String remoteAddress, JSONObject message) {
                    return handleIncomingMessage(remoteAddress, message);
                }
            });
            udpDiscoveryManager = new UdpDiscoveryManager(appContext,
                    new UdpDiscoveryManager.AnnouncementHandler() {
                        @Override
                        public void onAnnouncement(JSONObject announcement, InetAddress sourceAddress) {
                            handleDiscoveryAnnouncement(announcement, sourceAddress);
                        }
                    });
            tcpServer.start();
            udpDiscoveryManager.start();
            clipboardSyncManager.startForegroundMonitoring(localClipboardListener);
            SyncLog.i(TAG, "Sync runtime started");
        }
        refreshSnapshot();
    }

    public void stopRuntime() {
        if (runtimeRunning.compareAndSet(true, false)) {
            clipboardSyncManager.stopForegroundMonitoring();
            if (tcpServer != null) {
                tcpServer.stop();
                tcpServer = null;
            }
            if (udpDiscoveryManager != null) {
                udpDiscoveryManager.stop();
                udpDiscoveryManager = null;
            }
            repository.clearNearbyDevices();
            SyncLog.i(TAG, "Sync runtime stopped");
        }
        refreshSnapshot();
    }

    public void startAccessibilityBridge() {
        clipboardSyncManager.startAccessibilityMonitoring(localClipboardListener);
        refreshSnapshot();
    }

    public void stopAccessibilityBridge() {
        clipboardSyncManager.stopAccessibilityMonitoring();
        refreshSnapshot();
    }

    public void pollAccessibilityClipboard() {
        clipboardSyncManager.pollClipboardNow();
    }

    public boolean isRuntimeRunning() {
        return runtimeRunning.get();
    }

    public void refreshSnapshot() {
        ServiceSnapshot snapshot = repository.buildDefaultSnapshot();
        snapshot.serviceRunning = runtimeRunning.get();
        snapshot.tcpRunning = tcpServer != null && tcpServer.isRunning();
        snapshot.udpRunning = udpDiscoveryManager != null && udpDiscoveryManager.isRunning();
        snapshot.accessibilityEnabled = false;
        repository.updateServiceSnapshot(snapshot);
    }

    public void sendPairRequest(final String ipAddress, final int port, final String pairingCode,
                                final ActionCallback callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String localIp = ensureLocalIp();
                    JSONObject payload = new JSONObject();
                    String requestId = java.util.UUID.randomUUID().toString();
                    payload.put("type", "pair_request");
                    payload.put("requestId", requestId);
                    payload.put("fromDeviceId", repository.getLocalDeviceId());
                    payload.put("fromDeviceName", repository.getLocalDeviceName());
                    payload.put("ipAddress", localIp);
                    payload.put("port", TcpServer.PORT);
                    payload.put("pairingCode", pairingCode);
                    payload.put("timestamp", System.currentTimeMillis());

                    JSONObject response = new TcpClient().sendMessage(ipAddress, port, payload, true);
                    if (response == null) {
                        throw new IllegalStateException(appContext.getString(
                                R.string.error_no_pair_response, ipAddress, port));
                    }
                    boolean accepted = response.optBoolean("accepted", false);
                    if (!accepted) {
                        throw new IllegalStateException(response.optString("message", "Pairing rejected"));
                    }

                    PairedDevice device = new PairedDevice();
                    device.deviceId = response.optString("fromDeviceId");
                    device.deviceName = response.optString("fromDeviceName");
                    device.ipAddress = response.optString("ipAddress", ipAddress);
                    device.port = response.optInt("port", port);
                    device.transport = "wifi";
                    device.pairedAt = System.currentTimeMillis();
                    device.lastSeen = System.currentTimeMillis();
                    device.lastError = null;
                    repository.upsertPairedDevice(device);
                    repository.updateDeviceLastError(device.deviceId, null);
                    postCallback(callback, true, "Pairing successful");
                    refreshSnapshot();
                } catch (Exception exception) {
                    SyncLog.e(TAG, "Pair request failed", exception);
                    postCallback(callback, false, toUserFacingNetworkError(exception, ipAddress, port, "pair request"));
                }
            }
        });
    }

    public void pingDevice(final PairedDevice device, final ActionCallback callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put("type", "ping");
                    payload.put("requestId", java.util.UUID.randomUUID().toString());
                    payload.put("fromDeviceId", repository.getLocalDeviceId());
                    payload.put("fromDeviceName", repository.getLocalDeviceName());
                    payload.put("timestamp", System.currentTimeMillis());

                    JSONObject response = new TcpClient().sendMessage(device.ipAddress, device.port, payload, true);
                    if (response == null || !"pong".equals(response.optString("type"))) {
                        throw new IllegalStateException("No pong received");
                    }

                    repository.updateDeviceLastSeen(device.deviceId, System.currentTimeMillis());
                    repository.updateDeviceLastError(device.deviceId, null);
                    postCallback(callback, true, "Ping successful");
                    refreshSnapshot();
                } catch (Exception exception) {
                    String userFacingMessage = toUserFacingNetworkError(
                            exception, device.ipAddress, device.port, device.deviceName);
                    repository.updateDeviceLastError(device.deviceId, userFacingMessage);
                    SyncLog.e(TAG, "Ping failed for " + device.deviceId, exception);
                    postCallback(callback, false, userFacingMessage);
                }
            }
        });
    }

    public void sendManualClipboardText(final String text, final ActionCallback callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (text == null || text.trim().isEmpty()) {
                    postCallback(callback, false, appContext.getString(R.string.keyboard_status_no_clipboard));
                    return;
                }

                String eventId = UUID.randomUUID().toString();
                long timestamp = System.currentTimeMillis();
                saveLocalClipboardEntry(text, eventId, timestamp);
                clipboardSyncManager.markRecentEvent(eventId);

                ArrayList<PairedDevice> devices = repository.getPairedDevices();
                if (devices.isEmpty()) {
                    postCallback(callback, false, appContext.getString(R.string.error_no_paired_devices));
                    return;
                }

                JSONObject payload;
                try {
                    payload = buildClipboardPayload(text, eventId, timestamp);
                } catch (Exception exception) {
                    SyncLog.e(TAG, "Failed to build manual clipboard payload", exception);
                    postCallback(callback, false, appContext.getString(R.string.keyboard_status_send_failed));
                    return;
                }

                int successCount = 0;
                String lastError = appContext.getString(R.string.keyboard_status_send_failed);
                for (PairedDevice device : devices) {
                    try {
                        new TcpClient().sendMessage(device.ipAddress, device.port, payload, false);
                        repository.updateDeviceLastSeen(device.deviceId, System.currentTimeMillis());
                        repository.updateDeviceLastError(device.deviceId, null);
                        successCount++;
                    } catch (Exception exception) {
                        lastError = toUserFacingNetworkError(
                                exception, device.ipAddress, device.port, device.deviceName);
                        repository.updateDeviceLastError(device.deviceId, lastError);
                        SyncLog.e(TAG, "Failed manual clipboard send to " + device.deviceId, exception);
                    }
                }

                if (successCount > 0) {
                    postCallback(callback, true, appContext.getString(R.string.keyboard_status_sent));
                } else {
                    postCallback(callback, false, lastError);
                }
            }
        });
    }

    private void handleLocalClipboardChanged(String text, String eventId, long timestamp) {
        saveLocalClipboardEntry(text, eventId, timestamp);

        if (!runtimeRunning.get()) {
            return;
        }

        clipboardSyncManager.markRecentEvent(eventId);
        try {
            final JSONObject payload = buildClipboardPayload(text, eventId, timestamp);
            ArrayList<PairedDevice> devices = repository.getPairedDevices();
            for (final PairedDevice device : devices) {
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            new TcpClient().sendMessage(device.ipAddress, device.port, payload, false);
                            repository.updateDeviceLastSeen(device.deviceId, System.currentTimeMillis());
                            repository.updateDeviceLastError(device.deviceId, null);
                        } catch (Exception exception) {
                            repository.updateDeviceLastError(device.deviceId,
                                    toUserFacingNetworkError(exception, device.ipAddress, device.port, device.deviceName));
                            SyncLog.e(TAG, "Failed to send clipboard update to " + device.deviceId, exception);
                        }
                    }
                });
            }
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to build clipboard payload", exception);
        }
    }

    private String handleIncomingMessage(String remoteAddress, JSONObject message) {
        String type = message.optString("type");
        if ("pair_request".equals(type)) {
            return handlePairRequest(remoteAddress, message);
        }
        if ("clipboard_update".equals(type)) {
            handleRemoteClipboard(message);
            return null;
        }
        if ("ping".equals(type)) {
            return handlePing(message);
        }
        SyncLog.w(TAG, "Unhandled TCP message type: " + type);
        return null;
    }

    private String handlePairRequest(String remoteAddress, JSONObject message) {
        try {
            String incomingCode = message.optString("pairingCode");
            boolean accepted = repository.getLocalPairingCode().equals(incomingCode);
            String requestId = message.optString("requestId");

            if (accepted) {
                PairedDevice device = new PairedDevice();
                device.deviceId = message.optString("fromDeviceId");
                device.deviceName = message.optString("fromDeviceName");
                device.ipAddress = message.optString("ipAddress", remoteAddress);
                device.port = message.optInt("port", TcpServer.PORT);
                device.transport = "wifi";
                device.pairedAt = System.currentTimeMillis();
                device.lastSeen = System.currentTimeMillis();
                device.lastError = null;
                repository.upsertPairedDevice(device);
            }

            JSONObject response = new JSONObject();
            response.put("type", "pair_response");
            response.put("requestId", requestId);
            response.put("fromDeviceId", repository.getLocalDeviceId());
            response.put("fromDeviceName", repository.getLocalDeviceName());
            response.put("ipAddress", ensureLocalIp());
            response.put("port", TcpServer.PORT);
            response.put("accepted", accepted);
            response.put("message", accepted ? "Pairing successful" : "Invalid pairing code");
            response.put("timestamp", System.currentTimeMillis());
            refreshSnapshot();
            return response.toString();
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to process pair request", exception);
            return null;
        }
    }

    private void handleRemoteClipboard(JSONObject message) {
        try {
            String senderDeviceId = message.optString("fromDeviceId");
            if (!repository.isPairedDevice(senderDeviceId)) {
                SyncLog.w(TAG, "Ignoring clipboard update from unpaired device " + senderDeviceId);
                return;
            }

            String eventId = message.optString("eventId");
            if (clipboardSyncManager.hasRecentEvent(eventId)) {
                return;
            }

            String text = message.optString("text", null);
            if (text == null) {
                return;
            }

            ClipboardEntry entry = new ClipboardEntry();
            entry.eventId = eventId;
            entry.text = text;
            entry.sourceDeviceId = senderDeviceId;
            entry.sourceDeviceName = message.optString("fromDeviceName");
            entry.direction = "remote";
            entry.createdAt = message.optLong("timestamp", System.currentTimeMillis());
            repository.addClipboardEntry(entry);
            repository.updateDeviceLastSeen(senderDeviceId, System.currentTimeMillis());
            clipboardSyncManager.applyRemoteClipboard(text, eventId, entry.sourceDeviceName);
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to process remote clipboard", exception);
        }
    }

    private String handlePing(JSONObject message) {
        try {
            String senderDeviceId = message.optString("fromDeviceId");
            if (repository.isPairedDevice(senderDeviceId)) {
                repository.updateDeviceLastSeen(senderDeviceId, System.currentTimeMillis());
            }

            JSONObject response = new JSONObject();
            response.put("type", "pong");
            response.put("requestId", message.optString("requestId"));
            response.put("fromDeviceId", repository.getLocalDeviceId());
            response.put("fromDeviceName", repository.getLocalDeviceName());
            response.put("ipAddress", ensureLocalIp());
            response.put("port", TcpServer.PORT);
            response.put("message", "pong");
            response.put("timestamp", System.currentTimeMillis());
            return response.toString();
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to process ping", exception);
            return null;
        }
    }

    private void handleDiscoveryAnnouncement(JSONObject message, InetAddress sourceAddress) {
        try {
            if (!"discovery_announce".equals(message.optString("type"))) {
                return;
            }
            String deviceId = message.optString("deviceId");
            if (repository.getLocalDeviceId().equals(deviceId)) {
                return;
            }

            DiscoveredDevice device = new DiscoveredDevice();
            device.deviceId = deviceId;
            device.deviceName = message.optString("deviceName");
            device.ipAddress = message.optString("ipAddress",
                    sourceAddress == null ? null : sourceAddress.getHostAddress());
            device.port = message.optInt("port", TcpServer.PORT);
            device.timestamp = message.optLong("timestamp", System.currentTimeMillis());
            device.lastSeen = System.currentTimeMillis();
            DiscoveredDevice existingDevice = repository.getNearbyDevice(deviceId);
            repository.updateNearbyDevice(device);
            if (existingDevice == null) {
                SyncLog.i(TAG, "Nearby device discovered: " + formatNearbyDevice(device));
            } else if (!Objects.equals(existingDevice.deviceName, device.deviceName)
                    || !Objects.equals(existingDevice.ipAddress, device.ipAddress)
                    || existingDevice.port != device.port) {
                SyncLog.i(TAG, "Nearby device updated: " + formatNearbyDevice(device));
            }
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to process discovery announcement", exception);
        }
    }

    private void postCallback(final ActionCallback callback, final boolean success, final String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onComplete(success, message);
            }
        });
    }

    private String ensureLocalIp() {
        String ipAddress = NetworkUtils.getLocalIpv4Address();
        return ipAddress == null || ipAddress.trim().isEmpty() ? "0.0.0.0" : ipAddress;
    }

    private void saveLocalClipboardEntry(String text, String eventId, long timestamp) {
        ClipboardEntry entry = new ClipboardEntry();
        entry.eventId = eventId;
        entry.text = text;
        entry.sourceDeviceId = repository.getLocalDeviceId();
        entry.sourceDeviceName = repository.getLocalDeviceName();
        entry.direction = "local";
        entry.createdAt = timestamp;
        repository.addClipboardEntry(entry);
    }

    private JSONObject buildClipboardPayload(String text, String eventId, long timestamp) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("type", "clipboard_update");
        payload.put("eventId", eventId);
        payload.put("fromDeviceId", repository.getLocalDeviceId());
        payload.put("fromDeviceName", repository.getLocalDeviceName());
        payload.put("text", text);
        payload.put("timestamp", timestamp);
        return payload;
    }

    private String toUserFacingNetworkError(Exception exception, String ipAddress, int port, String label) {
        if (exception instanceof IllegalStateException && exception.getMessage() != null) {
            return exception.getMessage();
        }
        if (exception instanceof UnknownHostException) {
            return appContext.getString(R.string.error_invalid_ip_address);
        }
        if (exception instanceof SocketTimeoutException) {
            return appContext.getString(R.string.error_connection_timed_out, ipAddress, port);
        }
        if (exception instanceof ConnectException
                || (exception.getMessage() != null && exception.getMessage().contains("ECONNREFUSED"))) {
            return appContext.getString(R.string.error_remote_sync_not_running, ipAddress, port);
        }
        String fallbackLabel = label == null || label.trim().isEmpty() ? "device" : label;
        return appContext.getString(R.string.error_connection_failed, fallbackLabel, ipAddress, port);
    }

    private String formatNearbyDevice(DiscoveredDevice device) {
        String deviceName = device.deviceName == null || device.deviceName.trim().isEmpty()
                ? device.deviceId
                : device.deviceName;
        return deviceName + " (" + device.ipAddress + ":" + device.port + ")";
    }
}
