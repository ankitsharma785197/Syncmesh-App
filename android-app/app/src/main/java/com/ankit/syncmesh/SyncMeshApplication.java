package com.ankit.syncmesh;

import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.util.SyncLog;

import dev.patrickgold.florisboard.FlorisApplication;

public class SyncMeshApplication extends FlorisApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        SyncLog.init(this);
        AppRepository.getInstance(this);
    }
}
