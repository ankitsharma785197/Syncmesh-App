package com.ankit.syncmesh.util;

import android.text.format.DateUtils;

import java.text.DateFormat;
import java.util.Date;

public final class DisplayUtils {
    private DisplayUtils() {
    }

    public static String formatRelativeTime(long timestamp) {
        if (timestamp <= 0L) {
            return "Never";
        }
        return DateUtils.getRelativeTimeSpanString(
                timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
        ).toString();
    }

    public static String formatDateTime(long timestamp) {
        if (timestamp <= 0L) {
            return "Unknown";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                .format(new Date(timestamp));
    }

    public static String formatEndpoint(String ipAddress, int port) {
        return (ipAddress == null || ipAddress.trim().isEmpty() ? "Unknown" : ipAddress) + ":" + port;
    }

    public static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /** 1536 → "1.5 KB"; 0 → "0 B". */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(java.util.Locale.US, value >= 100 ? "%.0f %s" : "%.1f %s",
                value, units[unit]);
    }

    /** Bytes/second → "4.2 MB/s". */
    public static String formatSpeed(long bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s";
    }

    /** Seconds → "1m 12s" / "45s"; negative → "—". */
    public static String formatEta(long seconds) {
        if (seconds < 0) {
            return "—";
        }
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
