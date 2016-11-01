package com.shalomscott.backup;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.drive.DriveFolder;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.shalomscott.backup.Utils.DriveUtils;
import com.shalomscott.backup.Utils.JsapParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static com.shalomscott.backup.Utils.FileUtils.*;


public class BackupService extends JobService implements DriveUtils.OnConnectListener,
        Runnable {
    private static final String TAG = "BackupService";

    // The service's notification builder
    private NotificationCompat.Builder notifyBuilder;
    // Colors to use for the notification
    private int COLOR_PRIMARY;
    private int COLOR_SUCCESS;
    private int COLOR_ERROR;

    // The JobParameters passed to this service in onStart() (needed for endService)
    private JobParameters jobParams;

    // Indicates whether this job was started with startService(=true), or as a job(=false)
    private boolean manualStart;

    private boolean isRunning = false;

    @Override
    public void onCreate() {
        // Get the pending intent which launches the app
        Intent launcherIntent = new Intent(this, LaunchActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launcherIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Set notifyBuilder's defaults
        notifyBuilder = new NotificationCompat.Builder(this)
                // TODO: tweak these
                .setContentTitle("Android Backup")
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pendingIntent);

        // Setting up the colors
        COLOR_PRIMARY = ContextCompat.getColor(this, R.color.colorPrimary);
        COLOR_SUCCESS = ContextCompat.getColor(this, R.color.colorSuccess);
        COLOR_ERROR = ContextCompat.getColor(this, R.color.colorError);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            DriveUtils.connect(this, this); // Connect the google API client
            manualStart = true;
            isRunning = true;
        } else {
            Toast.makeText(this, "The service is already running", Toast.LENGTH_SHORT).show();
        }
        return START_NOT_STICKY;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!isRunning) {
            DriveUtils.connect(this, this); // Connect the google API client
            manualStart = false;
            jobParams = params;
            isRunning = true;
            return true; // TRUE means the job's process is still in use (separate thread)
        } else {
            Toast.makeText(this, "The service is already running", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        // TODO: implement this (when job gets canceled I think)
        return true;
    }

    @Override
    public void onConnect(DriveUtils.ConnectResult result) {
        if (result.success) {
            (new Thread(this)).start();
        } else {
            showNotification(notifyBuilder
                    .setContentText("Error connecting to Google Drive")
                    .setSmallIcon(R.drawable.ic_error_white_24dp)
                    .setColor(COLOR_ERROR));
            endService();
        }
    }

    @Override
    public void run() {
        // Ensure app's config files are in place (create if necessary)
        if (!ensureFiles()) {
            showNotification(notifyBuilder
                    .setContentText("Could not set up app's configuration files")
                    .setSmallIcon(R.drawable.ic_error_white_24dp)
                    .setColor(COLOR_ERROR));
            endService();
            return;
        }

        showNotification(notifyBuilder
                .setContentText("Backup started")
                .setSmallIcon(R.drawable.ic_cloud_upload_white_48dp)
                .setColor(COLOR_PRIMARY)
                .setProgress(0, 0, true)
                .setOngoing(true));

        File backupFile = getConfigFile(this, "backup");
        try (
                BufferedReader backupReader = new BufferedReader(new FileReader(backupFile))
        ) {
            // Iterate over backup text
            JSAP jsap = JsapParser.getInstance();
            String line;
            for (int lineCounter = 1; (line = backupReader.readLine()) != null; lineCounter++) {
                // Skip blank lines or comments
                if (line.trim().equals("") || line.trim().charAt(0) == '#') {
                    continue;
                }

                JSAPResult result = jsap.parse(line);

                if (result.success()) {
                    String filepath = result.getString("filepath");
                    File file = getPublicFile(filepath);

                    if (!file.exists()) {
                        showNotification(notifyBuilder
                                .setContentText("Could not find file/directory specified on line "
                                        + lineCounter + " of 'backup'")
                                .setSmallIcon(R.drawable.ic_error_white_24dp)
                                .setColor(COLOR_ERROR)
                                .setProgress(0, 0, false)
                                .setOngoing(false));
                        break;
                    } else {
                        showNotification(notifyBuilder
                                .setContentText("Processing " + file.getName()));
                        processFile(file, null);
                    }
                } else {
                    showNotification(notifyBuilder
                            .setContentText("Could not parse line " + lineCounter + " of 'backup'")
                            .setSmallIcon(R.drawable.ic_error_white_24dp)
                            .setColor(COLOR_ERROR)
                            .setProgress(0, 0, false)
                            .setOngoing(false));
                    break;
                }
            }

            // If we got to the end of 'backup' without problems
            if (line == null) {
                showNotification(notifyBuilder
                        .setContentText("Successfully backed up")
                        .setSmallIcon(R.drawable.ic_cloud_done_white_24dp)
                        .setColor(COLOR_SUCCESS)
                        .setProgress(0, 0, false)
                        .setOngoing(false));
            }
        } catch (Exception e) {
            Log.i(TAG, e.getMessage());
            showNotification(notifyBuilder
                    .setContentText("Error: " + e.getMessage())
                    .setSmallIcon(R.drawable.ic_error_white_24dp)
                    .setColor(COLOR_ERROR)
                    .setProgress(0, 0, false)
                    .setOngoing(false));
        }

        // TODO: Possibly reschedule job
        endService();
    }

    // Ensures proper file structure is set up
    private boolean ensureFiles() {

        if (!isExternalStorageWritable())
            return false;

        File backupFile = getConfigFile(this, "backup");
        File helpFile = getConfigFile(this, "help");

        boolean res = true;
        try {
            // Setup Backup directory
            if (!backupFile.getParentFile().isDirectory())
                res = backupFile.getParentFile().mkdirs();

            // Setup backup text
            if (!backupFile.isFile() && res)
                res = backupFile.createNewFile();

            // Setup help text
            if (!helpFile.isFile() && res) {
                res = helpFile.createNewFile();
                BufferedWriter helpWriter = new BufferedWriter(new FileWriter(helpFile));
                // TODO: set output specific to screen width
                helpWriter.write("Usage: " + JsapParser.getInstance().getUsage() + "\n\n");
                helpWriter.write(JsapParser.getInstance().getHelp(50));
                helpWriter.close();
            }

        } catch (IOException | JSAPException e) {
            e.printStackTrace();
        }

        return res;
    }

    private void processFile(File file, DriveFolder parent) throws NoSuchAlgorithmException, IOException {
        if (file.isDirectory()) {
            DriveFolder driveFolder = DriveUtils.getFolder(file, parent);
            // TODO: show more in-depth progress on the notification
            for (File child : file.listFiles()) {
                processFile(child, driveFolder);
            }
        } else if (file.isFile()) {
            if (hasFileChanged(this, file)) {
                DriveUtils.uploadFile(file, parent);
            }
        } else {
            throw new FileNotFoundException(file.getName() + " does not exists");
        }
    }

    private void endService() {
        if (manualStart) {
            stopSelf();
        } else {
            jobFinished(jobParams, false);
        }
    }

    private void showNotification(NotificationCompat.Builder builder) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(0, builder.build());
    }
}