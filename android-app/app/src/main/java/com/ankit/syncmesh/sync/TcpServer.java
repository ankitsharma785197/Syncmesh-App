package com.ankit.syncmesh.sync;

import com.ankit.syncmesh.util.SyncLog;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpServer {
    public interface MessageHandler {
        String onMessage(String remoteAddress, JSONObject message);
    }

    public static final int PORT = 8989;
    private static final String TAG = "TcpServer";

    private final MessageHandler messageHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;

    public TcpServer(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        acceptExecutor.execute(new Runnable() {
            @Override
            public void run() {
                runAcceptLoop();
            }
        });
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
        acceptExecutor.shutdownNow();
        clientExecutor.shutdownNow();
    }

    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    private void runAcceptLoop() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", PORT));
            SyncLog.i(TAG, "TCP server listening on 0.0.0.0:" + PORT);

            while (running.get()) {
                final Socket clientSocket = serverSocket.accept();
                clientExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        handleClient(clientSocket);
                    }
                });
            }
        } catch (SocketException socketException) {
            if (running.get()) {
                SyncLog.e(TAG, "TCP server socket error", socketException);
            }
        } catch (Exception exception) {
            SyncLog.e(TAG, "TCP server failed", exception);
        } finally {
            running.set(false);
        }
    }

    private void handleClient(Socket clientSocket) {
        String remoteAddress = clientSocket.getInetAddress() == null
                ? "unknown"
                : clientSocket.getInetAddress().getHostAddress();
        try {
            clientSocket.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));

            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                return;
            }
            SyncLog.i(TAG, "RECV " + remoteAddress + " -> " + line);
            JSONObject message = new JSONObject(line);
            String response = messageHandler == null ? null : messageHandler.onMessage(remoteAddress, message);
            if (response != null) {
                writer.write(response);
                writer.write('\n');
                writer.flush();
                SyncLog.i(TAG, "SEND " + remoteAddress + " -> " + response);
            }
        } catch (Exception exception) {
            SyncLog.e(TAG, "Failed to handle TCP client " + remoteAddress, exception);
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ignored) {
            }
        }
    }
}
