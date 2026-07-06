package com.ankit.syncmesh.model;

/** One row of the persisted transfer history. */
public class TransferRecord {
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_REJECTED = "rejected";

    public static final String DIRECTION_SENT = "sent";
    public static final String DIRECTION_RECEIVED = "received";

    public long id;
    public String transferId;
    /** {@link #DIRECTION_SENT} or {@link #DIRECTION_RECEIVED}. */
    public String direction;
    public String peerDeviceId;
    public String peerDeviceName;
    public int fileCount;
    public long totalSize;
    /** One of the STATUS_* constants. */
    public String status;
    public long startedAt;
    public long durationMs;
    /** Newline-separated file names (for display + search). */
    public String fileNames;
}
