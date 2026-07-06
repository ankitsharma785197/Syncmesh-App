package com.ankit.syncmesh.transfer;

import com.ankit.syncmesh.model.TransferFileInfo;

import java.util.ArrayList;

/**
 * Immutable-by-convention snapshot of the active transfer, published to the UI via LiveData.
 * A fresh copy is posted on every progress tick so observers never see torn state.
 */
public class TransferState {

    public enum Phase {
        IDLE,
        CONNECTING,
        WAITING_FOR_ACCEPT,   // sender: waiting for the remote user's decision
        INCOMING_REQUEST,     // receiver: local user must accept/reject
        TRANSFERRING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public Phase phase = Phase.IDLE;
    /** true = this device is sending, false = receiving. */
    public boolean outgoing;
    public String transferId;
    public String peerDeviceName;
    public String peerDeviceId;

    public ArrayList<TransferFileInfo> files = new ArrayList<>();
    public int currentIndex;
    public String currentFileName;
    public long currentFileSize;
    public long currentFileBytes;
    public int completedFiles;

    public long totalSize;
    public long transferredBytes;
    /** Smoothed transfer speed, bytes/second. */
    public long speedBps;
    /** Estimated seconds remaining, -1 when unknown. */
    public long etaSeconds = -1;
    /** Human-readable status / error message. */
    public String message;
    public long startedAt;

    public TransferState copy() {
        TransferState copy = new TransferState();
        copy.phase = phase;
        copy.outgoing = outgoing;
        copy.transferId = transferId;
        copy.peerDeviceName = peerDeviceName;
        copy.peerDeviceId = peerDeviceId;
        copy.files = files;
        copy.currentIndex = currentIndex;
        copy.currentFileName = currentFileName;
        copy.currentFileSize = currentFileSize;
        copy.currentFileBytes = currentFileBytes;
        copy.completedFiles = completedFiles;
        copy.totalSize = totalSize;
        copy.transferredBytes = transferredBytes;
        copy.speedBps = speedBps;
        copy.etaSeconds = etaSeconds;
        copy.message = message;
        copy.startedAt = startedAt;
        return copy;
    }

    public boolean isActive() {
        return phase == Phase.CONNECTING || phase == Phase.WAITING_FOR_ACCEPT
                || phase == Phase.TRANSFERRING || phase == Phase.PAUSED
                || phase == Phase.INCOMING_REQUEST;
    }
}
