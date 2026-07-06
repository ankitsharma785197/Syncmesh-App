package com.ankit.syncmesh.transfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles Accept / Reject taps on the incoming-transfer notification. */
public class TransferActionReceiver extends BroadcastReceiver {

    public static final String ACTION_ACCEPT = "com.ankit.syncmesh.transfer.ACTION_ACCEPT";
    public static final String ACTION_REJECT = "com.ankit.syncmesh.transfer.ACTION_REJECT";

    @Override
    public void onReceive(Context context, Intent intent) {
        FileTransferManager manager = FileTransferManager.getInstance(context);
        if (ACTION_ACCEPT.equals(intent.getAction())) {
            manager.acceptIncoming();
        } else if (ACTION_REJECT.equals(intent.getAction())) {
            manager.rejectIncoming();
        }
    }
}
