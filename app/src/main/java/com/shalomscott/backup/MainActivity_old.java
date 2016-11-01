package com.shalomscott.backup;

import android.Manifest;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.drive.Drive;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity_old extends Activity implements ConnectionCallbacks,
        OnConnectionFailedListener {

    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 0;
    private static final int REQUEST_DRIVE_ACCOUNT = 1;

    // Checkboxes
    private CheckBox googleDriveCheckbox,
            writePermissionCheckbox, scheduleBackupCheckbox;

    // State variables
    private boolean gettingDriveAuth;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setting up state
        gettingDriveAuth = false;

        // Syncing Checkboxes
        syncGoogleDriveCheckbox();
        syncWritePermissionCheckbox();
        sycScheduleBackupCheckbox();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_DRIVE_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    googleApiClient.connect();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL_STORAGE:
                syncWritePermissionCheckbox();
                break;
        }
    }


    /* ----------------- GOOGLE DRIVE ----------------- */

    private GoogleApiClient googleApiClient;

    private void syncGoogleDriveCheckbox() {
        gettingDriveAuth = false;
        connectGoogleApiClient();
    }

    public void googleDrive(View v) {
        if (googleDriveCheckbox.isChecked())
            Toast.makeText(this, "At present, app does not handle disconnecting",
                    Toast.LENGTH_SHORT).show();
        else {
            gettingDriveAuth = true;
            connectGoogleApiClient();
        }
    }

    private void connectGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (gettingDriveAuth) {
            Toast.makeText(this, "Drive account connected", Toast.LENGTH_SHORT).show();
            gettingDriveAuth = false;
        }
        googleDriveCheckbox.setChecked(true);
        googleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution() && gettingDriveAuth) {
            try {
                connectionResult.startResolutionForResult(this, REQUEST_DRIVE_ACCOUNT);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
                // TODO: update this
                googleDriveCheckbox.setChecked(false);
            }
        } else {
            if (gettingDriveAuth)
                GoogleApiAvailability.getInstance()
                        .getErrorDialog(this, connectionResult.getErrorCode(), 0).show();
            googleDriveCheckbox.setChecked(false);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }


    /* ----------------- WRITE PERMISSION ----------------- */

    private void syncWritePermissionCheckbox() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            writePermissionCheckbox.setChecked(true);
        } else {
            writePermissionCheckbox.setChecked(false);
        }
    }

    public void writePermission(View v) {
        if (writePermissionCheckbox.isChecked()) {
            Toast.makeText(this, "At present, app does not handle releasing permission",
                    Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }


    /* ----------------- SCHEDULE JOB ----------------- */

    private void sycScheduleBackupCheckbox() {
        JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler.getAllPendingJobs().size() == 0) {
            scheduleBackupCheckbox.setChecked(false);
        } else {
            scheduleBackupCheckbox.setChecked(true);
        }
    }

    // Toggle scheduling and cancellation of Backup service
    public void scheduleBackup(View v) {
        if (scheduleBackupCheckbox.isChecked()) {
            cancelBackup();
            scheduleBackupCheckbox.setChecked(false);
            Toast.makeText(this, "Backup job canceled", Toast.LENGTH_SHORT).show();
        } else {
            if (!ensureFiles()) {
                Toast.makeText(this, "Couldn't read storage to set up files",
                        Toast.LENGTH_SHORT).show();
                scheduleBackupCheckbox.setChecked(false);
                return;
            }

            if (scheduleBackup()) {
                scheduleBackupCheckbox.setChecked(true);
                Toast.makeText(this, "Backup job scheduled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean scheduleBackup() {
        JobScheduler jobScheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        ComponentName backupService = new ComponentName(this, BackupService.class);
        JobInfo.Builder builder = new JobInfo.Builder(2, backupService);
        builder.setPeriodic(5000);
        return jobScheduler.schedule(builder.build()) > 0;
    }

    private void cancelBackup() {
        ((JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE)).cancelAll();
    }

    // Ensures proper file structure is set up
    private boolean ensureFiles() {

        // TODO: possibly change this (not exact)
        if (!writePermissionCheckbox.isChecked()) {
            return false;
        }

        File backupDir = new File(Environment.getExternalStorageDirectory(), "Backup");

        File backupFile = new File(backupDir, "backup");
        File helpFile = new File(backupDir, "help");

        try {
            boolean res;

            // Setup Backup directory
            if (!backupDir.isDirectory()) {
                res = backupDir.mkdirs();
                if (!res) return false;
            }

            // Setup backup text
            if (!backupFile.isFile()) {
                res = backupFile.createNewFile();
                if (!res) return false;
            }

            // Setup help text
            if (!helpFile.isFile()) {
                res = helpFile.createNewFile();
                if (!res) return false;

                BufferedWriter helpWriter = new BufferedWriter(new FileWriter(helpFile));
                // TODO: set output specific to screen width
//                helpWriter.write("Usage: " + JsapParser.getInstance().getUsage());
//                helpWriter.write(JsapParser.getInstance().getHelp());
                helpWriter.close();
            }

        } catch (IOException e) {
            return false;
        }

        return true;
    }
}