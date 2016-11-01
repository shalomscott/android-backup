package com.shalomscott.backup.Utils;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.metadata.CustomPropertyKey;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

// TODO: add javadoc

/**
 * A helper class built to handle all of the app's interactions
 * with the Google Play Drive API.
 */
public class DriveUtils {
    private static final String TAG = "DriveUtils";
    private static GoogleApiClient googleApiClient;

    private static final String ROOT_NAME = "Android Backup";
    private static DriveFolder root;

    private static final CustomPropertyKey appProp =
            new CustomPropertyKey("ANDROID_BACKUP", CustomPropertyKey.PRIVATE);

    /*--------------- Google API Client ---------------*/

    private static ConnectResult connectResult;
    private static ArrayList<OnConnectListener> listeners = new ArrayList<>();

    public static class ConnectResult {
        public boolean success;
        public Bundle successBundle;
        public ConnectionResult failedResult;
    }

    public interface OnConnectListener {
        void onConnect(ConnectResult result);
    }

    public static void connect(Context context, OnConnectListener listener) {
        if (googleApiClient == null) {
            listeners.add(listener);
            googleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(@Nullable Bundle bundle) {
                            connectResult = new ConnectResult();
                            connectResult.success = true;
                            connectResult.successBundle = bundle;
                            connectResult.failedResult = null;
                            for (OnConnectListener l : listeners) {
                                l.onConnect(connectResult);
                            }
                            listeners.clear();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {
                            // TODO: something here?
                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                            connectResult = new ConnectResult();
                            connectResult.success = false;
                            connectResult.successBundle = null;
                            connectResult.failedResult = connectionResult;
                            for (OnConnectListener l : listeners) {
                                l.onConnect(connectResult);
                            }
                            listeners.clear();
                        }
                    })
                    .build();
            googleApiClient.connect();
        } else if (googleApiClient.isConnecting()) {
            listeners.add(listener);
        } else if (googleApiClient.isConnected()) {
            listener.onConnect(connectResult);
        } else {
            listeners.add(listener);
            googleApiClient.reconnect();
        }
    }

    public static void disconnect() {
        if (googleApiClient != null)
            googleApiClient.disconnect();
    }

    public static void sync() {
        if (googleApiClient != null)
            Drive.DriveApi.requestSync(googleApiClient);
    }

    /* Note: All of the functions that follow BLOCK THREAD EXECUTION */
    /*--------------- DriveFolders ---------------*/

    @Nullable
    public static DriveFolder getFolder(File folder, @Nullable DriveFolder parent) throws FileNotFoundException {
        if (!folder.isDirectory()) {
            String eMsg = folder.getName() + " is not an existing directory";
            Log.e(TAG, eMsg);
            throw new FileNotFoundException(eMsg);
        }

        DriveFolder driveParent = (parent != null) ? parent : getRoot();

        return getFolder(folder.getName(), driveParent);
    }

    @Nullable
    private static DriveFolder getRoot() {
        if (root == null) {
            root = getFolder(ROOT_NAME, null);
        }
        return root;
    }

    @Nullable
    private static DriveFolder getFolder(String foldername, @Nullable DriveFolder parent) {
        DriveFolder driveParent = parent;
        if (driveParent == null) {
            driveParent = Drive.DriveApi.getRootFolder(googleApiClient);
        }

        // Search Drive to see if folder already exists
        Query getFolder = new Query.Builder()
                .addFilter(Filters.eq(appProp, foldername))
                .build();
        DriveApi.MetadataBufferResult queryResult =
                driveParent.queryChildren(googleApiClient, getFolder).await();

        if (!queryResult.getStatus().isSuccess()) {
            // TODO: actually handle this
            return null;
        }

        MetadataBuffer buffer = queryResult.getMetadataBuffer();
        Metadata metadata = null;
        DriveFolder resFolder;

        // If folder already exists, grab it
        if (buffer.getCount() > 0 && (metadata = buffer.get(0)).isFolder()
                && !metadata.isTrashed()) {
            resFolder = buffer.get(0).getDriveId().asDriveFolder();
        } else {
            // If the previous folder was trashed by the user, delete the property
            if (metadata != null && metadata.isTrashed()) {
                MetadataChangeSet deleteProp = new MetadataChangeSet.Builder()
                        .deleteCustomProperty(appProp)
                        .build();
                metadata.getDriveId().asDriveFolder().updateMetadata(googleApiClient, deleteProp);
            }

            // Create the folder
            MetadataChangeSet md = new MetadataChangeSet.Builder()
                    .setTitle(foldername)
                    .setCustomProperty(appProp, foldername)
                    .build();
            DriveFolder.DriveFolderResult folderResult =
                    driveParent.createFolder(googleApiClient, md).await();

            if (!folderResult.getStatus().isSuccess()) {
                // TODO: actually handle this
                return null;
            }

            resFolder = folderResult.getDriveFolder();
        }
        return resFolder;
    }

    /*--------------- DriveFiles ---------------*/

    public static boolean uploadFile(File file, @Nullable DriveFolder parent) throws IOException {
        if (!file.isFile()) {
            String eMsg = file.getName() + " is not an existing file";
            Log.e(TAG, eMsg);
            throw new FileNotFoundException(eMsg);
        }

        DriveFolder driveParent = (parent != null) ? parent : getRoot();

        // Search Drive to see if file already exists
        Query getFile = new Query.Builder()
                .addFilter(Filters.eq(appProp, file.getName()))
                .build();
        DriveApi.MetadataBufferResult queryResult =
                driveParent.queryChildren(googleApiClient, getFile).await();

        // If the file exists (and is not in the trash), overwrite it
        MetadataBuffer buffer = queryResult.getMetadataBuffer();
        Metadata metadata = null;
        if (buffer.getCount() > 0 && !(metadata = buffer.get(0)).isFolder()
                && !metadata.isTrashed()) {
            DriveFile driveFile = buffer.get(0).getDriveId().asDriveFile();
            DriveApi.DriveContentsResult contentsResult = driveFile
                    .open(googleApiClient, DriveFile.MODE_WRITE_ONLY, null) // TODO: add progress listener option
                    .await();

            if (!contentsResult.getStatus().isSuccess()) {
                return false;
            }

            DriveContents contents = contentsResult.getDriveContents();
            MetadataChangeSet md = writeToContents(file, contents);
            contents.commit(googleApiClient, md);
        } else {
            if (metadata != null && metadata.isTrashed()) {
                MetadataChangeSet deleteProp = new MetadataChangeSet.Builder()
                        .deleteCustomProperty(appProp)
                        .build();
                // TODO: check if this actually works
                metadata.getDriveId().asDriveFile().updateMetadata(googleApiClient, deleteProp);
            }

            DriveApi.DriveContentsResult contentsResult =
                    Drive.DriveApi.newDriveContents(googleApiClient).await();

            if (!contentsResult.getStatus().isSuccess()) {
                return false;
            }

            DriveContents contents = contentsResult.getDriveContents();
            MetadataChangeSet md = writeToContents(file, contents);
            driveParent.createFile(googleApiClient, md, contents);
        }
        return true;
    }

    // TODO: possibly change function's return value. (Super unintuitive)
    private static MetadataChangeSet writeToContents(File src, DriveContents dst) throws IOException {
        try (OutputStream outputStream = dst.getOutputStream()) {
            outputStream.write(FileUtils.getFileBytes(src));
        } catch (IOException e) {
            String eMsg = "Could not upload " + src.getName() + " to Google Drive";
            Log.e(TAG, eMsg);
            throw new IOException(eMsg);
        }

        // Create the metadata - MIME type and title
        String mimeType = FileUtils.getMimeType(src.getName());
        mimeType = (mimeType != null) ? mimeType : "text/plain";

        return new MetadataChangeSet.Builder()
                .setTitle(src.getName())
                .setMimeType(mimeType) // TODO: make sure this is doing something
                .setCustomProperty(appProp, src.getName())
                .build();
    }
}