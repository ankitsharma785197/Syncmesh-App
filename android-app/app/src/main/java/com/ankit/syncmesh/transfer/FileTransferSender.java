package com.ankit.syncmesh.transfer;

import android.content.Context;

import com.ankit.syncmesh.model.PairedDevice;
import com.ankit.syncmesh.model.TransferFileInfo;
import com.ankit.syncmesh.model.TransferRecord;
import com.ankit.syncmesh.util.SyncLog;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Sends one prepared transfer to a paired device over a single streaming connection.
 * Runs entirely on a background thread owned by {@link FileTransferManager}; publishes
 * progress through the manager and never touches clipboard/pairing code.
 */
class FileTransferSender implements Runnable {

    private static final String TAG = "FileTransferSender";

    private final Context appContext;
    private final FileTransferManager manager;
    private final PairedDevice device;
    private final ArrayList<TransferFileInfo> files;
    private final String transferId;
    private final String localDeviceId;
    private final String localDeviceName;

    FileTransferSender(Context appContext, FileTransferManager manager, PairedDevice device,
                       ArrayList<TransferFileInfo> files, String transferId,
                       String localDeviceId, String localDeviceName) {
        this.appContext = appContext;
        this.manager = manager;
        this.device = device;
        this.files = files;
        this.transferId = transferId;
        this.localDeviceId = localDeviceId;
        this.localDeviceName = localDeviceName;
    }

    @Override
    public void run() {
        TransferState state = manager.beginOutgoingState(transferId, device, files);
        Socket socket = null;
        String finalStatus = TransferRecord.STATUS_FAILED;
        try {
            state.phase = TransferState.Phase.CONNECTING;
            manager.publish(state);

            socket = new Socket();
            socket.connect(new InetSocketAddress(device.ipAddress, TransferProtocol.PORT),
                    TransferProtocol.CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(TransferProtocol.ACCEPT_TIMEOUT_MS);

            OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream(),
                    TransferProtocol.CHUNK_SIZE);
            Writer writer = new OutputStreamWriter(rawOut, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            writeLine(writer, TransferProtocol.buildOffer(
                    transferId, localDeviceId, localDeviceName, files).toString());

            state.phase = TransferState.Phase.WAITING_FOR_ACCEPT;
            manager.publish(state);
            SyncLog.i(TAG, "Transfer offer sent to " + device.deviceName
                    + " (" + files.size() + " files)");

            String responseLine = reader.readLine();
            if (responseLine == null) {
                throw new IOException("Connection closed before response");
            }
            JSONObject response = new JSONObject(responseLine);
            if (!response.optBoolean("accepted", false)) {
                String message = response.optString("message", "");
                state.phase = TransferState.Phase.FAILED;
                state.message = message.isEmpty()
                        ? appContext.getString(com.ankit.syncmesh.R.string.transfer_rejected_by_peer)
                        : message;
                finalStatus = TransferRecord.STATUS_REJECTED;
                SyncLog.i(TAG, "Transfer rejected by " + device.deviceName);
                return;
            }

            socket.setSoTimeout(TransferProtocol.DATA_TIMEOUT_MS);
            state.phase = TransferState.Phase.TRANSFERRING;
            state.startedAt = System.currentTimeMillis();
            manager.publish(state);

            byte[] buffer = new byte[TransferProtocol.CHUNK_SIZE];
            for (int i = 0; i < files.size(); i++) {
                TransferFileInfo file = files.get(i);
                state.currentIndex = i;
                state.currentFileName = file.name;
                state.currentFileSize = file.size;
                state.currentFileBytes = 0L;
                manager.publish(state);

                writeLine(writer, TransferProtocol.buildFileHeader(
                        transferId, i, file.name, file.size).toString());

                streamFile(file, rawOut, buffer, state);
                rawOut.flush();

                String ackLine = reader.readLine();
                if (ackLine == null) {
                    throw new IOException("Connection closed while waiting for file ack");
                }
                JSONObject ack = new JSONObject(ackLine);
                if (!ack.optBoolean("ok", false)) {
                    throw new IOException("Receiver failed to store " + file.name);
                }
                file.completed = true;
                state.completedFiles = i + 1;
                manager.publish(state);
            }

            writeLine(writer, TransferProtocol.buildSimple(
                    TransferProtocol.TYPE_COMPLETE, transferId).toString());

            state.phase = TransferState.Phase.COMPLETED;
            state.etaSeconds = 0;
            state.message = appContext.getString(
                    com.ankit.syncmesh.R.string.transfer_completed_message);
            finalStatus = TransferRecord.STATUS_COMPLETED;
            SyncLog.i(TAG, "Transfer completed to " + device.deviceName
                    + " (" + files.size() + " files, " + state.totalSize + " bytes)");
        } catch (CancelledException cancelled) {
            state.phase = TransferState.Phase.CANCELLED;
            state.message = appContext.getString(
                    com.ankit.syncmesh.R.string.transfer_cancelled_message);
            finalStatus = TransferRecord.STATUS_CANCELLED;
            SyncLog.i(TAG, "Transfer cancelled by user");
        } catch (Exception exception) {
            state.phase = TransferState.Phase.FAILED;
            state.message = manager.toUserFacingTransferError(exception, device);
            finalStatus = TransferRecord.STATUS_FAILED;
            SyncLog.e(TAG, "Transfer to " + device.deviceName + " failed", exception);
        } finally {
            closeQuietly(socket);
            manager.finishSession(state, finalStatus);
        }
    }

    private void streamFile(TransferFileInfo file, OutputStream out, byte[] buffer,
                            TransferState state) throws Exception {
        InputStream input = appContext.getContentResolver().openInputStream(file.uri);
        if (input == null) {
            throw new IOException("Cannot read " + file.name);
        }
        long lastTick = System.currentTimeMillis();
        long lastTickBytes = state.transferredBytes;
        try (InputStream in = new BufferedInputStream(input, TransferProtocol.CHUNK_SIZE)) {
            long remaining = file.size;
            while (remaining > 0) {
                waitWhilePaused(state);
                if (manager.isCancelRequested()) {
                    throw new CancelledException();
                }
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = in.read(buffer, 0, toRead);
                if (read < 0) {
                    throw new IOException("File shrank while sending: " + file.name);
                }
                out.write(buffer, 0, read);
                remaining -= read;
                state.currentFileBytes += read;
                state.transferredBytes += read;

                long now = System.currentTimeMillis();
                if (now - lastTick >= 400) {
                    manager.updateSpeed(state, now - lastTick,
                            state.transferredBytes - lastTickBytes);
                    lastTick = now;
                    lastTickBytes = state.transferredBytes;
                    manager.publish(state);
                }
            }
        }
    }

    private void waitWhilePaused(TransferState state) throws CancelledException {
        boolean wasPaused = false;
        while (manager.isPauseRequested() && !manager.isCancelRequested()) {
            if (!wasPaused) {
                wasPaused = true;
                state.phase = TransferState.Phase.PAUSED;
                state.speedBps = 0;
                state.etaSeconds = -1;
                manager.publish(state);
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancelledException();
            }
        }
        if (manager.isCancelRequested()) {
            throw new CancelledException();
        }
        if (wasPaused) {
            state.phase = TransferState.Phase.TRANSFERRING;
            manager.publish(state);
        }
    }

    private static void writeLine(Writer writer, String line) throws IOException {
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing on the way out
            }
        }
    }

    /** Internal marker: the user cancelled; not a network failure. */
    static final class CancelledException extends Exception {
    }
}
