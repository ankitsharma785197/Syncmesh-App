package com.ankit.syncmesh.sync;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ankit.syncmesh.util.NotificationHelper;
import com.ankit.syncmesh.util.SyncLog;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ClipboardSyncManager {
    private static final String SOURCE_LISTENER = "listener";
    private static final String SOURCE_ACCESSIBILITY_POLL = "accessibility-poll";
    public interface LocalClipboardListener {
        void onLocalClipboardChanged(String text, String eventId, long timestamp);
    }

    private static final String TAG = "ClipboardSync";
    private static final long DUPLICATE_WINDOW_MS = 2000L;
    private static final long RECENT_EVENT_TTL_MS = 30000L;
    private static volatile ClipboardSyncManager instance;

    private final Context appContext;
    private final ClipboardManager clipboardManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> recentEventIds = new LinkedHashMap<>();

    private boolean listenerRegistered;
    private boolean serviceMonitorActive;
    private boolean accessibilityMonitorActive;
    private boolean applyingRemoteClipboard;
    private String lastObservedText;
    private long lastObservedAt;
    private String lastAppliedRemoteText;
    private long lastAppliedRemoteAt;
    private LocalClipboardListener clipboardListener;

    private final ClipboardManager.OnPrimaryClipChangedListener primaryClipChangedListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override
                public void onPrimaryClipChanged() {
                    handleClipboardChanged(SOURCE_LISTENER);
                }
            };

    private ClipboardSyncManager(Context context) {
        appContext = context.getApplicationContext();
        clipboardManager = (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public static ClipboardSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ClipboardSyncManager.class) {
                if (instance == null) {
                    instance = new ClipboardSyncManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized void startForegroundMonitoring(LocalClipboardListener listener) {
        clipboardListener = listener;
        serviceMonitorActive = true;
        ensureClipboardListener();
    }

    public synchronized void stopForegroundMonitoring() {
        serviceMonitorActive = false;
        removeClipboardListenerIfUnused();
    }

    public synchronized void startAccessibilityMonitoring(LocalClipboardListener listener) {
        clipboardListener = listener;
        accessibilityMonitorActive = true;
        ensureClipboardListener();
    }

    public synchronized void stopAccessibilityMonitoring() {
        accessibilityMonitorActive = false;
        removeClipboardListenerIfUnused();
    }

    public void pollClipboardNow() {
        handleClipboardChanged(SOURCE_ACCESSIBILITY_POLL);
    }

    public void applyRemoteClipboard(final String text, final String eventId, final String fromDeviceName) {
        markRecentEvent(eventId);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                applyingRemoteClipboard = true;
                lastAppliedRemoteText = text;
                lastAppliedRemoteAt = System.currentTimeMillis();
                lastObservedText = text;
                lastObservedAt = lastAppliedRemoteAt;
                clipboardManager.setPrimaryClip(ClipData.newPlainText("SyncMesh", text));
                NotificationHelper.showClipboardNotification(appContext, fromDeviceName, text);
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        applyingRemoteClipboard = false;
                    }
                }, DUPLICATE_WINDOW_MS);
            }
        });
    }

    public synchronized void markRecentEvent(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        recentEventIds.put(eventId, System.currentTimeMillis());
        pruneRecentEventsLocked();
    }

    public synchronized boolean hasRecentEvent(String eventId) {
        pruneRecentEventsLocked();
        return eventId != null && recentEventIds.containsKey(eventId);
    }

    private void handleClipboardChanged(String source) {
        String text = readCurrentClipboardText();
        if (text == null) {
            return;
        }

        LocalClipboardListener localListener;
        String eventId;
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (applyingRemoteClipboard
                    && text.equals(lastAppliedRemoteText)
                    && now - lastAppliedRemoteAt < DUPLICATE_WINDOW_MS) {
                lastObservedText = text;
                lastObservedAt = now;
                return;
            }
            if (SOURCE_ACCESSIBILITY_POLL.equals(source) && text.equals(lastObservedText)) {
                return;
            }
            if (SOURCE_LISTENER.equals(source)
                    && text.equals(lastObservedText)
                    && now - lastObservedAt < DUPLICATE_WINDOW_MS) {
                return;
            }
            lastObservedText = text;
            lastObservedAt = now;
            localListener = clipboardListener;
            eventId = UUID.randomUUID().toString();
            recentEventIds.put(eventId, now);
            pruneRecentEventsLocked();
        }

        SyncLog.d(TAG, "Clipboard changed from " + source);
        if (localListener != null) {
            localListener.onLocalClipboardChanged(text, eventId, now);
        }
    }

    private synchronized void ensureClipboardListener() {
        if (listenerRegistered || clipboardManager == null) {
            return;
        }
        clipboardManager.addPrimaryClipChangedListener(primaryClipChangedListener);
        listenerRegistered = true;
    }

    private synchronized void removeClipboardListenerIfUnused() {
        if (!listenerRegistered || serviceMonitorActive || accessibilityMonitorActive || clipboardManager == null) {
            return;
        }
        clipboardManager.removePrimaryClipChangedListener(primaryClipChangedListener);
        listenerRegistered = false;
    }

    private synchronized void pruneRecentEventsLocked() {
        long staleBefore = System.currentTimeMillis() - RECENT_EVENT_TTL_MS;
        Iterator<Map.Entry<String, Long>> iterator = recentEventIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < staleBefore) {
                iterator.remove();
            }
        }
    }

    private String readCurrentClipboardText() {
        try {
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
                return null;
            }
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) {
                return null;
            }
            CharSequence text = clipData.getItemAt(0).coerceToText(appContext);
            return text == null ? null : text.toString();
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to read clipboard", exception);
            return null;
        }
    }
}
