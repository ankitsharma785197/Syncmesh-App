package com.ankit.syncmesh.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.ankit.syncmesh.model.ClipboardEntry;

import java.util.ArrayList;

public class SyncDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "SyncDatabaseHelper";
    private static final String DATABASE_NAME = "syncmesh.db";
    private static final int DATABASE_VERSION = 8;

    public SyncDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
        ensureSchema(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureSchema(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Preserve data when an older build is installed over a newer local schema.
        ensureSchema(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        ensureSchema(db);
    }

    private void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS devices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "device_id TEXT UNIQUE," +
                "device_name TEXT," +
                "ip_address TEXT," +
                "port INTEGER," +
                "transport TEXT DEFAULT 'wifi'," +
                "bluetooth_address TEXT," +
                "paired_at INTEGER," +
                "last_seen INTEGER," +
                "last_error TEXT" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS clipboard_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "event_id TEXT UNIQUE," +
                "text TEXT," +
                "source_device_id TEXT," +
                "source_device_name TEXT," +
                "direction TEXT DEFAULT 'local'," +
                "created_at INTEGER," +
                "is_pinned INTEGER DEFAULT 0" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS sync_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "level TEXT," +
                "tag TEXT," +
                "message TEXT," +
                "created_at INTEGER" +
                ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (" +
                "key TEXT PRIMARY KEY," +
                "value TEXT" +
                ")");
    }

    private void ensureSchema(SQLiteDatabase db) {
        createTables(db);
        ensureColumn(db, "devices", "transport",
                "ALTER TABLE devices ADD COLUMN transport TEXT DEFAULT 'wifi'");
        ensureColumn(db, "devices", "bluetooth_address",
                "ALTER TABLE devices ADD COLUMN bluetooth_address TEXT");
        ensureColumn(db, "devices", "paired_at",
                "ALTER TABLE devices ADD COLUMN paired_at INTEGER");
        ensureColumn(db, "devices", "last_seen",
                "ALTER TABLE devices ADD COLUMN last_seen INTEGER");
        ensureColumn(db, "devices", "last_error",
                "ALTER TABLE devices ADD COLUMN last_error TEXT");

        ensureColumn(db, "clipboard_history", "direction",
                "ALTER TABLE clipboard_history ADD COLUMN direction TEXT DEFAULT 'local'");
        ensureColumn(db, "clipboard_history", "source_device_name",
                "ALTER TABLE clipboard_history ADD COLUMN source_device_name TEXT");
        ensureColumn(db, "clipboard_history", "is_pinned",
                "ALTER TABLE clipboard_history ADD COLUMN is_pinned INTEGER DEFAULT 0");

        ensureColumn(db, "sync_logs", "created_at",
                "ALTER TABLE sync_logs ADD COLUMN created_at INTEGER");
    }

    private void ensureColumn(SQLiteDatabase db, String tableName, String columnName, String alterStatement) {
        if (!hasColumn(db, tableName, columnName)) {
            try {
                db.execSQL(alterStatement);
            } catch (SQLiteException exception) {
                Log.w(TAG, "Ignoring schema change for " + tableName + "." + columnName, exception);
            }
        }
    }

    private boolean hasColumn(SQLiteDatabase db, String tableName, String columnName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            while (cursor.moveToNext()) {
                String currentName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if (columnName.equalsIgnoreCase(currentName)) {
                    return true;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    public synchronized boolean insertClipboardHistory(ClipboardEntry entry) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("event_id", entry.eventId);
            values.put("text", entry.text);
            values.put("source_device_id", entry.sourceDeviceId);
            values.put("source_device_name", entry.sourceDeviceName);
            values.put("direction", entry.direction);
            values.put("created_at", entry.createdAt);
            values.put("is_pinned", entry.pinned ? 1 : 0);
            long insertedId = db.insertWithOnConflict("clipboard_history", null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);
            return insertedId != -1;
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to insert clipboard history", exception);
            return false;
        }
    }

    public synchronized ArrayList<ClipboardEntry> getClipboardHistoryEntries() {
        ArrayList<ClipboardEntry> entries = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            cursor = db.query("clipboard_history", null, null, null, null, null,
                    "is_pinned DESC, created_at DESC");
            while (cursor.moveToNext()) {
                entries.add(readClipboardEntry(cursor));
            }
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to query clipboard history", exception);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return entries;
    }

    public synchronized ArrayList<ClipboardEntry> getClipboardHistory() {
        return getClipboardHistoryEntries();
    }

    @Nullable
    public synchronized String getLatestRemoteClipboardText() {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            cursor = db.query("clipboard_history", new String[]{"text"},
                    "direction=?", new String[]{"remote"}, null, null, "created_at DESC", "1");
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("text"));
            }
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to query latest remote clipboard", exception);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public synchronized void updateClipboardPinned(long id, boolean pinned) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_pinned", pinned ? 1 : 0);
            db.update("clipboard_history", values, "id=?", new String[]{String.valueOf(id)});
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to update clipboard pin state", exception);
        }
    }

    public synchronized void deleteClipboardEntry(long id) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete("clipboard_history", "id=?", new String[]{String.valueOf(id)});
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to delete clipboard entry", exception);
        }
    }

    public synchronized ArrayList<com.ankit.syncmesh.model.PairedDevice> getAllPairedDevices() {
        ArrayList<com.ankit.syncmesh.model.PairedDevice> devices = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            cursor = db.query("devices", null, null, null, null, null, "paired_at DESC");
            while (cursor.moveToNext()) {
                com.ankit.syncmesh.model.PairedDevice device = new com.ankit.syncmesh.model.PairedDevice();
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
                devices.add(device);
            }
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to query paired devices", exception);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return devices;
    }

    @Nullable
    public synchronized String getSetting(String key) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = getReadableDatabase();
            cursor = db.query("app_settings", new String[]{"value"}, "key=?",
                    new String[]{key}, null, null, null, "1");
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("value"));
            }
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to query setting " + key, exception);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public synchronized void setSetting(String key, @Nullable String value) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (value == null) {
                db.delete("app_settings", "key=?", new String[]{key});
                return;
            }
            ContentValues values = new ContentValues();
            values.put("key", key);
            values.put("value", value);
            db.insertWithOnConflict("app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteException exception) {
            Log.e(TAG, "Failed to save setting " + key, exception);
        }
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
        entry.pinned = cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1;
        return entry;
    }
}
