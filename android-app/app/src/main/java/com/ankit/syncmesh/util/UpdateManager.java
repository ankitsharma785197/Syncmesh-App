package com.ankit.syncmesh.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.ankit.syncmesh.R;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Lightweight update helper. On launch it asks Google Play whether a newer version of the
 * app is available; if so, it sends the user to the Play Store listing to update. A manual
 * "Check for updates" entry always opens the listing directly.
 *
 * Detection is a no-op for non-Play (sideloaded / debug) installs — the Play API simply
 * reports no update, so nothing happens.
 */
public final class UpdateManager {

    public static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.ankit.syncmesh";
    private static final String MARKET_URL = "market://details?id=com.ankit.syncmesh";
    private static final String TAG = "UpdateManager";

    private UpdateManager() {
    }

    /** Silent check on launch: redirects to the store only if an update is available. */
    public static void checkForUpdate(final Activity activity) {
        checkForUpdate(activity, false);
    }

    /**
     * Checks Play for an available update. When {@code userInitiated} the user gets instant,
     * brief feedback (checking / up-to-date) and is always taken somewhere useful; otherwise
     * it stays silent unless an update is found.
     */
    public static void checkForUpdate(final Activity activity, final boolean userInitiated) {
        if (userInitiated) {
            Toasts.brief(activity, R.string.toast_checking_updates);
        }
        try {
            final AppUpdateManager manager = AppUpdateManagerFactory.create(activity);
            manager.getAppUpdateInfo()
                    .addOnSuccessListener(info -> {
                        if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                            SyncLog.i(TAG, "App update available; redirecting to Play Store");
                            openPlayStore(activity);
                        } else if (userInitiated) {
                            Toasts.brief(activity, R.string.toast_up_to_date);
                        }
                    })
                    .addOnFailureListener(error -> {
                        SyncLog.w(TAG, "Update check unavailable: " + error.getMessage());
                        if (userInitiated) {
                            // Still take the user to the listing so the action isn't a dead end.
                            openPlayStore(activity);
                        }
                    });
        } catch (Throwable throwable) {
            SyncLog.w(TAG, "Update check skipped: " + throwable.getMessage());
            if (userInitiated) {
                openPlayStore(activity);
            }
        }
    }

    /** Opens the Play Store app on the listing, falling back to the browser URL. */
    public static void openPlayStore(Context context) {
        try {
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(market);
        } catch (ActivityNotFoundException noPlayApp) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
