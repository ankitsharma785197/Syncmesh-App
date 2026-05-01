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
}
