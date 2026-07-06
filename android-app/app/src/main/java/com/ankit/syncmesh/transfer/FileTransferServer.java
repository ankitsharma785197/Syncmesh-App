package com.ankit.syncmesh.transfer;

import android.content.Context;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.model.TransferFileInfo;
import com.ankit.syncmesh.model.TransferRecord;
import com.ankit.syncmesh.util.SyncLog;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens on {@link TransferProtocol#PORT} for incoming file transfers. Lives alongside —
 * and completely independent of — the clipboard {@code TcpServer} on port 8989.
 *
 * One transfer is served at a time; a second concurrent offer is politely rejected as busy.
 */
public class FileTransferServer {

    private static final String TAG = "FileTransferServer";

    private final Context appContext;
    private final FileTransferManager manager;
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();

    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public FileTransferServer(Context context, FileTransferManager manager) {
        this.appContext = context.getApplicationContext();
        this.manager = manager;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        acceptExecutor.execute(this::runAcceptLoop);
    }

    public void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // shutting down
            }
            serverSocket = null;
        }
    }

    public boolean isRunning() {
        ServerSocket socket = serverSocket;
        return running && socket != null && !socket.isClosed();
    }

    private void runAcceptLoop() {
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress("0.0.0.0", TransferProtocol.PORT));
            serverSocket = socket;
            SyncLog.i(TAG, "File transfer server listening on 0.0.0.0:" + TransferProtocol.PORT);
            while (running) {
                Socket client = socket.accept();
                clientExecutor.execute(() -> handleClient(client));
            }
        } catch (IOException exception) {
            if (running) {
                SyncLog.e(TAG, "Transfer server stopped unexpectedly", exception);
            }
        }
    }

    private void handleClient(Socket socket) {
        String remoteAddress = socket.getInetAddress() == null
                ? "unknown" : socket.getInetAddress().getHostAddress();
        TransferState state = null;
        String finalStatus = TransferRecord.STATUS_FAILED;
        ArrayList<TransferStorage.Destination> committed = new ArrayList<>();
        TransferStorage.Destination current = null;
        try {
            socket.setSoTimeout(TransferProtocol.ACCEPT_TIMEOUT_MS);
            InputStream rawIn = new BufferedInputStream(socket.getInputStream(),
                    TransferProtocol.CHUNK_SIZE);
            Writer writer = new OutputStreamWriter(socket.getOutputStream(),
                    StandardCharsets.UTF_8);

            String offerLine = readLine(rawIn);
            if (offerLine == null) {
                return;
            }
            JSONObject offer = new JSONObject(offerLine);
            if (!TransferProtocol.TYPE_OFFER.equals(offer.optString("type"))) {
                SyncLog.w(TAG, "Unexpected transfer message from " + remoteAddress);
                return;
            }
            String transferId = offer.optString("transferId");
            String senderId = offer.optString("fromDeviceId");
            String senderName = offer.optString("fromDeviceName");

            // Security gates: sender must be a paired device, metadata must be sane.
            ArrayList<TransferFileInfo> files = TransferProtocol.parseOfferFiles(offer);
            String rejection = null;
            if (!manager.isPairedDevice(senderId)) {
                rejection = appContext.getString(R.string.transfer_reject_unpaired);
                SyncLog.w(TAG, "Rejecting transfer from unpaired device " + senderId);
            } else {
                String invalid = TransferProtocol.validateOffer(offer, files);
                if (invalid != null) {
                    rejection = invalid;
                    SyncLog.w(TAG, "Rejecting invalid transfer offer: " + invalid);
                } else if (!manager.tryClaimIncoming()) {
                    rejection = appContext.getString(R.string.transfer_reject_busy);
                }
            }
            if (rejection != null) {
                writeLine(writer, TransferProtocol.buildResponse(transferId, false, rejection)
                        .toString());
                return;
            }

            // Ask the local user. Blocks (with timeout) until Accept/Reject.
            state = manager.beginIncomingState(transferId, senderId, senderName, files);
            boolean accepted = manager.awaitUserDecision(state);
            if (!accepted) {
                writeLine(writer, TransferProtocol.buildResponse(transferId, false,
                        appContext.getString(R.string.transfer_rejected_by_peer)).toString());
                state.phase = TransferState.Phase.CANCELLED;
                state.message = appContext.getString(R.string.transfer_declined_message);
                finalStatus = TransferRecord.STATUS_REJECTED;
                return;
            }

            writeLine(writer, TransferProtocol.buildResponse(transferId, true, "accepted")
                    .toString());
            socket.setSoTimeout(TransferProtocol.DATA_TIMEOUT_MS);
            state.phase = TransferState.Phase.TRANSFERRING;
            state.startedAt = System.currentTimeMillis();
            manager.publish(state);
            SyncLog.i(TAG, "Receiving " + files.size() + " file(s) from " + senderName);

            byte[] buffer = new byte[TransferProtocol.CHUNK_SIZE];
            for (int i = 0; i < files.size(); i++) {
                String headerLine = readLine(rawIn);
                if (headerLine == null) {
                    throw new IOException("Connection closed before file " + (i + 1));
                }
                JSONObject header = new JSONObject(headerLine);
                if (TransferProtocol.TYPE_CANCEL.equals(header.optString("type"))) {
                    throw new FileTransferSender.CancelledException();
                }
                if (!TransferProtocol.TYPE_FILE_HEADER.equals(header.optString("type"))) {
                    throw new IOException("Protocol error: expected file header");
                }
                // Re-validate every header against the accepted offer (never trust the wire).
                String name = TransferProtocol.sanitizeFileName(header.optString("name"));
                long size = header.optLong("size", -1L);
                TransferFileInfo expected = files.get(i);
                if (size < 0L || size > TransferProtocol.MAX_FILE_SIZE || size != expected.size) {
                    throw new IOException("File size mismatch for " + name);
                }

                state.currentIndex = i;
                state.currentFileName = name;
                state.currentFileSize = size;
                state.currentFileBytes = 0L;
                manager.publish(state);

                current = TransferStorage.open(appContext, name);
                receiveFile(rawIn, current.stream, size, buffer, state);
                current.commit();
                committed.add(current);
                current = null;

                expected.completed = true;
                state.completedFiles = i + 1;
                manager.publish(state);
                writeLine(writer, TransferProtocol.buildFileAck(transferId, i, true).toString());
            }

            String completeLine = readLine(rawIn);
            if (completeLine != null) {
                writeLine(writer, TransferProtocol.buildSimple(
                        TransferProtocol.TYPE_RESULT, transferId).toString());
            }

            String savedLocation = TransferStorage.saveLocationLabel(appContext);
            state.phase = TransferState.Phase.COMPLETED;
            state.etaSeconds = 0;
            state.message = appContext.getString(R.string.transfer_received_message, savedLocation);
            finalStatus = TransferRecord.STATUS_COMPLETED;
            SyncLog.i(TAG, "Received " + files.size() + " file(s) from " + senderName
                    + " into " + savedLocation);
        } catch (FileTransferSender.CancelledException cancelled) {
            if (state != null) {
                state.phase = TransferState.Phase.CANCELLED;
                state.message = appContext.getString(R.string.transfer_cancelled_message);
            }
            finalStatus = TransferRecord.STATUS_CANCELLED;
            SyncLog.i(TAG, "Incoming transfer cancelled");
        } catch (Exception exception) {
            if (state != null) {
                state.phase = TransferState.Phase.FAILED;
                state.message = appContext.getString(R.string.transfer_failed_message);
            }
            SyncLog.e(TAG, "Incoming transfer from " + remoteAddress + " failed", exception);
        } finally {
            if (current != null) {
                current.discard();
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing on the way out
            }
            if (state != null) {
                manager.finishSession(state, finalStatus);
            }
        }
    }

    private void receiveFile(InputStream in, OutputStream out, long size, byte[] buffer,
                             TransferState state) throws Exception {
        long remaining = size;
        long lastTick = System.currentTimeMillis();
        long lastTickBytes = state.transferredBytes;
        DataInputStream data = new DataInputStream(in);
        while (remaining > 0) {
            waitWhilePaused(state);
            if (manager.isCancelRequested()) {
                throw new FileTransferSender.CancelledException();
            }
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = data.read(buffer, 0, toRead);
            if (read < 0) {
                throw new EOFException("Stream ended mid-file");
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

    private void waitWhilePaused(TransferState state)
            throws FileTransferSender.CancelledException {
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
                throw new FileTransferSender.CancelledException();
            }
        }
        if (manager.isCancelRequested()) {
            throw new FileTransferSender.CancelledException();
        }
        if (wasPaused) {
            state.phase = TransferState.Phase.TRANSFERRING;
            manager.publish(state);
        }
    }

    /**
     * Reads a single '\n'-terminated UTF-8 line from a binary stream WITHOUT buffering past
     * the newline (a BufferedReader would swallow the start of the following raw payload).
     */
    private static String readLine(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream(256);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return new String(line.toByteArray(), StandardCharsets.UTF_8);
            }
            line.write(b);
            if (line.size() > 512 * 1024) {
                throw new IOException("Control line too long");
            }
        }
        return line.size() == 0 ? null : new String(line.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeLine(Writer writer, String line) throws IOException {
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }
}
