package com.ankit.syncmesh.util;

import android.content.Context;
import android.util.Log;

import com.ankit.syncmesh.data.AppRepository;

public final class SyncLog {
    private static Context appContext;

    private SyncLog() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void d(String tag, String message) {
        log("DEBUG", tag, message, null);
    }

    public static void i(String tag, String message) {
        log("INFO", tag, message, null);
    }

    public static void w(String tag, String message) {
        log("WARN", tag, message, null);
    }

    public static void e(String tag, String message) {
        log("ERROR", tag, message, null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        log("ERROR", tag, message, throwable);
    }

    private static void log(String level, String tag, String message, Throwable throwable) {
        if ("DEBUG".equals(level)) {
            Log.d(tag, message, throwable);
        } else if ("INFO".equals(level)) {
            Log.i(tag, message, throwable);
        } else if ("WARN".equals(level)) {
            Log.w(tag, message, throwable);
        } else {
            Log.e(tag, message, throwable);
        }

        if (appContext == null) {
            return;
        }

        try {
            AppRepository.getInstance(appContext).addLog(level, tag,
                    throwable == null ? message : message + " :: " + throwable.getMessage());
        } catch (Exception ignored) {
            Log.w(tag, "Failed to persist log", ignored);
        }
    }
}
