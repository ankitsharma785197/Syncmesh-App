package com.ankit.syncmesh.sync;

import com.ankit.syncmesh.util.SyncLog;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpClient {
    private static final String TAG = "TcpClient";
    private static final int TIMEOUT_MS = 3000;

    public JSONObject sendMessage(String ipAddress, int port, JSONObject payload, boolean expectResponse)
            throws Exception {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ipAddress, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write(payload.toString());
            writer.write('\n');
            writer.flush();
            SyncLog.i(TAG, "SEND " + ipAddress + ":" + port + " -> " + payload);

            if (!expectResponse) {
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) {
                return null;
            }
            SyncLog.i(TAG, "RECV " + ipAddress + ":" + port + " -> " + line);
            return new JSONObject(line);
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}
