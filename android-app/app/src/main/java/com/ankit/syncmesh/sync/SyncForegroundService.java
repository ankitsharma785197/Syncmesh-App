package com.ankit.syncmesh.sync;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.util.NotificationHelper;
import com.ankit.syncmesh.util.SyncLog;

public class SyncForegroundService extends Service {
    public static final String ACTION_START = "com.ankit.syncmesh.action.START_SYNC";
    public static final String ACTION_STOP = "com.ankit.syncmesh.action.STOP_SYNC";

    private SyncCoordinator coordinator;

    public static Intent createStartIntent(Context context) {
        return new Intent(context, SyncForegroundService.class).setAction(ACTION_START);
    }

    public static Intent createStopIntent(Context context) {
        return new Intent(context, SyncForegroundService.class).setAction(ACTION_STOP);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        coordinator = SyncCoordinator.getInstance(this);
        NotificationHelper.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = NotificationHelper.buildServiceNotification(
                this, getString(R.string.notification_service_text));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NotificationHelper.SERVICE_NOTIFICATION_ID,
                    notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification);
        }
        coordinator.startRuntime();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        coordinator.stopRuntime();
        SyncLog.i("SyncForegroundService", "Foreground service destroyed");
        super.onDestroy();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        SyncLog.w("SyncForegroundService", "Foreground service timeout reached");
        stopSelf(startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
