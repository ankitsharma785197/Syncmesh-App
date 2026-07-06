package com.ankit.syncmesh.transfer;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.data.TransferRepository;
import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.model.TransferFileInfo;
import com.ankit.syncmesh.model.TransferRecord;
import com.ankit.syncmesh.ui.IncomingTransferActivity;
import com.ankit.syncmesh.util.NotificationHelper;
import com.ankit.syncmesh.util.SyncLog;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton orchestrator for the file-transfer feature. Owns the transfer server lifecycle,
 * the single active {@link TransferState}, user controls (pause/resume/cancel/retry), the
 * incoming accept/reject handshake, notifications, and history persistence.
 *
 * It intentionally has no coupling to clipboard sync beyond reusing the paired-device list.
 */
public class FileTransferManager {

    private static final String TAG = "FileTransferManager";
    private static final long USER_DECISION_TIMEOUT_MS = 60_000;

    private static volatile FileTransferManager instance;

    private final Context appContext;
    private final AppRepository repository;
    private final TransferRepository transferRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final MutableLiveData<TransferState> transferStateLiveData = new MutableLiveData<>();

    private final AtomicBoolean sessionBusy = new AtomicBoolean(false);
    private volatile boolean pauseRequested;
    private volatile boolean cancelRequested;

    // Incoming accept/reject handshake between the server thread and the UI.
    private volatile CountDownLatch decisionLatch;
    private volatile boolean decisionAccepted;

    // Last outgoing request, kept for Retry.
    private volatile PairedDevice lastDevice;
    private volatile ArrayList<TransferFileInfo> lastFiles;

    private FileTransferServer server;
    private final AtomicInteger foregroundActivities = new AtomicInteger(0);

    private FileTransferManager(Context context) {
        appContext = context.getApplicationContext();
        repository = AppRepository.getInstance(appContext);
        transferRepository = TransferRepository.getInstance(appContext);
    }

    public static FileTransferManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FileTransferManager.class) {
                if (instance == null) {
                    instance = new FileTransferManager(context);
                }
            }
        }
        return instance;
    }

    // ---------------------------------------------------------------- server lifecycle

    /** Started/stopped together with the sync runtime (foreground service). */
    public synchronized void startServer() {
        if (server == null) {
            server = new FileTransferServer(appContext, this);
        }
        server.start();
    }

    public synchronized void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    public synchronized boolean isServerRunning() {
        return server != null && server.isRunning();
    }

    // ---------------------------------------------------------------- public state

    public LiveData<TransferState> getTransferStateLiveData() {
        return transferStateLiveData;
    }

    /** Clears a finished (terminal) state so the UI returns to the pick screen. */
    public void resetIfFinished() {
        TransferState state = transferStateLiveData.getValue();
        if (state != null && !state.isActive()) {
            transferStateLiveData.postValue(new TransferState());
        }
    }

    // ---------------------------------------------------------------- outgoing

    /**
     * Resolves display name + size for the picked documents. Runs synchronously (fast
     * metadata queries); call from a background thread or accept minor jank on huge picks.
     * Throws IllegalArgumentException with a user-facing message on validation failure.
     */
    public ArrayList<TransferFileInfo> prepareFiles(List<Uri> uris) {
        ArrayList<TransferFileInfo> files = new ArrayList<>(uris.size());
        int index = 0;
        for (Uri uri : uris) {
            TransferFileInfo file = new TransferFileInfo();
            file.index = index++;
            file.uri = uri;
            file.name = TransferProtocol.sanitizeFileName(resolveDisplayName(uri));
            file.size = resolveSize(uri);
            if (file.size < 0) {
                throw new IllegalArgumentException(
                        appContext.getString(R.string.transfer_error_unreadable, file.name));
            }
            if (file.size > TransferProtocol.MAX_FILE_SIZE) {
                throw new IllegalArgumentException(
                        appContext.getString(R.string.transfer_error_too_large, file.name));
            }
            files.add(file);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException(
                    appContext.getString(R.string.transfer_error_no_files));
        }
        if (files.size() > TransferProtocol.MAX_FILES_PER_TRANSFER) {
            throw new IllegalArgumentException(
                    appContext.getString(R.string.transfer_error_too_many));
        }
        return files;
    }

    /** Starts sending; returns false when another transfer is already active. */
    public boolean sendFiles(PairedDevice device, ArrayList<TransferFileInfo> files) {
        if (!sessionBusy.compareAndSet(false, true)) {
            return false;
        }
        pauseRequested = false;
        cancelRequested = false;
        lastDevice = device;
        lastFiles = files;
        String transferId = UUID.randomUUID().toString();
        executor.execute(new FileTransferSender(appContext, this, device, resetProgress(files),
                transferId, repository.getLocalDeviceId(), repository.getLocalDeviceName()));
        return true;
    }

    /** Re-sends the last outgoing request (after a failure/cancel). */
    public boolean retryLast() {
        PairedDevice device = lastDevice;
        ArrayList<TransferFileInfo> files = lastFiles;
        if (device == null || files == null) {
            return false;
        }
        return sendFiles(device, files);
    }

    public boolean canRetry() {
        return lastDevice != null && lastFiles != null;
    }

    private static ArrayList<TransferFileInfo> resetProgress(ArrayList<TransferFileInfo> files) {
        for (TransferFileInfo file : files) {
            file.transferredBytes = 0L;
            file.completed = false;
        }
        return files;
    }

    // ---------------------------------------------------------------- controls

    public void pause() {
        pauseRequested = true;
    }

    public void resume() {
        pauseRequested = false;
    }

    public void cancel() {
        cancelRequested = true;
        // Unblock a pending accept dialog as a rejection.
        CountDownLatch latch = decisionLatch;
        if (latch != null) {
            decisionAccepted = false;
            latch.countDown();
        }
    }

    boolean isPauseRequested() {
        return pauseRequested;
    }

    boolean isCancelRequested() {
        return cancelRequested;
    }

    // ---------------------------------------------------------------- incoming handshake

    boolean isPairedDevice(String deviceId) {
        return repository.isPairedDevice(deviceId);
    }

    /** Claims the single-session slot for an incoming offer. */
    boolean tryClaimIncoming() {
        boolean claimed = sessionBusy.compareAndSet(false, true);
        if (claimed) {
            pauseRequested = false;
            cancelRequested = false;
        }
        return claimed;
    }

    TransferState beginIncomingState(String transferId, String senderId, String senderName,
                                     ArrayList<TransferFileInfo> files) {
        TransferState state = new TransferState();
        state.phase = TransferState.Phase.INCOMING_REQUEST;
        state.outgoing = false;
        state.transferId = transferId;
        state.peerDeviceId = senderId;
        state.peerDeviceName = senderName;
        state.files = files;
        state.totalSize = totalSize(files);
        publish(state);
        return state;
    }

    TransferState beginOutgoingState(String transferId, PairedDevice device,
                                     ArrayList<TransferFileInfo> files) {
        TransferState state = new TransferState();
        state.phase = TransferState.Phase.CONNECTING;
        state.outgoing = true;
        state.transferId = transferId;
        state.peerDeviceId = device.deviceId;
        state.peerDeviceName = device.deviceName;
        state.files = files;
        state.totalSize = totalSize(files);
        publish(state);
        return state;
    }

    /**
     * Blocks the server thread until the local user accepts or rejects (or the timeout
     * elapses → reject). Shows the incoming dialog when the app is visible, and always
     * posts an actionable notification.
     */
    boolean awaitUserDecision(TransferState state) {
        CountDownLatch latch = new CountDownLatch(1);
        decisionAccepted = false;
        decisionLatch = latch;

        NotificationHelper.showIncomingTransferNotification(appContext,
                state.peerDeviceName, state.files.size(), state.totalSize);
        if (foregroundActivities.get() > 0) {
            appContext.startActivity(new Intent(appContext, IncomingTransferActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        boolean decided;
        try {
            decided = latch.await(USER_DECISION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            decided = false;
        }
        decisionLatch = null;
        NotificationHelper.cancelIncomingTransferNotification(appContext);
        if (!decided) {
            SyncLog.i(TAG, "Incoming transfer timed out waiting for user decision");
            return false;
        }
        return decisionAccepted;
    }

    /** Called from the incoming dialog / notification action. */
    public void acceptIncoming() {
        CountDownLatch latch = decisionLatch;
        if (latch != null) {
            decisionAccepted = true;
            latch.countDown();
        }
    }

    /** Called from the incoming dialog / notification action. */
    public void rejectIncoming() {
        CountDownLatch latch = decisionLatch;
        if (latch != null) {
            decisionAccepted = false;
            latch.countDown();
        }
    }

    public boolean hasPendingIncoming() {
        TransferState state = transferStateLiveData.getValue();
        return decisionLatch != null && state != null
                && state.phase == TransferState.Phase.INCOMING_REQUEST;
    }

    // ---------------------------------------------------------------- engine callbacks

    void publish(TransferState state) {
        transferStateLiveData.postValue(state.copy());
        if (state.phase == TransferState.Phase.TRANSFERRING
                || state.phase == TransferState.Phase.PAUSED) {
            NotificationHelper.showTransferProgressNotification(appContext, state);
        }
    }

    void updateSpeed(TransferState state, long elapsedMs, long deltaBytes) {
        if (elapsedMs <= 0) {
            return;
        }
        long instant = deltaBytes * 1000L / elapsedMs;
        state.speedBps = state.speedBps == 0
                ? instant
                : (long) (state.speedBps * 0.7 + instant * 0.3);
        long remaining = state.totalSize - state.transferredBytes;
        state.etaSeconds = state.speedBps > 0 ? remaining / state.speedBps : -1;
    }

    void finishSession(TransferState state, String status) {
        TransferRecord record = new TransferRecord();
        record.transferId = state.transferId;
        record.direction = state.outgoing
                ? TransferRecord.DIRECTION_SENT : TransferRecord.DIRECTION_RECEIVED;
        record.peerDeviceId = state.peerDeviceId;
        record.peerDeviceName = state.peerDeviceName;
        record.fileCount = state.files.size();
        record.totalSize = state.totalSize;
        record.status = status;
        record.startedAt = state.startedAt > 0 ? state.startedAt : System.currentTimeMillis();
        record.durationMs = state.startedAt > 0
                ? System.currentTimeMillis() - state.startedAt : 0;
        StringBuilder names = new StringBuilder();
        for (TransferFileInfo file : state.files) {
            if (names.length() > 0) {
                names.append('\n');
            }
            names.append(file.name);
        }
        record.fileNames = names.toString();
        transferRepository.addRecord(record);

        pauseRequested = false;
        cancelRequested = false;
        sessionBusy.set(false);
        transferStateLiveData.postValue(state.copy());
        NotificationHelper.showTransferFinishedNotification(appContext, state);
    }

    String toUserFacingTransferError(Exception exception, PairedDevice device) {
        if (exception instanceof UnknownHostException) {
            return appContext.getString(R.string.error_invalid_ip_address);
        }
        if (exception instanceof SocketTimeoutException) {
            return appContext.getString(R.string.transfer_error_timeout, device.deviceName);
        }
        if (exception instanceof ConnectException
                || (exception.getMessage() != null
                && exception.getMessage().contains("ECONNREFUSED"))) {
            return appContext.getString(R.string.transfer_error_peer_unavailable,
                    device.deviceName);
        }
        return appContext.getString(R.string.transfer_error_generic, device.deviceName);
    }

    // ---------------------------------------------------------------- helpers

    private long totalSize(List<TransferFileInfo> files) {
        long total = 0L;
        for (TransferFileInfo file : files) {
            total += file.size;
        }
        return total;
    }

    private String resolveDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = appContext.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
            }
        } catch (Exception exception) {
            SyncLog.w(TAG, "Could not resolve display name for " + uri);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment == null ? "file" : lastSegment;
    }

    private long resolveSize(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = appContext.getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception exception) {
            SyncLog.w(TAG, "Could not resolve size for " + uri);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    // ---------------------------------------------------------------- foreground tracking

    /** Registered once from {@code SyncMeshApplication}; used to decide dialog vs notification. */
    public void registerForegroundTracker(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                foregroundActivities.incrementAndGet();
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                foregroundActivities.decrementAndGet();
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity,
                                          @Nullable Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity,
                                                    @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }
}
