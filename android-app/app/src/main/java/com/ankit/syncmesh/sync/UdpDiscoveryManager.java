package com.ankit.syncmesh.sync;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.util.NetworkUtils;
import com.ankit.syncmesh.util.SyncLog;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdpDiscoveryManager {
    public interface AnnouncementHandler {
        void onAnnouncement(JSONObject announcement, InetAddress sourceAddress);
    }

    public static final int PORT = 8990;
    private static final String TAG = "UdpDiscovery";
    private static final long ANNOUNCE_INTERVAL_MS = 3000L;
    private static final long STALE_THRESHOLD_MS = 15000L;

    private final Context appContext;
    private final AppRepository repository;
    private final AnnouncementHandler announcementHandler;
    private final ExecutorService broadcastExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService listenExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket broadcastSocket;
    private DatagramSocket receiveSocket;
    private WifiManager.MulticastLock multicastLock;

    public UdpDiscoveryManager(Context context, AnnouncementHandler announcementHandler) {
        this.appContext = context.getApplicationContext();
        this.repository = AppRepository.getInstance(appContext);
        this.announcementHandler = announcementHandler;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        acquireMulticastLock();
        broadcastExecutor.execute(new Runnable() {
            @Override
            public void run() {
                broadcastLoop();
            }
        });
        listenExecutor.execute(new Runnable() {
            @Override
            public void run() {
                listenLoop();
            }
        });
    }

    public void stop() {
        running.set(false);
        closeSocket(broadcastSocket);
        closeSocket(receiveSocket);
        broadcastExecutor.shutdownNow();
        listenExecutor.shutdownNow();
        releaseMulticastLock();
    }

    public boolean isRunning() {
        return running.get();
    }

    private void broadcastLoop() {
        try {
            broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);

            while (running.get()) {
                String localIp = NetworkUtils.getLocalIpv4Address();
                if (localIp != null && !localIp.trim().isEmpty()) {
                    JSONObject payload = new JSONObject();
                    payload.put("type", "discovery_announce");
                    payload.put("deviceId", repository.getLocalDeviceId());
                    payload.put("deviceName", repository.getLocalDeviceName());
                    payload.put("ipAddress", localIp);
                    payload.put("port", TcpServer.PORT);
                    payload.put("timestamp", System.currentTimeMillis());

                    byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                    List<InetAddress> broadcastAddresses = NetworkUtils.getBroadcastAddresses();
                    for (InetAddress address : broadcastAddresses) {
                        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
                        broadcastSocket.send(packet);
                    }
                }

                repository.pruneNearbyDevices(System.currentTimeMillis() - STALE_THRESHOLD_MS);
                Thread.sleep(ANNOUNCE_INTERVAL_MS);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (running.get()) {
                SyncLog.e(TAG, "UDP broadcast loop failed", exception);
            }
        }
    }

    private void listenLoop() {
        try {
            receiveSocket = new DatagramSocket(null);
            receiveSocket.setReuseAddress(true);
            receiveSocket.bind(new InetSocketAddress(PORT));
            byte[] buffer = new byte[4096];

            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                receiveSocket.receive(packet);
                String raw = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8).trim();
                if (raw.isEmpty()) {
                    continue;
                }
                JSONObject payload = new JSONObject(raw);
                if (announcementHandler != null) {
                    announcementHandler.onAnnouncement(payload, packet.getAddress());
                }
            }
        } catch (Exception exception) {
            if (running.get()) {
                SyncLog.e(TAG, "UDP receive loop failed", exception);
            }
        }
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                return;
            }
            multicastLock = wifiManager.createMulticastLock("syncmesh:discovery");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to acquire multicast lock", exception);
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to release multicast lock", exception);
        }
    }

    private void closeSocket(DatagramSocket socket) {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
