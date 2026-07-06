package com.ankit.syncmesh.transfer;

import com.ankit.syncmesh.model.TransferFileInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wire protocol for the file-transfer channel. This is a NEW protocol on a NEW port; the
 * existing clipboard/pairing protocol on TcpServer.PORT (8989) is untouched.
 *
 * Framing: one UTF-8 JSON object per '\n'-terminated line for control messages, and exactly
 * {@code size} raw bytes immediately after each {@code file_header} line for file payloads.
 *
 * <pre>
 * sender → receiver : transfer_offer   {transferId, fromDeviceId, fromDeviceName,
 *                                       fileCount, totalSize, files:[{name,size}], timestamp}
 * receiver → sender : transfer_response{transferId, accepted, message}
 * per file:
 * sender → receiver : file_header      {transferId, index, name, size}
 * sender → receiver : &lt;size raw bytes&gt;
 * receiver → sender : file_ack         {transferId, index, ok}
 * finally:
 * sender → receiver : transfer_complete{transferId}
 * receiver → sender : transfer_result  {transferId, ok}
 * </pre>
 *
 * Cancel from either side = close the socket (senders best-effort write a transfer_cancel
 * line first so the peer can distinguish cancel from network failure).
 */
public final class TransferProtocol {

    public static final int PORT = 8991;

    public static final String TYPE_OFFER = "transfer_offer";
    public static final String TYPE_RESPONSE = "transfer_response";
    public static final String TYPE_FILE_HEADER = "file_header";
    public static final String TYPE_FILE_ACK = "file_ack";
    public static final String TYPE_COMPLETE = "transfer_complete";
    public static final String TYPE_RESULT = "transfer_result";
    public static final String TYPE_CANCEL = "transfer_cancel";

    /** Hard per-file and per-offer size cap: 2 GB. */
    public static final long MAX_FILE_SIZE = 2L * 1024L * 1024L * 1024L;
    /** Sanity cap on the number of files in one offer. */
    public static final int MAX_FILES_PER_TRANSFER = 500;
    /** Streaming chunk size (bytes); files are never fully loaded into RAM. */
    public static final int CHUNK_SIZE = 64 * 1024;

    /** How long the sender waits for the receiver's human to accept (ms). */
    public static final int ACCEPT_TIMEOUT_MS = 90_000;
    /** Socket timeout while streaming data; also bounds how long a pause can last (ms). */
    public static final int DATA_TIMEOUT_MS = 300_000;
    /** Socket connect timeout (ms). */
    public static final int CONNECT_TIMEOUT_MS = 5_000;

    private TransferProtocol() {
    }

    public static JSONObject buildOffer(String transferId, String deviceId, String deviceName,
                                        List<TransferFileInfo> files) throws JSONException {
        JSONArray fileArray = new JSONArray();
        long totalSize = 0L;
        for (TransferFileInfo file : files) {
            JSONObject item = new JSONObject();
            item.put("name", file.name);
            item.put("size", file.size);
            fileArray.put(item);
            totalSize += file.size;
        }
        JSONObject offer = new JSONObject();
        offer.put("type", TYPE_OFFER);
        offer.put("transferId", transferId);
        offer.put("fromDeviceId", deviceId);
        offer.put("fromDeviceName", deviceName);
        offer.put("fileCount", files.size());
        offer.put("totalSize", totalSize);
        offer.put("files", fileArray);
        offer.put("timestamp", System.currentTimeMillis());
        return offer;
    }

    public static JSONObject buildResponse(String transferId, boolean accepted, String message)
            throws JSONException {
        JSONObject response = new JSONObject();
        response.put("type", TYPE_RESPONSE);
        response.put("transferId", transferId);
        response.put("accepted", accepted);
        response.put("message", message == null ? "" : message);
        return response;
    }

    public static JSONObject buildFileHeader(String transferId, int index, String name, long size)
            throws JSONException {
        JSONObject header = new JSONObject();
        header.put("type", TYPE_FILE_HEADER);
        header.put("transferId", transferId);
        header.put("index", index);
        header.put("name", name);
        header.put("size", size);
        return header;
    }

    public static JSONObject buildFileAck(String transferId, int index, boolean ok)
            throws JSONException {
        JSONObject ack = new JSONObject();
        ack.put("type", TYPE_FILE_ACK);
        ack.put("transferId", transferId);
        ack.put("index", index);
        ack.put("ok", ok);
        return ack;
    }

    public static JSONObject buildSimple(String type, String transferId) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("type", type);
        message.put("transferId", transferId);
        return message;
    }

    /** Parses the {@code files} array of an offer into models. Returns null if malformed. */
    public static ArrayList<TransferFileInfo> parseOfferFiles(JSONObject offer) {
        JSONArray array = offer.optJSONArray("files");
        if (array == null) {
            return null;
        }
        ArrayList<TransferFileInfo> files = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                return null;
            }
            TransferFileInfo file = new TransferFileInfo();
            file.index = i;
            file.name = sanitizeFileName(item.optString("name"));
            file.size = item.optLong("size", -1L);
            files.add(file);
        }
        return files;
    }

    /**
     * Validates offer metadata (counts, sizes, names). Returns a human-readable rejection
     * reason, or null when the offer is acceptable. Security gate for the receiver.
     */
    public static String validateOffer(JSONObject offer, ArrayList<TransferFileInfo> files) {
        int fileCount = offer.optInt("fileCount", -1);
        long totalSize = offer.optLong("totalSize", -1L);
        if (files == null || files.isEmpty()) {
            return "No files in transfer offer";
        }
        if (fileCount != files.size()) {
            return "File count mismatch";
        }
        if (files.size() > MAX_FILES_PER_TRANSFER) {
            return "Too many files in one transfer";
        }
        long computedTotal = 0L;
        for (TransferFileInfo file : files) {
            if (file.name == null || file.name.isEmpty()) {
                return "Illegal file name";
            }
            if (file.size < 0L || file.size > MAX_FILE_SIZE) {
                return "File exceeds the 2 GB limit";
            }
            computedTotal += file.size;
        }
        if (totalSize != computedTotal) {
            return "Total size mismatch";
        }
        return null;
    }

    /**
     * Sanitizes a remote-supplied file name: strips any path components (defeats path
     * traversal like "../../x"), control characters and characters illegal on Android
     * storage, collapses whitespace, and bounds the length. Never returns null; falls back
     * to a generated name when nothing usable remains.
     */
    public static String sanitizeFileName(String rawName) {
        String name = rawName == null ? "" : rawName;
        // Keep only the last path segment for both separator styles.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder cleaned = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '/' || c == '\\' || c == ':' || c == '*'
                    || c == '?' || c == '"' || c == '<' || c == '>' || c == '|' || c == '\0') {
                continue;
            }
            cleaned.append(c);
        }
        name = cleaned.toString().trim();
        // Reject names that are only dots ("." / ".." and friends).
        boolean onlyDots = !name.isEmpty();
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) != '.') {
                onlyDots = false;
                break;
            }
        }
        if (name.isEmpty() || onlyDots) {
            name = String.format(Locale.US, "file_%d", System.currentTimeMillis());
        }
        if (name.length() > 200) {
            String extension = extensionOf(name);
            String base = name.substring(0, 200 - Math.min(extension.length() + 1, 50));
            name = extension.isEmpty() ? base : base + "." + extension;
        }
        return name;
    }

    /** Returns the extension (without dot, lowercase) or "" when none. */
    public static String extensionOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }
}
