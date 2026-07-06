package com.ankit.syncmesh.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Toast helpers, including a very brief (~0.1 s) flash for snappy feedback. */
public final class Toasts {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Toasts() {
    }

    /**
     * Shows a toast and dismisses it after ~120 ms so feedback feels instant rather than
     * lingering for the usual ~2 s of {@code LENGTH_SHORT}.
     */
    public static void brief(Context context, CharSequence text) {
        final Toast toast = Toast.makeText(context.getApplicationContext(), text,
                Toast.LENGTH_SHORT);
        toast.show();
        MAIN.postDelayed(toast::cancel, 120);
    }

    public static void brief(Context context, int textRes) {
        brief(context, context.getString(textRes));
    }
}
