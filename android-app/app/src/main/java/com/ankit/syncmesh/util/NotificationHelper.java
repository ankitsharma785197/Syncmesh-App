package com.ankit.syncmesh.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.sync.SyncForegroundService;
import com.ankit.syncmesh.ui.MainActivity;

public final class NotificationHelper {
    public static final String SERVICE_CHANNEL_ID = "syncmesh_service";
    public static final String CLIPBOARD_CHANNEL_ID = "syncmesh_clipboard";
    public static final int SERVICE_NOTIFICATION_ID = 8989;
    public static final int CLIPBOARD_NOTIFICATION_ID = 8991;

    private NotificationHelper() {
    }

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel serviceChannel = new NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription(context.getString(R.string.service_channel_description));

        NotificationChannel clipboardChannel = new NotificationChannel(
                CLIPBOARD_CHANNEL_ID,
                context.getString(R.string.clipboard_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        clipboardChannel.setDescription(context.getString(R.string.clipboard_channel_description));

        manager.createNotificationChannel(serviceChannel);
        manager.createNotificationChannel(clipboardChannel);
    }

    public static Notification buildServiceNotification(Context context, String bodyText) {
        PendingIntent openIntent = PendingIntent.getActivity(
                context,
                100,
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent stopIntent = PendingIntent.getService(
                context,
                101,
                SyncForegroundService.createStopIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(context.getString(R.string.notification_service_title))
                .setContentText(bodyText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openIntent)
                .addAction(0, context.getString(R.string.action_stop_sync), stopIntent)
                .build();
    }

    public static void showClipboardNotification(Context context, String deviceName, String textPreview) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
//                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
//                != PackageManager.PERMISSION_GRANTED) {
//            return;
//        }
//        Notification notification = new NotificationCompat.Builder(context, CLIPBOARD_CHANNEL_ID)
//                .setSmallIcon(android.R.drawable.stat_notify_more)
//                .setContentTitle(context.getString(R.string.notification_clipboard_title))
//                .setContentText(context.getString(R.string.notification_clipboard_text, deviceName))
//                .setStyle(new NotificationCompat.BigTextStyle().bigText(textPreview))
//                .setAutoCancel(true)
//                .build();
//
//        NotificationManagerCompat.from(context).notify(CLIPBOARD_NOTIFICATION_ID, notification);
        return;
    }
}
