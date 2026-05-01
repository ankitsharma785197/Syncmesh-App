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
        AppRepository.getInstance(this);
    }
}
