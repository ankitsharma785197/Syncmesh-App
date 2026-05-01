package com.ankit.syncmesh.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ankit.syncmesh.model.ClipboardEntry;
import com.ankit.syncmesh.model.DiscoveredDevice;
import com.ankit.syncmesh.model.LogEntry;
import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.model.ServiceSnapshot;
import com.ankit.syncmesh.util.NetworkUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppRepository {
    private static final String TAG = "AppRepository";
    private static final int MAX_LOG_ROWS = 250;
    private static volatile AppRepository instance;

    private final Context appContext;
    private final AppPreferences preferences;
    private final SyncDatabaseHelper databaseHelper;
    private final Object databaseLock = new Object();
    private final Map<String, DiscoveredDevice> nearbyDevices = new ConcurrentHashMap<>();

    private final MutableLiveData<List<PairedDevice>> pairedDevicesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ClipboardEntry>> clipboardHistoryLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<LogEntry>> logsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<DiscoveredDevice>> nearbyDevicesLiveData = new MutableLiveData<>();
    private final MutableLiveData<ServiceSnapshot> serviceSnapshotLiveData = new MutableLiveData<>();

    private AppRepository(Context context) {
        appContext = context.getApplicationContext();
        preferences = new AppPreferences(appContext);
        databaseHelper = new SyncDatabaseHelper(appContext);
        pairedDevicesLiveData.setValue(new ArrayList<>());
        clipboardHistoryLiveData.setValue(new ArrayList<>());
        logsLiveData.setValue(new ArrayList<>());
        nearbyDevicesLiveData.setValue(new ArrayList<>());
        serviceSnapshotLiveData.setValue(buildDefaultSnapshot());
        refreshAll();
    }

    public static AppRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (AppRepository.class) {
                if (instance == null) {
                    instance = new AppRepository(context);
                }
            }
        }
        return instance;
    }

    public AppPreferences getPreferences() {
        return preferences;
    }

    public String getLocalDeviceId() {
        return preferences.getDeviceId();
    }

    public String getLocalDeviceName() {
        return preferences.getDeviceName();
    }

    public String getLocalPairingCode() {
        return preferences.getPairingCode();
    }

    public String getShortDeviceId() {
        return NetworkUtils.shortenDeviceId(preferences.getDeviceId());
    }

    public LiveData<List<PairedDevice>> getPairedDevicesLiveData() {
        return pairedDevicesLiveData;
    }

    public LiveData<List<ClipboardEntry>> getClipboardHistoryLiveData() {
        return clipboardHistoryLiveData;
    }

    public LiveData<List<LogEntry>> getLogsLiveData() {
        return logsLiveData;
    }

    public LiveData<List<DiscoveredDevice>> getNearbyDevicesLiveData() {
        return nearbyDevicesLiveData;
    }

    public LiveData<ServiceSnapshot> getServiceSnapshotLiveData() {
        return serviceSnapshotLiveData;
    }

    public void refreshAll() {
        postPairedDevices();
        postClipboardHistory();
        postLogs();
        publishNearbyDevices();
        ServiceSnapshot snapshot = serviceSnapshotLiveData.getValue();
        if (snapshot == null) {
            serviceSnapshotLiveData.postValue(buildDefaultSnapshot());
        }
    }

    public ArrayList<PairedDevice> getPairedDevices() {
        ArrayList<PairedDevice> devices = new ArrayList<>();
        synchronized (databaseLock) {
            Cursor cursor = null;
            try {
                SQLiteDatabase db = databaseHelper.getReadableDatabase();
                cursor = db.query("devices", null, null, null, null, null, "paired_at DESC");
                while (cursor.moveToNext()) {
                    devices.add(readDevice(cursor));
                }
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to read paired devices", exception);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return devices;
    }

    public ArrayList<PairedDevice> getAllPairedDevices() {
        return getPairedDevices();
    }

    public PairedDevice getPairedDevice(String deviceId) {
        synchronized (databaseLock) {
            Cursor cursor = null;
            try {
                SQLiteDatabase db = databaseHelper.getReadableDatabase();
                cursor = db.query("devices", null, "device_id=?", new String[]{deviceId},
                        null, null, null, "1");
                if (cursor.moveToFirst()) {
                    return readDevice(cursor);
                }
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to read paired device " + deviceId, exception);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return null;
    }

    public boolean isPairedDevice(String deviceId) {
        return getPairedDevice(deviceId) != null;
    }

    public void upsertPairedDevice(PairedDevice device) {
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = databaseHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("device_id", device.deviceId);
                values.put("device_name", device.deviceName);
                values.put("ip_address", device.ipAddress);
                values.put("port", device.port);
                values.put("transport", device.transport == null ? "wifi" : device.transport);
                values.put("bluetooth_address", device.bluetoothAddress);
                values.put("paired_at", device.pairedAt);
                values.put("last_seen", device.lastSeen);
                values.put("last_error", device.lastError);
                db.insertWithOnConflict("devices", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to save paired device " + device.deviceId, exception);
            }
        }
        postPairedDevices();
    }

    public void removePairedDevice(String deviceId) {
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = databaseHelper.getWritableDatabase();
                db.delete("devices", "device_id=?", new String[]{deviceId});
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to remove paired device " + deviceId, exception);
            }
        }
        postPairedDevices();
    }

    public void updateDeviceLastSeen(String deviceId, long lastSeen) {
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = databaseHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("last_seen", lastSeen);
                db.update("devices", values, "device_id=?", new String[]{deviceId});
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to update last seen for " + deviceId, exception);
            }
        }
        postPairedDevices();
    }

    public void updateDeviceLastError(String deviceId, String lastError) {
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = databaseHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("last_error", lastError);
                db.update("devices", values, "device_id=?", new String[]{deviceId});
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to update last error for " + deviceId, exception);
            }
        }
        postPairedDevices();
    }

    public boolean addClipboardEntry(ClipboardEntry entry) {
        boolean insertedId = databaseHelper.insertClipboardHistory(entry);
        postClipboardHistory();
        return insertedId;
    }

    public ArrayList<ClipboardEntry> getClipboardHistory() {
        return databaseHelper.getClipboardHistoryEntries();
    }

    @Nullable
    public String getLatestRemoteClipboardText() {
        return databaseHelper.getLatestRemoteClipboardText();
    }

    public void clearClipboardHistory() {
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = databaseHelper.getWritableDatabase();
                db.delete("clipboard_history", null, null);
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to clear clipboard history", exception);
            }
        }
        postClipboardHistory();
    }

    public void updateClipboardPinned(long clipboardId, boolean pinned) {
        databaseHelper.updateClipboardPinned(clipboardId, pinned);
        postClipboardHistory();
    }

    public void deleteClipboardEntry(long clipboardId) {
        databaseHelper.deleteClipboardEntry(clipboardId);
        postClipboardHistory();
    }

    public void addLog(String level, String tag, String message) {
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = databaseHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("level", level);
                values.put("tag", tag);
                values.put("message", message);
                values.put("created_at", System.currentTimeMillis());
                db.insert("sync_logs", null, values);
                db.execSQL("DELETE FROM sync_logs WHERE id NOT IN (" +
                        "SELECT id FROM sync_logs ORDER BY id DESC LIMIT " + MAX_LOG_ROWS + ")");
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to persist log entry", exception);
            }
        }
        postLogs();
    }

    public ArrayList<LogEntry> getLogs() {
        ArrayList<LogEntry> entries = new ArrayList<>();
        synchronized (databaseLock) {
            Cursor cursor = null;
            try {
                SQLiteDatabase db = databaseHelper.getReadableDatabase();
                cursor = db.query("sync_logs", null, null, null, null, null, "created_at DESC");
                while (cursor.moveToNext()) {
                    entries.add(readLogEntry(cursor));
                }
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to read logs", exception);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return entries;
    }

    public void clearLogs() {
        synchronized (databaseLock) {
            try {
                SQLiteDatabase db = databaseHelper.getWritableDatabase();
                db.delete("sync_logs", null, null);
            } catch (SQLiteException exception) {
                Log.e(TAG, "Failed to clear logs", exception);
            }
        }
        postLogs();
    }

    public String buildLogExportText() {
        ArrayList<LogEntry> logs = getLogs();
        Collections.reverse(logs);
        StringBuilder builder = new StringBuilder();
        for (LogEntry entry : logs) {
            builder.append(entry.createdAt)
                    .append(" [")
                    .append(entry.level)
                    .append("/")
                    .append(entry.tag)
                    .append("] ")
                    .append(entry.message)
                    .append('\n');
        }
        return builder.toString().trim();
    }

    public void updateNearbyDevice(DiscoveredDevice device) {
        nearbyDevices.put(device.deviceId, device);
        publishNearbyDevices();
    }

    @Nullable
    public DiscoveredDevice getNearbyDevice(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }
        return nearbyDevices.get(deviceId);
    }

    public void pruneNearbyDevices(long staleBefore) {
        for (Map.Entry<String, DiscoveredDevice> entry : nearbyDevices.entrySet()) {
            if (entry.getValue().lastSeen < staleBefore) {
                nearbyDevices.remove(entry.getKey());
            }
        }
        publishNearbyDevices();
    }

    public void clearNearbyDevices() {
        nearbyDevices.clear();
        publishNearbyDevices();
    }

    public void updateServiceSnapshot(ServiceSnapshot snapshot) {
        if (snapshot == null) {
            snapshot = buildDefaultSnapshot();
        }
        serviceSnapshotLiveData.postValue(snapshot);
    }

    public ServiceSnapshot buildDefaultSnapshot() {
        ServiceSnapshot snapshot = new ServiceSnapshot();
        snapshot.serviceRunning = false;
        snapshot.tcpRunning = false;
        snapshot.udpRunning = false;
        snapshot.accessibilityEnabled = false;
        snapshot.localIpAddress = NetworkUtils.getLocalIpv4Address();
        snapshot.pairingCode = getLocalPairingCode();
        snapshot.deviceIdShort = getShortDeviceId();
        snapshot.pairedDeviceCount = getPairedDevices().size();
        return snapshot;
    }

    private void postPairedDevices() {
        pairedDevicesLiveData.postValue(getPairedDevices());
    }

    private void postClipboardHistory() {
        clipboardHistoryLiveData.postValue(getClipboardHistory());
    }

    private void postLogs() {
        logsLiveData.postValue(getLogs());
    }

    private void publishNearbyDevices() {
        List<DiscoveredDevice> devices = new ArrayList<>(nearbyDevices.values());
        devices.sort(new Comparator<DiscoveredDevice>() {
            @Override
            public int compare(DiscoveredDevice first, DiscoveredDevice second) {
                return Long.compare(second.lastSeen, first.lastSeen);
            }
        });
        nearbyDevicesLiveData.postValue(devices);
    }

    private PairedDevice readDevice(Cursor cursor) {
        PairedDevice device = new PairedDevice();
        device.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        device.deviceId = cursor.getString(cursor.getColumnIndexOrThrow("device_id"));
        device.deviceName = cursor.getString(cursor.getColumnIndexOrThrow("device_name"));
        device.ipAddress = cursor.getString(cursor.getColumnIndexOrThrow("ip_address"));
        device.port = cursor.getInt(cursor.getColumnIndexOrThrow("port"));
        device.transport = cursor.getString(cursor.getColumnIndexOrThrow("transport"));
        device.bluetoothAddress = cursor.getString(cursor.getColumnIndexOrThrow("bluetooth_address"));
        device.pairedAt = cursor.getLong(cursor.getColumnIndexOrThrow("paired_at"));
        device.lastSeen = cursor.getLong(cursor.getColumnIndexOrThrow("last_seen"));
        device.lastError = cursor.getString(cursor.getColumnIndexOrThrow("last_error"));
        return device;
    }

    private ClipboardEntry readClipboardEntry(Cursor cursor) {
        ClipboardEntry entry = new ClipboardEntry();
        entry.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        entry.eventId = cursor.getString(cursor.getColumnIndexOrThrow("event_id"));
        entry.text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
        entry.sourceDeviceId = cursor.getString(cursor.getColumnIndexOrThrow("source_device_id"));
        entry.sourceDeviceName = cursor.getString(cursor.getColumnIndexOrThrow("source_device_name"));
        entry.direction = cursor.getString(cursor.getColumnIndexOrThrow("direction"));
        entry.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        return entry;
    }

    private LogEntry readLogEntry(Cursor cursor) {
        LogEntry entry = new LogEntry();
        entry.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        entry.level = cursor.getString(cursor.getColumnIndexOrThrow("level"));
        entry.tag = cursor.getString(cursor.getColumnIndexOrThrow("tag"));
        entry.message = cursor.getString(cursor.getColumnIndexOrThrow("message"));
        entry.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        return entry;
    }
}
