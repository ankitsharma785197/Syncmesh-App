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
    public static final String TRANSFER_CHANNEL_ID = "syncmesh_transfer";
    public static final int SERVICE_NOTIFICATION_ID = 8989;
    public static final int CLIPBOARD_NOTIFICATION_ID = 8991;
    public static final int TRANSFER_PROGRESS_NOTIFICATION_ID = 8992;
    public static final int TRANSFER_INCOMING_NOTIFICATION_ID = 8993;

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

        NotificationChannel transferChannel = new NotificationChannel(
                TRANSFER_CHANNEL_ID,
                context.getString(R.string.transfer_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        transferChannel.setDescription(context.getString(R.string.transfer_channel_description));

        manager.createNotificationChannel(serviceChannel);
        manager.createNotificationChannel(clipboardChannel);
        manager.createNotificationChannel(transferChannel);
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

    private static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Incoming transfer request with Accept / Reject actions; tap opens the dialog. */
    public static void showIncomingTransferNotification(Context context, String senderName,
                                                        int fileCount, long totalSize) {
        if (!canNotify(context)) {
            return;
        }
        Intent openIntent = new Intent(context, com.ankit.syncmesh.ui.IncomingTransferActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(context, 200, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent accept = PendingIntent.getBroadcast(context, 201,
                new Intent(context, com.ankit.syncmesh.transfer.TransferActionReceiver.class)
                        .setAction(com.ankit.syncmesh.transfer.TransferActionReceiver.ACTION_ACCEPT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent reject = PendingIntent.getBroadcast(context, 202,
                new Intent(context, com.ankit.syncmesh.transfer.TransferActionReceiver.class)
                        .setAction(com.ankit.syncmesh.transfer.TransferActionReceiver.ACTION_REJECT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.transfer_incoming_title))
                .setContentText(context.getString(R.string.transfer_incoming_text,
                        senderName, fileCount, DisplayUtils.formatBytes(totalSize)))
                .setContentIntent(open)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, context.getString(R.string.transfer_action_accept), accept)
                .addAction(0, context.getString(R.string.transfer_action_reject), reject)
                .build();
        NotificationManagerCompat.from(context)
                .notify(TRANSFER_INCOMING_NOTIFICATION_ID, notification);
    }

    public static void cancelIncomingTransferNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(TRANSFER_INCOMING_NOTIFICATION_ID);
    }

    private static long lastTransferProgressUpdate;

    /** Ongoing progress notification while a transfer runs (throttled to ~1/s). */
    public static void showTransferProgressNotification(
            Context context, com.ankit.syncmesh.transfer.TransferState state) {
        if (!canNotify(context)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTransferProgressUpdate < 1000) {
            return;
        }
        lastTransferProgressUpdate = now;

        int percent = state.totalSize > 0
                ? (int) (state.transferredBytes * 100 / state.totalSize) : 0;
        Intent openIntent = new Intent(context, com.ankit.syncmesh.ui.FileTransferActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(context, 203, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
                .setSmallIcon(state.outgoing
                        ? android.R.drawable.stat_sys_upload
                        : android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(state.outgoing
                        ? R.string.transfer_notif_sending
                        : R.string.transfer_notif_receiving, state.peerDeviceName))
                .setContentText((state.completedFiles + (state.completedFiles < state.files.size() ? 1 : 0))
                        + "/" + state.files.size() + " · "
                        + DisplayUtils.formatBytes(state.transferredBytes) + " / "
                        + DisplayUtils.formatBytes(state.totalSize))
                .setProgress(100, percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        NotificationManagerCompat.from(context)
                .notify(TRANSFER_PROGRESS_NOTIFICATION_ID, notification);
    }

    /** Replaces the progress notification with a terminal completed/failed/cancelled one. */
    public static void showTransferFinishedNotification(
            Context context, com.ankit.syncmesh.transfer.TransferState state) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.cancel(TRANSFER_PROGRESS_NOTIFICATION_ID);
        lastTransferProgressUpdate = 0;
        if (!canNotify(context)) {
            return;
        }
        String title;
        switch (state.phase) {
            case COMPLETED:
                title = context.getString(state.outgoing
                        ? R.string.transfer_notif_sent_done
                        : R.string.transfer_notif_received_done, state.peerDeviceName);
                break;
            case CANCELLED:
                title = context.getString(R.string.transfer_notif_cancelled);
                break;
            default:
                title = context.getString(R.string.transfer_notif_failed);
                break;
        }
        Intent openIntent = new Intent(context, com.ankit.syncmesh.ui.FileTransferActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(context, 204, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(state.files.size() + " " + context.getString(R.string.transfer_files_label)
                        + " · " + DisplayUtils.formatBytes(state.totalSize))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build();
        manager.notify(TRANSFER_PROGRESS_NOTIFICATION_ID, notification);
    }

    /** Alerts the local user that a peer removed the pairing from their side. */
    public static void showUnpairedNotification(Context context, String deviceName) {
        if (!canNotify(context)) {
            return;
        }
        String name = deviceName == null || deviceName.trim().isEmpty()
                ? context.getString(R.string.unpair_unknown_device) : deviceName;
        Notification notification = new NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(context.getString(R.string.unpair_notification_title))
                .setContentText(context.getString(R.string.unpair_notification_text, name))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        NotificationManagerCompat.from(context).notify(
                TRANSFER_INCOMING_NOTIFICATION_ID + 1, notification);
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
