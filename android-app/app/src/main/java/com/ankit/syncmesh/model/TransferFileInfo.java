package com.ankit.syncmesh.model;

import android.net.Uri;

/** One file within a transfer. {@code uri} is sender-side only and never serialized. */
public class TransferFileInfo {
    public int index;
    public String name;
    public long size;
    public Uri uri;
    /** Bytes moved so far for this file (progress). */
    public long transferredBytes;
    /** true once this file finished successfully. */
    public boolean completed;
}
