package helium314.keyboard.latin.syncmesh;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class SyncMeshBridge {
    private static final String PREFS_NAME = "syncmesh_keyboard_bridge";
    private static final String KEY_LAST_AUTO_SENT_TEXT = "last_auto_sent_text";
    private static final String KEY_LAST_AUTO_SENT_AT = "last_auto_sent_at";
    private static final long AUTO_SEND_DEBOUNCE_MS = 3000L;

    private SyncMeshBridge() {
    }

    public static void sendClipboardText(Context context, String text, String source) {
        if (context == null || TextUtils.isEmpty(text)) {
            return;
        }
        try {
            Object coordinator = getSingleton(context, "com.ankit.syncmesh.sync.SyncCoordinator");
            Class<?> callbackClass = Class.forName("com.ankit.syncmesh.sync.SyncCoordinator$ActionCallback");
            Method method = coordinator.getClass().getMethod("sendManualClipboardText", String.class, callbackClass);
            method.invoke(coordinator, text, null);
            Toast.makeText(context, "Clipboard sent", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(context, "SyncMesh send failed", Toast.LENGTH_SHORT).show();
        }
    }

    public static void sendPrimaryClipboard(Context context) {
        String text = readPrimaryClipboardText(context);
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        sendClipboardText(context, text, "heliboard_toolbar");
    }

    public static String getLatestRemoteClipboardText(Context context) {
        try {
            Object repository = getSingleton(context, "com.ankit.syncmesh.data.AppRepository");
            Method method = repository.getClass().getMethod("getLatestRemoteClipboardText");
            Object value = method.invoke(repository);
            return value == null ? null : value.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    public static List<SyncMeshClipboardItem> getClipboardHistory(Context context) {
        ArrayList<SyncMeshClipboardItem> items = new ArrayList<>();
        try {
            Object repository = getSingleton(context, "com.ankit.syncmesh.data.AppRepository");
            Method method = repository.getClass().getMethod("getClipboardHistory");
            Object value = method.invoke(repository);
            if (!(value instanceof List<?>)) {
                return items;
            }
            for (Object entry : (List<?>) value) {
                SyncMeshClipboardItem item = new SyncMeshClipboardItem();
                item.text = readStringField(entry, "text");
                item.direction = readStringField(entry, "direction");
                item.sourceDeviceName = readStringField(entry, "sourceDeviceName");
                item.createdAt = readLongField(entry, "createdAt");
                if (!TextUtils.isEmpty(item.text)) {
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static String getSetting(Context context, String key, String defaultValue) {
        if ("auto_send_keyboard".equals(key)) {
            try {
                Object repository = getSingleton(context, "com.ankit.syncmesh.data.AppRepository");
                Method getPreferences = repository.getClass().getMethod("getPreferences");
                Object preferences = getPreferences.invoke(repository);
                Method method = preferences.getClass().getMethod("isKeyboardAutoSendEnabled");
                Object value = method.invoke(preferences);
                return Boolean.TRUE.equals(value) ? "true" : "false";
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static void setSetting(Context context, String key, String value) {
        if (!"auto_send_keyboard".equals(key)) {
            return;
        }
        try {
            Object repository = getSingleton(context, "com.ankit.syncmesh.data.AppRepository");
            Method getPreferences = repository.getClass().getMethod("getPreferences");
            Object preferences = getPreferences.invoke(repository);
            Method method = preferences.getClass().getMethod("setKeyboardAutoSendEnabled", boolean.class);
            method.invoke(preferences, Boolean.parseBoolean(value));
        } catch (Exception ignored) {
        }
    }

    public static void openSyncMeshApp(Context context) {
        openActivity(context, "com.ankit.syncmesh.ui.MainActivity");
    }

    public static void openSyncMeshHistory(Context context) {
        openActivity(context, "com.ankit.syncmesh.ui.ClipboardHistoryActivity");
    }

    public static String readPrimaryClipboardText(Context context) {
        try {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) {
                return null;
            }
            ClipData clipData = manager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) {
                return null;
            }
            CharSequence text = clipData.getItemAt(0).coerceToText(context);
            return text == null ? null : text.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    public static void autoSendClipboardIfNeeded(Context context) {
        if (!"true".equals(getSetting(context, "auto_send_keyboard", "true"))) {
            return;
        }
        String text = readPrimaryClipboardText(context);
        if (TextUtils.isEmpty(text)) {
            return;
        }
        long now = System.currentTimeMillis();
        String lastText = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_AUTO_SENT_TEXT, null);
        long lastAt = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_AUTO_SENT_AT, 0L);
        if (text.equals(lastText) || now - lastAt < AUTO_SEND_DEBOUNCE_MS) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_AUTO_SENT_TEXT, text)
                .putLong(KEY_LAST_AUTO_SENT_AT, now)
                .apply();
        sendClipboardText(context, text, "heliboard_auto_open");
    }

    private static Object getSingleton(Context context, String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        Method method = clazz.getMethod("getInstance", Context.class);
        return method.invoke(null, context.getApplicationContext());
    }

    private static void openActivity(Context context, String className) {
        try {
            Intent intent = new Intent(context, Class.forName(className))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(context, "Unable to open SyncMesh", Toast.LENGTH_SHORT).show();
        }
    }

    private static String readStringField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            Object value = field.get(target);
            return value == null ? "" : value.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private static long readLongField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            Object value = field.get(target);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        } catch (Exception exception) {
            return 0L;
        }
    }
}
