package com.ankit.syncmesh;

import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.util.SyncLog;

import android.app.Application;

import helium314.keyboard.latin.App;

public class SyncMeshApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        App.Companion.initialize(this);
        SyncLog.init(this);
        AppRepository repository = AppRepository.getInstance(this);
        // Apply the user's saved light/dark preference before any activity is shown.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                repository.getPreferences().getThemeMode());
        // Lets the transfer manager decide between the incoming dialog and a notification.
        com.ankit.syncmesh.transfer.FileTransferManager.getInstance(this)
                .registerForegroundTracker(this);
    }
}
