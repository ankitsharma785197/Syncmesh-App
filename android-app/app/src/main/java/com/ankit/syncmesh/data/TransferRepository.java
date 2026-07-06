package com.ankit.syncmesh.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ankit.syncmesh.model.TransferRecord;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Data layer for transfer history. Mirrors the AppRepository pattern: a singleton around
 * the shared SQLite helper publishing results through LiveData, with all I/O off the main
 * thread. It never touches clipboard/pairing tables.
 */
public class TransferRepository {

    private static volatile TransferRepository instance;

    private final SyncDatabaseHelper databaseHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<ArrayList<TransferRecord>> historyLiveData =
            new MutableLiveData<>();

    private volatile String currentQuery = "";

    private TransferRepository(Context context) {
        // Same database file as AppRepository; SQLiteOpenHelper serializes access and the
        // helper's methods are synchronized, so a second handle is safe.
        databaseHelper = new SyncDatabaseHelper(context.getApplicationContext());
    }

    public static TransferRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (TransferRepository.class) {
                if (instance == null) {
                    instance = new TransferRepository(context);
                }
            }
        }
        return instance;
    }

    public LiveData<ArrayList<TransferRecord>> getHistoryLiveData() {
        return historyLiveData;
    }

    /** Persists a finished transfer and refreshes observers. */
    public void addRecord(final TransferRecord record) {
        executor.execute(() -> {
            databaseHelper.insertTransferRecord(record);
            historyLiveData.postValue(databaseHelper.getTransferRecords(currentQuery));
        });
    }

    /** Re-queries history with the given search text ("" = all). */
    public void search(final String query) {
        currentQuery = query == null ? "" : query;
        executor.execute(() ->
                historyLiveData.postValue(databaseHelper.getTransferRecords(currentQuery)));
    }

    public void refresh() {
        search(currentQuery);
    }
}
