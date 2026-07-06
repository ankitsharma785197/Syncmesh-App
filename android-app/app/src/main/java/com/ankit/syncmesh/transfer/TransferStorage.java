package com.ankit.syncmesh.transfer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.ankit.syncmesh.data.AppRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Destination-side storage for received files.
 *
 * API 29+: files land in the public {@code Download/SyncMesh/} collection through MediaStore
 * (no storage permission needed, and MediaStore makes path traversal impossible — we only
 * ever hand it a sanitized display name, never a path).
 * API 26–28: files land in the app-specific external dir {@code Android/data/…/files/SyncMesh}
 * (no permission needed; the app cannot write outside its own storage there).
 *
 * Conflicts are never overwritten: "photo.jpg" → "photo (1).jpg" → "photo (2).jpg".
 */
final class TransferStorage {

    /** Handle for one in-progress destination file. */
    static final class Destination {
        final OutputStream stream;
        final String displayName;
        @Nullable
        private final Uri mediaStoreUri;
        @Nullable
        private final File legacyFile;
        @Nullable
        private final Uri documentUri;
        private final ContentResolver resolver;

        private Destination(OutputStream stream, String displayName,
                            @Nullable Uri mediaStoreUri, @Nullable File legacyFile,
                            @Nullable Uri documentUri, ContentResolver resolver) {
            this.stream = stream;
            this.displayName = displayName;
            this.mediaStoreUri = mediaStoreUri;
            this.legacyFile = legacyFile;
            this.documentUri = documentUri;
            this.resolver = resolver;
        }

        /** Marks the file finished (clears IS_PENDING on MediaStore). */
        void commit() throws IOException {
            stream.close();
            if (mediaStoreUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(mediaStoreUri, values, null, null);
            }
        }

        /** Deletes the partial file after a failed/cancelled transfer. */
        void discard() {
            try {
                stream.close();
            } catch (IOException ignored) {
                // already closing on the failure path
            }
            if (mediaStoreUri != null) {
                try {
                    resolver.delete(mediaStoreUri, null, null);
                } catch (Exception ignored) {
                    // best effort cleanup
                }
            }
            if (documentUri != null) {
                try {
                    android.provider.DocumentsContract.deleteDocument(resolver, documentUri);
                } catch (Exception ignored) {
                    // best effort cleanup
                }
            }
            if (legacyFile != null && legacyFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                legacyFile.delete();
            }
        }
    }

    private static final String FOLDER_NAME = "SyncMesh";
    static final String DEFAULT_LOCATION_LABEL = "Downloads/" + FOLDER_NAME;

    private TransferStorage() {
    }

    /**
     * Human-readable label for where received files are actually being saved: the name of the
     * user-chosen folder if one is set and still accessible, otherwise the default
     * {@code Downloads/SyncMesh} location. Used in completion messages/notifications so they
     * match reality.
     */
    static String saveLocationLabel(Context context) {
        String savedTree = AppRepository.getInstance(context).getPreferences().getTransferSaveUri();
        if (savedTree == null) {
            return DEFAULT_LOCATION_LABEL;
        }
        try {
            Uri treeUri = Uri.parse(savedTree);
            DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);
            if (dir != null && dir.canWrite() && dir.getName() != null) {
                return dir.getName();
            }
            // Folder no longer accessible: open() will fall back to the default.
            if (dir == null || !dir.canWrite()) {
                return DEFAULT_LOCATION_LABEL;
            }
            String segment = treeUri.getLastPathSegment();
            return segment != null ? segment : DEFAULT_LOCATION_LABEL;
        } catch (Exception ignored) {
            return DEFAULT_LOCATION_LABEL;
        }
    }

    /**
     * Opens a conflict-free destination for {@code sanitizedName}. The caller must pass a
     * name that already went through {@link TransferProtocol#sanitizeFileName}.
     */
    static Destination open(Context context, String sanitizedName) throws IOException {
        // Honor a user-chosen folder if one is set and still accessible.
        String savedTree = AppRepository.getInstance(context).getPreferences().getTransferSaveUri();
        if (savedTree != null) {
            try {
                return openCustomTree(context, Uri.parse(savedTree), sanitizedName);
            } catch (IOException treeFailure) {
                // Folder was moved or permission revoked: fall back to the default location.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openMediaStore(context, sanitizedName);
        }
        return openLegacy(context, sanitizedName);
    }

    /** Writes into a user-picked SAF tree, renaming on conflict. */
    private static Destination openCustomTree(Context context, Uri treeUri, String name)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);
        if (dir == null || !dir.canWrite()) {
            throw new IOException("Chosen folder is not writable");
        }
        String candidate = name;
        int counter = 1;
        while (dir.findFile(candidate) != null) {
            candidate = numberedName(name, counter++);
            if (counter > 1000) {
                candidate = System.currentTimeMillis() + "_" + name;
                break;
            }
        }
        DocumentFile created = dir.createFile(mimeTypeFor(candidate), candidate);
        if (created == null) {
            throw new IOException("Could not create file in chosen folder");
        }
        OutputStream stream = resolver.openOutputStream(created.getUri());
        if (stream == null) {
            throw new IOException("Could not open file in chosen folder");
        }
        // createFile may have appended its own suffix; report the real name.
        String finalName = created.getName() == null ? candidate : created.getName();
        return new Destination(stream, finalName, null, null, created.getUri(), resolver);
    }

    private static Destination openMediaStore(Context context, String name) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + FOLDER_NAME;

        String uniqueName = uniqueMediaStoreName(resolver, collection, relativePath, name);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, uniqueName);
        values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeTypeFor(uniqueName));
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri item = resolver.insert(collection, values);
        if (item == null) {
            throw new IOException("Could not create destination file");
        }
        OutputStream stream = resolver.openOutputStream(item);
        if (stream == null) {
            resolver.delete(item, null, null);
            throw new IOException("Could not open destination file");
        }
        return new Destination(stream, uniqueName, item, null, null, resolver);
    }

    private static Destination openLegacy(Context context, String name) throws IOException {
        File directory = new File(context.getExternalFilesDir(null), FOLDER_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create destination folder");
        }
        String candidate = name;
        int counter = 1;
        while (new File(directory, candidate).exists()) {
            candidate = numberedName(name, counter++);
        }
        File file = new File(directory, candidate);
        // Defense in depth: the sanitized name must resolve inside the target directory.
        if (!file.getCanonicalPath().startsWith(directory.getCanonicalPath() + File.separator)) {
            throw new IOException("Illegal destination path");
        }
        return new Destination(new FileOutputStream(file), candidate, null, file, null,
                context.getContentResolver());
    }

    /** Finds the first "name", "name (1)", "name (2)" … not present in the collection. */
    private static String uniqueMediaStoreName(ContentResolver resolver, Uri collection,
                                               String relativePath, String name) {
        String candidate = name;
        int counter = 1;
        while (mediaStoreNameExists(resolver, collection, relativePath, candidate)) {
            candidate = numberedName(name, counter++);
            if (counter > 1000) {
                return String.format(Locale.US, "%d_%s", System.currentTimeMillis(), name);
            }
        }
        return candidate;
    }

    private static boolean mediaStoreNameExists(ContentResolver resolver, Uri collection,
                                                String relativePath, String displayName) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(collection,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                            + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?",
                    new String[]{displayName, relativePath + "%"},
                    null);
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception exception) {
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /** "photo.jpg" + 2 → "photo (2).jpg"; "archive" + 1 → "archive (1)". */
    static String numberedName(String name, int counter) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return String.format(Locale.US, "%s (%d)", name, counter);
        }
        return String.format(Locale.US, "%s (%d)%s",
                name.substring(0, dot), counter, name.substring(dot));
    }

    private static String mimeTypeFor(String name) {
        String extension = TransferProtocol.extensionOf(name);
        String mime = extension.isEmpty()
                ? null
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? "application/octet-stream" : mime;
    }
}
